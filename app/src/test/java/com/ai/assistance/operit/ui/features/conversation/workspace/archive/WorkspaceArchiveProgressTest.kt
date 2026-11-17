package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import org.junit.Assert.*
import org.junit.Test

class WorkspaceArchiveProgressTest {
    @Test fun readingWithoutAnIndexDoesNotInventAPercentage() {
        assertNull(WorkspaceArchiveProgress(stage = WorkspaceArchiveStage.READING).fraction)
    }

    @Test fun currentEntryBytesContributeWithinTheCompletedEntryCount() {
        val progress = WorkspaceArchiveProgress(completedEntries = 3, totalEntries = 10, entryBytes = 5, entryTotal = 10)
        assertEquals(0.35f, checkNotNull(progress.fraction), 0.0001f)
    }

    @Test fun emptyFileDoesNotCompleteBeforeItsEntryIsClosed() {
        val progress = WorkspaceArchiveProgress(totalEntries = 1, entryTotal = 0)
        assertEquals(0f, checkNotNull(progress.fraction), 0f)
        assertEquals(1f, checkNotNull(progress.copy(completedEntries = 1, entryTotal = null).fraction), 0f)
    }

    @Test fun allTransferredBytesCannotClaimCompletionWithUnfinishedFiles() {
        val progress = WorkspaceArchiveProgress(stage = WorkspaceArchiveStage.TRANSFERRING, bytes = 100, total = 100,
            completedEntries = 1, totalEntries = 2)
        assertTrue(checkNotNull(progress.fraction) < 1f)
        assertEquals(1f, checkNotNull(progress.copy(completedEntries = 2).fraction), 0f)
    }

    @Test fun completedEntryBytesDoNotAdvanceTheNextEntryFraction() {
        val progress = WorkspaceArchiveProgress(completedEntries = 2, totalEntries = 4, entryBytes = 600_000)
        assertNull(progress.entryFraction)
        assertEquals(0.5f, checkNotNull(progress.fraction), 0f)
    }
}
