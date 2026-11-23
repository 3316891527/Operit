package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerminalObservationIndexTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun observed(value: String) = TerminalObservedFile(value, ChangeSnapshot(SnapshotKind.TEXT, value.length.toLong(), value.padEnd(64, '0')))
    private fun open(file: File) = TerminalObservationIndex(JdbcTerminalIndexDatabase(file))
    private fun seed(index: TerminalObservationIndex, vararg files: Pair<String, String>) = index.beginScan().use { scan ->
        for ((path, value) in files) scan.put(path, observed(value))
        scan.commit(completeInitialization = true)
    }

    @Test fun partialUpdatesPreserveUnrelatedPathsAndSurviveReopen() {
        val file = temporary.newFile()
        open(file).use { index ->
            seed(index, "a/file" to "a", "a-long/file" to "b", "sibling" to "c")
            index.beginScan(setOf("a")).use { scan -> scan.put("a/new", observed("d")); scan.commit() }
            assertEquals(SnapshotKind.ABSENT, index.snapshot("a/file").kind)
            assertEquals(observed("b"), index.file("a-long/file"))
        }
        open(file).use { index ->
            assertEquals(3, index.count())
            assertEquals(observed("d"), index.file("a/new"))
            assertEquals(observed("c"), index.file("sibling"))
        }
    }

    @Test fun literalSubtreeNamesCannotActAsSqlPatterns() = open(temporary.newFile()).use { index ->
        seed(index, "name%_'/文件😀/old" to "a", "nameXX/keep" to "b")
        index.beginScan(setOf("name%_'")).use { it.commit() }
        assertEquals(SnapshotKind.ABSENT, index.snapshot("name%_'/文件😀/old").kind)
        assertEquals(observed("b"), index.file("nameXX/keep"))
    }

    @Test fun startupEventsOverrideStaleFullScanIncludingDeletions() = open(temporary.newFile()).use { index ->
        seed(index, "changed" to "a", "deleted/file" to "b", "untouched" to "c")
        index.beginScan().use { full ->
            full.put("changed", observed("a")); full.put("deleted/file", observed("b")); full.put("untouched", observed("d"))
            index.acknowledge("changed", observed("e").snapshot, initializing = true)
            index.acknowledge("deleted", ChangeSnapshot(SnapshotKind.ABSENT), initializing = true)
            index.acknowledge("created/file", observed("f").snapshot, initializing = true)
            assertTrue(index.hasTouchedAncestor("deleted/file"))
            full.commit(completeInitialization = true)
        }
        assertEquals(observed("e").snapshot, index.snapshot("changed"))
        assertEquals(SnapshotKind.ABSENT, index.snapshot("deleted/file").kind)
        assertEquals(observed("f").snapshot, index.snapshot("created/file"))
        assertEquals(observed("d"), index.file("untouched"))
        assertFalse(index.hasTouchedAncestor("deleted/file"))
    }

    @Test fun failedReadPreservesEntireSubtreeAndUnknownDescendants() = open(temporary.newFile()).use { index ->
        seed(index, "closed/old" to "a", "other" to "b")
        index.beginScan(setOf("closed")).use { scan ->
            scan.unreadable("closed"); scan.preserveSubtree("closed"); scan.commit()
        }
        assertEquals(observed("a"), index.file("closed/old"))
        assertEquals(SnapshotKind.UNAVAILABLE, index.snapshot("closed/never-read").kind)
        index.beginScan(setOf("closed/child")).use { scan -> scan.commit() }
        assertEquals(SnapshotKind.UNAVAILABLE, index.snapshot("closed/child").kind)
        assertEquals(observed("b"), index.file("other"))
    }

    @Test fun abortDiscardsStagedRowsAndBlobLeasesWithoutChangingPublishedBaseline() = open(temporary.newFile()).use { index ->
        seed(index, "file" to "a")
        index.beginScan(setOf("file")).use { scan ->
            scan.put("file", observed("b")); scan.flush(); scan.pinBlob("in-flight")
            assertTrue(index.containsBlob(observed("b").snapshot.blob!!))
            assertTrue(index.containsBlob("in-flight"))
            assertEquals(observed("a"), index.file("file"))
        }
        assertFalse(index.containsBlob("in-flight"))
        assertFalse(index.containsBlob(observed("b").snapshot.blob!!))
        assertEquals(observed("a"), index.file("file"))
    }

    @Test fun interruptedProcessDiscardsUnpublishedGenerationsOnReopen() {
        val file = temporary.newFile()
        val database = JdbcTerminalIndexDatabase(file)
        val index = TerminalObservationIndex(database)
        seed(index, "file" to "a")
        val abandoned = index.beginScan()
        abandoned.put("stale", observed("b")); abandoned.flush(); abandoned.pinBlob("pending")
        database.close()
        open(file).use { reopened ->
            assertEquals(1, reopened.count())
            assertNull(reopened.file("stale"))
            assertFalse(reopened.containsBlob("pending"))
        }
    }

    @Test fun directoryRenameDiffsUseFixedPagesForRemovedAndAddedFiles() = open(temporary.newFile()).use { index ->
        index.beginScan().use { scan ->
            repeat(600) { scan.put("old/$it", observed("a")) }
            scan.put("unrelated", observed("b")); scan.commit()
        }
        index.beginScan(setOf("old", "new")).use { scan ->
            repeat(600) { scan.put("new/$it", observed("a")) }
            var last: String? = null
            var added = 0
            var deleted = 0
            while (true) {
                val page = scan.differences(last)
                assertTrue(page.size <= TerminalObservationIndex.PAGE_SIZE)
                if (page.isEmpty()) break
                for (difference in page) {
                    if (difference.beforeSnapshot.kind == SnapshotKind.ABSENT) added++
                    if (difference.afterSnapshot.kind == SnapshotKind.ABSENT) deleted++
                    assertNotEquals("unrelated", difference.path)
                }
                last = page.last().path
            }
            assertEquals(600, added); assertEquals(600, deleted)
            scan.commit()
        }
        assertEquals(601, index.count())
    }

    @Test fun unknownAncestorIsRetainedWhenOnlyOneChildIsRefreshed() = open(temporary.newFile()).use { index ->
        index.beginScan().use { it.unreadable("closed"); it.commit() }
        index.beginScan(setOf("closed/child")).use { scan ->
            scan.put("closed/child", observed("a"))
            val diff = scan.differences().single()
            assertEquals(SnapshotKind.UNAVAILABLE, diff.beforeSnapshot.kind)
            scan.commit()
        }
        assertEquals(SnapshotKind.UNAVAILABLE, index.snapshot("closed/other").kind)
    }

    @Test fun queryCacheHasAFixedLimitIndependentOfFileCount() = open(temporary.newFile()).use { index ->
        index.beginScan().use { scan -> repeat(4096) { scan.put("file/$it", observed("a")) }; scan.commit() }
        repeat(8192) { index.file("file/$it") }
        assertEquals(4096, index.count())
        assertEquals(TerminalObservationIndex.CACHE_SIZE, index.cachedEntries())
    }

    @Test fun failedCommitRollsBackPublishedFilesAndImportMarker() {
        val database = JdbcTerminalIndexDatabase(temporary.newFile())
        TerminalObservationIndex(database).use { index ->
            seed(index, "file" to "a")
            index.beginScan().use { scan ->
                scan.put("file", observed("b"))
                database.failOn = "INSERT OR REPLACE INTO metadata"
                try { scan.commit(imported = true); fail("Commit should fail") } catch (expected: IllegalStateException) { assertEquals("injected SQL failure", expected.message) }
                database.failOn = null
                assertEquals(observed("a"), index.file("file"))
                assertFalse(index.legacyImported())
            }
        }
    }
    @Test fun failedStageCleanupStillAllowsIndexResourcesToClose() {
        val file = temporary.newFile()
        val database = JdbcTerminalIndexDatabase(file)
        val index = TerminalObservationIndex(database)
        seed(index, "old" to "a")
        val scan = index.beginScan()
        scan.put("unpublished", observed("b")); scan.flush()
        database.failOn = "DELETE FROM files"
        try { scan.close(); fail("Stage cleanup should fail") }
        catch (expected: IllegalStateException) { assertEquals("injected SQL failure", expected.message) }
        database.failOn = null
        index.close()
        open(file).use { reopened ->
            assertEquals(observed("a"), reopened.file("old"))
            assertNull(reopened.file("unpublished"))
        }
    }
}

