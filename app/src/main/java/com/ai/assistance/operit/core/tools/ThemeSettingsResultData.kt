package com.ai.assistance.operit.core.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** 保存后返回目标、完整偏好与核验状态，读取值不冒充主题脚本最终渲染结果。 */
@Serializable
data class ThemeSettingsResultData(
    val targetType: String,
    val targetId: String,
    val targetName: String,
    val revision: String,
    val values: JsonObject,
    val active: Boolean,
    val section: String? = null,
    val saved: Boolean = false,
    val verified: Boolean = false,
    val updatedFields: List<String> = emptyList(),
    val hasThemeScript: Boolean = false,
    val restartRequired: Boolean = false,
    val valueSource: String = "preferences",
) : ToolResultData() {
    override fun toString(): String = buildString {
        appendLine("$targetType:$targetId ($targetName), saved=$saved, verified=$verified, active=$active")
        appendLine("revision=$revision")
        if (hasThemeScript) appendLine("Custom theme script is active; these are preference values, not resolved script appearance.")
        append(values)
    }
}