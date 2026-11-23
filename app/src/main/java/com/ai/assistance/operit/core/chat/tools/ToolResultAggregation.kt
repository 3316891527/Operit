package com.ai.assistance.operit.core.chat.tools

import com.ai.assistance.operit.core.chat.protocol.toolFailurePayload
import com.ai.assistance.operit.core.tools.HiddenTerminalCommandResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.TerminalCommandResultData
import com.ai.assistance.operit.core.tools.TerminalStreamEventData
import com.ai.assistance.operit.core.tools.TerminalTaskResultData
import com.ai.assistance.operit.core.tools.protocol.ToolResult

/** 终端完成结果已包含规范化原文，不把同一轮的 start/chunk 再拼入最终聊天结果。 */
internal fun aggregateToolResultValues(displayToolName: String, collected: List<ToolResult>): ToolResult {
    if (collected.isEmpty()) return ToolResult(
        toolName = displayToolName,
        success = false,
        result = StringResultData(""),
        error = "The tool execution returned no results.",
    )
    val last = collected.last()
    val finalData = last.result
    val isCompleteTerminalStream = finalData is TerminalCommandResultData && collected.dropLast(1).all { result ->
        val event = result.result
        result.success && event is TerminalStreamEventData && (event.type == "start" || event.type == "chunk") &&
            event.command == finalData.command && event.sessionId == finalData.sessionId
    }
    val isSingleTerminalResult = collected.size == 1 &&
        (finalData is HiddenTerminalCommandResultData || finalData is TerminalTaskResultData)
    if (isCompleteTerminalStream || isSingleTerminalResult) return last.copy(toolName = displayToolName)

    // 其他工具仍按原规则聚合，失败来源和说明保留在 error，独立输出保留在 result。
    val failures = collected.map { if (it.success) null else toolFailurePayload(it) }
    val combined = collected.mapIndexed { index, result ->
        val failure = failures[index]
        when {
            result.success -> result.result.toString().trim()
            index == collected.lastIndex && !failure?.message.isNullOrBlank() -> checkNotNull(failure).output
            else -> "Step error: ${checkNotNull(failure).render().ifBlank { "Unknown error" }}"
        }
    }.filter { it.isNotEmpty() }.joinToString("\n")
    val failure = failures.last()
    return ToolResult(
        toolName = displayToolName,
        success = last.success,
        result = StringResultData(combined),
        error = if (failure != null && failure.message.isNotBlank()) failure.message else last.error,
    )
}