package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyTerminalBaselineTest {
    @get:Rule val temporary = TemporaryFolder()
    private val hash = "a".repeat(64)
    private fun baseline(path: String = "目录/file") = """{"files":{"$path":{"stamp":"time:3:key","snapshot":{"kind":"TEXT","size":3,"blob":"$hash","detail":null}}},"unreadable":["closed"]}"""

    @Test fun publishedJsonStreamsFilesAndUnreadablePaths() {
        val source = temporary.newFile().apply { writeText(baseline(), Charsets.UTF_8) }
        val files = mutableMapOf<String, TerminalObservedFile>()
        val unreadable = mutableListOf<String>()
        readLegacyTerminalBaseline(source, { path, file -> files[path] = file }, unreadable::add)
        assertEquals(TerminalObservedFile("time:3:key", ChangeSnapshot(SnapshotKind.TEXT, 3, hash)), files["目录/file"])
        assertEquals(listOf("closed"), unreadable)
    }

    @Test fun importCommitsRowsAndMarkerTogether() {
        val source = temporary.newFile().apply { writeText(baseline()) }
        TerminalObservationIndex(JdbcTerminalIndexDatabase(temporary.newFile())).use { index ->
            index.beginScan().use { scan -> readLegacyTerminalBaseline(source, scan::put, scan::unreadable); scan.commit(imported = true) }
            assertTrue(index.legacyImported())
            assertEquals(hash, index.file("目录/file")!!.snapshot.blob)
            assertEquals(SnapshotKind.UNAVAILABLE, index.snapshot("closed/unknown").kind)
        }
    }

    @Test fun malformedImportPreservesSourceAndPublishedState() {
        val source = temporary.newFile().apply { writeText(baseline().dropLast(1)) }
        TerminalObservationIndex(JdbcTerminalIndexDatabase(temporary.newFile())).use { index ->
            index.acknowledge("old", ChangeSnapshot(SnapshotKind.ABSENT), initializing = false)
            try {
                index.beginScan().use { scan -> readLegacyTerminalBaseline(source, scan::put, scan::unreadable); scan.commit(imported = true) }
                fail("Malformed source should fail")
            } catch (expected: java.io.IOException) { assertTrue(source.exists()) }
            assertEquals(0, index.count())
            assertFalse(index.legacyImported())
        }
    }

    @Test fun cancellationDiscardsPartiallyImportedRows() {
        val source = temporary.newFile().apply { writeText(baseline()) }
        TerminalObservationIndex(JdbcTerminalIndexDatabase(temporary.newFile())).use { index ->
            try {
                index.beginScan().use { scan ->
                    readLegacyTerminalBaseline(source, scan::put, scan::unreadable) { throw CancellationException("cancel import") }
                    scan.commit(imported = true)
                }
                fail("Import should be cancelled")
            } catch (expected: CancellationException) { assertEquals("cancel import", expected.message) }
            assertFalse(index.legacyImported())
            assertEquals(0, index.count())
            assertTrue(source.exists())
        }
    }

    @Test fun defaultSnapshotFieldsAndNullReferencesRemainCompatible() {
        val source = temporary.newFile().apply { writeText("""{"files":{"x":{"stamp":"s","snapshot":{"kind":"UNAVAILABLE"}}}}""") }
        val files = mutableListOf<TerminalObservedFile>()
        readLegacyTerminalBaseline(source, { _, observed -> files.add(observed) })
        assertEquals(ChangeSnapshot(SnapshotKind.UNAVAILABLE), files.single().snapshot)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidBlobNamesAreRejected() {
        val source = temporary.newFile().apply { writeText(baseline().replace(hash, "../../objects")) }
        readLegacyTerminalBaseline(source, { _, _ -> })
    }
}