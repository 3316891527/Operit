package com.ai.assistance.operit.data.chat.archive

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.ai.assistance.operit.core.chat.message.ChatMessageTimestampAllocator
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.MessageSection

/** 消息片段只解码一次，元数据独立解码，避免片段对象重新编码后再次创建。 */
private data class DecodedArchiveMessage(val fields: JsonObject, val sections: List<MessageSection>)

private fun decodeSectionsObject(decoder: JsonDecoder): DecodedArchiveMessage {
    val fields = decoder.decodeJsonElement().jsonObject.toMutableMap()
    val content = fields.remove("content")
    val sections = fields.remove("sections")
    val decoded = if (sections != null) MessageSectionStorage.decodeLegacySections(sections)
        else MessageSectionStorage.decodeLegacy(content?.jsonPrimitive?.content.orEmpty())
    fields["sections"] = JsonArray(emptyList())
    return DecodedArchiveMessage(JsonObject(fields), decoded)
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.StoredChatMessage")
@Serializable
private data class StoredChatMessage(
    val sender: String,
    val sections: List<MessageSection>,
    val timestamp: Long = ChatMessageTimestampAllocator.next(),
    val roleName: String = "",
    val selectedVariantIndex: Int = 0,
    val variantCount: Int = 1,
    val provider: String = "",
    val modelName: String = "",
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val sentAt: Long = 0L,
    val outputDurationMs: Long = 0L,
    val waitDurationMs: Long = 0L,
    val completedAt: Long = 0L,
    val displayMode: ChatMessageDisplayMode = ChatMessageDisplayMode.NORMAL,
    val isFavorite: Boolean = false,
)

object ChatMessageSerializer : KSerializer<ChatMessage> {
    override val descriptor: SerialDescriptor = StoredChatMessage.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ChatMessage) {
        encoder.encodeSerializableValue(StoredChatMessage.serializer(), StoredChatMessage(
            sender = value.sender,
            sections = value.resolvedSections(),
            timestamp = value.timestamp,
            roleName = value.roleName,
            selectedVariantIndex = value.selectedVariantIndex,
            variantCount = value.variantCount,
            provider = value.provider,
            modelName = value.modelName,
            inputTokens = value.inputTokens,
            outputTokens = value.outputTokens,
            cachedInputTokens = value.cachedInputTokens,
            sentAt = value.sentAt,
            outputDurationMs = value.outputDurationMs,
            waitDurationMs = value.waitDurationMs,
            completedAt = value.completedAt,
            displayMode = value.displayMode,
            isFavorite = value.isFavorite,
        ))
    }

    override fun deserialize(decoder: Decoder): ChatMessage {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Message sections require JSON")
        val decoded = decodeSectionsObject(jsonDecoder)
        val stored = jsonDecoder.json.decodeFromJsonElement(StoredChatMessage.serializer(), decoded.fields)
        return ChatMessage(
            content = MessageSectionCodec.render(decoded.sections),
            sender = stored.sender,
            sections = decoded.sections,
            timestamp = stored.timestamp,
            roleName = stored.roleName,
            selectedVariantIndex = stored.selectedVariantIndex,
            variantCount = stored.variantCount,
            provider = stored.provider,
            modelName = stored.modelName,
            inputTokens = stored.inputTokens,
            outputTokens = stored.outputTokens,
            cachedInputTokens = stored.cachedInputTokens,
            sentAt = stored.sentAt,
            outputDurationMs = stored.outputDurationMs,
            waitDurationMs = stored.waitDurationMs,
            completedAt = stored.completedAt,
            displayMode = stored.displayMode,
            isFavorite = stored.isFavorite,
        )
    }
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.StoredMessageVariant")
@Serializable
private data class StoredMessageVariant(
    val variantIndex: Int,
    val sections: List<MessageSection>,
    val roleName: String = "",
    val provider: String = "",
    val modelName: String = "",
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val sentAt: Long = 0L,
    val outputDurationMs: Long = 0L,
    val waitDurationMs: Long = 0L,
    val completedAt: Long = 0L,
)

object ArchivedMessageVariantSerializer : KSerializer<OperitArchivedMessageVariant> {
    override val descriptor: SerialDescriptor = StoredMessageVariant.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OperitArchivedMessageVariant) {
        encoder.encodeSerializableValue(StoredMessageVariant.serializer(), StoredMessageVariant(
            variantIndex = value.variantIndex,
            sections = value.sections,
            roleName = value.roleName,
            provider = value.provider,
            modelName = value.modelName,
            inputTokens = value.inputTokens,
            outputTokens = value.outputTokens,
            cachedInputTokens = value.cachedInputTokens,
            sentAt = value.sentAt,
            outputDurationMs = value.outputDurationMs,
            waitDurationMs = value.waitDurationMs,
            completedAt = value.completedAt,
        ))
    }

    override fun deserialize(decoder: Decoder): OperitArchivedMessageVariant {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Message sections require JSON")
        val decoded = decodeSectionsObject(jsonDecoder)
        val stored = jsonDecoder.json.decodeFromJsonElement(StoredMessageVariant.serializer(), decoded.fields)
        return OperitArchivedMessageVariant(
            variantIndex = stored.variantIndex,
            sections = decoded.sections,
            roleName = stored.roleName,
            provider = stored.provider,
            modelName = stored.modelName,
            inputTokens = stored.inputTokens,
            outputTokens = stored.outputTokens,
            cachedInputTokens = stored.cachedInputTokens,
            sentAt = stored.sentAt,
            outputDurationMs = stored.outputDurationMs,
            waitDurationMs = stored.waitDurationMs,
            completedAt = stored.completedAt,
        )
    }
}
