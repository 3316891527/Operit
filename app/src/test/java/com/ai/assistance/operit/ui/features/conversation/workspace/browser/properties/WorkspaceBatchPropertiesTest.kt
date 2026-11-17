package com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties

import org.junit.Assert.*
import org.junit.Test

class WorkspaceBatchPropertiesTest {
    private fun node(directory: Boolean = false, size: Long? = 7, link: Boolean = false) =
        PropertyNode("/entry", directory, link, size, 0)
    @Test fun selectedFolderItselfAndDescendantsAreCountedOnce() {
        val totals = WorkspaceDirectoryTotals(30, 3, 2, 0, 0)
        val result = WorkspaceBatchProperties().add(node(true), totals).add(node(), null)
        assertEquals(37L, result.bytes)
        assertEquals(4L, result.files)
        assertEquals(3L, result.directories)
        assertEquals(2, result.processed)
    }
    @Test fun unknownAndUnreadableItemsCannotTurnIntoZeroSize() {
        val result = WorkspaceBatchProperties().add(node(size = null), null).failed().add(node(), null)
        assertNull(result.bytes)
        assertEquals(1L, result.unknownSizes)
        assertEquals(1L, result.unreadable)
        assertEquals(3, result.processed)
    }
    @Test fun symbolicDirectoriesAreCountedAsLinksWithoutTheirTargets() {
        val result = WorkspaceBatchProperties().add(node(directory = true, link = true), null)
        assertEquals(1L, result.files)
        assertEquals(0L, result.directories)
        assertEquals(7L, result.bytes)
    }
    @Test fun incompleteDirectoryStatisticsRetainTheirFailureCounts() {
        val result = WorkspaceBatchProperties().add(node(true), WorkspaceDirectoryTotals(null, 2, 1, 3, 4))
        assertNull(result.bytes)
        assertEquals(3L, result.unreadable)
        assertEquals(4L, result.unknownSizes)
    }
}
