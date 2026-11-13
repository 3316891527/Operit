package com.ai.assistance.operit.api.chat.model

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingConfigurationValidationTest {
    @Test
    fun allBuiltinRulesPassSemanticValidation() {
        ThinkingQualityMappingRegistry.validateConfigurations(ModelThinkingConfigDefaults.DEFAULT_JSON)
    }

    @Test
    fun emptyAndDuplicateOptionsAreRejected() {
        for (options in listOf("[]", """[{"id":"x","path":"effort","value":"low"},{"id":"x","path":"effort","value":"high"}]""")) {
            assertThrows(IllegalArgumentException::class.java) {
                ThinkingQualityMappingRegistry.validateConfigurations("""[{"control":"levels","options":$options}]""")
            }
        }
    }

    @Test
    fun invalidPathsAndRegexesAreRejectedAtSaveTime() {
        for (rule in listOf(
            """{"control":"toggle_only","enable":[{"path":"","value":true}]}""",
            """{"control":"toggle_only","enable":[{"path":"a..b","value":true}]}""",
            """{"control":"toggle_only","match":{"modelRegex":["["]}}"""
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                ThinkingQualityMappingRegistry.validateConfigurations("[$rule]")
            }
        }
    }

    @Test
    fun disabledDraftMayBeSavedAndOptionalNoOpLevelIsRetained() {
        ThinkingQualityMappingRegistry.validateConfigurations("""[{"enabled":false,"control":"levels","options":[]}]""")
        ThinkingQualityMappingRegistry.validateConfigurations("""[{"control":"levels","options":[{"id":"auto","label":"Use model default"}]}]""")
    }

    @Test
    fun firstMatchingRuleKeepsItsOriginalIndexWithoutAutomaticReordering() {
        val rules = """[{"id":"disabled","enabled":false},{"id":"general","control":"unsupported"},{"id":"specific","match":{"modelPrefix":["gpt"]},"control":"toggle_only"}]"""
        val match = ThinkingQualityMappingRegistry.resolveMatch("OPENAI_GENERIC", "gpt-5", "", rules)
        assertEquals("general", match?.id)
        assertEquals(1, match?.index)
    }

    @Test
    fun requiredModelUsesTheSameValidOptionResolutionAsConnectionTests() {
        val mapping = ThinkingQualityMappingRegistry.resolve("GOOGLE", "gemini-3-flash", ModelThinkingConfigDefaults.forProvider("GOOGLE"))
        val request = mapping.resolveRequest(false, "outdated")
        assertTrue(request.enableThinking)
        assertEquals("MINIMAL", request.thinkingOptionId)
        val json = JSONObject()
        ThinkingConfigurationApplier.apply(json, "GOOGLE", "gemini-3-flash", "", ModelThinkingConfigDefaults.forProvider("GOOGLE"), false, "outdated")
        assertEquals("MINIMAL", json.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }
}
