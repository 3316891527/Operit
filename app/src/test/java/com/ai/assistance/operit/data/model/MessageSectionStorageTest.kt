package com.ai.assistance.operit.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSectionStorageTest {
    @Test fun encode_writesReadableSectionsArray() {
        val message = ChatMessage(sender = "ai", content = sample())
        val entity = MessageEntity.fromChatMessage("chat", message, orderIndex = 0)
        assertTrue(entity.sections.startsWith("[{\"type\":\"thinking\""))
        assertFalse(entity.sections.contains("operit-sections:"))
        assertFalse(entity.searchText.contains("cGF5bG9hZA=="))
        assertTrue(entity.searchText.contains("answer"))
    }

    @Test fun entityRoundTrip_keepsProtocolAndHidesItFromDisplay() {
        val original = ChatMessage(sender = "ai", content = sample())
        val restored = MessageEntity.fromChatMessage("chat", original, 0).toChatMessage()
        assertEquals(original.content, restored.content)
        assertTrue(restored.sections.any { it is MessageSection.Protocol })
        assertEquals("answer", restored.displayContent())
    }

    @Test fun archiveRoundTrip_exportsSectionsWithoutContent() {
        val json = Json { encodeDefaults = true }
        val message = ChatMessage(sender = "ai", content = sample(), timestamp = 7L)
        val encoded = json.encodeToString(ChatMessage.serializer(), message)
        assertFalse(encoded.contains("\"content\""))
        assertTrue(encoded.contains("\"sections\""))
        val decoded = json.decodeFromString(ChatMessage.serializer(), encoded)
        assertEquals(message.content, decoded.content)
        assertEquals("answer", decoded.displayContent())
    }

    @Test fun archiveImport_convertsLegacyContent() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """{"sender":"ai","content":"<think>draft</think>answer","timestamp":7}"""
        val decoded = json.decodeFromString(ChatMessage.serializer(), legacy)
        assertEquals(2, decoded.sections.size)
        assertEquals("draft", (decoded.sections[0] as MessageSection.Thinking).content)
        assertEquals("answer", decoded.displayContent())
    }

    private fun sample(): String =
        "<think>draft</think>answer<meta provider=\"openai:responses_reasoning\">cGF5bG9hZA==</meta>"
}
