package com.ai.assistance.operit.ui.features.conversation.chat.subagent

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.util.toolmarkup.ToolResultMarkup

internal enum class SubagentToolPhase { PREPARING, RUNNING, COMPLETED, FAILED, STOPPED, RETURNED }

/** 展示身份取自片段位置，不使用会随流式输出变化的正文作为展开状态键。 */
internal sealed interface SubagentTranscriptBlock {
    val index: Int
    data class Prose(override val index: Int, val content: String) : SubagentTranscriptBlock
    data class Reasoning(override val index: Int, val content: String) : SubagentTranscriptBlock
    data class Tool(
        override val index: Int,
        val rawName: String,
        val name: String,
        val parameters: String,
        val output: String? = null,
        val phase: SubagentToolPhase,
    ) : SubagentTranscriptBlock
}

/** 调用与结果按真实目标名称和出现顺序配对；不修改工具身份或原始协议。 */
internal fun subagentTranscriptBlocks(raw: String, running: Boolean): List<SubagentTranscriptBlock> {
    val blocks = mutableListOf<SubagentTranscriptBlock>()
    val pending = mutableListOf<Int>()
    MessageSectionCodec.parseForDisplay(raw).forEachIndexed { index, section ->
        when (section) {
            is MessageSection.Text -> if (section.content.isNotBlank()) {
                blocks.add(SubagentTranscriptBlock.Prose(index, section.content))
            }
            is MessageSection.Thinking -> blocks.add(SubagentTranscriptBlock.Reasoning(index, section.content))
            is MessageSection.ToolCall -> {
                val proxy = section.name == "package_proxy" || section.name == "proxy"
                val name = if (proxy) section.params["tool_name"].orEmpty().trim() else section.name
                val parameters = if (proxy) section.params["params"].orEmpty()
                    else section.params.entries.joinToString("\n\n") { (key, value) -> "$key: $value" }
                val complete = ChatMarkupRegex.toolTag.matches(section.raw) || ChatMarkupRegex.toolSelfClosingTag.matches(section.raw)
                pending.add(blocks.size)
                blocks.add(SubagentTranscriptBlock.Tool(index, section.name, name, parameters, phase = when {
                    !running -> SubagentToolPhase.STOPPED
                    complete -> SubagentToolPhase.RUNNING
                    else -> SubagentToolPhase.PREPARING
                }))
            }
            is MessageSection.ToolResult -> {
                val match = pending.indexOfFirst { position ->
                    val call = blocks[position] as SubagentTranscriptBlock.Tool
                    call.name == section.name || call.rawName == section.name
                }
                val complete = ToolResultMarkup.isCompleteBlock(section.raw)
                val phase = when {
                    !complete -> if (running) SubagentToolPhase.RUNNING else SubagentToolPhase.STOPPED
                    section.status.equals("success", ignoreCase = true) -> SubagentToolPhase.COMPLETED
                    section.status.isBlank() -> SubagentToolPhase.RETURNED
                    else -> SubagentToolPhase.FAILED
                }
                if (match >= 0) {
                    val position = pending[match]
                    val call = blocks[position] as SubagentTranscriptBlock.Tool
                    blocks[position] = call.copy(output = section.content, phase = phase)
                    if (complete) pending.removeAt(match)
                } else {
                    blocks.add(SubagentTranscriptBlock.Tool(index, section.name, section.name, "", section.content, phase))
                }
            }
            // 协议元数据不展示，状态与搜索由普通正文之外的入口处理。
            is MessageSection.Protocol, is MessageSection.Status, is MessageSection.Search -> Unit
        }
    }
    return blocks
}

/** 历史事件仍保存代理入口，界面按消息中的调用顺序解析显示名，不改写旧记录。 */
internal fun subagentToolEventLabels(names: List<String>, transcripts: List<String>): List<String> {
    val calls = transcripts.flatMap { subagentTranscriptBlocks(it, running = false) }
        .filterIsInstance<SubagentTranscriptBlock.Tool>().toMutableList()
    return names.map { eventName ->
        val index = calls.indexOfFirst { it.rawName == eventName || it.name == eventName }
        if (index < 0) eventName else calls.removeAt(index).name
    }
}
