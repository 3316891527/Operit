package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.api.chat.model.ThinkingQualityControl
import com.ai.assistance.operit.api.chat.model.ThinkingQualityMapping
import com.ai.assistance.operit.api.chat.model.ThinkingQualityMappingRegistry
import com.ai.assistance.operit.data.config.model.ModelConfigData

internal fun ModelConfigData.subagentThinkingMapping(modelName: String): ThinkingQualityMapping =
    ThinkingQualityMappingRegistry.resolve(apiProviderTypeId, modelName, apiEndpoint, thinkingConfigurations)

internal fun ThinkingQualityMapping.selectSubagentThinking(enabled: Boolean, optionId: String?): SubagentThinking {
    if (!enabled) {
        require(!reasoningRequired) { "This model requires thinking; choose an advertised thinking option" }
        require(optionId.isNullOrBlank()) { "A thinking option requires enable_thinking=true" }
        return SubagentThinking()
    }
    require(control != ThinkingQualityControl.UNSUPPORTED) { "This model has no configured thinking controls" }
    val selected = if (control == ThinkingQualityControl.LEVELS) {
        requireNotNull(optionId?.takeIf { optionFor(it) != null }) { "Select an exact thinking_option_id from subagent_catalog" }
    } else {
        require(optionId.isNullOrBlank()) { "This model supports only a thinking switch, not levels" }
        ""
    }
    return SubagentThinking(enabled = true, optionId = selected)
}

/** 报告收尾只调整当前请求；自定义且无法比较的必选思考档保留原选择。 */
internal fun ThinkingQualityMapping.subagentReportThinking(current: SubagentThinking): SubagentThinking {
    if (!reasoningRequired) return selectSubagentThinking(false, null)
    if (control != ThinkingQualityControl.LEVELS) return selectSubagentThinking(true, null)
    val selected = requireNotNull(optionFor(current.optionId)) { "The report thinking option is no longer configured" }
    val efforts = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
    fun rank(option: com.ai.assistance.operit.api.chat.model.ThinkingQualityOption): Int? = when (val value = option.wireValue) {
        is com.ai.assistance.operit.api.chat.model.ThinkingQualityWireValue.Text -> efforts.indexOf(value.value).takeIf { it >= 0 }
        is com.ai.assistance.operit.api.chat.model.ThinkingQualityWireValue.Number -> value.value
        else -> null
    }
    val currentRank = rank(selected) ?: return selectSubagentThinking(true, current.optionId)
    val brief = options.filter { it.wireValue::class == selected.wireValue::class }
        .mapNotNull { option -> rank(option)?.takeIf { it <= currentRank }?.let { option to it } }
        .minByOrNull { it.second }?.first ?: selected
    return selectSubagentThinking(true, brief.id)
}
