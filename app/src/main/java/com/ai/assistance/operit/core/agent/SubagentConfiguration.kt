package com.ai.assistance.operit.core.agent

import kotlinx.serialization.Serializable
import java.util.UUID

/** 提示词与模型池分开保存，主代理在一次委派中组合两者。 */
@Serializable
data class SubagentPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val systemPrompt: String,
    val enabled: Boolean = true,
)

@Serializable
data class SubagentModel(
    val id: String = UUID.randomUUID().toString(),
    val configId: String,
    val modelName: String,
    val capabilities: String,
    val limitations: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class SubagentConfiguration(
    val presets: List<SubagentPreset> = emptyList(),
    val models: List<SubagentModel> = emptyList(),
    val autoArchiveCompleted: Boolean = true,
    val limits: SubagentLimits = SubagentLimits(),
)

internal fun SubagentPreset.validate() {
    require(id.isNotBlank()) { "Missing preset identity" }
    require(name.isNotBlank() && name.length <= 60) { "Preset name must contain 1–60 characters" }
    require(description.isNotBlank() && description.length <= 1000) { "Preset purpose must contain 1–1000 characters" }
    require(systemPrompt.isNotBlank() && systemPrompt.length <= 16000) { "Preset instructions must contain 1–16000 characters" }
}

internal fun SubagentModel.validate() {
    require(id.isNotBlank() && configId.isNotBlank() && modelName.isNotBlank()) { "Missing model identity" }
    require(capabilities.isNotBlank() && capabilities.length <= 2000) { "Describe the model's suitable tasks in 1–2000 characters" }
    require(limitations.length <= 2000) { "Model limitations exceed 2000 characters" }
}
