package com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem
import java.io.IOException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceDeleteOperationTest {
    private fun item(name: String, environment: String? = null, directory: Boolean = false) =
        FileBrowserClipboardItem("/selected/$name", environment, cut = false, isDirectory = directory)

    private fun flatOperation(deleteItem: suspend (FileBrowserClipboardItem) -> Unit) = WorkspaceDeleteOperation(
        describe = { it }, children = { emptyList() }, deleteItem = deleteItem,
    )

    @Test fun emptyDirectoryHasOneKnownEntryAndCompletesOnlyAfterDeletion() = runBlocking {
        val target = item("folder", "linux", directory = true)
        lateinit var operation: WorkspaceDeleteOperation
        operation = flatOperation {
            assertEquals(target, it)
            val progress = checkNotNull(operation.progress.value)
            assertEquals(target, progress.target)
            assertEquals(0, progress.completedItems)
            assertEquals(1, progress.totalItems)
            assertEquals(0f, checkNotNull(progress.fraction), 0f)
        }
        var notified = false
        operation.run(listOf(target)) {
            notified = true
            val progress = checkNotNull(operation.progress.value)
            assertEquals(1, progress.completedItems)
            assertEquals(1f, checkNotNull(progress.fraction), 0f)
        }
        assertTrue(notified)
        assertNull(operation.progress.value)
    }

    @Test fun batchProgressCountsOnlySuccessfulSelectedItemsAcrossEnvironments() = runBlocking {
        val targets = listOf(item("local"), item("ubuntu", "linux", true), item("document", "repo:docs"))
        val before = mutableListOf<WorkspaceDeleteProgress>()
        val after = mutableListOf<WorkspaceDeleteProgress>()
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        lateinit var operation: WorkspaceDeleteOperation
        operation = flatOperation { before += checkNotNull(operation.progress.value) }
        operation.run(targets) {
            deleted += it
            after += checkNotNull(operation.progress.value)
        }
        assertEquals(targets, before.map { it.target })
        assertEquals(listOf(0, 1, 2), before.map { it.completedItems })
        assertEquals(listOf(1, 2, 3), after.map { it.completedItems })
        assertEquals(targets, deleted)
        assertEquals(1f / 3f, checkNotNull(before[1].fraction), 0f)
        assertTrue(before.all { checkNotNull(it.fraction) < 1f })
        assertEquals(1f, checkNotNull(after.last().fraction), 0f)
        assertNull(operation.progress.value)
    }

    @Test fun failureIsNotCountedAndRemainingItemsAreNotStarted() = runBlocking {
        val targets = listOf(item("first"), item("failed"), item("remaining"))
        val attempted = mutableListOf<FileBrowserClipboardItem>()
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        val operation = flatOperation {
            attempted += it
            if (it == targets[1]) throw IOException("删除失败")
        }
        try {
            operation.run(targets) { deleted += it }
            fail("应传播工具删除错误")
        } catch (e: IOException) {
            assertEquals("删除失败", e.message)
        }
        assertEquals(targets.take(2), attempted)
        assertEquals(targets.take(1), deleted)
        assertNull(operation.progress.value)
    }

    @Test fun cancellingWhileCurrentToolRunsStopsTheNextItemWithoutUndoingSuccess() = runBlocking {
        val targets = listOf(item("current"), item("remaining"))
        val attempted = mutableListOf<FileBrowserClipboardItem>()
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        val operation = flatOperation {
            attempted += it
            // 模拟同步工具收到取消后仍返回成功：已删除项不能被视作撤销。
            currentCoroutineContext().cancel()
        }
        val task = launch { operation.run(targets) { deleted += it } }
        task.join()
        assertTrue(task.isCancelled)
        assertEquals(targets.take(1), attempted)
        assertEquals(targets.take(1), deleted)
        assertNull(operation.progress.value)
    }

    @Test fun alreadyCancelledOperationNeverStartsDeletion() = runBlocking {
        var invoked = false
        val operation = flatOperation { invoked = true }
        val task = launch {
            currentCoroutineContext().cancel()
            operation.run(listOf(item("not-started")))
        }
        task.join()
        assertTrue(task.isCancelled)
        assertFalse(invoked)
        assertNull(operation.progress.value)
    }

    @Test fun emptySelectionDoesNotOpenProgressOrInvokeTools() = runBlocking {
        var invoked = false
        var notified = false
        val operation = flatOperation { invoked = true }
        operation.run(emptyList()) { notified = true }
        assertFalse(invoked)
        assertFalse(notified)
        assertNull(operation.progress.value)
    }

    @Test fun nextOperationStartsWithFreshProgressAfterFailure() = runBlocking {
        val failed = item("failed")
        val next = item("next")
        lateinit var operation: WorkspaceDeleteOperation
        operation = flatOperation {
            if (it == failed) throw IOException("删除失败")
            assertEquals(next, checkNotNull(operation.progress.value).target)
            assertEquals(0, checkNotNull(operation.progress.value).completedItems)
            assertEquals(1, checkNotNull(operation.progress.value).totalItems)
        }
        try {
            operation.run(listOf(failed, item("unprocessed")))
            fail("应传播工具删除错误")
        } catch (e: IOException) {
            assertNull(operation.progress.value)
        }
        operation.run(listOf(next))
        assertNull(operation.progress.value)
    }

    @Test fun directoryDeletionCountsChildrenAndRemovesTheParentLast() = runBlocking {
        val parent = item("folder", directory = true)
        val sub = parent.copy(path = parent.path + "/sub")
        val first = parent.copy(path = parent.path + "/first.txt", isDirectory = false)
        val second = parent.copy(path = sub.path + "/second.txt", isDirectory = false)
        val attempted = mutableListOf<FileBrowserClipboardItem>()
        val notified = mutableListOf<FileBrowserClipboardItem>()
        val before = mutableListOf<WorkspaceDeleteProgress>()
        lateinit var operation: WorkspaceDeleteOperation
        operation = WorkspaceDeleteOperation(describe = { it }, children = {
            when (it.path) { parent.path -> listOf(first, sub); sub.path -> listOf(second); else -> emptyList() }
        }) { attempted += it; before += checkNotNull(operation.progress.value) }
        operation.run(listOf(parent)) {
            notified += it
            assertEquals(4, checkNotNull(operation.progress.value).completedItems)
        }
        assertEquals(listOf(first, second, sub, parent), attempted)
        assertEquals(listOf(parent), notified)
        assertEquals(listOf(0, 1, 2, 3), before.map { it.completedItems })
        assertTrue(before.all { it.totalItems == 4 && !it.scanning })
    }

    @Test fun overlappingSelectionsDoNotDeleteTheSameChildTwice() = runBlocking {
        val parent = item("folder", directory = true)
        val child = parent.copy(path = parent.path + "/file", isDirectory = false)
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        val notified = mutableListOf<FileBrowserClipboardItem>()
        val operation = WorkspaceDeleteOperation(describe = { it }, children = { listOf(child) }) { deleted += it }
        operation.run(listOf(parent, child, parent)) { notified += it }
        assertEquals(listOf(child, parent), deleted)
        assertEquals(listOf(parent), notified)
    }

    @Test fun cancellingDuringEnumerationNeverStartsDeletion() = runBlocking {
        var deleted = false
        val operation = WorkspaceDeleteOperation(describe = { it }, children = {
            currentCoroutineContext().cancel()
            emptyList()
        }) { deleted = true }
        val task = launch { operation.run(listOf(item("folder", directory = true))) }
        task.join()
        assertTrue(task.isCancelled)
        assertFalse(deleted)
        assertNull(operation.progress.value)
    }

    @Test fun enumerationFailurePreservesEveryPlannedFile() = runBlocking {
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        val operation = WorkspaceDeleteOperation(describe = { it }, children = { throw IOException("无法列出目录") }) { deleted += it }
        try {
            operation.run(listOf(item("file"), item("folder", directory = true)))
            fail("应传播扫描错误")
        } catch (_: IOException) { assertTrue(deleted.isEmpty()) }
        assertNull(operation.progress.value)
    }

    @Test fun childFailureDoesNotNotifyThatTheWholeDirectoryWasDeleted() = runBlocking {
        val parent = item("folder", directory = true)
        val child = parent.copy(path = parent.path + "/locked", isDirectory = false)
        val attempted = mutableListOf<FileBrowserClipboardItem>()
        var notified = false
        val operation = WorkspaceDeleteOperation(describe = { it }, children = { listOf(child) }) {
            attempted += it
            throw IOException("删除失败")
        }
        try { operation.run(listOf(parent)) { notified = true }; fail("应传播子项删除错误") }
        catch (_: IOException) { assertFalse(notified) }
        assertEquals(listOf(child), attempted)
    }

    @Test fun symbolicLinkMetadataPreventsTraversalIntoItsTarget() = runBlocking {
        var traversed = false
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        val operation = WorkspaceDeleteOperation(describe = { it.copy(isDirectory = false) }, children = {
            traversed = true
            emptyList()
        }) { deleted += it }
        operation.run(listOf(item("link", directory = true)))
        assertFalse(traversed)
        assertEquals(1, deleted.size)
        assertFalse(deleted.single().isDirectory)
    }

    @Test fun childrenOutsideTheSelectedDirectoryAreRejectedBeforeDeletingAnything() = runBlocking {
        var deleted = false
        val operation = WorkspaceDeleteOperation(describe = { it }, children = { listOf(item("outside")) }) { deleted = true }
        try { operation.run(listOf(item("folder", directory = true))); fail("应拒绝越界的子项") }
        catch (_: IllegalArgumentException) { assertFalse(deleted) }
    }
}
