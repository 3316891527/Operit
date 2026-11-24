package com.ai.assistance.operit.ui.features.models.tokenstats

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.stats.*
import kotlinx.coroutines.delay

@Composable
internal fun TokenStatsInsightsSettingsDialog(
    state: TokenStatsUiState,
    onSave: (PricingCurrency, Double, TokenStatsDisplayUnit, Boolean, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var currency by remember { mutableStateOf(state.targetCurrency) }
    var rateText by remember { mutableStateOf(state.manualRate.toString()) }
    var unit by remember { mutableStateOf(state.tokenDisplayUnit) }
    var red by remember { mutableStateOf(state.increaseIsRed) }
    var budgetText by remember { mutableStateOf(state.monthlyBudgetCny?.toString().orEmpty()) }
    val rate = rateText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val budget = budgetText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val validBudget = budgetText.isBlank() || budget != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.token_insights_settings)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PricingCurrency.values().forEach { value -> FilterChip(selected = currency == value, onClick = { currency = value }, label = { Text(value.name) }) }
                }
                OutlinedTextField(value = rateText, onValueChange = { rateText = it }, label = { Text(stringResource(R.string.token_insights_rate)) }, singleLine = true, isError = rate == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (rate == null) Text(stringResource(R.string.token_insights_rate_invalid), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.token_insights_unit), style = MaterialTheme.typography.titleSmall)
                InsightToggle(stringResource(R.string.token_insights_millions), unit == TokenStatsDisplayUnit.MILLIONS) { unit = TokenStatsDisplayUnit.MILLIONS }
                InsightToggle(stringResource(R.string.token_insights_billions), unit == TokenStatsDisplayUnit.BILLIONS) { unit = TokenStatsDisplayUnit.BILLIONS }
                InsightToggle(stringResource(R.string.token_insights_increase_red), red) { red = true }
                InsightToggle(stringResource(R.string.token_insights_increase_green), !red) { red = false }
                OutlinedTextField(value = budgetText, onValueChange = { budgetText = it }, label = { Text(stringResource(R.string.token_insights_monthly_budget)) }, supportingText = { Text(stringResource(if (validBudget) R.string.token_insights_budget_disabled else R.string.token_insights_budget_invalid)) }, singleLine = true, isError = !validBudget, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(enabled = rate != null && validBudget, onClick = { if (rate != null && validBudget) onSave(currency, rate, unit, red, budget) }) { Text(stringResource(R.string.token_insights_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.token_insights_cancel)) } },
    )
}

@Composable
internal fun TokenStatsInsightsFilterDialog(
    state: TokenStatsUiState,
    onApply: (TokenStatsInsightsFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    // 弹窗使用草稿，取消或返回不会把局部勾选提前写到查询条件中。
    var draft by remember { mutableStateOf(state.filter) }
    val modelOptions = state.catalog?.models.orEmpty().mapNotNull { it.key }.distinct().sorted().map { it to it }
    val configOptions = state.catalog?.configurations.orEmpty().map { it.configId }.distinct().filter { it.isBlank() || it in state.configurationNames }.map { id -> id to if (id.isBlank()) stringResource(R.string.token_insights_configuration_unscoped) else requireNotNull(state.configurationNames[id]) }
    val executionOptions = state.catalog?.executionModels.orEmpty().mapNotNull { it.key }.distinct().sorted().map { it to it }
    val functionOptions = FunctionType.values().map { it.name to insightFunctionName(it) }
    val statuses = listOf(
        "SUCCESS" to stringResource(R.string.token_insights_success),
        "FAILED" to stringResource(R.string.token_insights_failed),
        "CANCELLED" to stringResource(R.string.token_insights_cancelled),
        "COMPLETED" to stringResource(R.string.token_insights_completed),
    )
    val periods = listOf(
        TokenStatsPeakPeriod.PEAK.name to stringResource(R.string.token_insights_peak),
        TokenStatsPeakPeriod.OFF_PEAK.name to stringResource(R.string.token_insights_off_peak),
        TokenStatsPeakPeriod.NOT_ENABLED.name to stringResource(R.string.token_insights_not_enabled),
        TokenStatsPeakPeriod.UNKNOWN.name to stringResource(R.string.token_insights_unknown),
    )
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.token_insights_filters)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { draft = draft.copy(models = null, configIds = null, functionTypes = null, statuses = null, peakPeriods = null, executionModels = null) }) { Text(stringResource(R.string.token_insights_reset_filters)) }
                InsightSelection(stringResource(R.string.token_insights_models), modelOptions, draft.models) { draft = draft.copy(models = it) }
                InsightSelection(stringResource(R.string.token_insights_configurations), configOptions, draft.configIds) { draft = draft.copy(configIds = it) }
                InsightSelection(stringResource(R.string.token_insights_source), functionOptions, draft.functionTypes) { draft = draft.copy(functionTypes = it) }
                if (executionOptions.isNotEmpty()) InsightSelection(stringResource(R.string.token_insights_execution_model), executionOptions, draft.executionModels) { draft = draft.copy(executionModels = it) }
                InsightSelection(stringResource(R.string.token_insights_status), statuses, draft.statuses) { draft = draft.copy(statuses = it) }
                InsightSelection(stringResource(R.string.token_insights_peak_usage), periods, draft.peakPeriods?.map { it.name }?.toSet()) { values -> draft = draft.copy(peakPeriods = values?.map { TokenStatsPeakPeriod.valueOf(it) }?.toSet()) }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text(stringResource(R.string.token_insights_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.token_insights_cancel)) } },
    )
}

