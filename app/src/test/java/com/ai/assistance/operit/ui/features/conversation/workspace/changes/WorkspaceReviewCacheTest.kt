package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import org.junit.Assert.*
import org.junit.Test

class WorkspaceReviewCacheTest {
    private fun record(id: String, timestamp: Long) = FileChangeRecord(id, id, "chat", timestamp, "edit_file",
        ChangeLocation("/result.txt", "android", "android"), ChangeSnapshot(SnapshotKind.TEXT, blob = "before"), ChangeSnapshot(SnapshotKind.TEXT, blob = "after"))
    private fun key(id: String) = ReviewCacheKey(ReviewFile(id, ChangeLocation("/$id.txt", "android", "android"), "$id.txt", "M"), null)
    private fun loaded(before: String, after: String): LoadedFileReview = LoadedFileReview(
        ReviewContent(before, after, SnapshotKind.TEXT, SnapshotKind.TEXT),
        PreparedReviewDiff(WorkspaceDiff.compare(before, after), null, null, maxOf(before.length, after.length)))

    @Test fun latestTurnUsesTheMostRecentRequestWithRecordedFileOperations() {
        val records = listOf(record("first", 150), record("second", 220), record("third", 250))
        assertEquals(listOf("second", "third"), latestTurnRecords(records, listOf(100, 200, 300)).map { it.id })
        assertEquals(listOf("first"), latestTurnRecords(records.take(1), listOf(100, 200, 300)).map { it.id })
        assertTrue(latestTurnRecords(records, emptyList()).isEmpty())
    }

    @Test fun revisitingAFileKeepsPreparedContentAndEvictsTheLeastRecentlyUsedFile() {
        val cache = WorkspaceReviewCache(maximumEntries = 2)
        val first = loaded("old", "new")
        cache.put(key("first"), first)
        cache.put(key("second"), loaded("other", "next"))
        assertSame(first, cache[key("first")])
        cache.put(key("third"), loaded("third", "changed"))
        assertSame(first, cache[key("first")])
        assertNull(cache[key("second")])
        assertNotNull(cache[key("third")])
    }

    @Test fun cacheCharacterBudgetCountsReplacementsAndDoesNotRetainOversizedFiles() {
        val cache = WorkspaceReviewCache(maximumCharacters = 8)
        cache.put(key("first"), loaded("12", "34"))
        cache.put(key("first"), loaded("1", "2"))
        cache.put(key("second"), loaded("123", "456"))
        assertNotNull(cache[key("first")])
        assertNotNull(cache[key("second")])
        cache.put(key("large"), loaded("12345", "67890"))
        assertNull(cache[key("large")])
        cache.put(key("third"), loaded("x", "y"))
        assertNull(cache[key("first")])
        assertNotNull(cache[key("second")])
    }

    @Test fun markdownLinesAreReadyAndCacheableBeforeGrammarInitialization() {
        val content = ReviewContent("# 演示\n", "# 演示\n终端追加\n", SnapshotKind.TEXT, SnapshotKind.TEXT)
        val lines = prepareReviewLines(content)
        assertEquals(1, lines.document.additions)
        assertEquals("终端追加", lines.document.rows.last().text)
        val entry = LoadedFileReview(content, lines)
        val cache = WorkspaceReviewCache()
        cache.put(key("notes.md"), entry)
        assertSame(lines, cache[key("notes.md")]?.diff)
        assertFalse(entry.highlightComplete)
    }

    @Test fun textCanBeDeferredWithoutBeingMisclassifiedAsBinary() {
        val content = ReviewContent("", "hello", SnapshotKind.ABSENT, SnapshotKind.TEXT)
        assertEquals(ReviewDiffLoad.AUTOMATIC, LoadedFileReview(content, null).load)
        assertFalse(ReviewContent(null, null, SnapshotKind.BINARY, SnapshotKind.BINARY).isText)
        assertTrue(reviewDiffSupported(content))
        assertTrue(reviewDiffSupported(content.copy(after = "x".repeat(8193))))
    }
}