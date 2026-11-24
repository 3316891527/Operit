package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceHistoryStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root get() = File(temporary.root, "history")
    private fun open(): WorkspaceChangeStore = WorkspaceChangeStore(root, { file, durable ->
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        JdbcTerminalIndexDatabase(file).also { if (durable) it.execute("PRAGMA synchronous=FULL") }
    }, { directory -> FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } }, "/storage/emulated/0")
    private fun record(store: WorkspaceChangeStore, number: Int, before: String = "", after: String = "value-$number", time: Long = number.toLong()) = FileChangeRecord(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "chat", time, "edit_file", ChangeLocation("/sdcard/project/$number.txt", "android", "android"),
        if (before.isEmpty()) ChangeSnapshot(SnapshotKind.ABSENT) else ChangeSnapshot(SnapshotKind.TEXT, blob = store.putBlob(before.toByteArray())),
        ChangeSnapshot(SnapshotKind.TEXT, blob = store.putBlob(after.toByteArray())), executionId = "execute", sourceKind = "terminal", subagentExecutionId = "subagent")
    private fun counts(store: WorkspaceChangeStore, record: FileChangeRecord): FileChangeRecord {
        val content = store.content(record)
        val diff = WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after))
        return record.copy(additions = diff.additions, deletions = diff.deletions)
    }

    @Test fun mergePreservesIdentityEarliestBeforeAndLatestAfterWithoutLosingOtherBatchFiles() = runBlocking {
        val store = open()
        try {
            val operation = UUID.randomUUID().toString()
            val first = record(store, 1, before = "original", after = "middle", time = 100)
            store.mergeOperation(operation, listOf(first)) { counts(store, it) }
            val second = record(store, 1, before = "middle", after = "final\nextra", time = 200)
            val unrelated = record(store, 2)
            store.mergeOperation(operation, listOf(second, unrelated)) { counts(store, it) }
            val saved = store.operationRecords(operation)
            assertEquals(2, saved.size)
            val merged = saved.single { it.location == first.location }
            assertEquals(first.id, merged.id)
            assertEquals(100L, merged.timestamp)
            assertEquals(first.before, merged.before)
            assertEquals(second.after, merged.after)
            assertEquals("subagent", merged.subagentExecutionId)
            assertEquals("original", store.content(merged).before)
            assertEquals("final\nextra", store.content(merged).after)
            val reverted = record(store, 1, after = "original", time = 300)
            store.mergeOperation(operation, listOf(reverted)) { counts(store, it) }
            assertEquals(listOf(unrelated.id), store.operationRecords(operation).map { it.id })
        } finally { store.close() }
    }

    @Test fun historyPagesHoldOnlyEndpointsAndSelectedRecordHistoryIsIndependentlyPaged() = runBlocking {
        val store = open()
        try {
            repeat(3) { batch ->
                store.save((0 until 128).map { number -> counts(store, record(store, number, time = (batch * 128 + number).toLong())) })
            }
            val page = store.historyPage(WorkspaceHistoryQuery(chatId = "chat"), 0)
            assertEquals(128, page.total)
            assertEquals(128, page.files.size)
            assertTrue(page.files.all { it.records.size == 2 && it.historyCount == 3 })
            val file = page.files.first()
            val details = store.recordPage(checkNotNull(file.historyQuery), 0)
            assertEquals(3, details.total)
            assertEquals(3, details.records.size)
            assertEquals(file.records.first().id, details.records.first().id)
            assertEquals(file.records.last().id, details.records.last().id)
            assertTrue(page.files.all { !it.statisticsAvailable })
        } finally { store.close() }
    }

    @Test fun latestTurnIsChosenFromTheWholeChatBeforeWorkspaceFiltering() = runBlocking {
        val store = open()
        try {
            store.save(listOf(record(store, 1, time = 150)))
            store.save(listOf(record(store, 2, time = 250).copy(location = ChangeLocation("/outside/task.txt", "android", "android"))))
            val query = store.historyQuery("chat", ChangeLocation("/sdcard/project", "android", "android"), true, listOf(100, 200, 300), "")
            assertEquals(200L, checkNotNull(query).from)
            assertEquals(300L, query.until)
            assertTrue(store.historyPage(query, 0).files.isEmpty())
        } finally { store.close() }
    }

    @Test fun clearingAllPacksHidesHistoryAndTheSameContentCanBeRecordedAgain() = runBlocking {
        val store = open()
        try {
            val first = counts(store, record(store, 1))
            store.save(listOf(first))
            assertEquals(1, store.historyPage(WorkspaceHistoryQuery(chatId = "chat"), 0).files.size)
            assertTrue(File(root, "objects").deleteRecursively())
            assertTrue(store.historyPage(WorkspaceHistoryQuery(chatId = "chat"), 0).files.isEmpty())
            val next = counts(store, record(store, 1, time = 200))
            store.save(listOf(next))
            assertEquals(first.after.blob, next.after.blob)
            assertEquals(1, store.historyPage(WorkspaceHistoryQuery(chatId = "chat"), 0).files.size)
        } finally { store.close() }
    }

    @Test fun pagedRemovalProtectsOtherChatAndWorkspaceBaselineReferences() = runBlocking {
        val store = open()
        try {
            val first = record(store, 1)
            val second = first.copy(id = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString(), chatId = "other")
            store.save(listOf(first, second))
            val index = store.workspaceIndex("b".repeat(64))
            index.beginScan().use { scan -> scan.put("1.txt", TerminalObservedFile("", first.after)); scan.commit(completeInitialization = true) }
            val removal = store.prepareFileRemoval(first.location, "chat")
            assertEquals(1, removal.count)
            assertEquals(1, store.removeFileRecords(removal))
            assertEquals(1, store.records().size)
            assertEquals("value-1", store.content(second).after)
            store.removeRecords(setOf(second.id))
            assertTrue(store.records().isEmpty())
            assertEquals("value-1", store.content(second).after)
        } finally { store.close() }
    }

    @Test fun removalConfirmationDoesNotDeleteNewerOperations() = runBlocking {
        val store = open()
        try {
            val first = record(store, 1, time = 100)
            store.save(listOf(first))
            val removal = store.prepareFileRemoval(first.location, "chat")
            val second = record(store, 1, time = 200)
            store.save(listOf(second))
            assertEquals(1, store.removeFileRecords(removal))
            assertEquals(listOf(second.id), store.records().map { it.id })
        } finally { store.close() }
    }
    @Test fun anEmptyRemovalConfirmationCannotDeleteLaterRecords() = runBlocking {
        val store = open()
        try {
            val location = ChangeLocation("/sdcard/project/1.txt", "android", "android")
            val removal = store.prepareFileRemoval(location, "chat")
            assertEquals(0, removal.count)
            store.save(listOf(record(store, 1)))
            assertEquals(0, store.removeFileRecords(removal))
            assertEquals(1, store.records().size)
        } finally { store.close() }
    }

    @Test fun selectedRecordsRemainWithinTheCurrentTurnQuery() = runBlocking {
        val store = open()
        try {
            val first = record(store, 1, time = 100)
            store.save(listOf(first))
            val query = WorkspaceHistoryQuery(chatId = "chat", from = 200)
            assertNull(store.historyRecord(first.id, query))
            assertEquals(first, store.historyRecord(first.id))
        } finally { store.close() }
    }

    @Test fun failedContentBatchCannotPublishItsPreviouslyBufferedBaselineRows() = runBlocking {
        var content: JdbcTerminalIndexDatabase? = null
        val store = WorkspaceChangeStore(root, { file, durable ->
            check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
            JdbcTerminalIndexDatabase(file).also { if (durable) content = it }
        }, { directory -> FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } }, "/storage/emulated/0")
        try {
            store.putBlob("retained".toByteArray())
            store.flushBlobs()
            val index = store.terminalIndex("c".repeat(64))
            index.beginScan().use { scan ->
                repeat(127) { number ->
                    val hash = store.putBlob(("file-" + number).toByteArray(), scan)
                    scan.put(number.toString(), TerminalObservedFile("", ChangeSnapshot(SnapshotKind.TEXT, blob = hash)))
                }
                checkNotNull(content).failOn = "INSERT OR REPLACE INTO snapshot_objects"
                assertThrows(IllegalStateException::class.java) { store.putBlob("trigger-failure".toByteArray(), scan) }
                checkNotNull(content).failOn = null
                assertThrows(IllegalStateException::class.java) { scan.commit(completeInitialization = true) }
                assertEquals(0L, index.count())
            }
            assertEquals(0L, index.count())
        } finally { content?.failOn = null; store.close() }
    }

}