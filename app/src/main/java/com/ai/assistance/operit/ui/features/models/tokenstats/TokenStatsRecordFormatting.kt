package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.stats.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun insightsMoney(amount: Double, currency: PricingCurrency): String =
    String.format(Locale.getDefault(), "%s%,.4f", currency.symbol, amount)

@Composable
internal fun insightsCost(cost: TokenStatsCostSummary): String = when {
    cost.unknownContributionCount > 0 && cost.knownAmount == 0.0 -> stringResource(R.string.token_insights_unknown_cost)
    else -> insightsMoney(cost.knownAmount, cost.currency)
}

@Composable
internal fun insightsTokens(value: TokenStatsTokenAggregate, unit: TokenStatsDisplayUnit): String =
    if (value.knownEventCount == 0L && value.unknownEventCount > 0L) stringResource(R.string.token_insights_not_recorded)
    else formatTokenCount(value.knownSum, unit)

@Composable
internal fun insightsStatus(status: String?): String = stringResource(when (status) {
    "SUCCESS" -> R.string.token_insights_success
    "FAILED" -> R.string.token_insights_failed
    "CANCELLED" -> R.string.token_insights_cancelled
    "COMPLETED" -> R.string.token_insights_completed
    else -> R.string.token_insights_not_recorded
})

@Composable
internal fun insightsFunction(function: String?): String {
    val resource = when (function) {
        FunctionType.CHAT.name -> R.string.function_type_chat
        FunctionType.SUMMARY.name -> R.string.function_type_summary
        FunctionType.TITLE_GENERATION.name -> R.string.function_type_title_generation
        FunctionType.MEMORY.name -> R.string.function_type_memory
        FunctionType.MEMORY_EMBEDDING.name -> R.string.function_type_memory_embedding
        FunctionType.UI_CONTROLLER.name -> R.string.function_type_ui_controller
        FunctionType.TRANSLATION.name -> R.string.function_type_translation
        FunctionType.GREP.name -> R.string.function_type_grep
        FunctionType.ROLE_RESPONSE_PLANNER.name -> R.string.function_type_role_response_planner
        FunctionType.IMAGE_RECOGNITION.name -> R.string.function_type_image_recognition
        FunctionType.AUDIO_RECOGNITION.name -> R.string.function_type_audio_recognition
        FunctionType.VIDEO_RECOGNITION.name -> R.string.function_type_video_recognition
        else -> R.string.token_insights_not_recorded
    }
    return stringResource(resource)
}

@Composable
internal fun insightsPeakName(peak: TokenStatsPeakPeriod): String = stringResource(when (peak) {
    TokenStatsPeakPeriod.PEAK -> R.string.token_insights_peak
    TokenStatsPeakPeriod.OFF_PEAK -> R.string.token_insights_off_peak
    TokenStatsPeakPeriod.NOT_ENABLED -> R.string.token_insights_not_enabled
    TokenStatsPeakPeriod.UNKNOWN -> R.string.token_insights_unknown
})

@Composable
internal fun insightsConfigurationName(configId: String, state: TokenStatsUiState): String = when {
    configId.isBlank() -> stringResource(R.string.token_insights_configuration_unscoped)
    configId in state.configurationNames -> state.configurationNames.getValue(configId)
    else -> stringResource(R.string.token_insights_configuration_deleted)
}

internal fun insightsDate(timestamp: Long, zone: ZoneId, pattern: String = "MM/dd HH:mm"): String =
    Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
