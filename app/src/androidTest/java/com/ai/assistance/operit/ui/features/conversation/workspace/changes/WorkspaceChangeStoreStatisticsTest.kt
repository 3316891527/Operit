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

    @Test fun summaryRetainsAnEarlierDeletionWhenTheLatestOperationOnlyAdds() = withStore { store ->
        val first = record(store, "keep\nremoved\n", "keep\n", 100)
        val second = record(store, "keep\n", "keep\nadded\n", 200)
        val summary = store.reviewFiles(listOf(second, first), aggregate = true).single()
        val latest = store.reviewFiles(listOf(first, second), aggregate = false).single()
        assertEquals(1, summary.additions)
        assertEquals(1, summary.deletions)
        assertEquals(1, latest.additions)
        assertEquals(0, latest.deletions)
        val content = store.content(summary.records.asReversed())
        val detail = WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after))
        assertEquals(summary.additions, detail.additions)
        assertEquals(summary.deletions, detail.deletions)
    }

    @Test fun deletedFilesRetainTheirBeforeSnapshotAndNegativeCount() = withStore { store ->
        val deletion = record(store, "first\nsecond\n", "", 100).copy(after = ChangeSnapshot(SnapshotKind.ABSENT))
        store.save(listOf(deletion))
        val file = store.reviewFiles(store.records(chatId = "test-chat"), aggregate = true).single()
        assertEquals("D", file.status)
        assertEquals(0, file.additions)
        assertEquals(2, file.deletions)
        assertEquals("first\nsecond\n", store.content(deletion).before)
        assertEquals("", store.content(deletion).after)
        assertTrue(file.statisticsAvailable)
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