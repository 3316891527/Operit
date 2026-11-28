package com.ai.assistance.operit.ui.features.models.tokenstats

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.stats.*
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** 总览布局对应已确认 HTML V2；这里只读取已有汇总，不从分页请求推算周期指标。 */
@Composable
internal fun TokenStatsOverviewContent(
    state: TokenStatsUiState,
    zone: ZoneId,
    onGranularity: (TokenStatsInsightsGranularity) -> Unit,
    onRequests: (model: String?, functionType: String?, executionModel: String?, range: TokenStatsTimeRange?) -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onRequestSort: (TokenStatsRequestSort) -> Unit,
) {
    val data = state.overview ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StatsOverviewHero(data, state.visibleConfigurations) { onRequests(null, null, null, null) }
        StatsOverviewMetrics(data.totals, state.tokenDisplayUnit)
        StatsOverviewTrend(state, zone, data, onGranularity) { onRequests(null, null, null, it) }
        StatsOverviewExpenses(state, data)
        StatsOverviewAttention(data, { onRequests(null, null, null, null) }, onRequestSort)
        StatsOverviewTokens(data.totals, state.tokenDisplayUnit)
        StatsOverviewQuality(data.quality, data.totals.cost.currency) { onRequestSort(TokenStatsRequestSort.DURATION_DESC) }
        StatsOverviewRanking(data, state.visibleConfigurations, state.tokenDisplayUnit, onModels) { onRequests(it, null, null, null) }
        StatsOverviewSources(data, onRequests)
        StatsOverviewBudget(state, onSettings)
    }
}

