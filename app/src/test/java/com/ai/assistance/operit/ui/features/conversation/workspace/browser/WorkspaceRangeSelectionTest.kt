package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import org.junit.Assert.*
import org.junit.Test

class WorkspaceRangeSelectionTest {
    private val names = listOf("1", "2", "3", "4", "5")
    @Test fun firstAndFourthSwipeSelectTheClosedIntervalAndAdvanceAnchor() {
        val first = selectWorkspaceRange(names, emptySet(), null, "1")
        assertEquals(setOf("1"), first.selected)
        val fourth = selectWorkspaceRange(names, first.selected, first.anchor, "4")
        assertEquals(setOf("1", "2", "3", "4"), fourth.selected)
        assertEquals("4", fourth.anchor)
    }
    @Test fun reverseRangesAndRepeatedEndpointsAreStable() {
        val result = selectWorkspaceRange(names, setOf("5"), "5", "2")
        assertEquals(setOf("2", "3", "4", "5"), result.selected)
        assertEquals(result, selectWorkspaceRange(names, result.selected, result.anchor, "2"))
    }
    @Test fun individualTogglesDoNotMoveTheSwipeAnchorOrClearOtherSelections() {
        val result = selectWorkspaceRange(names, setOf("1", "5"), "1", "3")
        assertEquals(setOf("1", "2", "3", "5"), result.selected)
        val afterClick = result.selected - "2"
        assertEquals(setOf("1", "3", "4", "5"), selectWorkspaceRange(names, afterClick, result.anchor, "4").selected)
    }
    @Test fun filteredAndSortedViewsResolveNamesInsteadOfOldIndices() {
        val sorted = listOf("5", "3", "1")
        assertEquals(setOf("5", "3", "1"), selectWorkspaceRange(sorted, setOf("1"), "1", "5").selected)
        assertEquals(setOf("1", "5"), selectWorkspaceRange(listOf("5"), setOf("1"), "1", "5").selected)
    }
    @Test fun missingTargetsAndEmptyViewsCannotSelectInvisibleItems() {
        val original = WorkspaceRangeSelection(setOf("1"), "1")
        assertEquals(original, selectWorkspaceRange(emptyList(), original.selected, original.anchor, "4"))
        assertEquals(original, selectWorkspaceRange(names, original.selected, original.anchor, ".."))
    }
}
