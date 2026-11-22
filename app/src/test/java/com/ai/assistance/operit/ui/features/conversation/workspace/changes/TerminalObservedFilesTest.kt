package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerminalObservedFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun snapshot(bytes: ByteArray) = ChangeSnapshot(SnapshotKind.TEXT, bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    private fun scan(root: File, before: TerminalObservationBaseline = TerminalObservationBaseline(), paths: Set<String>? = null) =
        memoryScanTerminalFiles(root, before, paths, snapshotBytes = ::snapshot)

    @Test fun creationModificationAndDeletionNeedNoWorkspaceBinding() {
        val root = temporary.newFolder()
        val initial = scan(root)
        val file = File(root, "created.txt").apply { writeText("first") }
        val created = scan(root, initial, setOf(file.name))
        assertEquals(SnapshotKind.ABSENT, initial.snapshot(file.name).kind)
        assertEquals(snapshot("first".toByteArray()), created.snapshot(file.name))
        file.writeText("second")
        val modified = scan(root, created, setOf(file.name))
        assertEquals(snapshot("second".toByteArray()), modified.snapshot(file.name))
        assertTrue(file.delete())
        assertEquals(SnapshotKind.ABSENT, scan(root, modified, setOf(file.name)).snapshot(file.name).kind)
    }

    @Test fun eventRereadsEqualLengthAndPreservedTimestamp() {
        val root = temporary.newFolder()
        val file = File(root, "same.txt").apply { writeText("before") }
        val initial = scan(root)
        val time = Files.getLastModifiedTime(file.toPath())
        file.writeText("after!")
        Files.setLastModifiedTime(file.toPath(), time)
        val next = scan(root, initial, setOf(file.name))
        assertNotEquals(initial.snapshot(file.name), next.snapshot(file.name))
    }

    @Test fun incrementalScanDoesNotReadUnrelatedFiles() {
        val root = temporary.newFolder()
        File(root, "one").writeText("one")
        File(root, "two").writeText("two")
        val initial = scan(root)
        File(root, "one").writeText("updated")
        var reads = 0
        val next = memoryScanTerminalFiles(root, initial, setOf("one"), snapshotBytes = { reads++; snapshot(it) })
        assertEquals(1, reads)
        assertEquals(initial.snapshot("two"), next.snapshot("two"))
    }

    @Test fun baselineRegistersVisibleDirectoriesDuringTheSameTraversal() {
        val root = temporary.newFolder()
        File(root, "tmp/operit-edit-demo").mkdirs()
        File(root, "tmp/operit-edit-demo/message.txt").writeText("first")
        File(root, ".git/objects").mkdirs()
        val watched = mutableListOf<String>()
        val initial = memoryScanTerminalFiles(root, TerminalObservationBaseline(),
            onDirectory = { watched += it.relativeTo(root).invariantSeparatorsPath }, snapshotBytes = ::snapshot)
        assertEquals(listOf("", "tmp", "tmp/operit-edit-demo"), watched)
        assertEquals(snapshot("first".toByteArray()), initial.snapshot("tmp/operit-edit-demo/message.txt"))
    }

    @Test fun directoryEventCapturesAlreadyCreatedDescendants() {
        val root = temporary.newFolder()
        val initial = scan(root)
        File(root, "new/deep").mkdirs()
        File(root, "new/deep/file").writeText("content")
        assertEquals(snapshot("content".toByteArray()), scan(root, initial, setOf("new")).snapshot("new/deep/file"))
    }

    @Test fun renamingDirectoryRemovesOldDescendants() {
        val root = temporary.newFolder()
        File(root, "old").mkdir()
        File(root, "old/file").writeText("content")
        val initial = scan(root)
        assertTrue(File(root, "old").renameTo(File(root, "new")))
        val next = scan(root, initial, setOf("old", "new"))
        assertEquals(SnapshotKind.ABSENT, next.snapshot("old/file").kind)
        assertEquals(initial.snapshot("old/file"), next.snapshot("new/file"))
    }

    @Test fun symlinkIsRecordedWithoutFollowingItsTarget() {
        val root = temporary.newFolder()
        val outside = temporary.newFolder()
        File(outside, "private").writeText("outside")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        val initial = scan(root)
        assertEquals(setOf("link"), initial.files.keys)
        val next = scan(root, initial, setOf("link/private"))
        assertFalse(next.files.containsKey("link/private"))
        assertEquals(initial.snapshot("link"), next.snapshot("link"))
    }

    @Test fun gitMetadataAndExplicitExclusionsStayOutsideInventory() {
        val root = temporary.newFolder()
        File(root, ".git").mkdir()
        File(root, ".git/config").writeText("git")
        val own = File(root, "journal").apply { mkdir() }
        File(own, "self").writeText("journal")
        File(root, ".env").writeText("visible hidden file")
        val next = memoryScanTerminalFiles(root, TerminalObservationBaseline(), excluded = { it.toPath().startsWith(own.toPath()) }, snapshotBytes = ::snapshot)
        assertEquals(setOf(".env"), next.files.keys)
    }

    @Test fun exhaustedBudgetDoesNotInventEmptyBeforeContent() {
        val root = temporary.newFolder()
        File(root, "existing").writeText("existing")
        val initial = memoryScanTerminalFiles(root, TerminalObservationBaseline(), byteBudget = 0, snapshotBytes = ::snapshot)
        assertEquals(SnapshotKind.UNAVAILABLE, initial.snapshot("existing").kind)
        assertNull(initial.snapshot("existing").blob)
        assertEquals(SnapshotKind.ABSENT, initial.snapshot("never-existed").kind)
    }

    @Test fun failedReadPreservesOldSnapshotAndMarksUnreadablePath() {
        val root = temporary.newFolder()
        File(root, "file").writeText("old")
        val initial = scan(root)
        File(root, "file").writeText("new")
        val next = memoryScanTerminalFiles(root, initial, setOf("file"), snapshotBytes = { error("injected read failure") })
        assertEquals(initial.snapshot("file"), next.snapshot("file"))
        assertEquals(setOf("file"), next.unreadable)
    }

    @Test fun unreadableDirectoryCannotEstablishAbsence() {
        val baseline = TerminalObservationBaseline(unreadable = setOf("closed"))
        assertEquals(SnapshotKind.UNAVAILABLE, baseline.snapshot("closed/unknown").kind)
        assertEquals(SnapshotKind.ABSENT, baseline.snapshot("elsewhere").kind)
    }

    @Test(expected = IllegalArgumentException::class) fun eventPathsCannotEscapeRoot() {
        scan(temporary.newFolder(), paths = setOf("../outside"))
    }

    @Test fun emptyCreatedFileIsTextRatherThanAbsent() {
        val root = temporary.newFolder()
        File(root, "empty").writeText("")
        assertEquals(SnapshotKind.TEXT, scan(root).snapshot("empty").kind)
    }
}
