package com.ai.assistance.operit.ui.features.models.tokenstats

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.stats.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 对齐设计稿：标题和操作固定，仅正文滚动，避免长明细挤走关闭与保存入口。 */
@Composable
internal fun StatsSheet(
    eyebrow: String,
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {},
    editor: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalTokenStatsPalette.current
    val configuration = LocalConfiguration.current
    val phone = configuration.screenWidthDp <= 560
    val compact = configuration.screenWidthDp < 370
    val maxHeight = (configuration.screenHeightDp * if (editor && phone) .94f else .92f).dp
    val horizontal = if (compact) 19.dp else if (phone) 22.dp else 24.dp
    val bodyHorizontal = if (editor && phone) 19.dp else horizontal
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = if (phone) Alignment.BottomCenter else Alignment.Center) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            val sheetModifier = if (phone) Modifier.fillMaxWidth() else Modifier.padding(horizontal = 18.dp).widthIn(max = 640.dp).fillMaxWidth()
            Surface(
                modifier = sheetModifier.heightIn(max = maxHeight).then(if (editor && phone) Modifier.height(maxHeight) else Modifier),
                shape = if (phone) RoundedCornerShape(topStart = if (editor) 20.dp else 24.dp, topEnd = if (editor) 20.dp else 24.dp) else RoundedCornerShape(23.dp),
                color = p.panel,
                contentColor = p.ink,
                border = if (phone) null else BorderStroke(1.dp, p.line),
                tonalElevation = 0.dp,
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = horizontal, end = horizontal - 10.dp, top = 23.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            StatsText(eyebrow, size = 8, color = p.accent, weight = FontWeight.SemiBold, mono = true)
                            StatsText(title, size = 20, weight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.token_insights_close), tint = p.muted, modifier = Modifier.size(19.dp))
                        }
                    }
                    StatsDivider()
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = bodyHorizontal, vertical = 19.dp), content = content)
                    StatsDivider()
                    Box(Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 14.dp)) { footer() }
                }
            }
        }
    }
}

