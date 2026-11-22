package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.api.chat.model.ThinkingConfigurationApplier
import com.ai.assistance.operit.api.chat.model.ThinkingQualityMapping
import com.ai.assistance.operit.api.chat.model.ThinkingQualityMappingRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentThinkingTest {
    private val rules = """
        [{
          "control": "levels",
          "parameterLabel": "reasoning.effort",
          "options": [
            {"id": "brief", "label": "简要", "value": "low"},
            {"id": "deep", "label": "深入", "value": "high"}
          ]
        }]
    """.trimIndent()

    private fun levels(): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve("OPENAI_RESPONSES", "helper", rules)

    @Test
    fun eachExecutionSelectsItsAdvertisedIdAndProviderWireValue() {
        val mapping = levels()
        for ((id, effort) in listOf("brief" to "low", "deep" to "high")) {
            val selection = mapping.selectSubagentThinking(true, id)
            val request = JSONObject()
            ThinkingConfigurationApplier.apply(
                requestJson = request,
                providerTypeId = "OPENAI_RESPONSES",
                modelName = "helper",
                apiEndpoint = "https://example.invalid/responses",
                thinkingConfigurations = rules,
                enableThinking = selection.enabled,
                optionId = selection.optionId,
            )
            assertEquals(id, selection.optionId)
            assertEquals(effort, request.getJSONObject("reasoning").getString("effort"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingLevelIsRejectedInsteadOfUsingTheFirstOption() {
        levels().selectSubagentThinking(true, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unadvertisedIdIsRejectedEvenIfItLooksLikeAnEffortName() {
        levels().selectSubagentThinking(true, "high")
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiredThinkingCannotBeDisabled() {
        levels().copy(reasoningRequired = true).selectSubagentThinking(false, null)
    }

    @Test
    fun toggleAndUnsupportedControlsKeepTheirDeclaredCapabilities() {
        val toggle = ThinkingQualityMapping.toggleOnly("thinking.type")
        assertEquals(SubagentThinking(true, ""), toggle.selectSubagentThinking(true, null))
        assertEquals(SubagentThinking(), toggle.selectSubagentThinking(false, null))
        assertEquals(SubagentThinking(), ThinkingQualityMapping.unsupported().selectSubagentThinking(false, null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun toggleOnlyModelsCannotReceiveALevel() {
        ThinkingQualityMapping.toggleOnly("thinking.type").selectSubagentThinking(true, "deep")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedModelsCannotInventThinkingControls() {
        ThinkingQualityMapping.unsupported().selectSubagentThinking(true, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun disabledThinkingCannotCarryAStaleLevel() {
        levels().selectSubagentThinking(false, "deep")
    }

    @Test
    fun legacyRunSnapshotsRetainTheirArchiveStateAndRemainReadable() {
        val json = Json { encodeDefaults = true }
        for (archived in listOf(false, true)) {
            val task = task().copy(archived = archived, status = SubagentStatus.COMPLETED)
            val legacy = JsonObject(json.parseToJsonElement(json.encodeToString(task)).jsonObject
                .filterKeys { it != "thinking" && it != "autoArchiveOnCompletion" })
            val restored = json.decodeFromString<SubagentTask>(legacy.toString())
            assertEquals(task.executionId, restored.executionId)
            assertEquals(archived, restored.archived)
            assertEquals(SubagentThinking(), restored.thinking)
            assertFalse(restored.autoArchiveOnCompletion)
        }
    }

    @Test
    fun executionChoicesSurviveStorageAndLegacyConfigurationGetsTheNewDefault() {
        val json = Json { encodeDefaults = true }
        val original = task().copy(thinking = SubagentThinking(true, "deep"), autoArchiveOnCompletion = true)
        val restored = json.decodeFromString<SubagentTask>(json.encodeToString(original))
        assertEquals(original.thinking, restored.thinking)
        assertTrue(restored.autoArchiveOnCompletion)
        assertTrue(json.decodeFromString<SubagentConfiguration>("""{"presets":[],"models":[]}""").autoArchiveCompleted)
    }

    @Test
    fun reportRequestDisablesOptionalThinkingWithoutChangingTheTaskSetting() {
        val original = SubagentThinking(true, "deep")
        assertEquals(SubagentThinking(), levels().subagentReportThinking(original))
        assertEquals(SubagentThinking(true, "deep"), original)
    }

    @Test
    fun requiredThinkingUsesAnAdvertisedBriefLevel() {
        val mapping = levels().copy(reasoningRequired = true)
        assertEquals(SubagentThinking(true, "brief"), mapping.subagentReportThinking(SubagentThinking(true, "deep")))
        assertEquals(SubagentThinking(true, ""), ThinkingQualityMapping.toggleOnly("thinking", reasoningRequired = true)
            .subagentReportThinking(SubagentThinking(true, "")))
    }

    @Test
    fun unknownCustomRequiredEffortIsPreservedRatherThanGuessingAnId() {
        val mapping = levels().copy(reasoningRequired = true, options = levels().options.map { option ->
            option.copy(wireValue = com.ai.assistance.operit.api.chat.model.ThinkingQualityWireValue.Text("custom-${option.id}"))
        })
        assertEquals(SubagentThinking(true, "deep"), mapping.subagentReportThinking(SubagentThinking(true, "deep")))
    }

    @Test
    fun legacySnapshotsDefaultToNormalWorkAndNoMissingReportFlag() {
        val json = Json { encodeDefaults = true }
        val original = task()
        val legacy = JsonObject(json.parseToJsonElement(json.encodeToString(original)).jsonObject
            .filterKeys { it != "reportOnly" && it != "reportMissing" })
        val restored = json.decodeFromString<SubagentTask>(legacy.toString())
        assertFalse(restored.reportOnly)
        assertFalse(restored.reportMissing)
    }

    private fun task() = SubagentTask(
        parentChatId = "parent",
        parentToolCallId = "invocation",
        title = "历史任务",
        goal = "保留既有记录",
        preset = SubagentPreset(id = "preset", name = "帮手", description = "分析", systemPrompt = "完成任务"),
        model = SubagentModel(id = "model", configId = "profile", modelName = "helper", capabilities = "分析"),
    )
}
