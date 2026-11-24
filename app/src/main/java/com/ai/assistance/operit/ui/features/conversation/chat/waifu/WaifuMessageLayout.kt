package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

/** 只让同一次生成的连续过程和紧接正文保持相邻布局，头像由正文自己的片段负责。 */
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

/** 头像属于回复正文，思考、工具调用和隐藏协议都不占用头像位置。 */
internal fun isWaifuReplyNode(node: MarkdownNodeStable): Boolean = when (node.type) {
    MarkdownProcessorType.PLAIN_TEXT -> node.content.isNotBlank() && !WaifuProcessContent.isProcessOnly(node.content.trim())
    MarkdownProcessorType.XML_BLOCK -> {
        val tag = ChatMarkupRegex.normalizeToolLikeTagName(ChatMarkupRegex.extractOpeningTagName(node.content))
        tag !in WaifuProcessContent.tags && tag !in setOf("meta", "status")
    }
    else -> true
}
