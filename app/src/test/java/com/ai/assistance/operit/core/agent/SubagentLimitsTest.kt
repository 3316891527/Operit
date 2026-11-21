package com.ai.assistance.operit.core.agent

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubagentLimitsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun supportedBoundariesKeepPerChatWithinTheGlobalBudget() {
        assertEquals(SubagentLimits(), SubagentLimits().validate())
        assertEquals(SubagentLimits(1, 1, 1), SubagentLimits(1, 1, 1).validate())
        assertEquals(SubagentLimits(64, 64, 120), SubagentLimits(64, 64, 120).validate())
        for (invalid in listOf(
            SubagentLimits(0, 1, 20), SubagentLimits(65, 1, 20),
            SubagentLimits(6, 0, 20), SubagentLimits(6, 7, 20),
            SubagentLimits(6, 3, 0), SubagentLimits(6, 3, 121),
            SubagentLimits(maxDepth = 0), SubagentLimits(maxDepth = 9),
        )) assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
    }

    @Test
    fun legacyConfigurationPreservesPresetsModelsAndArchivePreference() {
        val original = SubagentConfiguration(
            presets = listOf(preset), models = listOf(model), autoArchiveCompleted = false,
            limits = SubagentLimits(12, 4, 60),
        )
        val legacy = JsonObject(json.parseToJsonElement(json.encodeToString(original)).jsonObject.filterKeys { it != "limits" })
        val restored = json.decodeFromString<SubagentConfiguration>(legacy.toString())
        assertEquals(original.presets, restored.presets)
        assertEquals(original.models, restored.models)
        assertEquals(false, restored.autoArchiveCompleted)
        assertEquals(SubagentLimits(6, 3, 20), restored.limits.validate())
    }

    @Test
    fun oldRunSnapshotsRetainTwentyMinutesAndIndependentContext() {
        val original = task().copy(archived = true, status = SubagentStatus.COMPLETED)
        val legacy = JsonObject(json.parseToJsonElement(json.encodeToString(original)).jsonObject
            .filterKeys { it != "forkTurns" && it != "executionTimeoutMinutes" })
        val restored = json.decodeFromString<SubagentTask>(legacy.toString())
        assertEquals(original, restored)
        assertEquals("none", restored.forkTurns)
        assertEquals(20, restored.executionTimeoutMinutes)
    }

    @Test
    fun executionChoicesPersistAndDoNotFollowLaterConfigurationChanges() {
        val settings = SubagentConfiguration(limits = SubagentLimits(8, 2, 45))
        val original = task().copy(forkTurns = "2", executionTimeoutMinutes = settings.limits.executionTimeoutMinutes)
        val changed = settings.copy(limits = SubagentLimits(1, 1, 5))
        val restored = json.decodeFromString<SubagentTask>(json.encodeToString(original))
        assertEquals(original, restored)
        assertEquals(45, restored.executionTimeoutMinutes)
        assertEquals(5, changed.limits.executionTimeoutMinutes)
        assertEquals("2", restored.forkTurns)
        assertEquals(settings.limits, json.decodeFromString<SubagentConfiguration>(json.encodeToString(settings)).limits)
    }

    private fun task() = SubagentTask(
        parentChatId = "parent", parentToolCallId = "invocation", title = "兼容任务", goal = "保留已有记录",
        preset = preset, model = model,
    )

    private val preset = SubagentPreset(id = "preset", name = "帮手", description = "分析", systemPrompt = "独立工作")
    private val model = SubagentModel(id = "model", configId = "profile", modelName = "helper", capabilities = "分析")
}