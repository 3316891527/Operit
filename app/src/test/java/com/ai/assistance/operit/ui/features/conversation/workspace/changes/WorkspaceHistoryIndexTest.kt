package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceHistoryIndexTest {
    @get:Rule val temporary = TemporaryFolder()
    private val operation = UUID.randomUUID().toString()
    private fun record(index: Int, path: String = "/sdcard/project/$index.txt", time: Long = index.toLong(), chat: String = "chat") =
        FileChangeRecord("record-$index", operation, chat, time, "edit_file", ChangeLocation(path, "android", "android"),
            ChangeSnapshot(SnapshotKind.ABSENT), ChangeSnapshot(SnapshotKind.TEXT, blob = "a".repeat(64)), additions = 1, deletions = 0,
            executionId = "execution", sourceKind = "terminal", subagentExecutionId = "subagent")
    private fun withIndex(block: (JdbcTerminalIndexDatabase, WorkspaceHistoryIndex) -> Unit) {
        JdbcTerminalIndexDatabase(File(temporary.root, "history.sqlite")).use { db ->
            SnapshotPackIndex(db)
            block(db, WorkspaceHistoryIndex(db, "/storage/emulated/0"))
        }
    }

    @Test fun latestOutputIsChatScopedAndUsesTheSameTieOrderAsPagedHistory() = withIndex { db, history ->
        assertNull(history.latestRecord("chat"))
        val first = record(1, time = 100)
        val last = record(2, time = 100)
        db.transaction {
            history.put(first)
            history.put(last)
            history.put(record(3, time = 200, chat = "other"))
        }
        assertEquals(last, history.latestRecord("chat"))
        history.delete(last.id)
        assertEquals(first, history.latestRecord("chat"))
        history.delete(first.id)
        assertNull(history.latestRecord("chat"))
    }

    @Test fun fileAndRecordPagesAreBoundedOrderedAndDoNotLoseEqualTimestamps() = withIndex { db, history ->
        db.transaction { repeat(350) { history.put(record(it, path = "/sdcard/project/${it.toString().padStart(4, '0')}.txt", time = 100)) } }
        val query = WorkspaceHistoryQuery(chatId = "chat")
        assertEquals(350, history.summaryCount(query))
        val files = (0..2).flatMap { page -> history.summaries(query, page * 128).also { assertTrue(it.size <= 128) } }
        assertEquals(350, files.map { it.latest.id }.distinct().size)
        assertEquals(files.map { it.latest.location.path }.sorted(), files.map { it.latest.location.path })
        val records = (0..2).flatMap { page -> history.recordsPage(query, page * 128).also { assertTrue(it.size <= 128) } }
        assertEquals(350, records.map { it.id }.distinct().size)
        assertTrue(history.recordsPage(query, 384).isEmpty())
    }

    @Test fun aliasesGroupTogetherButRemoteBackendsAndPathPrefixesRemainSeparate() = withIndex { db, history ->
        val first = record(1, "/sdcard/project/a.txt")
        val last = record(2, "/storage/emulated/0/project/a.txt").copy(location = ChangeLocation("/storage/emulated/0/project/a.txt", "linux", "linux:local"))
        db.transaction {
            listOf(first, last, record(3, "/sdcard/project-other/a.txt"), record(4, "/sdcard/project/a.txt").copy(location = first.location.copy(backend = "linux:ssh:remote"))).forEach { history.put(it) }
        }
        val query = WorkspaceHistoryQuery(directory = ChangeLocation("/storage/self/primary/project", "android", "android"))
        val summary = history.summaries(query, 0).single()
        assertEquals(first, summary.first)
        assertEquals(last, summary.latest)
        assertEquals(2, summary.count)
        assertEquals(2, history.count(WorkspaceHistoryQuery(file = first.location)))
        assertEquals(3, history.summaryCount(WorkspaceHistoryQuery()))
    }

    @Test fun linuxParentIncludesItsLogicalMountedPathsAndRootDoesNotMatchRemote() = withIndex { db, history ->
        val mounted = record(1).copy(location = ChangeLocation("/sdcard/project/1.txt", "linux", "linux:local"))
        db.transaction { history.put(mounted); history.put(record(2).copy(location = mounted.location.copy(backend = "linux:ssh:a"))) }
        val query = WorkspaceHistoryQuery(directory = ChangeLocation("/", "linux", "linux:local"))
        assertEquals(listOf(mounted), history.recordsPage(query))
    }

    @Test fun literalSearchEscapesWildcardsAndPreservesWhitespace() = withIndex { db, history ->
        db.transaction { history.put(record(1, "/sdcard/project/ a%_b.txt")); history.put(record(2, "/sdcard/project/axxb.txt")) }
        assertEquals("record-1", history.summaries(WorkspaceHistoryQuery(search = "a%_b"), 0).single().latest.id)
    }

    @Test fun rangeSelectionIsInclusiveAtStartAndExclusiveAtNextTurn() = withIndex { db, history ->
        db.transaction { listOf(record(1, time = 99), record(2, time = 100), record(3, time = 199), record(4, time = 200), record(5, time = 300, chat = "other")).forEach { history.put(it) } }
        assertEquals(200L, history.latestTimestamp("chat"))
        assertEquals(listOf("record-3", "record-2"), history.recordsPage(WorkspaceHistoryQuery(chatId = "chat", from = 100, until = 200)).map { it.id })
    }

    @Test fun removalBoundaryDoesNotIncludeRowsCreatedAfterConfirmation() = withIndex { db, history ->
        val location = record(0).location
        db.transaction { repeat(260) { history.put(record(it).copy(location = location)) } }
        val query = WorkspaceHistoryQuery(chatId = "chat", file = location)
        val bounded = query.copy(through = history.latestCursor(query))
        history.put(record(400).copy(location = location))
        var removed = 0
        while (true) {
            val page = history.recordsPage(bounded)
            if (page.isEmpty()) break
            db.transaction { page.forEach { history.delete(it.id) } }
            removed += page.size
        }
        assertEquals(260, removed)
        assertEquals(listOf("record-400"), history.recordsPage(query).map { it.id })
    }

    @Test fun referencesIncludeOtherChatsAndBothSnapshotSides() = withIndex { _, history ->
        val first = record(1)
        val second = record(2, chat = "other").copy(before = first.after, after = ChangeSnapshot(SnapshotKind.ABSENT))
        history.put(first); history.put(second); history.delete(first.id)
        assertEquals(setOf("a".repeat(64)), history.referenced(setOf("a".repeat(64), "b".repeat(64))))
        history.delete(second.id)
        assertTrue(history.referenced(setOf("a".repeat(64))).isEmpty())
    }

    @Test fun clearedVolumesAreExcludedFromSummariesWithoutReadingBodies() = withIndex { db, history ->
        history.put(record(1))
        db.execute("INSERT INTO snapshot_objects(hash,volume,offset,length,live) VALUES(?,?,?,?,0)", listOf("a".repeat(64), "cleared", 16, 1))
        assertEquals(0, history.summaryCount(WorkspaceHistoryQuery()))
        assertTrue(history.summaries(WorkspaceHistoryQuery(), 0).isEmpty())
        assertEquals(1, history.count(WorkspaceHistoryQuery()))
    }

    @Test fun trackedPagesUseTheLatestStateAndReturnNoEntireExecutionList() = withIndex { db, history ->
        db.transaction { repeat(260) { history.put(record(it)) } }
        history.put(record(999, path = record(1).location.path).copy(after = ChangeSnapshot(SnapshotKind.ABSENT)))
        val first = history.trackedPage("chat", 0)
        assertEquals(128, first.size)
        val all = first + history.trackedPage("chat", 128) + history.trackedPage("chat", 256)
        assertEquals(259, all.size)
        assertFalse(all.contains(record(1).location))
    }

    @Test fun legacyImportPreservesAllFieldsAndNeverReimportsDeletedRecordsAfterPostCommitInterruption() = withIndex { _, history ->
        val directory = temporary.newFolder("operations")
        val file = File(directory, "$operation.json")
        val original = listOf(record(1), record(2))
        file.writeText(Json { encodeDefaults = true }.encodeToString(original))
        history.importLegacy(directory)
        assertFalse(file.exists())
        assertEquals(original, history.recordsPage(WorkspaceHistoryQuery(), ascending = true))
        history.delete(original.first().id)
        file.writeText(Json.encodeToString(original))
        history.importLegacy(directory)
        assertEquals(listOf(original.last()), history.recordsPage(WorkspaceHistoryQuery()))
        assertFalse(file.exists())
    }

    @Test fun malformedLegacyArrayRollsBackAndRetainsSource() = withIndex { _, history ->
        val directory = temporary.newFolder("malformed")
        val file = File(directory, "$operation.json")
        file.writeText("[" + Json.encodeToString(record(1)) + ",{")
        assertThrows(Exception::class.java) { history.importLegacy(directory) }
        assertEquals(0, history.count(WorkspaceHistoryQuery()))
        assertTrue(file.exists())
    }

    @Test fun cancelledLegacyImportRollsBackAndCanResume() = withIndex { _, history ->
        val directory = temporary.newFolder("cancelled")
        val file = File(directory, "$operation.json")
        file.writeText(Json.encodeToString((0..140).map { record(it) }))
        var checks = 0
        assertThrows(CancellationException::class.java) { history.importLegacy(directory) { if (++checks == 100) throw CancellationException() } }
        assertEquals(0, history.count(WorkspaceHistoryQuery()))
        assertTrue(file.exists())
        history.importLegacy(directory)
        assertEquals(141, history.count(WorkspaceHistoryQuery()))
    }

    @Test fun sqlFailureDuringImportRetainsSourceWithoutPartialRows() = withIndex { db, history ->
        val directory = temporary.newFolder("failure")
        val file = File(directory, "$operation.json")
        file.writeText(Json.encodeToString(listOf(record(1), record(2))))
        db.failOn = "INSERT INTO operation_imports"
        assertThrows(IllegalStateException::class.java) { history.importLegacy(directory) }
        db.failOn = null
        assertEquals(0, history.count(WorkspaceHistoryQuery()))
        assertTrue(file.exists())
        history.importLegacy(directory)
        assertEquals(2, history.count(WorkspaceHistoryQuery()))
    }
    @Test fun searchUsesRelativePathsAndUnicodeCaseFolding() = withIndex { db, history ->
        db.transaction { history.put(record(1, "/sdcard/project/Ä.TXT")); history.put(record(2, "/sdcard/project/other.txt")) }
        val query = WorkspaceHistoryQuery(directory = ChangeLocation("/sdcard/project", "android", "android"))
        assertEquals("record-1", history.summaries(query.copy(search = "ä.txt"), 0).single().latest.id)
        assertTrue(history.summaries(query.copy(search = "project"), 0).isEmpty())
    }

}