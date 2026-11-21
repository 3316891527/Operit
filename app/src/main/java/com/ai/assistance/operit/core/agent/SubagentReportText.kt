package com.ai.assistance.operit.core.agent

internal fun subagentFinalReport(raw: String): String {
    val sections = com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec.parse(raw)
    val lastTool = sections.indexOfLast { it is com.ai.assistance.operit.data.model.MessageSection.ToolCall || it is com.ai.assistance.operit.data.model.MessageSection.ToolResult }
    return sections.drop(lastTool + 1).filterIsInstance<com.ai.assistance.operit.data.model.MessageSection.Text>()
        .joinToString("") { it.content }.trim()
}