package com.ai.assistance.operit.core.agent

internal fun subagentFinalReport(raw: String, reportOnly: Boolean = false): String {
    val sections = com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec.parseForDisplay(raw)
    val lastTool = sections.indexOfLast { it is com.ai.assistance.operit.data.model.MessageSection.ToolCall || it is com.ai.assistance.operit.data.model.MessageSection.ToolResult }
    if (reportOnly && lastTool >= 0) return ""
    return sections.drop(lastTool + 1).filterIsInstance<com.ai.assistance.operit.data.model.MessageSection.Text>()
        .joinToString("") { it.content }.trim()
}
