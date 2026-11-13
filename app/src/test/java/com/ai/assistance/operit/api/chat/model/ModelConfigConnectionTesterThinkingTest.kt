package com.ai.assistance.operit.api.chat.model

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigConnectionTesterThinkingTest {
    @Test
    fun levelMappingUsesTheFirstConfiguredOptionForConnectionTests() {
        val config = configFor(
            provider = ApiProviderType.OPENAI,
            model = "gpt-5",
        )

        val request = resolveConnectionTestThinking(config, config.modelName)

        assertTrue(request.enableThinking)
        assertEquals("low", request.thinkingOptionId)
    }

    @Test
    fun toggleOnlyMappingEnablesThinkingWithoutAnOptionId() {
        val config = configFor(
            provider = ApiProviderType.ZHIPU,
            model = "glm-4.7-thinking",
        )

        val request = resolveConnectionTestThinking(config, config.modelName)

        assertTrue(request.enableThinking)
        assertEquals("", request.thinkingOptionId)
    }

    @Test
    fun unsupportedModelDoesNotReceiveThinkingParameters() {
        val config = configFor(
            provider = ApiProviderType.ZHIPU,
            model = "glm-3-turbo",
        )

        val request = resolveConnectionTestThinking(config, config.modelName)

        assertFalse(request.enableThinking)
        assertEquals("", request.thinkingOptionId)
    }

    @Test
    fun testUsesLowestLevelEvenWhenSavedChoiceIsHighOrStale() {
        for (saved in listOf("high", "outdated", "")) {
            val config = configFor(ApiProviderType.OPENAI_RESPONSES, "gpt-5").copy(thinkingOptionId = saved)
            val request = resolveConnectionTestThinking(config, config.modelName)
            assertEquals("low", request.thinkingOptionId)
            assertEquals(saved, config.thinkingOptionId)
        }
    }

    @Test
    fun requiredThinkingWithNoSavedChoiceUsesFirstConfiguredLevel() {
        val config = configFor(ApiProviderType.GOOGLE, "gemini-3-flash")
        val request = resolveConnectionTestThinking(config, config.modelName)
        assertTrue(request.enableThinking)
        assertEquals("MINIMAL", request.thinkingOptionId)
    }

    private fun configFor(provider: ApiProviderType, model: String): ModelConfigData =
        ModelConfigData(
            id = "connection-test",
            name = "Connection test",
            apiProviderType = provider,
            apiProviderTypeId = provider.name,
            modelName = model,
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(provider.name),
        )
}
