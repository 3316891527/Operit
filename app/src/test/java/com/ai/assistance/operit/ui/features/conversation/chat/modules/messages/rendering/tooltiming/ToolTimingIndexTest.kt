package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming

import com.ai.assistance.operit.core.chat.tools.timing.ToolExecutionTiming
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.*
import org.junit.Test

class ToolTimingIndexTest {
    private fun node(xml: String) = MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, xml, emptyList())
    private fun tool() = node("<tool name=\"read_file\"></tool>")
    private fun result(closed: Boolean = true) = node(
        "<tool_result name=\"read_file\" status=\"success\"><content>正文</content>" +
            if (closed) "</tool_result>" else ""
    )
    private fun record(index: Int, start: Long, duration: Long? = null, end: Long? = null, stopped: Boolean = false) =
        ToolCallExecution(index, ToolExecutionTiming.clockId, start, duration, end, stopped)

    @Test
    fun parallelSameNameCallsKeepOwnersAndCountOverlapOnce() {
        val nodes = listOf(tool(), tool(), result(), result())
        val records = listOf(record(1, 1_000L, 2_000L), record(0, 1_000L, 5_000L))
        val saved = ToolTimingIndex.parse(nodes, records)
        assertEquals(5_000L, saved.group(0, nodes.lastIndex).elapsedMillis(100_000L))
        assertEquals(2_000L, saved.tool(1)?.elapsedMillis(100_000L))
        assertTrue(saved.group(0, nodes.lastIndex).complete)
    }

    @Test
    fun multipleBatchesExcludeTimeSpentGeneratingNextCall() {
        val nodes = listOf(tool(), result(), node("<think>模型继续思考</think>"), tool(), result())
        val records = listOf(record(0, 1_000L, 2_000L), record(1, 20_000L, 3_000L))
        assertEquals(5_000L, ToolTimingIndex.parse(nodes, records).group(0, nodes.lastIndex).elapsedMillis(90_000L))
    }

    @Test
    fun liveTimerAdvancesWithoutNewMarkdownAndStopsAfterCompletion() {
        val nodes = listOf(tool())
        val running = checkNotNull(ToolTimingIndex.parse(nodes, listOf(record(0, 1_000L)), live = true).tool(0))
        assertEquals(3_000L, running.elapsedMillis(4_000L))
        assertEquals(8_000L, running.elapsedMillis(9_000L))
        assertTrue(running.ticking())
        val static = checkNotNull(ToolTimingIndex.parse(nodes, listOf(record(0, 1_000L))).tool(0))
        assertNull(static.elapsedMillis(4_000L))
        assertFalse(static.ticking())
        val fixed = checkNotNull(ToolTimingIndex.parse(nodes + result(), listOf(record(0, 1_000L, 5_000L))).tool(0))
        assertEquals(5_000L, fixed.elapsedMillis(500_000L))
        assertFalse(fixed.ticking())
    }

    @Test
    fun cancellationFreezesPartialDurationWithoutClaimingCompletion() {
        val summary = checkNotNull(ToolTimingIndex.parse(listOf(tool()), listOf(record(0, 1_000L, end = 4_000L, stopped = true))).tool(0))
        assertTrue(summary.stopped)
        assertFalse(summary.complete)
        assertEquals(3_000L, summary.elapsedMillis(100_000L))
    }

    @Test
    fun legacyResultKeepsCompletionButDoesNotInventDuration() {
        val summary = checkNotNull(ToolTimingIndex.parse(listOf(tool(), result())).tool(0))
        assertTrue(summary.complete)
        assertNull(summary.elapsedMillis(100_000L))
    }

    @Test
    fun distinctClockDomainsDoNotMergePersistedIntervals() {
        val summary = ToolTimingSummary(listOf(
            ToolCallTiming(0, true, "previous-process", 1_000L, 2_000L),
            ToolCallTiming(1, true, "current-process", 1_000L, 3_000L),
        ))
        assertEquals(5_000L, summary.elapsedMillis(100_000L))
    }

    @Test
    fun truncatedResultCannotMarkTheBlockComplete() {
        val nodes = listOf(tool(), result(false))
        assertFalse(checkNotNull(ToolTimingIndex.parse(nodes, listOf(record(0, 1_000L, 2_000L))).tool(0)).complete)
    }
    @Test
    fun parallelSubsecondGroupRetainsMillisecondsInsteadOfSummingOverlaps() {
        val nodes = listOf(tool(), tool(), result(), result())
        val saved = ToolTimingIndex.parse(nodes, listOf(record(0, 10_000L, 175L), record(1, 10_050L, 125L)))
        assertEquals(175L, saved.group(0, nodes.lastIndex).elapsedMillis(100_000L))
        assertEquals(125L, saved.tool(1)?.elapsedMillis(100_000L))
        assertTrue(saved.group(0, nodes.lastIndex).complete)
    }

    @Test
    fun aQueuedOrPartialNewCallDoesNotHideTheRunningGroup() {
        val nodes = listOf(tool(), node("<tool name=\"read_file\"><param name=\"path\">"))
        val index = ToolTimingIndex.parse(nodes, listOf(record(0, 1_000L)), live = true)
        val summary = index.group(0, nodes.lastIndex)
        assertEquals(0L, summary.elapsedMillis(1_000L))
        assertEquals(3_000L, summary.elapsedMillis(4_000L))
        assertTrue(summary.ticking())
        assertNull(index.tool(1)?.elapsedMillis(4_000L))
    }

    @Test
    fun anotherBatchAppendsToCompletedIntervalsWithoutCountingTheModelGap() {
        val first = record(0, 1_000L, 2_000L)
        val nodes = listOf(tool(), result(), tool())
        val queued = ToolTimingIndex.parse(nodes, listOf(first), live = true).group(0, nodes.lastIndex)
        assertEquals(2_000L, queued.elapsedMillis(20_000L))
        assertFalse(queued.ticking())
        val running = ToolTimingIndex.parse(nodes, listOf(first, record(1, 20_000L)), live = true)
        assertEquals(5_000L, running.group(0, nodes.lastIndex).elapsedMillis(23_000L))
        assertEquals(3_000L, running.tool(2)?.elapsedMillis(23_000L))
    }

    @Test
    fun laterCallCanShowItsFixedTimeBeforeAnEarlierResultIsPublished() {
        val nodes = listOf(tool(), tool())
        val index = ToolTimingIndex.parse(nodes, listOf(record(0, 1_000L), record(1, 1_500L, 500L)), live = true)
        val later = checkNotNull(index.tool(1))
        assertFalse(later.complete)
        assertFalse(later.ticking())
        assertEquals(500L, later.elapsedMillis(9_000L))
        assertEquals(8_000L, index.group(0, 1).elapsedMillis(9_000L))
        assertTrue(index.group(0, 1).ticking())
    }

    @Test
    fun legacyPartialGroupRemainsUnknownWhenNoLiveHolderExists() {
        val nodes = listOf(tool(), result(), tool(), result())
        val summary = ToolTimingIndex.parse(nodes, listOf(record(0, 1_000L, 2_000L))).group(0, nodes.lastIndex)
        assertTrue(summary.complete)
        assertFalse(summary.ticking())
        assertNull(summary.elapsedMillis(100_000L))
    }

    @Test
    fun anUnfinishedRecordFromAnotherClockNeverStartsTicking() {
        val summary = checkNotNull(ToolTimingIndex.parse(
            listOf(tool()),
            listOf(ToolCallExecution(0, "previous-process", startedMs = 1_000L)),
            live = true,
        ).tool(0))
        assertFalse(summary.ticking())
        assertNull(summary.elapsedMillis(50_000L))
    }

    @Test
    fun stoppingALiveParallelGroupFreezesItsUnion() {
        val nodes = listOf(tool(), tool())
        val summary = ToolTimingIndex.parse(nodes, listOf(
            record(0, 1_000L, end = 4_000L, stopped = true),
            record(1, 2_000L, end = 4_000L, stopped = true),
        ), live = true).group(0, 1)
        assertTrue(summary.stopped)
        assertFalse(summary.ticking())
        assertEquals(3_000L, summary.elapsedMillis(5_000L))
        assertEquals(3_000L, summary.elapsedMillis(500_000L))
    }

    @Test
    fun invalidatedEarlierCallDoesNotTakeTheNextCallsRunningTime() {
        val nodes = listOf(tool(), tool())
        val index = ToolTimingIndex.parse(nodes, listOf(record(1, 5_000L)), live = true)
        assertNull(index.tool(0)?.elapsedMillis(8_000L))
        assertEquals(3_000L, index.tool(1)?.elapsedMillis(8_000L))
        assertEquals(3_000L, index.group(0, 1).elapsedMillis(8_000L))
        assertTrue(checkNotNull(index.tool(1)).ticking())
    }

    @Test
    fun aKnownZeroDurationRemainsVisibleBesideAQueuedCall() {
        val summary = ToolTimingIndex.parse(
            listOf(tool(), tool()), listOf(record(0, 1_000L, 0L)), live = true,
        ).group(0, 1)
        assertEquals(0L, summary.elapsedMillis(100_000L))
        assertFalse(summary.ticking())
    }
}