/** 使用真实 SQLite 驱动执行生产 SQL，故障注入只改变指定语句的执行结果。 */
internal class JdbcTerminalIndexDatabase(file: File) : TerminalIndexDatabase {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${file.path}")
    var failOn: String? = null

    init {
        execute("PRAGMA journal_mode=WAL")
        execute("PRAGMA cache_size=-2048")
        execute("PRAGMA synchronous=NORMAL")
    }

    override fun execute(sql: String, arguments: List<Any?>) {
        if (failOn?.let(sql::startsWith) == true) error("injected SQL failure")
        connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.execute()
        }
    }

    override fun query(sql: String, arguments: List<String>): TerminalIndexRows {
        val statement = connection.prepareStatement(sql)
        try {
            arguments.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            val result = statement.executeQuery()
            return object : TerminalIndexRows {
                override fun next(): Boolean = result.next()
                override fun string(column: Int): String? = result.getString(column + 1)
                override fun long(column: Int): Long = result.getLong(column + 1)
                override fun close() { result.close(); statement.close() }
            }
        } catch (error: Exception) { statement.close(); throw error }
    }

    override fun <T> transaction(block: () -> T): T {
        connection.autoCommit = false
        try { val value = block(); connection.commit(); return value }
        catch (error: Exception) { connection.rollback(); throw error }
        finally { connection.autoCommit = true }
    }

    override fun close() = connection.close()
}