package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceDirectoryNavigationTest {
    private val current = WorkspaceLocation("/visible/child")
    private fun entry(location: WorkspaceLocation) = WorkspaceSearchEntry(
        workspaceChild(location, "file.txt"), "file.txt", false, 4L, 100L)

    @Test fun unreadableParentKeepsTheCurrentDirectoryAndItsEntries() = runBlocking<Unit> {
        val navigation = WorkspaceDirectoryNavigation(current) { location ->
            if (location != current) throw SecurityException("没有读取权限")
            listOf(entry(location))
        }
        assertTrue(navigation.open(current))
        val before = navigation.snapshot.value
        assertThrows(SecurityException::class.java) { runBlocking { navigation.open(checkNotNull(workspaceParent(current))) } }
        assertEquals(before, navigation.snapshot.value)
        assertTrue(navigation.snapshot.value.readable)
    }
    @Test fun failedInitialReadDoesNotEnablePickingAnUnreadableDirectory() {
        val navigation = WorkspaceDirectoryNavigation(current) { throw SecurityException("没有读取权限") }
        assertThrows(SecurityException::class.java) { runBlocking { navigation.open(current) } }
        assertFalse(navigation.snapshot.value.readable)
        assertTrue(navigation.snapshot.value.entries.isEmpty())
    }
    @Test fun aSlowerPreviousRequestCannotReplaceTheLatestDirectory() = runBlocking<Unit> {
        val release = CompletableDeferred<Unit>()
        val slow = WorkspaceLocation("/slow")
        val latest = WorkspaceLocation("/latest")
        val navigation = WorkspaceDirectoryNavigation(current) { location ->
            if (location == slow) release.await()
            listOf(entry(location))
        }
        navigation.open(current)
        val older = async(start = CoroutineStart.UNDISPATCHED) { navigation.open(slow) }
        assertTrue(navigation.open(latest))
        release.complete(Unit)
        assertFalse(older.await())
        assertEquals(latest, navigation.snapshot.value.location)
    }
    @Test fun cancellationAfterAReadFinishesCannotCommitItsDirectory() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val next = WorkspaceLocation("/next")
        val navigation = WorkspaceDirectoryNavigation(current) { location ->
            if (location == next) {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
            }
            listOf(entry(location))
        }
        navigation.open(current)
        val before = navigation.snapshot.value
        val pending = async { navigation.open(next) }
        entered.await()
        pending.cancel()
        release.complete(Unit)
        pending.join()
        assertTrue(pending.isCancelled)
        assertEquals(before, navigation.snapshot.value)
    }
}
