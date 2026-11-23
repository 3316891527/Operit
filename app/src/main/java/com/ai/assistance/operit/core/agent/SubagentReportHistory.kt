package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.core.chat.protocol.ThinkingMarkup
import com.ai.assistance.operit.data.model.MessageSection

/** 封存被时间窗口打断的思考，保留 token 边界，避免下一段报告被当成旧思考正文。 */
internal fun sealSubagentReportPrefix(raw: String): String {
    val last = MessageSectionCodec.parseForDisplay(raw).lastOrNull() as? MessageSection.Thinking ?: return raw
    if (ThinkingMarkup.isClosed(last.markup)) return raw
    val opening = Regex("<think(?:ing)?\\b[^<>]*>", RegexOption.IGNORE_CASE).find(last.markup)?.value ?: return raw
    return raw + opening.replaceFirst("<", "</")
}

/** 报告请求只带可读证据，移除思考及未配对工具，不能向供应商重发未结束的工具调用。 */
internal fun subagentReportEvidenceContent(raw: String): String =
    MessageSectionCodec.parseForDisplay(raw).joinToString("") { section ->
        when (section) {
            is MessageSection.Text -> section.content
            is MessageSection.ToolResult -> {
                val longestFence = Regex("`+").findAll(section.content).maxOfOrNull { it.value.length } ?: 0
                val fence = "`".repeat(maxOf(3, longestFence + 1))
                "\nRecorded tool result (${section.name}, ${section.status}):\n$fence\n${section.content}\n$fence\n"
            }
            else -> ""
        }
    }