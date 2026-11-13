package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelThinkingSelectionTest {
    @Test
    fun changingModelsWithinOneProviderReselectsOnlyAnInvalidLevel() {
        val config = ModelConfigData(
            id = "selection", name = "Selection", apiProviderType = ApiProviderType.GOOGLE,
            modelName = "gemini-2.5-flash", thinkingOptionId = "1024",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider("GOOGLE"),
        )
        assertEquals("1024", ModelConfigManager.nextThinkingOptionIdForProvider(config, "GOOGLE", "gemini-2.5-pro"))
        assertEquals("MINIMAL", ModelConfigManager.nextThinkingOptionIdForProvider(config, "GOOGLE", "gemini-3-flash"))
        assertEquals("", ModelConfigManager.nextThinkingOptionIdForProvider(config, "GOOGLE", "unconfigured-model"))
    }

    @Test
    fun recheckingEditedRulesKeepsAValidChoiceAndReplacesADeletedChoice() {
        val config = ModelConfigData(
            id = "selection", name = "Selection", apiProviderType = ApiProviderType.OPENAI_RESPONSES,
            modelName = "gpt-5", thinkingOptionId = "high",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider("OPENAI_RESPONSES"),
        )
        assertEquals("high", ModelConfigManager.nextThinkingOptionIdForProvider(config, "OPENAI_RESPONSES", "gpt-5"))
        val changed = config.copy(thinkingConfigurations = """[{"control":"levels","parameterLabel":"reasoning.effort","options":[{"id":"my-low","value":"low"}]}]""")
        assertEquals("my-low", ModelConfigManager.nextThinkingOptionIdForProvider(changed, "OPENAI_RESPONSES", "gpt-5"))
    }

    @Test
    fun endpointSpecificRuleUsesTheActualEndpoint() {
        val rules = """[
            {"control":"levels","match":{"endpointSuffix":"/responses"},"parameterLabel":"reasoning.effort","options":[{"id":"response-low","value":"low"}]},
            {"control":"levels","parameterLabel":"reasoning_effort","options":[{"id":"chat-low","value":"low"}]}
        ]"""
        val config = ModelConfigData(id="endpoint", name="Endpoint", apiProviderType=ApiProviderType.OPENAI_GENERIC, modelName="gpt-5", thinkingConfigurations=rules, thinkingOptionId="chat-low")
        assertEquals("response-low", ModelConfigManager.nextThinkingOptionIdForProvider(config, "OPENAI_GENERIC", "gpt-5", "https://example.test/v1/responses"))
    }
    @Test
    fun aSharedPreferenceCanBelongToTheSecondModel() {
        val config = multiModelConfig()
        assertEquals("second-high", ModelConfigManager.resolveThinkingOptionIdForModels(config))
        assertEquals("second-high", ModelConfigManager.validateThinkingOptionId(config, "second-high"))
        val reordered = config.copy(modelName = "second-model,first-model")
        assertEquals("second-high", ModelConfigManager.resolveThinkingOptionIdForModels(reordered))
    }

    @Test
    fun anUnsupportedFirstModelDoesNotEraseAnotherModelsPreference() {
        val config = multiModelConfig().copy(modelName = "unsupported-model,second-model")
        assertEquals("second-high", ModelConfigManager.resolveThinkingOptionIdForModels(config))
        assertEquals("second-low", ModelConfigManager.resolveThinkingOptionIdForModels(config, "removed"))
    }

    @Test
    fun removedModelsAndRulesReselectTheFirstAvailableLevel() {
        val config = multiModelConfig()
        assertEquals("first-low", ModelConfigManager.resolveThinkingOptionIdForModels(config.copy(modelName = "first-model")))
        assertEquals("", ModelConfigManager.resolveThinkingOptionIdForModels(config.copy(modelName = "unsupported-model")))
        assertEquals("first-low", ModelConfigManager.resolveThinkingOptionIdForModels(config.copy(thinkingConfigurations = config.thinkingConfigurations.replace("second-high", "renamed"))))
    }

    @Test
    fun savingAnOptionRejectsIdsUnusedByEveryConfiguredModel() {
        val config = multiModelConfig()
        assertThrows(IllegalArgumentException::class.java) {
            ModelConfigManager.validateThinkingOptionId(config, "not-configured")
        }
        assertEquals("first-low", ModelConfigManager.validateThinkingOptionId(config, ""))
        assertEquals("first-low", ModelConfigManager.validateThinkingOptionId(config, " first-low "))
    }

    private fun multiModelConfig() = ModelConfigData(
        id = "multi", name = "Multiple models", apiProviderType = ApiProviderType.OPENAI_GENERIC,
        modelName = "first-model,second-model", thinkingOptionId = "second-high",
        thinkingConfigurations = """[
            {"control":"levels","match":{"modelPrefix":"first-"},"parameterLabel":"reasoning_effort","options":[{"id":"first-low","value":"low"}]},
            {"control":"levels","match":{"modelPrefix":"second-"},"parameterLabel":"reasoning_effort","options":[{"id":"second-low","value":"low"},{"id":"second-high","value":"high"}]}
        ]""",
    )
}
