package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.core.chat.protocol.ThinkingMarkup
import com.ai.assistance.operit.data.model.MessageSection

/** 思考原文单独展示；正文继续使用原分句和朗读链，不采集或保存耗时。 */
internal object WaifuThinkingPresentation {
    private val progressAttribute = Regex(
        """\s+data-waifu-thinking-open\s*=\s*(['"])[\s\S]*?\1""",
        RegexOption.IGNORE_CASE,
    )

    fun snapshot(content: String, final: Boolean): String? {
        val thoughts = MessageSectionCodec.parse(content).filterIsInstance<MessageSection.Thinking>()
        return thoughts.joinToString("\n") { thought ->
            val raw = thought.markup
            val block = requireNotNull(ThinkingMarkup.blockAt(raw))
            val opening = progressAttribute.replace(raw.substring(0, block.bodyStart - 1), "")
            opening + " data-waifu-thinking-open=\"${!block.closed && !final}\">" + raw.substring(block.bodyStart)
        }.takeIf { it.isNotEmpty() }
    }

    fun isInProgress(content: String): Boolean = MessageSectionCodec.parse(content)
        .filterIsInstance<MessageSection.Thinking>()
        .any { thought ->
            val block = requireNotNull(ThinkingMarkup.blockAt(thought.markup))
            !block.closed && thought.markup.substring(0, block.bodyStart)
                .contains(" data-waifu-thinking-open=\"true\"")
        }
}
