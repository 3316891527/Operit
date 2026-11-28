package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
internal fun markdownProcessTag(node: MarkdownNodeStable): String? {
    if (node.type != MarkdownProcessorType.XML_BLOCK) return null
    return ChatMarkupRegex.normalizeToolLikeTagName(ChatMarkupRegex.extractOpeningTagName(node.content)?.lowercase())
}

/** 工具过程有自己的唯一入口；整段回复用时只接管已完成的纯思考前缀。 */
internal fun completedProcessEnd(
    nodes: List<MarkdownNodeStable>,
    collapseEnabled: Boolean = true,
    streaming: Boolean = false,
    showThinkingProcess: Boolean = true,
): Int {
    if (!collapseEnabled || streaming || !showThinkingProcess) return -1
    var end = -1
    for ((index, node) in nodes.withIndex()) {
        when (markdownProcessTag(node)) {
            "tool", "tool_result", "search" -> return -1
            "think", "thinking" -> end = index
        }
    }
    if (end < 0) return -1
    // 纯思考或中断且没有答案时保持可见，不把回复折成空白。
    val answerStart = (end + 1 until nodes.size).firstOrNull { index ->
        val node = nodes[index]
        val tag = markdownProcessTag(node)
        tag != "meta" && tag != "status" &&
            (node.content.isNotBlank() || node.children.any { child -> child.content.isNotBlank() })
    } ?: return -1
    return answerStart - 1
}

internal fun responseProcessItemVisible(itemEnd: Int, processEnd: Int, expanded: Boolean): Boolean =
    processEnd < 0 || expanded || itemEnd > processEnd


internal fun responseProcessKey(timestamp: Long, variant: Int): String =
    "single-process-$timestamp/$variant"
