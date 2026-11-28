package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.core.chat.tools.timing.ToolExecutionTiming
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.toolmarkup.ToolResultMarkup

internal data class ToolCallTiming(
    val nodeIndex: Int,
    val resultSeen: Boolean,
    val clockId: String? = null,
    val startedMs: Long? = null,
    val durationMs: Long? = null,
    val batchEndedMs: Long? = null,
    val batchStopped: Boolean = false,
) {
    val complete: Boolean get() = resultSeen && !batchStopped &&
        (clockId == null || durationMs != null || batchEndedMs != null)
    fun ticking(live: Boolean): Boolean = live && clockId == ToolExecutionTiming.clockId &&
        startedMs != null && durationMs == null && batchEndedMs == null && !batchStopped

    fun interval(now: Long, live: Boolean): LongRange? {
        val start = startedMs
        if (start == null) return if (durationMs == 0L) 0L..0L else null
        val duration = durationMs
        val end = when {
            duration != null -> start + duration
            batchEndedMs != null -> batchEndedMs
            ticking(live) -> now
            else -> return null
        }
        return start..end.coerceAtLeast(start)
    }
}

internal data class ToolTimingSummary(
    val calls: List<ToolCallTiming>,
    val live: Boolean = false,
) {
    val complete: Boolean get() = calls.isNotEmpty() && calls.all { it.complete }
    val stopped: Boolean get() = !complete && calls.any { it.batchStopped }
    fun ticking(): Boolean = calls.any { it.ticking(live) }

    fun elapsedMillis(now: Long): Long? {
        // 活动组只统计已开始的真实区间；追加尚未执行的调用不能遮蔽已有时间。
        // 旧历史仍要求每项都有记录，避免把不完整快照当成整组耗时。
        if (calls.isEmpty() || (!live && calls.any { it.clockId == null })) return null
        val clockGroups = calls.filter { it.clockId != null }.groupBy { it.clockId }
        val known = clockGroups.values.map { group -> group.mapNotNull { it.interval(now, live) } }
        if (known.all { it.isEmpty() }) return null
        // 不同进程或重启后的单调时钟不能直接互相合并。
        return known.sumOf(::elapsedUnion)
    }
}

/** 合并重叠区间，工具并行时不重复累计；模型生成和确认等待不在区间内。 */
internal fun elapsedUnion(intervals: List<LongRange>): Long {
    val sorted = intervals.filter { it.last > it.first }.sortedBy { it.first }
    if (sorted.isEmpty()) return 0L
    var start = sorted.first().first
    var end = sorted.first().last
    var total = 0L
    for (interval in sorted.drop(1)) {
        if (interval.first <= end) end = maxOf(end, interval.last)
        else {
            total += end - start
            start = interval.first
            end = interval.last
        }
    }
    return total + end - start
}

/** 按批次和原始位置关联，支持同名工具、串行、并行及多轮工具调用。 */
internal class ToolTimingIndex private constructor(
    private val calls: Map<Int, ToolCallTiming>,
    private val live: Boolean = false,
) {
    fun tool(index: Int): ToolTimingSummary? = calls[index]?.let { ToolTimingSummary(listOf(it), live) }
    fun group(start: Int, end: Int): ToolTimingSummary =
        ToolTimingSummary(calls.values.filter { it.nodeIndex in start..end }, live)

    companion object {
        val Empty = ToolTimingIndex(emptyMap())

        fun parse(
            nodes: List<MarkdownNodeStable>,
            records: List<ToolCallExecution> = emptyList(),
            live: Boolean = false,
        ): ToolTimingIndex {
            data class Call(val node: Int, var result: Boolean = false)
            val calls = mutableListOf<Call>()
            for ((index, node) in nodes.withIndex()) {
                if (node.type != MarkdownProcessorType.XML_BLOCK) continue
                val tag = ChatMarkupRegex.normalizeToolLikeTagName(ChatMarkupRegex.extractOpeningTagName(node.content))
                if (tag == "tool") calls += Call(index)
                if (tag == "tool_result" && ToolResultMarkup.isCompleteBlock(node.content)) {
                    // 结果层已经按原始调用顺序发布，同名并行工具仍按位置对应。
                    calls.firstOrNull { !it.result }?.result = true
                }
            }
            val executions = records.associateBy { it.callIndex }
            return ToolTimingIndex(calls.mapIndexed { index, call ->
                val execution = executions[index]
                call.node to ToolCallTiming(
                    nodeIndex = call.node,
                    resultSeen = call.result,
                    clockId = execution?.clockId,
                    startedMs = execution?.startedMs,
                    durationMs = execution?.durationMs,
                    batchEndedMs = execution?.endedMs,
                    batchStopped = execution?.stopped == true,
                )
            }.toMap(), live)
        }
    }
}
