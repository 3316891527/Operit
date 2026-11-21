package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.data.model.InputProcessingState

internal fun formatToolCallCounts(toolNames: List<String>): String =
    formatToolNameCounts(toolNames.groupingBy { it }.eachCount())

private fun formatToolNameCounts(counts: Map<String, Int>): String =
    counts.entries.joinToString(", ") { (name, count) ->
        if (count > 1) "$name * $count" else name
    }

/** 由主线程按调用编号结算，排队和执行中的调用都计入剩余数量。 */
internal class ToolBatchStatus(private val toolNames: List<String>) {
    private val completed = BooleanArray(toolNames.size)
    private val remaining = toolNames.groupingBy { it }.eachCount().toMutableMap()
    private val totalSummary = formatToolNameCounts(remaining)

    val state: InputProcessingState
        get() = if (remaining.isEmpty()) {
            InputProcessingState.ProcessingToolResult(totalSummary)
        } else {
            InputProcessingState.ExecutingTool(formatToolNameCounts(remaining))
        }

    fun onTiming(record: ToolCallExecution, callOffset: Int): InputProcessingState? {
        // 排队、开始和取消时冻结的计时记录都不表示调用已返回结果。
        if (record.durationMs == null || record.stopped) return null
        val index = record.callIndex - callOffset
        require(index in toolNames.indices) { "工具计时编号超出当前批次：${record.callIndex}" }
        if (completed[index]) return null
        completed[index] = true
        val name = toolNames[index]
        val count = remaining.getValue(name)
        if (count == 1) remaining.remove(name) else remaining[name] = count - 1
        return state
    }
}