package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceFileObservationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun snapshot(bytes: ByteArray) = ChangeSnapshot(
        if (bytes.any { it == 0.toByte() }) SnapshotKind.BINARY else SnapshotKind.TEXT,
        bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    private fun scan(root: File, before: Map<String, ObservedWorkspaceFile> = emptyMap(), forced: Set<String> = emptySet()) =
        observeLocalWorkspace(root, before, forced, { false }, ::snapshot)

    @Test fun `shell edits preserve deleted content and detect additions without a file tool`() {
        val root = temporary.newFolder()
        val original = File(root, "file with space.txt").apply { writeText("deleted\nkept\n") }
        val before = scan(root)
        val child = ProcessBuilder("sh", "-c", "printf 'kept\\nadded\\n' > \"$1\"; printf 'new\\n' > \"$2\"", "sh", original.path, File(root, "new\nname.txt").path)
            .redirectErrorStream(true).start()
        val output = child.inputStream.bufferedReader().readText()
        assertEquals(output, 0, child.waitFor())
        val after = scan(root, before)
        assertEquals(listOf("file with space.txt", "new\nname.txt"), observedWorkspaceChanges(before, after))
        assertEquals(snapshot("deleted\nkept\n".toByteArray()), before.getValue(original.name).snapshot)
        assertEquals(snapshot("kept\nadded\n".toByteArray()), after.getValue(original.name).snapshot)
    }

    @Test fun `rename and delete are detected without retaining nonexistent paths`() {
        val root = temporary.newFolder()
        val old = File(root, "old.txt").apply { writeText("content") }
        val before = scan(root)
        Files.move(old.toPath(), File(root, "renamed.txt").toPath())
        val renamed = scan(root, before)
        assertEquals(listOf("old.txt", "renamed.txt"), observedWorkspaceChanges(before, renamed))
        assertEquals(before.getValue("old.txt").snapshot, renamed.getValue("renamed.txt").snapshot)
        File(root, "renamed.txt").delete()
        assertEquals(listOf("renamed.txt"), observedWorkspaceChanges(renamed, scan(root, renamed)))
    }

    @Test fun `unchanged content does not create records after metadata updates`() {
        val root = temporary.newFolder()
        val file = File(root, "a").apply { writeText("same") }
        val before = scan(root)
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(System.currentTimeMillis() + 20_000))
        assertTrue(observedWorkspaceChanges(before, scan(root, before)).isEmpty())
    }

    @Test fun `file events force reread for same size and restored modification time`() {
        val root = temporary.newFolder()
        val file = File(root, "a").apply { writeText("old") }
        val time = Files.getLastModifiedTime(file.toPath())
        val before = scan(root)
        file.writeText("new")
        Files.setLastModifiedTime(file.toPath(), time)
        val after = scan(root, before, setOf("a"))
        assertEquals(listOf("a"), observedWorkspaceChanges(before, after))
        assertEquals(snapshot("new".toByteArray()), after.getValue("a").snapshot)
    }

    @Test fun `directory events force rereading descendant files`() {
        val root = temporary.newFolder()
        val dir = File(root, "nested").apply { mkdir() }
        val file = File(dir, "a").apply { writeText("old") }
        val time = Files.getLastModifiedTime(file.toPath())
        val before = scan(root)
        file.writeText("new")
        Files.setLastModifiedTime(file.toPath(), time)
        assertEquals(listOf("nested/a"), observedWorkspaceChanges(before, scan(root, before, setOf("nested"))))
    }

    @Test fun `git internals and history storage are excluded but hidden project files are observed`() {
        val root = temporary.newFolder()
        File(root, ".git").mkdir(); File(root, ".git/HEAD").writeText("metadata")
        File(root, ".env").writeText("project")
        val storage = File(root, "history").apply { mkdir() }; File(storage, "snapshot").writeText("internal")
        val result = observeLocalWorkspace(root, emptyMap(), emptySet(), { it == storage }, ::snapshot)
        assertEquals(setOf(".env"), result.keys)
        assertFalse(observedWorkspacePath("nested/.git/index"))
        assertTrue(observedWorkspacePath(".gitignore"))
    }

    @Test fun `symbolic links are recorded without following directory cycles`() {
        val root = temporary.newFolder()
        val outside = temporary.newFile().apply { writeText("outside secret") }
        Files.createSymbolicLink(File(root, "cycle").toPath(), root.toPath())
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        val result = scan(root)
        assertEquals(setOf("cycle", "link"), result.keys)
        assertEquals(snapshot(outside.path.toByteArray()), result.getValue("link").snapshot)
    }

    @Test fun `oversized files are catalogued without loading their bytes`() {
        val root = temporary.newFolder()
        File(root, "large").outputStream().use { it.write(ByteArray(CHANGE_SNAPSHOT_LIMIT + 1)) }
        val result = observeLocalWorkspace(root, emptyMap(), emptySet(), { false }, { throw AssertionError("Oversized file read") })
        assertEquals(ChangeSnapshot(SnapshotKind.TOO_LARGE, CHANGE_SNAPSHOT_LIMIT + 1L), result.getValue("large").snapshot)
        val changed = result.mapValues { (_, value) -> value.copy(stamp = "different") }
        assertEquals(listOf("large"), observedWorkspaceChanges(result, changed))
    }

    @Test fun `incomplete scans throw and leave the previous baseline untouched`() {
        val root = temporary.newFolder()
        File(root, "a").writeText("old")
        val before = scan(root)
        File(root, "a").writeText("new")
        assertThrows(IOException::class.java) {
            observeLocalWorkspace(root, before, setOf("a"), { false }, { throw IOException("Read failed") })
        }
        assertEquals(snapshot("old".toByteArray()), before.getValue("a").snapshot)
    }

    @Test fun `removed roots produce deletions but nondirectory roots fail`() {
        val root = temporary.newFolder()
        File(root, "a").writeText("old")
        val before = scan(root)
        root.deleteRecursively()
        assertEquals(listOf("a"), observedWorkspaceChanges(before, scan(root, before)))
        root.writeText("not a directory")
        assertThrows(IllegalStateException::class.java) { scan(root, before) }
    }

    @Test fun `observation baselines retain snapshot identity through serialization`() {
        val root = temporary.newFolder()
        File(root, "a").writeBytes(byteArrayOf(1, 0, 2))
        val baseline = scan(root)
        val restored = Json.decodeFromString<Map<String, ObservedWorkspaceFile>>(Json.encodeToString(baseline))
        assertEquals(baseline, restored)
        assertEquals(SnapshotKind.BINARY, restored.getValue("a").snapshot.kind)
        assertTrue(observedWorkspaceChanges(restored, scan(root, restored)).isEmpty())
    }

    @Test fun `tool sources preserve names and ambiguous edits are marked filesystem`() {
        assertEquals("super_admin:terminal_start", observedChangeSource(listOf("super_admin:terminal_start"), false))
        assertEquals("edit_file", observedChangeSource(listOf("edit_file", "edit_file"), false))
        assertEquals("filesystem", observedChangeSource(listOf("terminal_start", "edit_file"), false))
        assertEquals("terminal", observedChangeSource(emptyList(), true))
        assertEquals("filesystem", observedChangeSource(emptyList(), false))
    }
}