@Composable
internal fun TokenStatsRequestDialog(
    row: TokenStatsRequestRow,
    state: TokenStatsUiState,
    zone: ZoneId,
    onEdit: (TokenStatsPriceSetting?, TokenStatsPriceDraft, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalTokenStatsPalette.current
    val unknown = stringResource(R.string.token_insights_not_recorded)
    val editable = row.configId.isBlank() || row.configId in state.configurationNames
    val name = insightsConfigurationName(row.configId, state)
    val draft = priceDraftForIdentity(row.configId, row.provider, row.model, state.priceSettings)
    val setting = state.priceSettings.firstOrNull {
        it.provider == draft.provider && it.model == row.model && it.configId.orEmpty() == row.configId
    }
    StatsSheet("REQUEST DETAIL", stringResource(R.string.token_insights_details), onDismiss, footer = {
        if (editable) StatsPrimaryAction(stringResource(R.string.token_stats_design_edit_configuration_price), {
            onEdit(setting, draft, name)
        }) else StatsAction(stringResource(R.string.token_insights_close), onDismiss, Modifier.fillMaxWidth())
    }) {
        StatsText(row.id + " · " + (row.occurredAtMs?.let { insightsDate(it, zone, "yyyy-MM-dd HH:mm") } ?: unknown), size = 10, color = p.muted, mono = true)
        Spacer(Modifier.height(15.dp))
        StatsText(insightsCost(row.cost), size = 37, weight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 7.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StatsDetailTag(insightsStatus(row.status), when (row.status) {
                "SUCCESS" -> p.teal
                "FAILED" -> p.red
                "CANCELLED" -> p.amber
                else -> p.muted
            })
            if (row.peakPeriod == TokenStatsPeakPeriod.PEAK || row.peakPeriod == TokenStatsPeakPeriod.OFF_PEAK) StatsDetailTag(insightsPeakName(row.peakPeriod), p.muted)
            if (row.longContext == true) StatsDetailTag(stringResource(R.string.token_insights_long_context), p.accent)
        }
        StatsDetailLine(stringResource(R.string.token_insights_models), row.model)
        StatsDetailLine(stringResource(R.string.token_insights_configurations), name)
        StatsDetailLine(stringResource(R.string.token_stats_design_provider), row.provider)
        if (row.executionModel != null) {
            StatsDetailLine(stringResource(R.string.token_insights_execution_model), row.executionModel)
        } else {
            StatsDetailLine(stringResource(R.string.token_insights_source), insightsFunction(row.functionType))
        }
        val tokens = listOf(R.string.token_insights_uncached to row.uncachedInputTokens,
            R.string.token_insights_cached to row.cachedInputTokens,
            R.string.token_insights_cache_write to row.cacheWriteTokens,
            R.string.token_insights_output to row.outputTokens)
        Surface(Modifier.fillMaxWidth().padding(vertical = 18.dp), shape = RoundedCornerShape(12.dp), color = p.panel,
            border = BorderStroke(1.dp, p.line), tonalElevation = 0.dp) {
            Column {
                tokens.chunked(2).forEachIndexed { index, pair ->
                    if (index > 0) StatsDivider()
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        pair.forEachIndexed { column, (label, count) ->
                            if (column > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(p.line))
                            Column(Modifier.weight(1f).padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatsText(stringResource(label), size = 10, color = p.muted)
                                StatsText(count?.let { String.format(Locale.getDefault(), "%,d", it) } ?: unknown, size = 16, weight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        StatsDetailLine(stringResource(R.string.token_stats_design_total_tokens), row.totalTokens?.let { String.format(Locale.getDefault(), "%,d", it) } ?: unknown)
        StatsDetailLine(stringResource(R.string.token_stats_design_first_token), row.firstTokenLatencyMs?.let { String.format(Locale.getDefault(), "%.2fs", it / 1000.0) } ?: unknown)
        StatsDetailLine(stringResource(R.string.token_insights_duration), row.durationMs?.let { String.format(Locale.getDefault(), "%.2fs", it / 1000.0) } ?: unknown)
        Spacer(Modifier.height(22.dp))
        StatsText(stringResource(R.string.token_stats_design_formula_title), size = 12, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Surface(Modifier.fillMaxWidth(), color = p.background, border = BorderStroke(1.dp, p.line), shape = RoundedCornerShape(10.dp), tonalElevation = 0.dp) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatsText(stringResource(R.string.token_insights_pricing_now), size = 10, color = p.muted, mono = true)
                if (!row.hasUsageFact) {
                    StatsText(stringResource(R.string.token_insights_usage_unconfirmed), size = 10, color = p.muted, mono = true)
                } else if (!editable) {
                    StatsText(stringResource(R.string.token_insights_configuration_deleted), size = 10, color = p.muted, mono = true)
                } else {
                    StatsRequestFormula(row, draft, state)
                }
            }
        }
    }
}

@Composable
private fun StatsDetailTag(label: String, color: Color) {
    val p = LocalTokenStatsPalette.current
    Surface(color = p.panelAlt, shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, p.line), tonalElevation = 0.dp) {
        StatsText(label, Modifier.padding(horizontal = 5.dp, vertical = 3.dp), size = 9, color = color)
    }
}

@Composable
private fun StatsDetailLine(label: String, value: String) {
    val p = LocalTokenStatsPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        StatsText(label, size = 11, color = p.muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { StatsText(value, size = 11, weight = FontWeight.Medium) }
    }
    StatsDivider()
}

@Composable
private fun StatsRequestFormula(row: TokenStatsRequestRow, draft: TokenStatsPriceDraft, state: TokenStatsUiState) {
    val p = LocalTokenStatsPalette.current
    val unknown = stringResource(R.string.token_insights_not_recorded)
    val pricing = TokenPriceResolver.resolve("${draft.provider}:${draft.model}", draft.statsModelSettings())
    // 金额始终由原计价器提供；这里只把它已采用的真实数量、单价与规则展开为文字。
    val record = com.ai.assistance.operit.data.model.TokenUsageRecordEntity(
        occurredAtMs = row.occurredAtMs, configId = row.configId, provider = row.provider, model = row.model,
        requestCount = row.requestCount, uncachedInputTokens = row.uncachedInputTokens,
        cachedInputTokens = row.cachedInputTokens, cacheWriteTokens = row.cacheWriteTokens,
        totalInputTokens = row.totalInputTokens, outputTokens = row.outputTokens)
    val confirmedCost = TokenCostCalculator.currentCost(listOf(record), pricing, state.targetCurrency, state.manualRate)
    if (pricing.billingMode == BillingMode.COUNT) {
        StatsText("${row.requestCount} × ${pricing.pricePerRequest} ${pricing.currency.name}", size = 10, color = p.muted, mono = true)
    } else {
        val eligible = row.occurredAtMs != null && row.requestCount == 1L
        val peak = eligible && pricing.peakPricingEnabled && row.peakPeriod == TokenStatsPeakPeriod.PEAK
        val long = eligible && pricing.longContextPricingEnabled && row.longContext == true
        val prices = listOf(pricing.inputPricePerMillion, pricing.cachedInputPricePerMillion, pricing.cacheWritePricePerMillion, pricing.outputPricePerMillion)
        val peakFactors = if (peak) listOf(pricing.peakInputMultiplier, pricing.peakCachedInputMultiplier, pricing.peakCacheWriteMultiplier, pricing.peakOutputMultiplier) else List(4) { 1.0 }
        val longFactors = if (long) listOf(pricing.longContextInputMultiplier, pricing.longContextCachedInputMultiplier, pricing.longContextCacheWriteMultiplier, pricing.longContextOutputMultiplier) else List(4) { 1.0 }
        val effectiveInput = (0..2).map { prices[it] * peakFactors[it] * longFactors[it] }
        val sameInputRate = effectiveInput.distinct().size == 1
        val completeInput = listOf(row.uncachedInputTokens, row.cachedInputTokens, row.cacheWriteTokens).all { it != null }
        if (sameInputRate && (!completeInput || row.totalInputTokens != null && row.totalInputTokens != insightsKnownSum(row.uncachedInputTokens, row.cachedInputTokens, row.cacheWriteTokens))) {
            val totalInput = row.totalInputTokens ?: insightsKnownSum(row.uncachedInputTokens, row.cachedInputTokens, row.cacheWriteTokens)
            StatsText("${stringResource(R.string.token_stats_design_input)}: ${totalInput?.let { String.format(Locale.getDefault(), "%,d", it) } ?: unknown} ÷ 1M × ${effectiveInput[0]}", size = 10, color = p.muted, mono = true)
        } else {
            listOf(R.string.token_insights_uncached to row.uncachedInputTokens, R.string.token_insights_cached to row.cachedInputTokens, R.string.token_insights_cache_write to row.cacheWriteTokens).forEachIndexed { index, (label, count) ->
                StatsText("${stringResource(label)}: ${count?.let { String.format(Locale.getDefault(), "%,d", it) } ?: unknown} ÷ 1M × ${prices[index]} × ${peakFactors[index]} × ${longFactors[index]}", size = 10, color = p.muted, mono = true)
            }
        }
        StatsText("${stringResource(R.string.token_insights_output)}: ${row.outputTokens?.let { String.format(Locale.getDefault(), "%,d", it) } ?: unknown} ÷ 1M × ${prices[3]} × ${peakFactors[3]} × ${longFactors[3]}", size = 10, color = p.muted, mono = true)
        StatsText(stringResource(R.string.token_stats_design_formula_currency, pricing.currency.name), size = 10, color = p.muted, mono = true)
    }
    val exchange = TokenCostCurrency.convertTo(1.0, pricing.currency, state.targetCurrency, state.manualRate)
    StatsText(stringResource(R.string.token_stats_design_formula_exchange, String.format(Locale.getDefault(), "%.4f", exchange), state.targetCurrency.name, pricing.currency.name), size = 10, color = p.muted, mono = true)
    StatsText(stringResource(R.string.token_stats_design_formula_total, insightsCost(confirmedCost)), size = 10, mono = true)
}

private fun TokenStatsPriceDraft.statsModelSettings() = ModelPriceSettings(
    billingMode, currency, inputPricePerMillion, cachedInputPricePerMillion, cacheWritePricePerMillion,
    outputPricePerMillion, pricePerRequest, peakPricingEnabled, weekendOffPeakPricingEnabled,
    holidayOffPeakPricingEnabled, peakSchedule, peakInputMultiplier, peakCachedInputMultiplier,
    peakCacheWriteMultiplier, peakOutputMultiplier, longContextPricingEnabled, longContextThreshold,
    longContextInputMultiplier, longContextCachedInputMultiplier, longContextCacheWriteMultiplier,
    longContextOutputMultiplier)

@Composable
internal fun TokenStatsSettingsDialog(
    state: TokenStatsUiState,
    onSave: (PricingCurrency, Double, TokenStatsDisplayUnit, Boolean, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalTokenStatsPalette.current
    var currency by remember { mutableStateOf(state.targetCurrency) }
    var rateText by remember { mutableStateOf(state.manualRate.toString()) }
    var unit by remember { mutableStateOf(state.tokenDisplayUnit) }
    var budgetText by remember { mutableStateOf(state.monthlyBudgetCny?.toString().orEmpty()) }
    val rate = rateText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val budget = budgetText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val validBudget = budgetText.isBlank() || budget != null
    StatsSheet("PREFERENCES", stringResource(R.string.token_insights_settings), onDismiss, footer = {
        StatsPrimaryAction(stringResource(R.string.token_insights_save), {
            if (rate != null && validBudget) onSave(currency, rate, unit, state.increaseIsRed, budget)
        }, enabled = rate != null && validBudget)
    }) {
        StatsSettingRow(stringResource(R.string.token_stats_design_currency), stringResource(R.string.token_stats_design_currency_note)) {
            StatsSelect(currency.name, PricingCurrency.values().map { it.name }, PricingCurrency.values().indexOf(currency), { currency = PricingCurrency.values()[it] })
        }
        StatsSettingRow(stringResource(R.string.token_insights_rate), stringResource(R.string.token_stats_design_rate_note)) {
            StatsDialogField(rateText, { rateText = it }, Modifier.width(112.dp), isError = rate == null)
        }
        if (rate == null) StatsText(stringResource(R.string.token_insights_rate_invalid), size = 10, color = p.red)
        StatsSettingRow(stringResource(R.string.token_insights_unit), "") {
            val units = listOf(TokenStatsDisplayUnit.MILLIONS, TokenStatsDisplayUnit.BILLIONS)
            val names = listOf(stringResource(R.string.token_insights_millions), stringResource(R.string.token_insights_billions))
            StatsSelect(names[units.indexOf(unit)], names, units.indexOf(unit), { unit = units[it] })
        }
        StatsSettingRow(stringResource(R.string.token_insights_monthly_budget), stringResource(R.string.token_insights_budget_disabled)) {
            StatsDialogField(budgetText, { budgetText = it }, Modifier.width(112.dp), isError = !validBudget)
        }
        if (!validBudget) StatsText(stringResource(R.string.token_insights_budget_invalid), size = 10, color = p.red)
    }
}

@Composable
private fun StatsSettingRow(title: String, note: String, control: @Composable () -> Unit) {
    val p = LocalTokenStatsPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatsText(title, size = 12, weight = FontWeight.SemiBold)
            if (note.isNotBlank()) StatsText(note, size = 10, color = p.muted)
        }
        control()
    }
    StatsDivider()
}

@Composable
internal fun StatsDialogField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    fieldHeight: Dp = 41.dp,
    fontSize: Int = 14,
    labelSize: Int = 11,
) {
    val p = LocalTokenStatsPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        label?.let { StatsText(it, size = labelSize, color = p.muted, maxLines = if (labelSize < 10) 1 else Int.MAX_VALUE) }
        androidx.compose.foundation.text.BasicTextField(value, onChange,
            modifier = Modifier.fillMaxWidth().height(fieldHeight).background(p.background, RoundedCornerShape(9.dp))
                .border(1.dp, if (isError) p.red else p.line, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp).wrapContentHeight(Alignment.CenterVertically),
            textStyle = TextStyle(color = p.ink, fontSize = fontSize.sp, fontFamily = FontFamily.SansSerif),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
    }
}

@Composable
internal fun TokenStatsResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalTokenStatsPalette.current
    // 单调时钟保护原有五秒决定时间，重新组合和系统时间变化均不能提前解锁。
    val deadline = remember { SystemClock.elapsedRealtime() + 5_000L }
    var remaining by remember { mutableStateOf(5) }
    LaunchedEffect(deadline) {
        while (remaining > 0) {
            delay(100)
            remaining = ((deadline - SystemClock.elapsedRealtime() + 999L) / 1_000L).coerceAtLeast(0L).toInt()
        }
    }
    StatsSheet("RESET USAGE", stringResource(R.string.token_insights_clear_all), onDismiss, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsAction(stringResource(R.string.token_insights_cancel), onDismiss, Modifier.weight(.6f))
            StatsPrimaryAction(if (remaining > 0) stringResource(R.string.token_stats_usage_reset_confirm_wait, remaining) else stringResource(R.string.token_insights_clear_confirm), {
                if (SystemClock.elapsedRealtime() >= deadline) onConfirm()
            }, Modifier.weight(1f), enabled = remaining == 0)
        }
    }) { StatsText(stringResource(R.string.token_insights_clear_warning), size = 12, color = p.muted) }
}

