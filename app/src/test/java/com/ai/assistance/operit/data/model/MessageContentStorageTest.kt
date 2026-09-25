package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentStorageTest {
    @Test fun encode_writesSectionsJson() {
        val stored = MessageContentStorage.encode("answer", listOf(MessageSection.Text("answer")))
        assertTrue(stored.startsWith("operit-sections:1:"))
        val decoded = MessageContentStorage.decode(stored)
        assertEquals("answer", decoded.content)
        assertEquals(1, decoded.sections.size)
    }

    @Test fun decode_readsLegacyMarkup() {
        val decoded = MessageContentStorage.decode("plain answer")
        assertEquals("plain answer", decoded.content)
        assertTrue(decoded.sections.single() is MessageSection.Text)
    }
}
