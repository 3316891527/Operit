package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import org.junit.Assert.*
import org.junit.Test

class WorkspaceDiffPolicyTest {
    private fun policy(before: String = "", after: String) = WorkspaceDiffPolicy.classify(ReviewContent(before, after, SnapshotKind.TEXT, SnapshotKind.TEXT))

    @Test fun linesLongerThanTheOldLimitAreSupported() {
        assertEquals(ReviewDiffLoad.AUTOMATIC, policy(after = "x".repeat(8193)))
        assertEquals(ReviewDiffLoad.AUTOMATIC, policy(after = "x".repeat(WorkspaceDiffPolicy.AUTO_LINE_CHARACTERS)))
        assertEquals(ReviewDiffLoad.MANUAL, policy(after = "x".repeat(WorkspaceDiffPolicy.AUTO_LINE_CHARACTERS + 1)))
    }

    @Test fun characterThresholdsCountBothVersionsAndIncludeTheBoundary() {
        val line = "x".repeat(1023) + "\n"
        val automatic = line.repeat(256)
        assertEquals(ReviewDiffLoad.AUTOMATIC, policy(after = automatic))
        assertEquals(ReviewDiffLoad.MANUAL, policy("x", automatic))
        val maximum = line.repeat(4096)
        assertEquals(ReviewDiffLoad.MANUAL, policy(after = maximum))
        assertEquals(ReviewDiffLoad.BLOCKED, policy("x", maximum))
    }

    @Test fun lineThresholdsCountTrailingNewlinesAndBothVersions() {
        assertEquals(ReviewDiffLoad.AUTOMATIC, policy(after = "x\n".repeat(4000)))
        assertEquals(ReviewDiffLoad.MANUAL, policy("y", "x\n".repeat(4000)))
        assertEquals(ReviewDiffLoad.MANUAL, policy(after = "x\n".repeat(20000)))
        assertEquals(ReviewDiffLoad.BLOCKED, policy("y", "x\n".repeat(20000)))
    }

    @Test fun anOversizedSingleLineCannotBeForcedToLoad() {
        assertEquals(ReviewDiffLoad.MANUAL, policy(after = "x".repeat(WorkspaceDiffPolicy.MAX_LINE_CHARACTERS)))
        assertEquals(ReviewDiffLoad.BLOCKED, policy(after = "x".repeat(WorkspaceDiffPolicy.MAX_LINE_CHARACTERS + 1)))
    }
}
