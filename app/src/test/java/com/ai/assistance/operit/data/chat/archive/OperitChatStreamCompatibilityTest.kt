package com.ai.assistance.operit.data.chat.archive

import java.io.StringReader
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection
import org.junit.Assert.*
import org.junit.Test

/** 使用应用实际序列化器检查 metadata/消息拆分后的旧格式和候选版本语义。 */
@OptIn(ExperimentalSerializationApi::class)
class OperitChatStreamCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val fields = listOf(OperitArchivedChat.serializer().descriptor, ChatHistory.serializer().descriptor)
        .flatMap { descriptor -> (0 until descriptor.elementsCount).map(descriptor::getElementName) }.toSet() - "messages"

    private fun read(source: String, consume: suspend (StreamedChat) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("operit-import-compat").toFile()
        try {
            OperitChatStreamReader(directory, fields).read(StringReader(source), {}, consume)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun archiveKeepsAllChatMetadataWhenMessagesAreReadSeparately() {
        val source = """{"chats":[{
            "messages":[{"baseMessage":{"sender":"user","content":"原文","timestamp":123}}],
            "id":"a","title":"会话","createdAt":"2026-01-01T00:00:00","updatedAt":"2026-02-01T00:00:00",
            "inputTokens":1,"outputTokens":2,"currentWindowSize":3,"group":"组","displayOrder":-9,
            "workspace":"/workspace","workspaceEnv":"linux","parentChatId":"parent","characterCardName":"Echo",
            "characterGroupId":"characters","locked":true,"pinned":true,"archived":true,"folderId":"folder",
            "lastUsedAt":9223372036854775807,"folderPath":[{"id":"folder","name":"组","displayOrder":-1}],"usageBreakdownJson":"{\"user\":1}"
        }]}"""
        val expected = json.decodeFromString<OperitChatArchive>(source).chats.single()
        read(source) { chat ->
            val metadata = json.decodeFromJsonElement<OperitArchivedChat>(chat.metadata)
            assertEquals(expected.copy(messages = emptyList()), metadata)
            chat.forEachMessage { _, element ->
                assertEquals(expected.messages.single(), json.decodeFromJsonElement<OperitArchivedMessage>(element))
            }
        }
    }

    @Test fun archiveVariantsAndSelectedIndexSurviveMessageWiseDecode() {
        read("""{"formatVersion":2,"chats":[{"id":"v","title":"候选","messages":[{
            "baseMessage":{"sender":"ai","content":"基础","timestamp":123,"selectedVariantIndex":2},
            "variants":[{"variantIndex":2,"content":"候选","provider":"provider","modelName":"model","inputTokens":42}]
        }]}]}""") { chat ->
            chat.forEachMessage { _, element ->
                val message = json.decodeFromJsonElement<OperitArchivedMessage>(element)
                assertEquals(2, message.baseMessage.selectedVariantIndex)
                assertEquals("基础", message.baseMessage.content)
                val variant = message.variants.single()
                assertEquals(listOf(MessageSection.Text("候选")), variant.sections)
                assertEquals(2, variant.variantIndex)
                assertEquals("provider", variant.provider)
                assertEquals(42L, variant.inputTokens)
            }
        }
    }

    @Test fun sectionsRemainStructuredWithoutRenderingAndReparsingTheEntireChat() {
        read("""{"formatVersion":3,"chats":[{"id":"s","title":"结构","messages":[{
            "baseMessage":{"sender":"ai","sections":[{"type":"text","content":"正文😀"},{"type":"thinking","content":"思考"}],"timestamp":456}
        }]}]}""") { chat ->
            chat.forEachMessage { _, element ->
                val message = json.decodeFromJsonElement<OperitArchivedMessage>(element).baseMessage
                assertEquals(listOf(MessageSection.Text("正文😀"), MessageSection.Thinking("思考")), message.sections)
            }
        }
    }

    @Test fun legacyChatArrayUsesPublishedMessageMigrationAndDefaults() {
        val source = """[{"title":"旧记录","id":"legacy","createdAt":"2026-01-01T00:00:00","updatedAt":"2026-01-01T00:00:00","messages":[{"sender":"user","content":"你好😀","timestamp":123}]}]"""
        val expected = json.decodeFromString<List<ChatHistory>>(source).single()
        read(source) { chat ->
            assertFalse(chat.archived)
            assertEquals(expected.copy(messages = emptyList()), json.decodeFromJsonElement<ChatHistory>(chat.metadata))
            chat.forEachMessage { _, element -> assertEquals(expected.messages.single(), json.decodeFromJsonElement<ChatMessage>(element)) }
        }
    }

    @Test fun missingRequiredMessagesIsStillRejectedByThePublishedSerializer() {
        try {
            read("""[{"title":"missing"}]""") { chat -> json.decodeFromJsonElement<ChatHistory>(chat.metadata) }
            fail()
        } catch (_: SerializationException) { }
    }
}