@Composable
internal fun TokenStatsFilterDialog(state: TokenStatsUiState, onApply: (TokenStatsInsightsFilter) -> Unit, onDismiss: () -> Unit) {
    val p = LocalTokenStatsPalette.current
    // 保留原筛选草稿语义，取消不会写回，全选与不限制维持原来的区别。
    var draft by remember { mutableStateOf(state.filter) }
    var category by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    val models = state.catalog?.models.orEmpty().mapNotNull { it.key }.distinct().sorted().map { it to it }
    val configs = state.catalog?.configurations.orEmpty().map { it.configId }.distinct()
        .filter { it.isBlank() || it in state.configurationNames }
        .map { it to insightsConfigurationName(it, state) }
    val functions = FunctionType.values().map { it.name to insightsFunction(it.name) }
    val executions = state.catalog?.executionModels.orEmpty().mapNotNull { it.key }.distinct().sorted().map { it to it }
    val statuses = listOf("SUCCESS", "FAILED", "CANCELLED", "COMPLETED").map { it to insightsStatus(it) }
    val periods = TokenStatsPeakPeriod.values().map { it.name to insightsPeakName(it) }
    val optionGroups = listOf(models, configs, functions, executions, statuses, periods)
    val categories = listOf(stringResource(R.string.token_insights_models), stringResource(R.string.token_insights_configurations),
        stringResource(R.string.token_insights_source), stringResource(R.string.token_insights_execution_model),
        stringResource(R.string.token_insights_status), stringResource(R.string.token_insights_peak_usage))
    val selected = when (category) {
        0 -> draft.models
        1 -> draft.configIds
        2 -> draft.functionTypes
        3 -> draft.executionModels
        4 -> draft.statuses
        else -> draft.peakPeriods?.map { it.name }?.toSet()
    }
    val change: (Set<String>?) -> Unit = { values ->
        draft = when (category) {
            0 -> draft.copy(models = values)
            1 -> draft.copy(configIds = values)
            2 -> draft.copy(functionTypes = values)
            3 -> draft.copy(executionModels = values)
            4 -> draft.copy(statuses = values)
            else -> draft.copy(peakPeriods = values?.map { TokenStatsPeakPeriod.valueOf(it) }?.toSet())
        }
    }
    StatsSheet("FILTERS", stringResource(R.string.token_insights_filters), onDismiss, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsAction(stringResource(R.string.token_insights_reset_filters), {
                draft = draft.copy(models = null, configIds = null, functionTypes = null, executionModels = null, statuses = null, peakPeriods = null)
            }, Modifier.weight(.6f))
            StatsPrimaryAction(stringResource(R.string.token_insights_apply), { onApply(draft) }, Modifier.weight(1f))
        }
    }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            StatsSegment(categories, category, { category = it; search = "" })
        }
        Spacer(Modifier.height(16.dp))
        StatsDialogField(search, { search = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.token_stats_design_filter_search), keyboardType = KeyboardType.Text)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatsText("${selected?.size ?: optionGroups[category].size} / ${optionGroups[category].size}", Modifier.weight(1f), size = 10, color = p.muted)
            StatsAction(stringResource(R.string.token_insights_select_all), { change(optionGroups[category].map { it.first }.toSet()) })
            StatsAction(stringResource(R.string.token_insights_select_none), { change(emptySet()) })
        }
        optionGroups[category].filter { it.second.contains(search, ignoreCase = true) || it.first.contains(search, ignoreCase = true) }.forEach { (id, label) ->
            val checked = selected == null || id in selected
            val toggle = { change((selected ?: optionGroups[category].map { it.first }.toSet()).let { if (id in it) it - id else it + id }) }
            Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { toggle() }, colors = CheckboxDefaults.colors(checkedColor = p.accent, uncheckedColor = p.faint, checkmarkColor = p.background))
                StatsText(label, Modifier.weight(1f), size = 12)
            }
            StatsDivider()
        }
    }
}