@Composable
private fun StatsOverviewHero(
    data: TokenStatsInsightsOverview,
    visibleConfigurations: List<TokenStatsInsightsConfiguration>,
    onRequests: () -> Unit,
) {
    val compact = LocalConfiguration.current.screenWidthDp < 370
    val ink = Color(0xFFF2F5FF)
    val muted = Color(0xFFAEBBD8)
    val cost = data.totals.cost
    val unknown = cost.unknownContributionCount
    val amount = String.format(Locale.US, "%,.8f", cost.knownAmount).split('.')
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF263554))
        .border(1.dp, Color(0xFF4C5E8B), RoundedCornerShape(20.dp))) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width - 5.dp.toPx(), 20.dp.toPx())
            listOf(56f to .21f, 80f to .04f, 104f to .025f).forEach { (radius, alpha) ->
                drawCircle(Color(0xFF738CC7).copy(alpha = alpha), radius.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxWidth().padding(if (compact) 20.dp else 23.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatsText(stringResource(R.string.token_stats_design_known_cost), color = Color(0xFFD2DAEA), size = 12)
                StatsOverviewGlyph("info", Color(0xFFD2DAEA), Modifier.size(13.dp))
            }
            Row(Modifier.padding(top = 13.dp, bottom = 5.dp), verticalAlignment = Alignment.Bottom) {
                if (unknown > 0L && cost.knownAmount == 0.0) {
                    StatsText("—", size = if (compact) 48 else 55, color = ink, weight = FontWeight.Medium)
                } else {
                    StatsText(cost.currency.symbol, Modifier.alignByBaseline().padding(end = 5.dp), size = if (compact) 21 else 23, color = ink)
                    StatsText(amount[0], Modifier.alignByBaseline(), size = if (compact) 48 else 55, color = ink, weight = FontWeight.Medium, maxLines = 1)
                    StatsText("." + amount[1], Modifier.alignByBaseline(), size = if (compact) 21 else 26, color = Color(0xFFC2CFE9), weight = FontWeight.Medium)
                }
            }
            val comparison = data.comparison
            val other = comparison?.totals?.cost
            if (comparison?.validity == TokenStatsComparisonValidity.AVAILABLE && other != null &&
                other.knownAmount > 0.0 && other.unknownContributionCount == 0L && unknown == 0L) {
                val delta = (cost.knownAmount / other.knownAmount - 1.0) * 100.0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsText((if (delta < 0) "↘ " else "↗ ") + String.format(Locale.getDefault(), "%.1f%%", abs(delta)),
                        size = 11, color = if (delta < 0) Color(0xFF9BD9CA) else Color(0xFFEDB6A3), weight = FontWeight.SemiBold)
                    StatsText(stringResource(R.string.token_insights_previous_period) + " " + insightsMoney(other.knownAmount, other.currency), size = 11, color = muted)
                }
            } else {
                StatsText(stringResource(if (comparison == null) R.string.token_insights_comparison_off else R.string.token_stats_design_comparison_missing), size = 11, color = muted)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF8093BC).copy(alpha = .25f)))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val coverage = if (unknown > 0L) stringResource(R.string.token_stats_design_unpriced_count, overviewCount(unknown)) else stringResource(R.string.token_stats_design_coverage_complete)
                Column(Modifier.weight(1f).clickable(onClick = onRequests).padding(vertical = 3.dp)) {
                    StatsText(coverage + " · " + stringResource(R.string.token_stats_design_model_config_count, data.models.size, visibleConfigurations.size) + "  ›",
                        color = Color(0xFFCAD5EE), size = 10)
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewMetrics(totals: TokenStatsInsightsTotals, unit: TokenStatsDisplayUnit) {
    val inputParts = listOf(totals.uncachedInput, totals.cachedInput, totals.cacheWrite)
    val input = inputParts.fold(0L) { sum, part -> TokenCostCalculator.saturatedAdd(sum, part.knownSum) }
    val inputText = if (inputParts.all { it.isFullyKnown }) formatTokenCount(input, unit) else "—"
    val cacheRate = tokenStatsCacheReadRatio(totals)
    val success = totals.statusCounts["SUCCESS"] ?: 0L
    val failed = totals.statusCounts["FAILED"] ?: 0L
    val cancelled = totals.statusCounts["CANCELLED"] ?: 0L
    val successRate = tokenStatsSuccessRatio(totals)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsOverviewMetric("cube", stringResource(R.string.token_stats_design_total_tokens), overviewTokens(totals.totalTokens, unit),
                stringResource(R.string.token_stats_design_input_output, inputText, overviewTokens(totals.output, unit)), Modifier.weight(1f))
            StatsOverviewMetric("list", stringResource(R.string.token_stats_design_model_requests), overviewCount(totals.requests),
                if (totals.unknownStatusRequests > 0L) stringResource(R.string.token_insights_unknown_status) + " " + overviewCount(totals.unknownStatusRequests)
                else stringResource(R.string.token_stats_design_success_failed, overviewCount(success), overviewCount(failed)), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsOverviewMetric("spark", stringResource(R.string.token_stats_design_cache_rate), overviewRatioText(cacheRate),
                stringResource(R.string.token_stats_design_cache_basis), Modifier.weight(1f),
                tokenStatsRatioLevel(cacheRate, TokenStatsRatioKind.CACHE_READ))
            StatsOverviewMetric("check", stringResource(R.string.token_stats_design_success_rate), overviewRatioText(successRate),
                if (successRate == null) stringResource(R.string.token_insights_not_recorded)
                else stringResource(R.string.token_stats_design_cancelled_count, overviewCount(cancelled)), Modifier.weight(1f),
                tokenStatsRatioLevel(successRate, TokenStatsRatioKind.REQUEST_SUCCESS))
        }
    }
}

@Composable
private fun StatsOverviewMetric(
    icon: String,
    label: String,
    value: String,
    note: String,
    modifier: Modifier,
    level: TokenStatsRatioLevel? = null,
) {
    val p = LocalTokenStatsPalette.current
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(p.panel).border(1.dp, p.line, RoundedCornerShape(15.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatsOverviewGlyph(icon, p.faint, Modifier.size(12.dp))
            StatsText(label, size = 10, color = p.muted, maxLines = 1)
        }
        val dark = p.panel.luminance() < 0.5f
        val valueColor = when (level) {
            TokenStatsRatioLevel.GREEN -> if (dark) Color(0xFF85D8A4) else Color(0xFF176B39)
            TokenStatsRatioLevel.YELLOW -> if (dark) p.amber else Color(0xFF795000)
            TokenStatsRatioLevel.RED -> if (dark) p.red else Color(0xFFA33139)
            TokenStatsRatioLevel.UNKNOWN -> p.muted
            null -> p.ink
        }
        StatsText(value, Modifier.padding(top = 7.dp), size = 23, color = valueColor, weight = FontWeight.Medium, maxLines = 1)
        StatsText(note, Modifier.padding(top = 5.dp).heightIn(min = 27.dp), size = 9, color = p.muted, maxLines = 2)
    }
}

