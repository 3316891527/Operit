package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceChangeStoreStatisticsTest {
    @Test fun legacySnapshotsHaveTheSameCountsInTheFileListAndDetailWithoutSavedStatistics() = withStore { store ->
        val record = record(store, "", "first\n\nthird\nfourth", 100)
        store.save(listOf(record))
        val file = store.reviewFiles(store.records(chatId = "test-chat")).single()
        val content = store.content(record)
        val detail = WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after))
        assertEquals(4, file.additions)
        assertEquals(0, file.deletions)
        assertEquals(detail.additions, file.additions)
        assertEquals(detail.deletions, file.deletions)
        assertTrue(file.statisticsAvailable)
        assertEquals(100L, file.records.single().timestamp)
    }

    @Test fun turnSummaryComparesTheFirstBeforeAndLastAfterAndKeepsEveryOperation() = withStore { store ->
        val first = record(store, "original", "intermediate", 100)
        val second = record(store, "intermediate", "final\nextra", 200)
        val files = store.reviewFiles(listOf(second, first), aggregate = true)
        val file = files.single()
        val content = store.content(file.records.asReversed())
        assertEquals("original", content.before)
        assertEquals("final\nextra", content.after)
        assertEquals(2, file.additions)
        assertEquals(1, file.deletions)
        assertEquals(listOf(second.id, first.id), file.records.map { it.id })
        val latest = store.reviewFiles(listOf(first, second)).single()
        assertEquals(2, latest.additions)
        assertEquals(1, latest.deletions)
    }

    @Test fun binarySnapshotsExposeUnknownStatisticsInsteadOfClaimingZeroTextChanges() = withStore { store ->
        val record = FileChangeRecord("binary", "operation", "test-chat", 100, "write_file_binary",
            ChangeLocation("/outside/data.bin", "android", "android"), ChangeSnapshot(SnapshotKind.ABSENT), ChangeSnapshot(SnapshotKind.BINARY))
        val file = store.reviewFiles(listOf(record)).single()
        assertFalse(file.statisticsAvailable)
        assertTrue(file.binary)
        assertFalse(store.content(record).isText)
    }

    private fun record(store: WorkspaceChangeStore, before: String, after: String, timestamp: Long) = FileChangeRecord(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "test-chat", timestamp, "edit_file",
        ChangeLocation("/outside/result.txt", "android", "android"),
        if (before.isEmpty()) ChangeSnapshot(SnapshotKind.ABSENT) else ChangeSnapshot(SnapshotKind.TEXT, blob = store.putBlob(before.toByteArray())),
        ChangeSnapshot(SnapshotKind.TEXT, blob = store.putBlob(after.toByteArray())))

    private fun withStore(block: (WorkspaceChangeStore) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(target.cacheDir, "review-statistics-${UUID.randomUUID()}")
        val context = object : ContextWrapper(target) { override fun getFilesDir(): File = folder }
        try { block(WorkspaceChangeStore(context)) } finally { folder.deleteRecursively() }
    }
}