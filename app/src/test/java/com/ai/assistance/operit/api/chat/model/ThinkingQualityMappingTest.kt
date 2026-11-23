package com.ai.assistance.operit.api.chat.model

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.config.model.ApiProviderType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingQualityMappingTest {
    private fun mapping(providerType: ApiProviderType, modelName: String): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve(
            providerTypeId = providerType.name,
            modelName = modelName,
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(providerType.name)
        )

    @Test
    fun xaiMapsSupportedModelsToReasoningEfforts() {
        val mapping = mapping(ApiProviderType.XAI, "grok-4.6")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning_effort", mapping.parameterLabel)
        assertEquals(listOf("low", "medium", "high", "xhigh"), mapping.options.map { it.displayLabel })
        assertEquals("high", mapping.textValueFor("high"))
    }

    @Test
    fun xaiKeepsGenericControlsForNewGrokModels() {
        val mapping = mapping(ApiProviderType.XAI, "grok-3-mini")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertTrue(mapping.options.isNotEmpty())
    }

    @Test
    fun openAiUsesFiveNamedEffortValues() {
        val mapping = mapping(ApiProviderType.OPENAI, "gpt-5.6-luna")

        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), mapping.options.map { it.displayLabel })
        assertEquals("high", mapping.textValueFor("high"))
    }

    @Test
    fun providersUseModelSpecificWireValues() {
        val gemini = mapping(ApiProviderType.GOOGLE, "gemini-3-flash")
        val deepseek = mapping(ApiProviderType.DEEPSEEK, "deepseek-reasoner")

        assertEquals(listOf("MINIMAL", "LOW", "MEDIUM", "HIGH"), gemini.options.map { it.displayLabel })
        assertEquals(listOf("low", "high", "max"), deepseek.options.map { it.displayLabel })
        assertEquals(listOf("low", "high", "max"), deepseek.options.map { deepseek.textValueFor(it.id) })
    }

    @Test
    fun deepSeekChatRemainsInTheDeepSeekFamilyMapping() {
        val mapping = mapping(ApiProviderType.DEEPSEEK, "deepseek-chat")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals(3, mapping.options.size)
        assertEquals(listOf("low", "high", "max"), mapping.options.map { it.id })
    }

    @Test
    fun providerSpecificModelMatchingControlsLevelSupport() {
        val gptOss = mapping(ApiProviderType.NVIDIA, "gpt-oss-120b")
        val otherNvidiaModel = mapping(ApiProviderType.NVIDIA, "nemotron-future")

        assertTrue(gptOss.options.isNotEmpty())
        assertTrue(otherNvidiaModel.options.isNotEmpty())
    }

    @Test
    fun numericProviderValuesRemainTyped() {
        val mapping = mapping(ApiProviderType.SILICONFLOW, "Qwen3")

        assertEquals("128", mapping.options.first().displayLabel)
        assertEquals(8_192, mapping.numberValueFor("8192"))
    }

    @Test
    fun zhipuGlm53UsesRequiredReasoningEffortLevels() {
        val mapping = mapping(ApiProviderType.ZHIPU, "glm-5.3")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning_effort", mapping.parameterLabel)
        assertTrue(mapping.reasoningRequired)
        assertEquals(listOf("low", "high", "max"), mapping.options.map { it.id })
    }

    @Test
    fun zhipuOlderThinkingModelsUseToggleOnly() {
        val mapping = mapping(ApiProviderType.ZHIPU, "glm-4.7-thinking")

        assertEquals(ThinkingQualityControl.TOGGLE_ONLY, mapping.control)
        assertEquals("thinking.type", mapping.parameterLabel)
        assertFalse(mapping.reasoningRequired)
    }

    @Test
    fun zhipuLegacyModelsDoNotShowThinkingControls() {
        val mapping = mapping(ApiProviderType.ZHIPU, "glm-3-turbo")

        assertEquals(ThinkingQualityControl.UNSUPPORTED, mapping.control)
    }

    @Test
    fun resolveOptionIdFallsBackToFirstLevelWhenBlank() {
        val mapping = mapping(ApiProviderType.OPENAI, "gpt-5.6-luna")
        assertEquals(mapping.options.first().id, mapping.resolveOptionId("", ""))
    }

    @Test
    fun resolveOptionIdKeepsPreferredWhenSupported() {
        val mapping = mapping(ApiProviderType.OPENAI, "gpt-5.6-luna")
        val preferred = mapping.options.last().id
        assertEquals(preferred, mapping.resolveOptionId(preferred, mapping.options.first().id))
    }

    @Test
    fun emptyModelDoesNotExposeGenericThinkingLevels() = runBlocking {
        listOf(ApiProviderType.OPENAI_GENERIC, ApiProviderType.DEEPSEEK, ApiProviderType.SILICONFLOW)
            .forEach { provider ->
                val rules = ModelThinkingConfigDefaults.forProvider(provider.name)
                val genericMapping = ThinkingQualityMappingRegistry.resolve(provider.name, "", "", rules)
                assertEquals(ThinkingQualityControl.LEVELS, genericMapping.control)

                val preview = ThinkingQualityMappingRegistry.resolveForModel(provider.name, "", "", rules)

                assertEquals(ThinkingQualityControl.UNSUPPORTED, preview.control)
                assertTrue(preview.options.isEmpty())
            }
    }

    @Test
    fun whitespaceModelDoesNotExposeGenericThinkingToggle() = runBlocking {
        val provider = ApiProviderType.MOONSHOT
        val rules = ModelThinkingConfigDefaults.forProvider(provider.name)
        assertEquals(
            ThinkingQualityControl.TOGGLE_ONLY,
            ThinkingQualityMappingRegistry.resolve(provider.name, " \t\n", "", rules).control
        )

        val preview = ThinkingQualityMappingRegistry.resolveForModel(provider.name, " \t\n", "", rules)

        assertEquals(ThinkingQualityControl.UNSUPPORTED, preview.control)
        assertTrue(preview.options.isEmpty())
    }

    @Test
    fun configuredModelsKeepTheirLevelToggleAndUnsupportedControls() = runBlocking {
        val cases = listOf(
            Triple(ApiProviderType.OPENAI_GENERIC, "configured-model", ThinkingQualityControl.LEVELS),
            Triple(ApiProviderType.MOONSHOT, "kimi-k2.5", ThinkingQualityControl.TOGGLE_ONLY),
            Triple(ApiProviderType.ZHIPU, "glm-3-turbo", ThinkingQualityControl.UNSUPPORTED),
        )
        cases.forEach { (provider, model, expectedControl) ->
            val preview = ThinkingQualityMappingRegistry.resolveForModel(
                provider.name, model, "", ModelThinkingConfigDefaults.forProvider(provider.name)
            )
            assertEquals(expectedControl, preview.control)
        }
    }

    @Test
    fun blankSavedLevelBuildsFirstLevelRequestWithoutSavingADefault() {
        val request = JSONObject()

        ThinkingConfigurationApplier.apply(
            request, ApiProviderType.DEEPSEEK.name, "deepseek-reasoner", "",
            ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name), true, ""
        )

        assertEquals("low", request.getString("reasoning_effort"))
        assertEquals("enabled", request.getJSONObject("thinking").getString("type"))
    }

    @Test
    fun explicitSelectionStillControlsTheRequestLevel() {
        val request = JSONObject()

        ThinkingConfigurationApplier.apply(
            request, ApiProviderType.DEEPSEEK.name, "deepseek-reasoner", "",
            ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name), true, "high"
        )

        assertEquals("high", request.getString("reasoning_effort"))
    }

    @Test
    fun blankSavedLevelDoesNotEnableOptionalThinking() {
        val request = JSONObject()

        ThinkingConfigurationApplier.apply(
            request, ApiProviderType.DEEPSEEK.name, "deepseek-reasoner", "",
            ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name), false, ""
        )

        assertFalse(request.has("reasoning_effort"))
        assertEquals("disabled", request.getJSONObject("thinking").getString("type"))
    }

    @Test
    fun switchingModelsUsesTheCurrentModelsDefaultForAnIncompatibleSavedLevel() = runBlocking {
        val previous = mapping(ApiProviderType.DEEPSEEK, "deepseek-reasoner")
        val provider = ApiProviderType.GOOGLE
        val rules = ModelThinkingConfigDefaults.forProvider(provider.name)
        val current = ThinkingQualityMappingRegistry.resolveForModel(
            provider.name, "gemini-2.5-pro", "", rules
        )
        val request = JSONObject()

        ThinkingConfigurationApplier.apply(
            request, provider.name, "gemini-2.5-pro", "", rules, true, "high"
        )

        assertEquals("high", previous.optionFor("high")?.id)
        assertEquals("1024", current.resolveRequest(true, "high").thinkingOptionId)
        assertEquals(
            1024,
            request.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getInt("thinkingBudget")
        )
    }

    @Test
    fun requiredThinkingStillUsesItsDefaultWithoutAPersistedLevel() {
        val request = JSONObject()

        ThinkingConfigurationApplier.apply(
            request, ApiProviderType.GOOGLE.name, "gemini-3-flash", "",
            ModelThinkingConfigDefaults.forProvider(ApiProviderType.GOOGLE.name), false, ""
        )

        val thinking = request.getJSONObject("generationConfig").getJSONObject("thinkingConfig")
        assertTrue(thinking.getBoolean("includeThoughts"))
        assertEquals("MINIMAL", thinking.getString("thinkingLevel"))
    }
}
