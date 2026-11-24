package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceSnapshotReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val objects get() = File(temporary.root, "objects")
    private fun blob(text: String): ChangeSnapshot {
        objects.mkdirs()
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        File(objects, hash).writeText(text, Charsets.UTF_8)
        return ChangeSnapshot(SnapshotKind.TEXT, text.toByteArray().size.toLong(), hash)
    }
    private fun record(id: String, before: ChangeSnapshot, after: ChangeSnapshot) = FileChangeRecord(
        id, "operation", "chat", 100, "edit_file", ChangeLocation("/workspace/$id", "android", "android"), before, after,
    )

    @Test fun intactSnapshotsKeepTheirExactText() {
        val before = blob("旧内容\n")
        val after = blob("新内容\n")
        val data = WorkspaceSnapshotReader(objects).content(before, after)
        assertEquals("旧内容\n", data.before)
        assertEquals("新内容\n", data.after)
        assertTrue(data.isText)
        assertFalse(data.snapshotsCleared)
    }

    @Test fun removingTheWholeObjectsDirectoryLeavesNoReadableRecords() {
        val change = record("file", blob("旧"), blob("新"))
        val reader = WorkspaceSnapshotReader(objects)
        assertTrue(objects.deleteRecursively())
        assertTrue(reader.readableRecords(listOf(change)).isEmpty())
        val data = reader.content(change.before, change.after)
        assertTrue(data.snapshotsCleared)
        assertFalse(data.isText)
        assertNull(data.before)
        assertNull(data.after)
        assertEquals(ReviewDiffLoad.BLOCKED, WorkspaceDiffPolicy.classify(data))
    }

    @Test fun partialCleanupKeepsOtherFilesAndExcludesBothSidesOfMissingChanges() {
        val missing = blob("已清理")
        val kept = blob("保留")
        val complete = record("complete", kept, kept)
        val oldMissing = record("before", missing, kept)
        val newMissing = record("after", kept, missing)
        assertTrue(File(objects, checkNotNull(missing.blob)).delete())
        assertEquals(listOf(complete), WorkspaceSnapshotReader(objects).readableRecords(listOf(oldMissing, complete, newMissing)))
    }

    @Test fun cleanupBetweenListingAndOpeningDoesNotProduceAnEmptyFileDiff() {
        val change = record("file", blob("旧"), blob("新"))
        val reader = WorkspaceSnapshotReader(objects)
        assertEquals(listOf(change), reader.readableRecords(listOf(change)))
        assertTrue(File(objects, checkNotNull(change.before.blob)).delete())
        val data = reader.content(change.before, change.after)
        assertTrue(data.snapshotsCleared)
        assertEquals(SnapshotKind.UNAVAILABLE, data.beforeKind)
        assertNull(data.before)
        assertEquals("新", data.after)
        assertFalse(data.isText)
        assertEquals(ReviewDiffLoad.BLOCKED, WorkspaceDiffPolicy.classify(data))
    }

    @Test fun recreatingObjectsAllowsNewCapturesWithoutRecreatingTheReader() {
        val change = record("file", blob("旧"), blob("新"))
        val reader = WorkspaceSnapshotReader(objects)
        objects.deleteRecursively()
        assertTrue(reader.readableRecords(listOf(change)).isEmpty())
        val before = blob("下一次旧内容")
        val after = blob("下一次新内容")
        val next = record("next", before, after)
        assertEquals(listOf(next), reader.readableRecords(listOf(change, next)))
        assertTrue(reader.content(before, after).isText)
    }

    @Test fun trulyAbsentFilesStillRepresentAdditionsAndDeletions() {
        val absent = ChangeSnapshot(SnapshotKind.ABSENT)
        val text = blob("创建的内容")
        val reader = WorkspaceSnapshotReader(objects)
        val added = reader.content(absent, text)
        val removed = reader.content(text, absent)
        assertEquals("", added.before)
        assertEquals("", removed.after)
        assertTrue(added.isText)
        assertTrue(removed.isText)
        assertFalse(added.snapshotsCleared)
        assertFalse(removed.snapshotsCleared)
    }

    @Test fun removedBinarySnapshotsAlsoHaveTheClearedState() {
        val binary = blob("bytes").copy(kind = SnapshotKind.BINARY)
        val reader = WorkspaceSnapshotReader(objects)
        assertFalse(reader.content(binary, binary).snapshotsCleared)
        File(objects, checkNotNull(binary.blob)).delete()
        val data = reader.content(binary, binary)
        assertTrue(data.snapshotsCleared)
        assertEquals(SnapshotKind.UNAVAILABLE, data.afterKind)
    }

    @Test fun existingButInvalidObjectsStillRaiseTheirReadError() {
        val text = blob("content")
        val file = File(objects, checkNotNull(text.blob))
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        assertThrows(FileNotFoundException::class.java) { WorkspaceSnapshotReader(objects).content(text, text) }
    }

    @Test fun cleanupSuppressesInternalDetailsButPreservesOtherUnavailableReasons() {
        val text = blob("content")
        val unavailable = ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "限额已达到")
        val reader = WorkspaceSnapshotReader(objects)
        assertEquals("限额已达到", reader.content(unavailable, text).detail)
        File(objects, checkNotNull(text.blob)).delete()
        val data = reader.content(unavailable, text)
        assertTrue(data.snapshotsCleared)
        assertNull(data.detail)
    }

    @Test fun availabilityChecksDoNotCacheExternalCleanupAcrossRefreshes() {
        val before = blob("旧")
        val after = blob("新")
        val change = record("file", before, after)
        val reader = WorkspaceSnapshotReader(objects)
        assertEquals(listOf(change), reader.readableRecords(listOf(change)))
        File(objects, checkNotNull(after.blob)).delete()
        assertTrue(reader.readableRecords(listOf(change)).isEmpty())
        blob("新")
        assertEquals(listOf(change), reader.readableRecords(listOf(change)))
    }
}