@Composable
private fun StatsOverviewTrend(
    state: TokenStatsUiState,
    zone: ZoneId,
    data: TokenStatsInsightsOverview,
    onGranularity: (TokenStatsInsightsGranularity) -> Unit,
    onRequests: (TokenStatsTimeRange) -> Unit,
) {
    val p = LocalTokenStatsPalette.current
    var metric by rememberSaveable { mutableIntStateOf(0) }
    val buckets = data.buckets
    var selected by remember(buckets) { mutableIntStateOf(buckets.lastIndex) }
    val previous = data.comparison?.takeIf { it.validity == TokenStatsComparisonValidity.AVAILABLE }?.buckets.orEmpty()
    val options = listOf(R.string.token_insights_auto, R.string.token_insights_hourly, R.string.token_insights_daily, R.string.token_insights_weekly, R.string.token_insights_monthly).map { stringResource(it) }
    val labels = listOf(R.string.token_insights_cost, R.string.token_insights_tokens, R.string.token_insights_request_count).map { stringResource(it) }
    StatsPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StatsText(stringResource(R.string.token_insights_trend), size = 15, weight = FontWeight.SemiBold)
            StatsSelect(options[state.granularity.ordinal], options, state.granularity.ordinal, { onGranularity(TokenStatsInsightsGranularity.entries[it]) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StatsSegment(labels, metric, { metric = it })
            StatsText(options[data.granularity.ordinal], size = 11, color = p.muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatsOverviewLegend(stringResource(R.string.token_stats_design_current_period), p.accent)
            if (previous.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.width(12.dp).height(6.dp)) { drawLine(p.teal, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))) }
                StatsText(stringResource(R.string.token_stats_design_comparison_relative), size = 9, color = p.muted)
            }
        }
        if (buckets.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(211.dp), contentAlignment = Alignment.Center) { StatsText(stringResource(R.string.token_insights_empty), color = p.muted) }
        } else {
            val values = buckets.map { overviewMetric(it.totals, metric) }
            val comparisons = previous.map { overviewMetric(it.totals, metric) }
            val dates = buckets.map { insightsDate(it.startMs, zone, if (data.granularity == TokenStatsInsightsGranularity.HOURLY) "HH:mm" else "MM/dd") }
            val maximum = max(values.maxOrNull() ?: 0.0, comparisons.maxOrNull() ?: 0.0)
            val power = if (maximum > 0.0) 10.0.pow(floor(log10(maximum))) else 1.0
            val ceiling = max(power, ceil(maximum / power / .5) * power * .5)
            val ticks = listOf(0.0, .5, 1.0).map { f ->
                val value = ceiling * f
                when (metric) {
                    0 -> state.targetCurrency.symbol + String.format(Locale.getDefault(), if (value >= 10) "%.0f" else "%.1f", value)
                    1 -> formatTokenCount(value.toLong(), state.tokenDisplayUnit)
                    else -> overviewCompact(value)
                }
            }
            val chartDescription = stringResource(R.string.token_insights_chart_accessibility)
            Canvas(Modifier.fillMaxWidth().height(211.dp).semantics { contentDescription = chartDescription }
                .pointerInput(buckets) {
                    detectTapGestures { point ->
                        val left = 35.dp.toPx()
                        val width = size.width - left - 4.dp.toPx()
                        if (point.x >= left && point.x <= left + width) selected = ((point.x - left) / width * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
                    }
                }) {
                val left = 35.dp.toPx()
                val top = 13.dp.toPx()
                val width = size.width - left - 4.dp.toPx()
                val height = size.height - top - 29.dp.toPx()
                val base = top + height
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.muted.toArgb(); textSize = 9.sp.toPx(); textAlign = Paint.Align.RIGHT }
                ticks.forEachIndexed { index, text ->
                    val y = base - height * index / 2
                    drawLine(p.line, Offset(left, y), Offset(left + width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())))
                    drawContext.canvas.nativeCanvas.drawText(text, left - 6.dp.toPx(), y + 3.dp.toPx(), paint)
                }
                val step = width / buckets.size
                val barWidth = minOf(34.dp.toPx(), step * .54f)
                val labelEvery = ceil(buckets.size.toDouble() / max(3, (width / 48.dp.toPx()).toInt())).toInt().coerceAtLeast(1)
                paint.textAlign = Paint.Align.CENTER
                values.forEachIndexed { index, value ->
                    val x = left + step * (index + .5f)
                    val barHeight = (value / ceiling * height).toFloat().coerceAtLeast(0f)
                    if (barHeight > 0f) drawRoundRect(p.accent.copy(alpha = if (selected == index) 1f else .36f),
                        Offset(x - barWidth / 2, base - barHeight), Size(barWidth, barHeight), CornerRadius(minOf(5.dp.toPx(), barWidth / 3)))
                    if ((index % labelEvery == 0 && index < buckets.lastIndex - labelEvery / 2) || index == buckets.lastIndex) {
                        drawContext.canvas.nativeCanvas.drawText(dates[index], x, base + 20.dp.toPx(), paint)
                    }
                }
                if (comparisons.isNotEmpty()) {
                    val path = Path()
                    comparisons.forEachIndexed { index, value ->
                        val x = left + (index + .5f) / comparisons.size * width
                        val y = base - (value / ceiling * height).toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, p.teal.copy(alpha = .85f), style = Stroke(1.7.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
                }
            }
            StatsDivider()
            val point = buckets[selected.coerceIn(0, buckets.lastIndex)]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatsOverviewIconButton("left", stringResource(R.string.token_insights_previous), { selected-- }, enabled = selected > 0)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    StatsText(insightsDate(point.startMs, zone) + " — " + insightsDate(point.endMs - 1, zone), size = 10, color = p.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatsText(overviewMetricLabel(point.totals, metric, state.tokenDisplayUnit), size = 14, weight = FontWeight.Medium)
                        StatsText(if (metric == 2) overviewTokens(point.totals.totalTokens, state.tokenDisplayUnit) + " Token" else stringResource(R.string.token_stats_design_request_times, overviewCount(point.totals.requests)), size = 10, color = p.muted)
                    }
                    StatsAction(stringResource(R.string.token_stats_design_point_requests, overviewCount(point.totals.requests)), {
                        val range = state.filter.range
                        val start = if (range == null) point.startMs else maxOf(point.startMs, range.startMs)
                        val end = if (range == null) point.endMs else minOf(point.endMs, range.endMs)
                        if (end > start) onRequests(TokenStatsTimeRange(start, end))
                    }, enabled = point.totals.requests > 0)
                }
                StatsOverviewIconButton("right", stringResource(R.string.token_insights_next), { selected++ }, enabled = selected < buckets.lastIndex)
            }
        }
    }
}

