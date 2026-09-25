package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 数据库 content 列里的 sections JSON。旧标记字符串仍可直接读取。 */
object MessageContentStorage {
    private const val MARKER = "operit-sections:1:"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    data class StoredMessage(
        val content: String,
        val sections: List<MessageSection>,
    )

    @Serializable
    private data class Envelope(
        val sections: List<MessageSection>,
    )

    fun encode(content: String, sections: List<MessageSection>): String {
        val resolved = sections.ifEmpty { MessageSectionCodec.parse(content) }
        if (resolved.isEmpty()) {
            return content
        }
        return MARKER + json.encodeToString(Envelope(resolved))
    }

    fun decode(stored: String): StoredMessage {
        if (!stored.startsWith(MARKER)) {
            return StoredMessage(
                content = stored,
                sections = MessageSectionCodec.parse(stored),
            )
        }
        val envelope = json.decodeFromString<Envelope>(stored.removePrefix(MARKER))
        return StoredMessage(
            content = MessageSectionCodec.render(envelope.sections),
            sections = envelope.sections,
        )
    }
}