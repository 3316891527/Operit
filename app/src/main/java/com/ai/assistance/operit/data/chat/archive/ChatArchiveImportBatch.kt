package com.ai.assistance.operit.data.chat.archive

import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity

/** 按消息数及序列化文本总量限制批次；单条超大消息独立写入，不积累整个会话。 */
internal class ChatArchiveImportBatch(
    private val maximumMessages: Int = 32,
    private val maximumCharacters: Long = 256 * 1024L,
    private val save: suspend (List<MessageEntity>, List<MessageVariantEntity>) -> Unit,
) {
    private val messages = mutableListOf<MessageEntity>()
    private val variants = mutableListOf<MessageVariantEntity>()
    private var characters = 0L

    init { require(maximumMessages > 0 && maximumCharacters > 0) }

    suspend fun add(message: MessageEntity, messageVariants: List<MessageVariantEntity>) {
        val size = message.sections.length.toLong() + message.searchText.length +
            messageVariants.sumOf { it.sections.length.toLong() + it.searchText.length }
        if (messages.isNotEmpty() && (messages.size >= maximumMessages || characters + size > maximumCharacters)) flush()
        messages += message
        variants += messageVariants
        characters += size
        if (messages.size >= maximumMessages || characters >= maximumCharacters) flush()
    }

    suspend fun flush() {
        if (messages.isEmpty()) return
        save(messages, variants)
        messages.clear()
        variants.clear()
        characters = 0L
    }
}