@Composable
private fun StatsOverviewExpenses(state: TokenStatsUiState, data: TokenStatsInsightsOverview) {
    val p = LocalTokenStatsPalette.current
    val parts = data.costComposition
    val labels = listOf(R.string.token_insights_uncached, R.string.token_insights_cached, R.string.token_insights_cache_write, R.string.token_insights_output, R.string.token_insights_per_request, R.string.token_insights_unallocated_input)
    val values = listOf(parts.uncachedInput, parts.cachedInput, parts.cacheWrite, parts.output, parts.perRequest, parts.unallocatedInput)
    val colors = listOf(p.accent, p.teal, p.violet, p.amber, p.muted, p.faint)
    val total = values.sum()
    val cost = data.totals.cost
    val priced = (cost.totalContributionCount - cost.unknownContributionCount).coerceAtLeast(0L)
    StatsPanel {
        StatsOverviewHeading(stringResource(R.string.token_insights_cost_composition), tag = stringResource(R.string.token_stats_design_known_prices))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Box(Modifier.size(if (LocalConfiguration.current.screenWidthDp < 370) 98.dp else 115.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val stroke = 11.dp.toPx()
                    val ringSize = Size(size.width - stroke, size.height - stroke)
                    val offset = Offset(stroke / 2, stroke / 2)
                    drawArc(p.panelAlt, -90f, 360f, false, offset, ringSize, style = Stroke(stroke))
                    if (total > 0) {
                        var start = -90f
                        values.forEachIndexed { index, value ->
                            val sweep = (value / total * 360).toFloat()
                            if (sweep > 0f) drawArc(colors[index], start, sweep, false, offset, ringSize, style = Stroke(stroke))
                            start += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatsText(overviewPercent(priced.toDouble(), cost.totalContributionCount.toDouble()), size = 12, weight = FontWeight.SemiBold)
                    StatsText(stringResource(R.string.token_stats_design_cost_coverage), size = 8, color = p.muted, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                values.forEachIndexed { index, value ->
                    if (index < 5 || value > 0) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(colors[index]))
                        StatsText(stringResource(labels[index]), Modifier.weight(1f), size = 10, color = p.muted, maxLines = 1)
                        StatsText(insightsMoney(value, state.targetCurrency), size = 11, weight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        StatsOverviewNote(stringResource(R.string.token_stats_design_cost_note))
    }
}

@Composable
private fun StatsOverviewAttention(data: TokenStatsInsightsOverview, onRequests: () -> Unit, onSort: (TokenStatsRequestSort) -> Unit) {
    StatsPanel {
        StatsOverviewHeading(stringResource(R.string.token_stats_design_attention), tag = stringResource(R.string.token_stats_design_details_available))
        Column {
            StatsOverviewAttentionRow("alert", stringResource(R.string.token_stats_design_unconfirmed_count, overviewCount(data.totals.unconfirmedUsageRequests)),
                stringResource(R.string.token_stats_design_unconfirmed_note), onRequests)
            StatsDivider()
            StatsOverviewAttentionRow("chart", stringResource(R.string.token_stats_design_expensive), stringResource(R.string.token_stats_design_expensive_note)) { onSort(TokenStatsRequestSort.COST_DESC) }
            StatsDivider()
            StatsOverviewAttentionRow("clock", stringResource(R.string.token_stats_design_slow), stringResource(R.string.token_stats_design_slow_note)) { onSort(TokenStatsRequestSort.DURATION_DESC) }
        }
    }
}

@Composable
private fun StatsOverviewAttentionRow(icon: String, title: String, note: String, onClick: () -> Unit) {
    val p = LocalTokenStatsPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(33.dp).clip(RoundedCornerShape(10.dp)).background(p.panelAlt), contentAlignment = Alignment.Center) {
            StatsOverviewGlyph(icon, p.amber, Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatsText(title, size = 12, weight = FontWeight.Medium)
            StatsText(note, size = 10, color = p.muted)
        }
        StatsOverviewGlyph("right", p.faint, Modifier.size(13.dp))
    }
}

@Composable
private fun StatsOverviewTokens(totals: TokenStatsInsightsTotals, unit: TokenStatsDisplayUnit) {
    val p = LocalTokenStatsPalette.current
    val parts = listOf(totals.uncachedInput, totals.cachedInput, totals.cacheWrite, totals.output)
    val colors = listOf(p.accent, p.teal, p.violet, p.amber)
    val labels = listOf(R.string.token_insights_uncached, R.string.token_insights_cached, R.string.token_insights_cache_write, R.string.token_insights_output)
    val total = totals.totalTokens.knownSum.toDouble()
    StatsPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatsText(stringResource(R.string.token_insights_token_composition), size = 15, weight = FontWeight.SemiBold)
            StatsText(overviewTokens(totals.totalTokens, unit), size = 11, color = p.muted)
        }
        Canvas(Modifier.fillMaxWidth().height(11.dp).clip(RoundedCornerShape(4.dp)).background(p.panelAlt)) {
            if (total > 0) {
                var x = 0f
                parts.forEachIndexed { index, part ->
                    val width = (part.knownSum / total * size.width).toFloat().coerceIn(0f, size.width - x)
                    if (width > 0) drawRect(colors[index], Offset(x, 0f), Size((width - 2.dp.toPx()).coerceAtLeast(1f), size.height))
                    x += width
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            parts.forEachIndexed { index, part ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatsOverviewLegend(stringResource(labels[index]), colors[index], size = 9)
                    StatsText(overviewTokens(part, unit), size = 16, weight = FontWeight.Medium, maxLines = 1)
                    StatsText(if (part.isFullyKnown && totals.totalTokens.isFullyKnown) overviewPercent(part.knownSum.toDouble(), total) else "—", size = 10, color = p.muted)
                }
            }
        }
        StatsOverviewNote(stringResource(R.string.token_stats_design_token_note))
    }
}

@Composable
private fun StatsOverviewQuality(quality: TokenStatsInsightsQuality, currency: PricingCurrency, onSlow: () -> Unit) {
    StatsPanel {
        StatsOverviewHeading(stringResource(R.string.token_stats_design_quality), stringResource(R.string.token_stats_design_slow), onSlow)
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatsOverviewQualityMetric(stringResource(R.string.token_stats_design_average_first_token),
                    quality.averageFirstTokenLatencyMs?.let(::overviewSeconds), quality.firstTokenLatencySamples, Modifier.weight(1f))
                StatsOverviewQualityMetric(stringResource(R.string.token_stats_design_p95_duration),
                    quality.p95DurationMs?.let { overviewSeconds(it.toDouble()) }, quality.durationSamples, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatsOverviewQualityMetric(stringResource(R.string.token_stats_design_average_cost),
                    quality.averagePricedRequestCost?.let { insightsMoney(it, currency) }, quality.pricedRequestSamples, Modifier.weight(1f))
                StatsOverviewQualityMetric(stringResource(R.string.token_stats_design_output_speed),
                    quality.averageSuccessfulTokensPerSecond?.let { String.format(Locale.getDefault(), "%,.2f tok/s", it) },
                    quality.successfulTokensPerSecondSamples, Modifier.weight(1f))
            }
        }
        StatsOverviewNote(stringResource(R.string.token_stats_design_quality_note))
    }
}

@Composable
private fun StatsOverviewQualityMetric(label: String, value: String?, samples: Long, modifier: Modifier) {
    val p = LocalTokenStatsPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        StatsText(label, size = 10, color = p.muted)
        StatsText(value ?: "—", size = 18, weight = FontWeight.Medium, color = if (value == null) p.muted else p.ink)
        StatsText(stringResource(R.string.token_stats_design_quality_samples, overviewCount(samples)), size = 9, color = p.muted)
    }
}

@Composable
private fun StatsOverviewRanking(
    data: TokenStatsInsightsOverview,
    visibleConfigurations: List<TokenStatsInsightsConfiguration>,
    unit: TokenStatsDisplayUnit,
    onModels: () -> Unit,
    onModel: (String) -> Unit,
) {
    val p = LocalTokenStatsPalette.current
    StatsPanel {
        StatsOverviewHeading(stringResource(R.string.token_stats_design_model_ranking), stringResource(R.string.token_stats_design_all_models), onModels)
        Column {
            val models = data.models.sortedByDescending { it.totals.cost.knownAmount }.take(3)
            if (models.isEmpty()) StatsText(stringResource(R.string.token_insights_empty), color = p.muted)
            models.forEachIndexed { index, model ->
                val name = model.key
                if (index > 0) StatsDivider()
                Row(Modifier.fillMaxWidth().clickable(enabled = name != null) { if (name != null) onModel(name) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatsText(String.format(Locale.US, "%02d", index + 1), size = 9, color = p.faint, mono = true)
                    StatsAvatar(statsModelProvider(name, data.configurations))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatsText(name ?: stringResource(R.string.token_insights_not_recorded), size = 12, weight = FontWeight.Medium, maxLines = 1)
                        val configs = visibleConfigurations.count { it.model == name }
                        StatsText(stringResource(R.string.token_stats_design_config_tokens, configs, overviewTokens(model.totals.totalTokens, unit)), size = 9, color = p.muted)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatsText(insightsCost(model.totals.cost), size = 13, weight = FontWeight.Medium, maxLines = 1)
                        StatsText(stringResource(R.string.token_stats_design_known_share, overviewPercent(model.totals.cost.knownAmount, data.totals.cost.knownAmount)), size = 9, color = p.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewSources(data: TokenStatsInsightsOverview, onRequests: (String?, String?, String?, TokenStatsTimeRange?) -> Unit) {
    val p = LocalTokenStatsPalette.current
    val executions = data.executionModels.filter { it.key != null }
    StatsPanel {
        StatsOverviewHeading(stringResource(R.string.token_stats_design_sources), stringResource(R.string.token_stats_design_records), { onRequests(null, null, null, null) })
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            data.functions.sortedByDescending { it.totals.requests }.forEach { row ->
                StatsOverviewSourceRow(insightsFunction(row.key), row.totals.requests, data.totals.requests, enabled = row.key != null) { onRequests(null, row.key, null, null) }
            }
            // 执行模型与功能用途是不同维度，分隔展示，避免把两组请求数相加。
            if (executions.isNotEmpty()) {
                StatsDivider()
                StatsText(stringResource(R.string.token_insights_execution_model), size = 10, color = p.muted)
                executions.sortedByDescending { it.totals.requests }.forEach { row ->
                    StatsOverviewSourceRow(row.key ?: stringResource(R.string.token_insights_not_recorded), row.totals.requests, data.totals.requests) { onRequests(null, null, row.key, null) }
                }
            }
            if (data.functions.isEmpty() && executions.isEmpty()) StatsText(stringResource(R.string.token_insights_empty), color = p.muted)
        }
        StatsOverviewNote(stringResource(R.string.token_stats_design_source_note))
    }
}

@Composable
private fun StatsOverviewSourceRow(label: String, count: Long, total: Long, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalTokenStatsPalette.current
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        StatsText(label, Modifier.widthIn(min = 55.dp, max = 110.dp), size = 11, color = p.muted, maxLines = 2)
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.panelAlt)) {
            if (total > 0) Box(Modifier.fillMaxWidth((count.toDouble() / total).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(p.teal))
        }
        StatsText(stringResource(R.string.token_stats_design_request_times, overviewCount(count)), size = 11, weight = FontWeight.Medium)
    }
}

@Composable
private fun StatsOverviewBudget(state: TokenStatsUiState, onSettings: () -> Unit) {
    val p = LocalTokenStatsPalette.current
    val budgetCny = state.monthlyBudgetCny
    val cost = state.monthlyCost
    val budget = budgetCny?.let { if (state.targetCurrency == PricingCurrency.CNY) it else it / state.manualRate }
    val amount = cost?.knownAmount
    val ratio = if (budget != null && budget > 0 && amount != null) amount / budget else null
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.panel).border(1.dp, p.line, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatsText(stringResource(R.string.token_stats_design_budget), size = 11, weight = FontWeight.Medium)
            StatsText(stringResource(R.string.token_stats_design_budget_scope, if (cost == null) "—" else insightsCost(cost), if (budget == null) "—" else insightsMoney(budget, state.targetCurrency)), size = 9, color = p.muted)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(p.panelAlt)) {
                if (ratio != null) Box(Modifier.fillMaxWidth(ratio.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(if (ratio > 1) p.amber else p.teal))
            }
            StatsText(if (ratio == null) "—" else stringResource(R.string.token_stats_design_budget_used, String.format(Locale.getDefault(), "%.0f%%", ratio * 100)), size = 9, color = p.muted)
        }
        StatsOverviewIconButton("edit", stringResource(R.string.token_insights_settings), onSettings)
    }
}

@Composable
private fun StatsOverviewHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null, tag: String? = null) {
    val p = LocalTokenStatsPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        StatsText(title, Modifier.weight(1f), size = 15, weight = FontWeight.SemiBold)
        if (action != null && onAction != null) StatsAction(action + "  ›", onAction)
        if (tag != null) Box(Modifier.clip(RoundedCornerShape(5.dp)).background(p.panelAlt).border(1.dp, p.line, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 3.dp)) {
            StatsText(tag, size = 9, color = p.muted)
        }
    }
}

@Composable
private fun StatsOverviewLegend(label: String, color: Color, size: Int = 10) {
    val p = LocalTokenStatsPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(color))
        StatsText(label, size = size, color = p.muted, maxLines = 1)
    }
}

