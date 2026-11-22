package com.ai.assistance.operit.core.agent

import android.content.Context
import com.ai.assistance.operit.api.chat.model.ThinkingQualityControl
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.config.model.getModelList
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SubagentPreferences
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal data class ResolvedSubagentModel(val entry: SubagentModel, val config: ModelConfigData)

/** 模型选择依赖用户维护的能力目录和实际配置，不从型号名称推断能力。 */
class SubagentCatalog(context: Context) {
    private val preferences = SubagentPreferences(context)
    private val models = ModelConfigManager(context)

    internal suspend fun resolve(modelId: String): ResolvedSubagentModel {
        val entry = preferences.configuration.first().models.singleOrNull { it.id == modelId && it.enabled }
        requireNotNull(entry) { "This model is not enabled in the subagent model pool" }
        val config = models.requireModelConfig(entry.configId)
        require(entry.modelName in getModelList(config.modelName)) { "The selected model is no longer present in its profile" }
        return ResolvedSubagentModel(entry, config)
    }

    suspend fun describe(): String {
        val settings = preferences.configuration.first()
        val summaries = models.getAllConfigSummaries().associateBy { it.id }
        // JSON 数组构建块不支持挂起，先在当前协程中读取配置，再序列化快照。
        val availableModels = mutableListOf<ResolvedSubagentModel>()
        for (entry in settings.models) {
            if (!entry.enabled) continue
            val summary = summaries[entry.configId] ?: continue
            if (entry.modelName !in getModelList(summary.modelName)) continue
            availableModels += ResolvedSubagentModel(entry, models.requireModelConfig(entry.configId))
        }
        val body = buildJsonObject {
            put("selection_policy", "Choose a preset and an allowed model independently. Use the supplied capability descriptions and limitations, not prior knowledge of model names. Never invent model quality, speed, price or capabilities. Explain missing configuration instead of selecting a model outside this pool.")
            put("auto_archive_completed_default", settings.autoArchiveCompleted)
            putJsonObject("runtime_limits") {
                put("max_depth", settings.limits.maxDepth)
                put("global_concurrency", settings.limits.maxRunning)
                put("per_chat_concurrency", settings.limits.maxPerChat)
                put("execution_timeout_minutes", settings.limits.executionTimeoutMinutes)
                put("report_window_max_seconds", 120)
                put("report_retry_attempts", SUBAGENT_REPORT_ATTEMPTS)
            }
            put("fork_turns", "none (default), all, or the most recent 1–64 real user turns")
            put("inherited_context_max_characters", SubagentContextHistory.MAX_CHARACTERS)
            putJsonArray("presets") {
                settings.presets.filter { it.enabled }.forEach { preset ->
                    add(buildJsonObject { put("id", preset.id); put("name", preset.name); put("purpose", preset.description) })
                }
            }
            putJsonArray("models") {
                availableModels.forEach { (entry, config) ->
                    add(buildJsonObject {
                        put("id", entry.id); put("profile", summaries.getValue(entry.configId).name); put("model", entry.modelName)
                        put("suitable_tasks", entry.capabilities); put("limitations", entry.limitations)
                        put("configured_context_k_tokens", config.contextLength)
                        put("configured_direct_images", config.enableDirectImageProcessing)
                        put("configured_direct_audio", config.enableDirectAudioProcessing)
                        put("configured_direct_video", config.enableDirectVideoProcessing)
                        put("native_tool_call_enabled", config.enableToolCall)
                        val thinking = config.subagentThinkingMapping(entry.modelName)
                        put("thinking_control", when (thinking.control) {
                            ThinkingQualityControl.LEVELS -> "levels"
                            ThinkingQualityControl.TOGGLE_ONLY -> "toggle"
                            ThinkingQualityControl.UNSUPPORTED -> "unavailable"
                        })
                        put("thinking_required", thinking.reasoningRequired)
                        putJsonArray("thinking_options") {
                            thinking.options.forEach { option -> add(buildJsonObject {
                                put("id", option.id); put("label", option.displayLabel)
                            }) }
                        }
                    })
                }
            }
        }
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body)
    }
}
