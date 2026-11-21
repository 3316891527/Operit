package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import org.junit.Assert.*
import org.junit.Test

class WorkspaceReviewStateTest {
    @Test fun snapshotIdentitySeparatesSidesAndIncludesNewlinesAndUnicode() {
        assertEquals(reviewSnapshotKey("旧😀\n", "新😁\n"), reviewSnapshotKey("旧😀\n", "新😁\n"))
        assertNotEquals(reviewSnapshotKey("ab", "c"), reviewSnapshotKey("a", "bc"))
        assertNotEquals(reviewSnapshotKey("old", "new"), reviewSnapshotKey("new", "old"))
        assertNotEquals(reviewSnapshotKey("old", "new"), reviewSnapshotKey("old", "new\n"))
        assertNotEquals(reviewSnapshotKey("旧😀", "新"), reviewSnapshotKey("旧😁", "新"))
    }

    @Test fun reparsingTheSameContentsRetainsExpandedContext() {
        val old = (1..80).joinToString("\n") { "line $it" }
        val next = old.replace("line 40\n", "changed\n")
        val initial = WorkspaceDiff.compare(old, next)
        val gaps = WorkspaceDiff.items(initial, false, false, emptySet()).filterIsInstance<ReviewDiffItem.Gap>()
        val choices = gaps.fold(ReviewViewChoices()) { state, gap -> state.expand(initial.snapshotKey, gap.start) }
        val reloaded = WorkspaceDiff.compare(old, next)
        assertNotSame(initial, reloaded)
        val restored = WorkspaceDiff.items(reloaded, false, false, choices.forSnapshot(reloaded.snapshotKey).expanded)
        assertFalse(restored.any { it is ReviewDiffItem.Gap })
        assertEquals(reloaded.rows, restored.filterIsInstance<ReviewDiffItem.Line>().map { it.right })
    }

    @Test fun switchingComparisonsDoesNotGrantLoadPermissionOrApplyOldRowIndices() {
        val first = reviewSnapshotKey("before", "after")
        val second = reviewSnapshotKey("before", "different")
        val firstChoices = ReviewViewChoices().requestLoad(first).expand(first, 12)
        assertFalse(firstChoices.forSnapshot(second).loadRequested)
        assertTrue(firstChoices.forSnapshot(second).expanded.isEmpty())
        val bothChoices = firstChoices.expand(second, 48)
        assertTrue(bothChoices.forSnapshot(first).loadRequested)
        assertEquals(setOf(12), bothChoices.forSnapshot(first).expanded)
        assertFalse(bothChoices.forSnapshot(second).loadRequested)
        assertEquals(setOf(48), bothChoices.forSnapshot(second).expanded)
    }

    @Test fun savedStateRestoresIndependentLoadRequestsAndExpandedRanges() {
        val first = reviewSnapshotKey("first before", "first after")
        val second = reviewSnapshotKey("second before", "second after")
        val third = reviewSnapshotKey("third before", "third after")
        val choices = ReviewViewChoices().requestLoad(first).expand(first, 12).expand(first, 0)
            .expand(second, 100).requestLoad(third)
        val restored = ReviewViewChoices.restore(choices.saved())
        assertEquals(choices, restored)
        assertTrue(restored.forSnapshot(first).loadRequested)
        assertEquals(setOf(0, 12), restored.forSnapshot(first).expanded)
        assertFalse(restored.forSnapshot(second).loadRequested)
        assertTrue(restored.forSnapshot(third).expanded.isEmpty())
        assertEquals(ReviewViewChoices(), ReviewViewChoices.restore(emptyList()))
    }

    @Test fun aSavedLoadRequestCannotOverrideTheHardDiffLimit() {
        val content = ReviewContent("", "x".repeat(WorkspaceDiffPolicy.MAX_LINE_CHARACTERS + 1), SnapshotKind.ABSENT, SnapshotKind.TEXT)
        val key = reviewSnapshotKey(checkNotNull(content.before), checkNotNull(content.after))
        val choices = ReviewViewChoices().requestLoad(key)
        assertTrue(choices.forSnapshot(key).loadRequested)
        assertEquals(ReviewDiffLoad.BLOCKED, WorkspaceDiffPolicy.classify(content))
    }
}
