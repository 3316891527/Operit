package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.stats.*
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
internal fun InsightsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTokenStatsColors.current
    Surface(color = colors.cardContainer, contentColor = colors.cardContent,
        shape = RoundedCornerShape(20.dp), tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun InsightsKeyValue(label: String, value: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalTokenStatsColors.current.cardSupportingContent, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
            color = if (accent) LocalTokenStatsColors.current.cardAccent else LocalTokenStatsColors.current.cardContent,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
internal fun InsightsChips(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, (label, selected) ->
            FilterChip(selected = selected, onClick = { onSelect(index) }, label = { Text(label, maxLines = 1) })
        }
    }
}

internal fun insightsMoney(amount: Double, currency: PricingCurrency): String =
    String.format(Locale.getDefault(), "%s%,.4f", currency.symbol, amount)

@Composable
internal fun insightsCost(cost: TokenStatsCostSummary): String = when {
    cost.unknownContributionCount > 0 && cost.knownAmount == 0.0 -> stringResource(R.string.token_insights_unknown_cost)
    cost.unknownContributionCount > 0 -> insightsMoney(cost.knownAmount, cost.currency) + " + ?"
    else -> insightsMoney(cost.knownAmount, cost.currency)
}

@Composable
internal fun insightsTokens(value: TokenStatsTokenAggregate, unit: TokenStatsDisplayUnit): String =
    if (value.knownEventCount == 0L && value.unknownEventCount > 0L) stringResource(R.string.token_insights_not_recorded)
    else formatTokenCount(value.knownSum, unit) + if (value.unknownEventCount > 0L) " + ?" else ""

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

@Composable
internal fun InsightsTotals(totals: TokenStatsInsightsTotals, state: TokenStatsUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.token_insights_tokens), style = MaterialTheme.typography.labelSmall, color = LocalTokenStatsColors.current.cardSupportingContent)
            Text(insightsTokens(totals.totalTokens, state.tokenDisplayUnit), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.token_insights_request_count), style = MaterialTheme.typography.labelSmall, color = LocalTokenStatsColors.current.cardSupportingContent)
            Text(String.format(Locale.getDefault(), "%,d", totals.requests), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.token_insights_cost), style = MaterialTheme.typography.labelSmall, color = LocalTokenStatsColors.current.cardSupportingContent)
            Text(insightsCost(totals.cost), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, color = LocalTokenStatsColors.current.cardAccent)
        }
    }
}