@Composable
internal fun TokenStatsInsightsResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    // 使用单调时钟保证真正经过五秒，暂停、恢复或系统时间修改不会提前解锁。
    val deadline = remember { SystemClock.elapsedRealtime() + 5_000L }
    var remaining by remember { mutableStateOf(5) }
    LaunchedEffect(deadline) {
        while (remaining > 0) {
            delay(100)
            remaining = ((deadline - SystemClock.elapsedRealtime() + 999L) / 1_000L).coerceAtLeast(0L).toInt()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.token_insights_clear_all)) },
        text = { Text(stringResource(R.string.token_insights_clear_warning)) },
        confirmButton = {
            TextButton(enabled = remaining == 0, onClick = { if (SystemClock.elapsedRealtime() >= deadline) onConfirm() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(if (remaining > 0) stringResource(R.string.token_stats_usage_reset_confirm_wait, remaining) else stringResource(R.string.token_insights_clear_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.token_insights_cancel)) } },
    )
}

@Composable
private fun InsightToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InsightSelection(title: String, options: List<Pair<String, String>>, selected: Set<String>?, onChange: (Set<String>?) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.token_insights_filter_all)) }
        TextButton(onClick = { onChange(options.map { it.first }.toSet()) }) { Text(stringResource(R.string.token_insights_select_all)) }
        TextButton(onClick = { onChange(emptySet()) }) { Text(stringResource(R.string.token_insights_select_none)) }
    }
    options.forEach { (id, label) ->
        val checked = selected == null || id in selected
        val toggle = { onChange((selected ?: options.map { it.first }.toSet()).let { if (id in it) it - id else it + id }) }
        Row(Modifier.fillMaxWidth().clickable(onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { toggle() })
            Text(label, modifier = Modifier.weight(1f))
        }
    }
    HorizontalDivider()
}

@Composable
private fun insightFunctionName(type: FunctionType): String = when (type) {
    FunctionType.CHAT -> stringResource(R.string.function_type_chat)
    FunctionType.SUMMARY -> stringResource(R.string.function_type_summary)
    FunctionType.TITLE_GENERATION -> stringResource(R.string.function_type_title_generation)
    FunctionType.MEMORY -> stringResource(R.string.function_type_memory)
    FunctionType.MEMORY_EMBEDDING -> stringResource(R.string.token_insights_function_memory_embedding)
    FunctionType.UI_CONTROLLER -> stringResource(R.string.function_type_ui_controller)
    FunctionType.TRANSLATION -> stringResource(R.string.function_type_translation)
    FunctionType.GREP -> stringResource(R.string.function_type_grep)
    FunctionType.ROLE_RESPONSE_PLANNER -> stringResource(R.string.function_type_role_response_planner)
    FunctionType.IMAGE_RECOGNITION -> stringResource(R.string.function_type_image_recognition)
    FunctionType.AUDIO_RECOGNITION -> stringResource(R.string.function_type_audio_recognition)
    FunctionType.VIDEO_RECOGNITION -> stringResource(R.string.function_type_video_recognition)
}
