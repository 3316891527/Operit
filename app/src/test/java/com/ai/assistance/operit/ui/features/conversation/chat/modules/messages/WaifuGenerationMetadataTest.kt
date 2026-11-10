package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class WaifuGenerationMetadataTest {
    private fun reply(sentAt: Long = 100L, role: String = "角色", provider: String = "provider", model: String = "model") =
        ChatMessage(sender = "ai", content = "正文", sentAt = sentAt, roleName = role, provider = provider, modelName = model)

    @Test fun fiveSegmentsOnlyShowGenerationMetadataOnTheLastOne() {
        val messages = List(5) { reply() }
        assertEquals(listOf(false, false, false, false, true), messages.indices.map {
            showWaifuGenerationMetadata(messages, it, true)
        })
    }

    @Test fun normalModeKeepsEveryMessagesExistingMetadata() {
        val messages = List(5) { reply() }
        assertTrue(messages.indices.all { showWaifuGenerationMetadata(messages, it, false) })
    }

    @Test fun independentRequestsEachRetainTheirOwnFinalMetadata() {
        val messages = listOf(reply(100), reply(100), reply(200), reply(200))
        assertEquals(listOf(false, true, false, true), messages.indices.map {
            showWaifuGenerationMetadata(messages, it, true)
        })
    }

    @Test fun differentRolesProvidersModelsOrUserBoundariesAreNotMerged() {
        val first = reply()
        val following = listOf(reply(role = "另一个角色"), reply(provider = "other"), reply(model = "other"), ChatMessage(sender = "user"))
        following.forEach { next -> assertTrue(showWaifuGenerationMetadata(listOf(first, next), 0, true)) }
    }

    @Test fun legacyMessagesWithoutARequestIdentityRemainIndependent() {
        val messages = listOf(reply(sentAt = 0), reply(sentAt = 0))
        assertTrue(messages.indices.all { showWaifuGenerationMetadata(messages, it, true) })
    }
}