@Composable
internal fun InsightsOverviewHero(state: TokenStatsUiState) {
    val data = state.overview ?: return
    InsightsCard {
        Text(stringResource(R.string.token_insights_cost), style = MaterialTheme.typography.labelLarge, color = LocalTokenStatsColors.current.cardSupportingContent)
        Text(insightsCost(data.totals.cost), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = LocalTokenStatsColors.current.cardAccent)
        val comparison = data.comparison
        if (comparison != null) {
            val other = comparison.totals
            when {
                comparison.validity != TokenStatsComparisonValidity.AVAILABLE -> Text(stringResource(R.string.token_insights_comparison_invalid), style = MaterialTheme.typography.bodySmall)
                other != null && (data.totals.cost.unknownContributionCount > 0 || other.cost.unknownContributionCount > 0) -> Text(stringResource(R.string.token_insights_comparison_incomplete), style = MaterialTheme.typography.bodySmall)
                other != null -> {
                    val difference = data.totals.cost.knownAmount - other.cost.knownAmount
                    val change = if (other.cost.knownAmount == 0.0) (if (difference > 0) "+" else "") + insightsMoney(difference, state.targetCurrency)
                        else String.format(Locale.getDefault(), "%+.1f%%", difference / other.cost.knownAmount * 100)
                    Text(stringResource(R.string.token_insights_change, change), color = insightsChangeColor(difference >= 0, state.increaseIsRed), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        HorizontalDivider(color = LocalTokenStatsColors.current.cardBorder)
        InsightsTotals(data.totals, state)
        val inputParts = listOf(data.totals.uncachedInput, data.totals.cachedInput, data.totals.cacheWrite)
        val input = inputParts.sumOf { it.knownSum.toDouble() }
        InsightsKeyValue(stringResource(R.string.token_insights_cache_rate), if (inputParts.all { it.isFullyKnown } && input > 0) String.format(Locale.getDefault(), "%.1f%%", data.totals.cachedInput.knownSum / input * 100) else stringResource(R.string.token_insights_not_recorded))
        if (data.totals.unconfirmedUsageRequests > 0L) Text(stringResource(R.string.token_insights_usage_unconfirmed), style = MaterialTheme.typography.bodySmall, color = LocalTokenStatsColors.current.cardSupportingContent)
        if (data.totals.historicalAggregateRequests > 0L) Text(stringResource(R.string.token_insights_legacy_aggregate_note, data.totals.historicalAggregateRequests), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun insightsChangeColor(increased: Boolean, increaseIsRed: Boolean): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (increased == increaseIsRed) { if (dark) Color(0xFFE36870) else Color(0xFFBA3043) }
    else { if (dark) Color(0xFF39AE88) else Color(0xFF137B5E) }
}

@Composable
internal fun InsightsBudget(state: TokenStatsUiState) {
    val budgetCny = state.monthlyBudgetCny ?: return
    val cost = state.monthlyCost ?: return
    val budget = if (state.targetCurrency == PricingCurrency.CNY) budgetCny else budgetCny / state.manualRate
    InsightsCard(stringResource(R.string.token_insights_budget_title)) {
        InsightsKeyValue(stringResource(R.string.token_insights_this_month), stringResource(R.string.token_insights_budget_progress, insightsCost(cost), insightsMoney(budget, state.targetCurrency)))
        LinearProgressIndicator(progress = { (cost.knownAmount / budget).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (cost.knownAmount > budget) MaterialTheme.colorScheme.error else LocalTokenStatsColors.current.cardAccent)
    }
}

@Composable
internal fun InsightsComposition(state: TokenStatsUiState, cost: Boolean) {
    val data = state.overview ?: return
    val composition = data.costComposition
    val totals = data.totals
    val labels = listOf(R.string.token_insights_uncached, R.string.token_insights_cached, R.string.token_insights_cache_write, R.string.token_insights_output)
    val values = if (cost) listOf(composition.uncachedInput, composition.cachedInput, composition.cacheWrite, composition.output, composition.perRequest, composition.unallocatedInput)
        else listOf(totals.uncachedInput.knownSum.toDouble(), totals.cachedInput.knownSum.toDouble(), totals.cacheWrite.knownSum.toDouble(), totals.output.knownSum.toDouble())
    val aggregates = listOf(totals.uncachedInput, totals.cachedInput, totals.cacheWrite, totals.output)
    val denominator = values.sum().let { if (it > 0.0) it else 1.0 }
    InsightsCard(stringResource(if (cost) R.string.token_insights_cost_composition else R.string.token_insights_token_composition)) {
        values.forEachIndexed { index, value ->
            val label = stringResource(if (index < 4) labels[index] else if (index == 4) R.string.token_insights_per_request else R.string.token_insights_unallocated_input)
            InsightsKeyValue(label, if (cost) insightsMoney(value, state.targetCurrency) else insightsTokens(aggregates[index], state.tokenDisplayUnit))
            LinearProgressIndicator(progress = { (value / denominator).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = LocalTokenStatsColors.current.modelPalette[index % LocalTokenStatsColors.current.modelPalette.size])
        }
        if (cost && totals.cost.unknownContributionCount > 0L) Text(stringResource(R.string.token_insights_partial_cost), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun InsightsQuality(state: TokenStatsUiState) {
    val totals = state.overview?.totals ?: return
    InsightsCard(stringResource(R.string.token_insights_request_quality)) {
        listOf("SUCCESS", "FAILED", "CANCELLED", "COMPLETED").forEach { status ->
            InsightsKeyValue(insightsStatus(status), (totals.statusCounts[status] ?: 0L).toString())
        }
        InsightsKeyValue(stringResource(R.string.token_insights_unknown_status), totals.unknownStatusRequests.toString())
        InsightsKeyValue(stringResource(R.string.token_insights_known_usage), totals.usageFactRequests.toString())
    }
}

@Composable
internal fun InsightsBreakdowns(state: TokenStatsUiState, peak: Boolean, onSelect: (String?) -> Unit) {
    val items = if (peak) state.overview?.peakPeriods.orEmpty() else state.overview?.functions.orEmpty()
    InsightsCard(stringResource(if (peak) R.string.token_insights_peak_usage else R.string.token_insights_source)) {
        if (items.isEmpty()) Text(stringResource(R.string.token_insights_empty), style = MaterialTheme.typography.bodySmall)
        items.sortedByDescending { it.totals.requests }.forEach { row ->
            Column(Modifier.fillMaxWidth().clickable(enabled = row.key != null) { onSelect(row.key) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (peak) insightsPeakName(TokenStatsPeakPeriod.valueOf(requireNotNull(row.key))) else insightsFunction(row.key), style = MaterialTheme.typography.labelLarge)
                InsightsTotals(row.totals, state)
            }
        }
    }
}

private fun insightsMetric(totals: TokenStatsInsightsTotals, metric: TokenStatsInsightMetric): Double = when (metric) {
    TokenStatsInsightMetric.COST -> totals.cost.knownAmount
    TokenStatsInsightMetric.TOKENS -> totals.totalTokens.knownSum.toDouble()
    TokenStatsInsightMetric.REQUESTS -> totals.requests.toDouble()
}

@Composable
internal fun InsightsTrend(state: TokenStatsUiState, zone: ZoneId, onBucket: (TokenStatsTimeRange) -> Unit) {
    val data = state.overview ?: return
    var metric by rememberSaveable { mutableStateOf(TokenStatsInsightMetric.COST) }
    val accent = LocalTokenStatsColors.current.chartAccent
    val grid = LocalTokenStatsColors.current.chartGrid
    val increasing = insightsChangeColor(true, state.increaseIsRed)
    val decreasing = insightsChangeColor(false, state.increaseIsRed)
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val metricLabels = listOf(R.string.token_insights_cost, R.string.token_insights_tokens, R.string.token_insights_request_count)
    InsightsCard(stringResource(R.string.token_insights_trend)) {
        InsightsChips(metricLabels.mapIndexed { i, label -> stringResource(label) to (TokenStatsInsightMetric.entries[i] == metric) }) { metric = TokenStatsInsightMetric.entries[it] }
        val buckets = data.buckets
        val previous = data.comparison?.takeIf { it.validity == TokenStatsComparisonValidity.AVAILABLE }?.buckets.orEmpty()
        if (buckets.isEmpty()) Text(stringResource(R.string.token_insights_empty), style = MaterialTheme.typography.bodySmall)
        else {
            val values = buckets.map { insightsMetric(it.totals, metric) }
            val comparison = previous.map { insightsMetric(it.totals, metric) }
            val maxValue = max(values.maxOrNull() ?: 0.0, comparison.maxOrNull() ?: 0.0).let { if (it > 0.0) it else 1.0 }
            val description = stringResource(R.string.token_insights_chart_accessibility)
            Text(when (metric) {
                TokenStatsInsightMetric.COST -> insightsCost(data.totals.cost)
                TokenStatsInsightMetric.TOKENS -> insightsTokens(data.totals.totalTokens, state.tokenDisplayUnit)
                TokenStatsInsightMetric.REQUESTS -> data.totals.requests.toString()
            }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = accent)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chartWidth = maxOf(maxWidth, 12.dp * buckets.size)
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Canvas(Modifier.width(chartWidth).height(172.dp).semantics { contentDescription = description }
                        .pointerInput(buckets, state.filter.range) {
                            detectTapGestures { tap ->
                                val index = (tap.x / size.width * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
                                val bucket = buckets[index]
                                val range = state.filter.range
                                val start = if (range == null) bucket.startMs else maxOf(bucket.startMs, range.startMs)
                                val end = if (range == null) bucket.endMs else minOf(bucket.endMs, range.endMs)
                                if (end > start) onBucket(TokenStatsTimeRange(start, end))
                            }
                        }) {
                        val base = size.height - 6.dp.toPx()
                        val height = base - 10.dp.toPx()
                        repeat(4) { line -> val y = base - height * line / 3; drawLine(grid.copy(alpha = .5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
                        val step = size.width / values.size
                        values.forEachIndexed { index, value ->
                            val reference = comparison.getOrNull(index) ?: values.getOrNull(index - 1)
                            val color = if (reference == null || reference == value) accent else if (value > reference) increasing else decreasing
                            val barHeight = (value / maxValue * height).toFloat()
                            if (barHeight > 0f) drawRoundRect(color.copy(alpha = .88f), Offset(index * step + step * .18f, base - barHeight), Size(step * .64f, barHeight), CornerRadius(3.dp.toPx()))
                        }
                        if (comparison.isNotEmpty()) {
                            val path = Path()
                            comparison.forEachIndexed { index, value ->
                                val x = if (comparison.size == 1) size.width / 2 else (index.toFloat() / (comparison.size - 1)) * (size.width - step) + step / 2
                                val y = base - (value / maxValue * height).toFloat()
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
                        }
                    }
                    Row(Modifier.width(chartWidth), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(0, buckets.size / 2, buckets.lastIndex).distinct().forEach { index ->
                            Text(insightsDate(buckets[index].startMs, zone, if (data.granularity == TokenStatsInsightsGranularity.HOURLY) "MM/dd HH:mm" else "MM/dd"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(stringResource(if (comparison.isEmpty()) R.string.token_insights_chart_hint else R.string.token_insights_chart_comparison_hint), style = MaterialTheme.typography.bodySmall, color = LocalTokenStatsColors.current.cardSupportingContent)
        }
    }
}

@Composable
internal fun InsightsModelCard(model: TokenStatsInsightsBreakdown, state: TokenStatsUiState, onRequests: (String) -> Unit) {
    val name = model.key ?: return
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    InsightsCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.token_insights_models), style = MaterialTheme.typography.labelSmall, color = LocalTokenStatsColors.current.cardSupportingContent)
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "−" else "+") }
        }
        InsightsTotals(model.totals, state)
        if (expanded) {
            state.overview?.configurations.orEmpty().filter { it.model == name && (it.configId.isBlank() || it.configId in state.configurationNames) }.forEach { config ->
                HorizontalDivider(color = LocalTokenStatsColors.current.cardBorder)
                InsightsKeyValue(insightsConfigurationName(config.configId, state), config.provider)
                InsightsTotals(config.totals, state)
            }
            TextButton(onClick = { onRequests(name) }) { Text(stringResource(R.string.token_insights_view_model_requests)) }
        }
    }
}

@Composable
internal fun InsightsConfigurationCard(config: TokenStatsInsightsConfiguration, state: TokenStatsUiState, onRequests: () -> Unit, onEdit: (TokenStatsPriceSetting?, TokenStatsPriceDraft, String) -> Unit, onReset: (TokenStatsPriceSetting) -> Unit) {
    var expanded by rememberSaveable(config.configId, config.provider, config.model) { mutableStateOf(false) }
    val name = insightsConfigurationName(config.configId, state)
    val draft = priceDraftForIdentity(config.configId, config.provider, config.model, state.priceSettings)
    val setting = state.priceSettings.firstOrNull { it.provider == config.provider && it.model == config.model && it.configId.orEmpty() == config.configId }
    InsightsCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${config.provider} · ${config.model}", style = MaterialTheme.typography.bodySmall, color = LocalTokenStatsColors.current.cardSupportingContent)
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "−" else "+") }
        }
        InsightsTotals(config.totals, state)
        if (expanded) {
            HorizontalDivider(color = LocalTokenStatsColors.current.cardBorder)
            InsightsPricingRules(draft)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onEdit(setting, draft, name) }) { Text(stringResource(R.string.token_insights_edit_price)) }
                if (setting != null) TextButton(onClick = { onReset(setting) }) { Text(stringResource(R.string.token_insights_reset_price)) }
            }
            TextButton(onClick = onRequests) { Text(stringResource(R.string.token_insights_view_requests)) }
        }
    }
}

@Composable
internal fun InsightsPricingRules(draft: TokenStatsPriceDraft) {
    InsightsKeyValue(stringResource(R.string.token_insights_pricing_rules), stringResource(if (draft.billingMode == BillingMode.COUNT) R.string.token_insights_billing_count else R.string.token_insights_billing_token))
    if (draft.billingMode == BillingMode.COUNT) InsightsKeyValue(stringResource(R.string.token_insights_per_request), draft.pricePerRequest?.let { insightsMoney(it, draft.currency) } ?: stringResource(R.string.token_insights_unknown_cost))
    else {
        listOf(R.string.token_insights_uncached to draft.inputPricePerMillion, R.string.token_insights_cached to draft.cachedInputPricePerMillion, R.string.token_insights_cache_write to draft.cacheWritePricePerMillion, R.string.token_insights_output to draft.outputPricePerMillion).forEach { (label, value) ->
            InsightsKeyValue(stringResource(label), value?.let { insightsMoney(it, draft.currency) } ?: stringResource(R.string.token_insights_unknown_cost))
        }
        Text(stringResource(R.string.token_insights_price_unit), style = MaterialTheme.typography.labelSmall)
    }
    if (draft.peakPricingEnabled) InsightsKeyValue(stringResource(R.string.token_insights_peak_usage), "×${draft.peakInputMultiplier} / ×${draft.peakCachedInputMultiplier} / ×${draft.peakCacheWriteMultiplier} / ×${draft.peakOutputMultiplier}")
    if (draft.longContextPricingEnabled) InsightsKeyValue(stringResource(R.string.token_insights_long_context), "${draft.longContextThreshold} · ×${draft.longContextInputMultiplier} / ×${draft.longContextCachedInputMultiplier} / ×${draft.longContextCacheWriteMultiplier} / ×${draft.longContextOutputMultiplier}")
}

@Composable
internal fun InsightsRequestCard(row: TokenStatsRequestRow, state: TokenStatsUiState, zone: ZoneId, onClick: () -> Unit) {
    InsightsCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.model, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(insightsCost(row.cost), style = MaterialTheme.typography.labelLarge, color = LocalTokenStatsColors.current.cardAccent)
            }
            Text(insightsConfigurationName(row.configId, state) + " · " + row.provider, style = MaterialTheme.typography.bodySmall, color = LocalTokenStatsColors.current.cardSupportingContent)
            InsightsKeyValue(row.occurredAtMs?.let { insightsDate(it, zone) } ?: stringResource(R.string.token_insights_not_recorded), insightsStatus(row.status))
            InsightsKeyValue(insightsFunction(row.functionType), row.totalTokens?.let { formatTokenCount(it, state.tokenDisplayUnit) } ?: stringResource(R.string.token_insights_not_recorded))
            if (row.executionModel != null) InsightsKeyValue(stringResource(R.string.token_insights_execution_model), row.executionModel)
        }
        TextButton(onClick = onClick) { Text(stringResource(R.string.token_insights_details)) }
    }
}

@Composable
internal fun InsightsExecutionModels(state: TokenStatsUiState, onSelect: (String) -> Unit) {
    val rows = state.overview?.executionModels.orEmpty()
    if (rows.isEmpty()) return
    InsightsCard(stringResource(R.string.token_insights_execution_model)) {
        rows.forEach { row ->
            val model = row.key
            if (model != null) Column(Modifier.fillMaxWidth().clickable { onSelect(model) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(model, style = MaterialTheme.typography.labelLarge)
                InsightsTotals(row.totals, state)
            }
        }
    }
}