@Composable
private fun StatsOverviewNote(text: String) {
    val p = LocalTokenStatsPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatsDivider()
        StatsText(text, color = p.muted, size = 10)
    }
}

@Composable
private fun StatsOverviewIconButton(glyph: String, label: String, onClick: () -> Unit, enabled: Boolean = true, color: Color = LocalTokenStatsPalette.current.muted, bordered: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    val outline = if (bordered) Modifier.border(1.dp, color.copy(alpha = .22f), shape) else Modifier
    Box(Modifier.size(32.dp).then(outline).clip(shape).semantics { contentDescription = label }.clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        StatsOverviewGlyph(glyph, color.copy(alpha = if (enabled) 1f else .33f), Modifier.size(18.dp))
    }
}

/** 使用稿件的细线图标，避免默认组件的实心图标改变信息密度。 */
@Composable
private fun StatsOverviewGlyph(glyph: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), stroke)
        fun path(vararg points: Pair<Float, Float>) {
            val p = Path()
            points.forEachIndexed { index, (x, y) -> if (index == 0) p.moveTo(x * w, y * h) else p.lineTo(x * w, y * h) }
            drawPath(p, color, style = Stroke(stroke))
        }
        when (glyph) {
            "left" -> path(.65f to .15f, .3f to .5f, .65f to .85f)
            "right" -> path(.35f to .15f, .7f to .5f, .35f to .85f)
            "edit" -> { path(.2f to .62f, .7f to .12f, .88f to .3f, .38f to .8f, .14f to .86f, .2f to .62f); line(.61f, .22f, .79f, .4f) }
            "cube" -> { path(.5f to .1f, .86f to .3f, .86f to .7f, .5f to .9f, .14f to .7f, .14f to .3f, .5f to .1f); path(.14f to .3f, .5f to .5f, .86f to .3f); line(.5f, .5f, .5f, .9f) }
            "list" -> { for (y in listOf(.25f, .5f, .75f)) { line(.34f, y, .9f, y); drawCircle(color, stroke * .55f, Offset(.12f * w, y * h)) } }
            "check" -> path(.15f to .5f, .4f to .75f, .88f to .2f)
            "spark" -> path(.5f to .08f, .62f to .37f, .91f to .5f, .62f to .63f, .5f to .92f, .38f to .63f, .09f to .5f, .38f to .37f, .5f to .08f)
            "chart" -> { path(.12f to .1f, .12f to .88f, .9f to .88f); line(.3f, .7f, .3f, .4f); line(.52f, .7f, .52f, .18f); line(.75f, .7f, .75f, .32f) }
            "clock" -> { drawCircle(color, minOf(w, h) * .4f, style = Stroke(stroke)); path(.5f to .24f, .5f to .51f, .68f to .64f) }
            "alert" -> { path(.5f to .1f, .92f to .85f, .08f to .85f, .5f to .1f); line(.5f, .35f, .5f, .6f); drawCircle(color, stroke * .55f, Offset(.5f * w, .73f * h)) }
            "info" -> { drawCircle(color, minOf(w, h) * .4f, style = Stroke(stroke)); line(.5f, .45f, .5f, .73f); drawCircle(color, stroke * .55f, Offset(.5f * w, .28f * h)) }
        }
    }
}

