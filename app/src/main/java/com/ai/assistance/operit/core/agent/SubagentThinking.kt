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
