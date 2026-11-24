package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceFileMutationLocksTest {
    private fun path(value: String) = ChangeLocation(value, "android", "android")

    @Test fun slowDirectoryOperationDoesNotBlockAnUnrelatedFile() = runBlocking {
        val locks = WorkspaceFileMutationLocks()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch { locks.withPaths(listOf(path("/project/archive"))) { entered.complete(Unit); release.await() } }
        entered.await()
        try {
            withTimeout(1_000) { locks.withPaths(listOf(path("/project/notes.txt"))) { assertFalse(release.isCompleted) } }
            val overlapping = async { locks.withPaths(listOf(path("/project/archive/entry"))) { true } }
            yield()
            assertFalse(overlapping.isCompleted)
            release.complete(Unit)
            assertTrue(withTimeout(1_000) { overlapping.await() })
            first.join()
            assertTrue(locks.activeLocations().isEmpty())
        } finally { release.complete(Unit); first.cancelAndJoin() }
    }

    @Test fun cancellingAWaiterOrFailingAWriterDoesNotLeakAPathLock() = runBlocking {
        val locks = WorkspaceFileMutationLocks()
        val owner = locks.acquire(listOf(path("/project")))
        val waiter = launch { locks.withPaths(listOf(path("/project/file"))) { fail("等待者已经取消") } }
        yield(); waiter.cancelAndJoin(); owner.close()
        try { locks.withPaths(listOf(path("/project/file"))) { error("模拟写入失败") } }
        catch (expected: IllegalStateException) { assertEquals("模拟写入失败", expected.message) }
        withTimeout(1_000) { locks.withPaths(listOf(path("/project/file"))) { assertEquals(1, locks.activeLocations().size) } }
        assertTrue(locks.activeLocations().isEmpty())
    }
    @Test fun logicalAliasesShareAPhysicalLockAndKeepObservationPaths() = runBlocking {
        val locks = WorkspaceFileMutationLocks()
        val physical = path("/real/file")
        val logical = path("/project/link")
        val owner = locks.acquire(listOf(logical), listOf(physical))
        val waiter = async { locks.acquire(listOf(path("/project/real")), listOf(physical)).use { true } }
        try {
            yield()
            assertFalse(waiter.isCompleted)
            assertEquals(listOf(logical), locks.activeLocations())
            owner.close()
            assertTrue(withTimeout(1_000) { waiter.await() })
        } finally { owner.close(); waiter.cancelAndJoin() }
        assertTrue(locks.activeLocations().isEmpty())
    }
}