private fun overviewMetric(totals: TokenStatsInsightsTotals, metric: Int): Double = when (metric) {
    0 -> totals.cost.knownAmount
    1 -> totals.totalTokens.knownSum.toDouble()
    else -> totals.requests.toDouble()
}

@Composable
private fun overviewMetricLabel(totals: TokenStatsInsightsTotals, metric: Int, unit: TokenStatsDisplayUnit): String = when (metric) {
    0 -> insightsCost(totals.cost)
    1 -> overviewTokens(totals.totalTokens, unit)
    else -> overviewCount(totals.requests)
}

private fun overviewTokens(value: TokenStatsTokenAggregate, unit: TokenStatsDisplayUnit): String =
    if (value.knownEventCount == 0L && value.unknownEventCount > 0L) "—"
    else formatTokenCount(value.knownSum, unit)

private fun overviewCompact(value: Double): String = when {
    abs(value) >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1fB", value / 1_000_000_000)
    abs(value) >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000)
    abs(value) >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1_000)
    else -> String.format(Locale.getDefault(), "%.0f", value)
}
private fun overviewCount(value: Long): String = String.format(Locale.getDefault(), "%,d", value)
private fun overviewPercent(value: Double, total: Double): String =
    if (total <= 0.0) "—" else String.format(Locale.getDefault(), "%.1f%%", value / total * 100)
private fun overviewRatioText(ratio: Double?): String =
    if (ratio == null) "—" else String.format(Locale.getDefault(), "%.1f%%", floor(ratio * 1_000.0) / 10.0)
private fun overviewSeconds(milliseconds: Double): String =
    String.format(Locale.getDefault(), "%,.3f s", milliseconds / 1_000.0)
