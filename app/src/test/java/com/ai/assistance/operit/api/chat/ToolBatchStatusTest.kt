package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.data.model.InputProcessingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolBatchStatusTest {
    private fun completed(index: Int) = ToolCallExecution(
        callIndex = index,
        clockId = "test",
        durationMs = 0L,
        endedMs = 10L,
    )

    @Test
    fun fiveSameNamedCallsCountDownUntilResultsAreProcessed() {
        val status = ToolBatchStatus(List(5) { "create_file" })
        assertEquals(InputProcessingState.ExecutingTool("create_file * 5"), status.state)
        for (index in 0..3) {
            val remaining = 4 - index
            val expected = if (remaining > 1) "create_file * $remaining" else "create_file"
            assertEquals(InputProcessingState.ExecutingTool(expected), status.onTiming(completed(index), 0))
        }
        assertEquals(
            InputProcessingState.ProcessingToolResult("create_file * 5"),
            status.onTiming(completed(4), 0),
        )
    }

    @Test
    fun parallelCompletionUpdatesOnlyItsToolAndRemovesFinishedNames() {
        val status = ToolBatchStatus(listOf("create_file", "read_file", "create_file", "read_file", "visit_web"))
        assertEquals(InputProcessingState.ExecutingTool("create_file * 2, read_file * 2, visit_web"), status.state)
        assertEquals(InputProcessingState.ExecutingTool("create_file * 2, read_file, visit_web"), status.onTiming(completed(3), 0))
        assertEquals(InputProcessingState.ExecutingTool("create_file * 2, read_file"), status.onTiming(completed(4), 0))
        assertEquals(InputProcessingState.ExecutingTool("create_file * 2"), status.onTiming(completed(1), 0))
    }

    @Test
    fun queuedStartedAndStoppedRecordsDoNotDecrementRemainingCalls() {
        val status = ToolBatchStatus(List(2) { "read_file" })
        val queued = ToolCallExecution(0, "test")
        assertNull(status.onTiming(queued, 0))
        assertNull(status.onTiming(queued.copy(startedMs = 1L), 0))
        assertNull(status.onTiming(queued.copy(endedMs = 2L, stopped = true), 0))
        assertNull(status.onTiming(completed(0).copy(stopped = true), 0))
        assertEquals(InputProcessingState.ExecutingTool("read_file * 2"), status.state)
    }

    @Test
    fun repeatedCompletionIsIgnoredAndCallOffsetsAreRespected() {
        val status = ToolBatchStatus(List(2) { "read_file" })
        assertEquals(InputProcessingState.ExecutingTool("read_file"), status.onTiming(completed(13), 12))
        assertNull(status.onTiming(completed(13), 12))
        assertEquals(InputProcessingState.ExecutingTool("read_file"), status.state)
        assertEquals(InputProcessingState.ProcessingToolResult("read_file * 2"), status.onTiming(completed(12), 12))
    }

    @Test
    fun packageToolNamesAreCountedSeparatelyAndKeepTheirFirstAppearanceOrder() {
        assertEquals(
            "alpha:run * 2, beta:run, create_file",
            formatToolCallCounts(listOf("alpha:run", "beta:run", "alpha:run", "create_file")),
        )
    }
}