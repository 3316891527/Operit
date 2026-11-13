package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection

/** Waifu 分句共用请求 sentAt；统计只属于该连续回复的末条，原数据和消息操作保留。 */
internal fun showWaifuGenerationMetadata(messages: List<ChatMessage>, index: Int, waifuEnabled: Boolean): Boolean {
    if (!waifuEnabled) return true
    val message = messages[index]
    if (message.sender != "ai" || message.sentAt <= 0L) return true
    for (nextIndex in index + 1 until messages.size) {
        val next = messages[nextIndex]
        if (next.sender != "ai" || next.sentAt != message.sentAt ||
            next.roleName != message.roleName || next.provider != message.provider || next.modelName != message.modelName
        ) return true
        // Hidden thinking records from the removed feature must not take the final reply footer.
        val sections = next.displaySections()
        val thinkingOnly = sections.any { it is MessageSection.Thinking } && sections.all {
            it is MessageSection.Thinking ||
                (it is MessageSection.Text && it.content.isBlank())
        }
        if (!thinkingOnly) return false
    }
    return true
}
