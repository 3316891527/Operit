package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages

import com.ai.assistance.operit.data.model.ChatMessage

/** Waifu 分句共用请求 sentAt；统计只属于该连续回复的末条，原数据和消息操作保留。 */
internal fun showWaifuGenerationMetadata(messages: List<ChatMessage>, index: Int, waifuEnabled: Boolean): Boolean {
    if (!waifuEnabled) return true
    val message = messages[index]
    if (message.sender != "ai" || message.sentAt <= 0L) return true
    val next = messages.getOrNull(index + 1) ?: return true
    return next.sender != "ai" || next.sentAt != message.sentAt ||
        next.roleName != message.roleName || next.provider != message.provider || next.modelName != message.modelName
}
