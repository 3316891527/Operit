package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceIndexedScanTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun selectedSubtreesPreserveUnrelatedBaselineRowsAndDetectRemovedFiles() {
        val root = temporary.newFolder("workspace")
        File(root, "a.txt").writeText("a")
        File(root, "b.txt").writeText("b")
        val index = TerminalObservationIndex(JdbcTerminalIndexDatabase(File(temporary.root, "baseline.sqlite")))
        try {
            fun scan(paths: Set<String>?) = index.beginScan(paths).also { target ->
                scanLocalWorkspace(root, { path -> index.file(path)?.let { ObservedWorkspaceFile(it.stamp, it.snapshot) } }, paths.orEmpty(), { false },
                    { path, file -> target.put(path, TerminalObservedFile(file.stamp, file.snapshot)) },
                    { bytes -> ChangeSnapshot(SnapshotKind.TEXT, bytes.size.toLong(), SnapshotPackStore.hash(bytes)) }, paths)
            }
            scan(null).use { it.commit(completeInitialization = true) }
            val beforeB = index.file("b.txt")
            File(root, "a.txt").delete()
            scan(setOf("a.txt")).use { target ->
                val changes = target.differences().single()
                assertEquals("a.txt", changes.path)
                assertEquals(SnapshotKind.ABSENT, changes.afterSnapshot.kind)
                target.commit(completeInitialization = true)
            }
            assertNull(index.file("a.txt"))
            assertEquals(beforeB, index.file("b.txt"))
        } finally { index.close() }
    }

    @Test fun selectedPathDoesNotFollowAnIntermediateSymbolicLinkOutsideWorkspace() {
        val root = temporary.newFolder("linked-workspace")
        val outside = temporary.newFolder("outside")
        File(outside, "secret.txt").writeText("outside")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        val read = mutableListOf<String>()
        scanLocalWorkspace(root, { null }, setOf("link/secret.txt"), { false }, { path, _ -> read += path },
            { ChangeSnapshot(SnapshotKind.TEXT) }, setOf("link/secret.txt"))
        assertTrue(read.isEmpty())
    }
}