package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

/** 只让同一次生成的连续过程和紧接正文共用标题，后续分句继续独立展示。 */
internal fun waifuJoinsPrevious(previous: ChatMessage?, message: ChatMessage, enabled: Boolean, previousIsProcess: Boolean): Boolean =
    enabled && previousIsProcess && previous != null && previous.sender == "ai" && message.sender == "ai" &&
        previous.sentAt > 0L && previous.sentAt == message.sentAt &&
        previous.roleName == message.roleName && previous.provider == message.provider && previous.modelName == message.modelName

/** 专用块由自己的组件负责边界；普通文本和隐藏协议不因此失去聊天样式。 */
internal fun isWaifuStandaloneNode(node: MarkdownNodeStable, supportsXmlTag: (String) -> Boolean): Boolean = when (node.type) {
    MarkdownProcessorType.CODE_BLOCK, MarkdownProcessorType.TABLE, MarkdownProcessorType.IMAGE,
    MarkdownProcessorType.BLOCK_LATEX, MarkdownProcessorType.HTML_BLOCK -> true
    MarkdownProcessorType.XML_BLOCK -> {
        val tag = ChatMarkupRegex.normalizeToolLikeTagName(ChatMarkupRegex.extractOpeningTagName(node.content))
        tag != null && tag !in setOf("think", "thinking", "tool", "tool_result", "meta", "status", "font", "emotion") &&
            (tag in setOf("html", "details", "detail", "search") || supportsXmlTag(tag))
    }
    else -> false
}
