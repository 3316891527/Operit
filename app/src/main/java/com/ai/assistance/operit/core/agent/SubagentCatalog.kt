package com.ai.assistance.operit.core.agent

import android.content.Context
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.config.model.getModelList
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SubagentPreferences
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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
        val body = buildJsonObject {
            put("selection_policy", "Choose a preset and an allowed model independently. Use the supplied capability descriptions and limitations, not prior knowledge of model names. Never invent model quality, speed, price or capabilities. Explain missing configuration instead of selecting a model outside this pool.")
            putJsonArray("presets") {
                settings.presets.filter { it.enabled }.forEach { preset ->
                    add(buildJsonObject { put("id", preset.id); put("name", preset.name); put("purpose", preset.description) })
                }
            }
            putJsonArray("models") {
                settings.models.filter { it.enabled }.forEach { entry ->
                    val summary = summaries[entry.configId]
                    if (summary != null && entry.modelName in getModelList(summary.modelName)) {
                        val config = models.requireModelConfig(entry.configId)
                        add(buildJsonObject {
                            put("id", entry.id); put("profile", summary.name); put("model", entry.modelName)
                            put("suitable_tasks", entry.capabilities); put("limitations", entry.limitations)
                            put("configured_context_k_tokens", config.contextLength)
                            put("configured_direct_images", config.enableDirectImageProcessing)
                            put("configured_direct_audio", config.enableDirectAudioProcessing)
                            put("configured_direct_video", config.enableDirectVideoProcessing)
                            put("native_tool_call_enabled", config.enableToolCall)
                        })
                    }
                }
            }
        }
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body)
    }
}
