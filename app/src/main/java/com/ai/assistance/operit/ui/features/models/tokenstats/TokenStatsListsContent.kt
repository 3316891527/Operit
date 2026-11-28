package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.stats.*
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun TokenStatsModelsContent(state: TokenStatsUiState, onRequests: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val palette = LocalTokenStatsPalette.current
    val all = state.overview?.models.orEmpty().filter { it.key != null }
    val configurationsByModel = state.visibleConfigurations.groupBy { it.model }
    val models = all.filter { it.key.orEmpty().contains(query, ignoreCase = true) }
        .sortedByDescending { statsListMetric(it.totals, sort) }
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        StatsListHeader(stringResource(R.string.token_stats_design_models_title), stringResource(R.string.token_stats_design_models_note), stringResource(R.string.token_stats_design_models_count, all.size))
        StatsListTools(query, { query = it }, stringResource(R.string.token_insights_search_models), statsListSortLabels(), sort, { sort = it })
        StatsPanel {
            Column {
                if (models.isEmpty()) StatsListEmpty()
                models.forEachIndexed { index, model ->
                    if (index > 0) StatsDivider()
                    val name = requireNotNull(model.key)
                    var expanded by rememberSaveable(name) { mutableStateOf(false) }
                    val configs = configurationsByModel[name].orEmpty()
                    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 17.dp)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val rightColumnWidth = maxWidth * .44f
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                StatsText(String.format(Locale.ROOT, "%02d", index + 1), Modifier.width(17.dp), 10, palette.faint, mono = true)
                                StatsAvatar(statsModelProvider(name, state.overview?.configurations.orEmpty()), Modifier.size(32.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatsText(name, size = 12, weight = FontWeight.Medium, maxLines = 1, mono = true)
                                    StatsText(stringResource(R.string.token_stats_design_model_meta, configs.size, model.totals.requests), size = 10, color = palette.muted, maxLines = 1)
                                }
                                Column(Modifier.widthIn(max = rightColumnWidth), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    StatsText(insightsCost(model.totals.cost), size = 13, weight = FontWeight.SemiBold, maxLines = 1)
                                    val unknown = model.totals.cost.unknownContributionCount
                                    StatsText(if (unknown > 0) stringResource(R.string.token_stats_design_unknown_count, unknown) else stringResource(R.string.token_stats_design_cost_share, statsPercent(model.totals.cost.knownAmount, state.overview?.totals?.cost?.knownAmount ?: 0.0)), size = 9, color = palette.muted, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatsText(insightsTokens(model.totals.totalTokens, state.tokenDisplayUnit) + " Token", size = 10, color = palette.muted)
                            StatsText(stringResource(R.string.token_stats_design_cache_hit, statsCacheRate(model.totals)), size = 10, color = palette.muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        val totalCost = state.overview?.totals?.cost?.knownAmount ?: 0.0
                        StatsListMeter(if (totalCost > 0.0) (model.totals.cost.knownAmount / totalCost).toFloat() else 0f)
                    }
                    if (expanded) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            configs.forEach { config ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        StatsText(insightsConfigurationName(config.configId, state), size = 11, weight = FontWeight.Medium)
                                        StatsText(stringResource(R.string.token_stats_design_request_count, config.totals.requests) + " · " + config.provider, size = 9, color = palette.muted)
                                    }
                                    StatsText(insightsCost(config.totals.cost), size = 12)
                                }
                            }
                            StatsAction(stringResource(R.string.token_insights_view_model_requests) + " ›", { onRequests(name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TokenStatsConfigurationsContent(
    state: TokenStatsUiState,
    onRequests: (String, String) -> Unit,
    onEdit: (TokenStatsPriceSetting?, TokenStatsPriceDraft, String) -> Unit,
    onReset: (TokenStatsPriceSetting) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    val all = state.visibleConfigurations
    val configurations = all.filter { config ->
        listOf(state.configurationNames[config.configId].orEmpty(), config.model, config.provider).any { it.contains(query, ignoreCase = true) }
    }.filter { config ->
        val draft = priceDraftForIdentity(config.configId, config.provider, config.model, state.priceSettings)
        when (kind) {
            1 -> config.totals.cost.unknownContributionCount > 0L || !statsHasPrice(draft)
            2 -> draft.billingMode == BillingMode.TOKEN && (draft.peakPricingEnabled || draft.longContextPricingEnabled)
            3 -> draft.billingMode == BillingMode.COUNT
            else -> true
        }
    }.sortedByDescending { statsListMetric(it.totals, sort) }
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        StatsListHeader(stringResource(R.string.token_stats_design_configs_title), stringResource(R.string.token_stats_design_configs_note), stringResource(R.string.token_stats_design_configs_count, all.size))
        StatsListTools(query, { query = it }, stringResource(R.string.token_insights_search_configurations), statsListSortLabels(), sort, { sort = it })
        StatsListChips(listOf(stringResource(R.string.token_stats_design_configs_all), stringResource(R.string.token_stats_design_unpriced), stringResource(R.string.token_stats_design_advanced), stringResource(R.string.token_insights_billing_count)), kind, { kind = it })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (configurations.isEmpty()) StatsPanel { StatsListEmpty() }
            configurations.forEach { config ->
                key(config.configId, config.provider, config.model) {
                    StatsConfigurationRow(config, state, { onRequests(config.model, config.configId) }, onEdit, onReset)
                }
            }
        }
    }
}

@Composable
private fun StatsConfigurationRow(
    config: TokenStatsInsightsConfiguration,
    state: TokenStatsUiState,
    onRequests: () -> Unit,
    onEdit: (TokenStatsPriceSetting?, TokenStatsPriceDraft, String) -> Unit,
    onReset: (TokenStatsPriceSetting) -> Unit,
) {
    var expanded by rememberSaveable(config.configId, config.provider, config.model) { mutableStateOf(false) }
    val palette = LocalTokenStatsPalette.current
    val name = insightsConfigurationName(config.configId, state)
    val draft = priceDraftForIdentity(config.configId, config.provider, config.model, state.priceSettings)
    val setting = state.priceSettings.firstOrNull { it.provider == config.provider && it.model == config.model && it.configId.orEmpty() == config.configId }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(palette.panel).border(1.dp, palette.line, RoundedCornerShape(15.dp))) {
        BoxWithConstraints(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)) {
            val rightColumnWidth = maxWidth * .37f
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatsAvatar(config.provider, Modifier.size(32.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatsText(name, size = 12, weight = FontWeight.SemiBold, maxLines = 1)
                    StatsText(config.model, size = 11, maxLines = 1, mono = true)
                }
                Column(Modifier.widthIn(max = rightColumnWidth), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatsText(insightsCost(config.totals.cost), size = 13, color = palette.accent, weight = FontWeight.SemiBold, maxLines = 1)
                    StatsText(stringResource(R.string.token_stats_design_request_count, config.totals.requests), size = 9, color = palette.muted, maxLines = 1)
                }
                StatsListChevron(expanded)
            }
        }
        if (expanded) {
            StatsDivider()
            Column(Modifier.fillMaxWidth().background(palette.panelAlt).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatsText(config.provider, Modifier.weight(1f), 9, palette.muted, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatsListTag(stringResource(if (!statsHasPrice(draft)) R.string.token_stats_design_unpriced else if (draft.billingMode == BillingMode.COUNT) R.string.token_insights_billing_count else R.string.token_insights_billing_token), if (!statsHasPrice(draft)) palette.amber else palette.muted)
                        if (draft.peakPricingEnabled && draft.billingMode == BillingMode.TOKEN) StatsListTag(stringResource(R.string.token_insights_peak_usage), palette.teal)
                        if (draft.longContextPricingEnabled && draft.billingMode == BillingMode.TOKEN) StatsListTag(stringResource(R.string.token_insights_long_context), palette.accent)
                    }
                }
                val parts = listOf(config.totals.uncachedInput, config.totals.cachedInput, config.totals.cacheWrite, config.totals.output)
                val labels = listOf(R.string.token_insights_uncached, R.string.token_insights_cached, R.string.token_insights_cache_write, R.string.token_insights_output)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    parts.forEachIndexed { index, part ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatsText(stringResource(labels[index]), size = 9, color = palette.muted, maxLines = 1)
                            StatsText(insightsTokens(part, state.tokenDisplayUnit), size = 14, weight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    when {
                        !statsHasPrice(draft) -> StatsText(stringResource(R.string.token_stats_design_price_missing), size = 10, color = palette.muted)
                        draft.billingMode == BillingMode.COUNT -> StatsText(stringResource(R.string.token_stats_design_count_rate, draft.currency.name, statsRate(draft.pricePerRequest)), size = 10, color = palette.muted)
                        else -> {
                            StatsText(stringResource(R.string.token_stats_design_price_unit, draft.currency.name), size = 10, color = palette.muted)
                            val rates = listOf(draft.inputPricePerMillion, draft.cachedInputPricePerMillion, draft.cacheWritePricePerMillion, draft.outputPricePerMillion)
                            val rateLabels = labels.map { stringResource(it) }
                            StatsText(rates.indices.joinToString(" / ") { index -> rateLabels[index] + " " + statsRate(rates[index]) }, size = 10, color = palette.muted)
                        }
                    }
                    if (draft.billingMultiplier != 1.0) {
                        StatsText(stringResource(R.string.token_stats_billing_multiplier) + " × " + formatTokenStatsPrice(draft.billingMultiplier), size = 10, color = palette.muted)
                    }
                }
                StatsDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatsAction(stringResource(R.string.token_stats_design_edit_price_short), { onEdit(setting, draft, name) }, Modifier.weight(1f))
                    if (setting != null) StatsAction(stringResource(R.string.token_stats_design_restore_price_short), { onReset(setting) }, Modifier.weight(1f))
                    StatsAction(stringResource(R.string.token_stats_design_request_details_short), onRequests, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun TokenStatsRequestsContent(
    state: TokenStatsUiState,
    zone: ZoneId,
    onFilter: (TokenStatsInsightsFilter) -> Unit,
    onSort: (TokenStatsRequestSort) -> Unit,
    onPage: (Int) -> Unit,
    onDetail: (String) -> Unit,
) {
    var query by rememberSaveable(state.filter.search) { mutableStateOf(state.filter.search) }
    val palette = LocalTokenStatsPalette.current
    val page = state.requests
    val rows = page?.rows.orEmpty()
    val totals = state.overview?.totals
    val statuses = listOf(null, "SUCCESS", "FAILED", "CANCELLED", "COMPLETED")
    val currentStatus = statuses.indexOfFirst { status -> if (status == null) state.filter.statuses == null else state.filter.statuses == setOf(status) }
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        StatsListHeader(stringResource(R.string.token_stats_design_requests_title), stringResource(R.string.token_stats_design_requests_note), stringResource(R.string.token_stats_design_requests_count, page?.totalRows ?: 0L))
        StatsListTools(query, { query = it }, stringResource(R.string.token_insights_search), listOf(stringResource(R.string.token_insights_sort_newest), stringResource(R.string.token_insights_sort_oldest), stringResource(R.string.token_insights_sort_tokens), stringResource(R.string.token_insights_sort_cost), stringResource(R.string.token_insights_sort_duration)), state.requestSort.ordinal, { onSort(TokenStatsRequestSort.entries[it]) }, { onFilter(state.filter.copy(search = query)) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsSelect(if (currentStatus == 0) stringResource(R.string.token_stats_design_states_all) else if (currentStatus > 0) insightsStatus(statuses[currentStatus]) else stringResource(R.string.token_insights_status), listOf(stringResource(R.string.token_stats_design_states_all)) + statuses.drop(1).map { insightsStatus(it) }, currentStatus, { onFilter(state.filter.copy(statuses = statuses[it]?.let { value -> setOf(value) })) })
        }
        StatsListChips(listOf(stringResource(R.string.token_stats_design_requests_all)), 0, { onFilter(state.filter.copy(statuses = null)) })
        if (state.requestsLoading) Box(Modifier.fillMaxWidth().height(2.dp).background(palette.accentBackground)) { Box(Modifier.fillMaxWidth(.4f).height(2.dp).background(palette.accent)) }
        if (page != null && page.historicalAggregateRequests > 0L) StatsText(stringResource(R.string.token_insights_legacy_aggregate_note, page.historicalAggregateRequests), size = 10, color = palette.muted)
        StatsPanel {
            Column {
                if (rows.isEmpty()) StatsListEmpty()
                else {
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatsText(stringResource(R.string.token_stats_design_results, page?.totalRows ?: 0L, if (totals == null) "—" else insightsTokens(totals.totalTokens, state.tokenDisplayUnit)), size = 10, color = palette.muted)
                        if (totals != null) StatsText(insightsCost(totals.cost), size = 10, color = palette.muted)
                    }
                    rows.forEachIndexed { index, row ->
                        if (index > 0) StatsDivider()
                        StatsRequestRow(row, state, zone, { onDetail(row.id) })
                    }
                }
                if (page != null && page.totalRows > 0L) {
                    StatsDivider()
                    val pageCount = ((page.totalRows + page.pageSize - 1) / page.pageSize).coerceAtLeast(1L)
                    val start = page.pageIndex.toLong() * page.pageSize + 1
                    val end = minOf((page.pageIndex.toLong() + 1) * page.pageSize, page.totalRows)
                    Row(Modifier.fillMaxWidth().padding(top = 15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        StatsText(stringResource(R.string.token_stats_design_page_range, start, end, page.totalRows), size = 10, color = palette.muted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatsAction("‹", { onPage(page.pageIndex - 1) }, enabled = page.pageIndex > 0 && !state.requestsLoading && !state.loading)
                            StatsText("${page.pageIndex + 1} / $pageCount", size = 10, color = palette.muted)
                            StatsAction("›", { onPage(page.pageIndex + 1) }, enabled = page.pageIndex + 1 < pageCount && !state.requestsLoading && !state.loading)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRequestRow(row: TokenStatsRequestRow, state: TokenStatsUiState, zone: ZoneId, onClick: () -> Unit) {
    val palette = LocalTokenStatsPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 17.dp)) {
        val rightColumnWidth = maxWidth * .44f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            val statusColor = when (row.status) { "SUCCESS" -> palette.teal; "FAILED" -> palette.red; "CANCELLED" -> palette.amber; else -> palette.faint }
            Box(Modifier.padding(top = 7.dp).align(Alignment.Top).size(6.dp).clip(CircleShape).background(statusColor))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatsText((row.occurredAtMs?.let { insightsDate(it, zone) } ?: stringResource(R.string.token_insights_not_recorded)) + " · " + insightsConfigurationName(row.configId, state), size = 10, color = palette.muted, mono = true, maxLines = 1)
                StatsText(row.model, size = 12, weight = FontWeight.Medium, mono = true, maxLines = 1)
                val tokens = row.totalTokens?.let { formatTokenCount(it, state.tokenDisplayUnit) } ?: stringResource(R.string.token_insights_not_recorded)
                val source = if (row.executionModel != null) stringResource(R.string.token_insights_execution_model) + " · " + row.executionModel else insightsFunction(row.functionType)
                StatsText(tokens + " Token · " + source + " · " + insightsStatus(row.status), size = 9, color = palette.muted, maxLines = 2)
            }
            Column(Modifier.widthIn(max = rightColumnWidth), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StatsText(insightsCost(row.cost), size = 13, weight = FontWeight.SemiBold, maxLines = 1)
                val duration = row.durationMs?.let { String.format(Locale.getDefault(), "%.1fs", it / 1000.0) } ?: "—"
                StatsText(duration + if (row.longContext == true) " · " + stringResource(R.string.token_insights_long_context) else "", size = 9, color = palette.muted, maxLines = 1)
                tokenStatsOutputTokensPerSecond(row)?.let { speed ->
                    StatsText(String.format(Locale.getDefault(), "%.2f tok/s", speed), size = 9, color = palette.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StatsListHeader(title: String, note: String, badge: String) {
    val palette = LocalTokenStatsPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsText(title, size = 20, weight = FontWeight.SemiBold)
            StatsText(note, size = 11, color = palette.muted)
        }
        StatsText(badge, Modifier.clip(RoundedCornerShape(7.dp)).background(palette.accentBackground).padding(horizontal = 9.dp, vertical = 7.dp), 10, palette.accent, maxLines = 1)
    }
}

@Composable
private fun StatsListTools(query: String, onQuery: (String) -> Unit, placeholder: String, sorts: List<String>, selectedSort: Int, onSort: (Int) -> Unit, onSubmit: (() -> Unit)? = null) {
    val palette = LocalTokenStatsPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).height(43.dp).clip(RoundedCornerShape(11.dp)).background(palette.panelAlt).border(1.dp, palette.line, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val searchDescription = stringResource(R.string.token_stats_design_search_submit)
            Canvas(Modifier.size(15.dp).clickable(enabled = onSubmit != null, onClickLabel = searchDescription) { onSubmit?.invoke() }) {
                val radius = size.width * .3f
                drawCircle(palette.faint, radius, Offset(size.width * .4f, size.height * .4f), style = Stroke(1.3.dp.toPx()))
                drawLine(palette.faint, Offset(size.width * .63f, size.height * .63f), Offset(size.width * .95f, size.height * .95f), 1.3.dp.toPx())
            }
            BasicTextField(query, onQuery, Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = palette.ink, fontSize = 12.sp), cursorBrush = SolidColor(palette.accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }), decorationBox = { input ->
                Box {
                    if (query.isEmpty()) StatsText(placeholder, size = 12, color = palette.faint, maxLines = 1)
                    input()
                }
            })
            if (onSubmit != null) StatsAction("›", onSubmit)
        }
        StatsSelect(sorts[selectedSort.coerceIn(sorts.indices)], sorts, selectedSort, onSort, Modifier.widthIn(min = 89.dp, max = 112.dp))
    }
}

@Composable
private fun StatsListChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val palette = LocalTokenStatsPalette.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, label ->
            val active = selected == index
            Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (active) palette.accentBackground else Color.Transparent).border(1.dp, if (active) palette.accent else palette.line, RoundedCornerShape(7.dp)).clickable { onSelect(index) }.padding(horizontal = 10.dp, vertical = 9.dp)) {
                StatsText(label, size = 11, color = if (active) palette.accent else palette.muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatsListTag(label: String, color: Color) {
    val palette = LocalTokenStatsPalette.current
    StatsText(label, Modifier.clip(RoundedCornerShape(5.dp)).border(1.dp, palette.line, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 3.dp), 8, color, maxLines = 1)
}

@Composable
private fun StatsListChevron(expanded: Boolean) {
    val palette = LocalTokenStatsPalette.current
    Canvas(Modifier.size(12.dp)) {
        val path = Path()
        if (expanded) { path.moveTo(2.dp.toPx(), 8.dp.toPx()); path.lineTo(6.dp.toPx(), 4.dp.toPx()); path.lineTo(10.dp.toPx(), 8.dp.toPx()) }
        else { path.moveTo(2.dp.toPx(), 4.dp.toPx()); path.lineTo(6.dp.toPx(), 8.dp.toPx()); path.lineTo(10.dp.toPx(), 4.dp.toPx()) }
        drawPath(path, palette.faint, style = Stroke(1.2.dp.toPx()))
    }
}

@Composable
private fun StatsListMeter(fraction: Float) {
    val palette = LocalTokenStatsPalette.current
    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(palette.panelAlt)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(palette.accent))
    }
}

@Composable
private fun StatsListEmpty() {
    val palette = LocalTokenStatsPalette.current
    Box(Modifier.fillMaxWidth().padding(vertical = 45.dp), contentAlignment = Alignment.Center) { StatsText(stringResource(R.string.token_insights_empty), size = 12, color = palette.muted) }
}

@Composable
private fun statsListSortLabels(): List<String> = listOf(stringResource(R.string.token_insights_sort_cost), stringResource(R.string.token_insights_sort_tokens), stringResource(R.string.token_stats_design_sort_requests))

private fun statsListMetric(totals: TokenStatsInsightsTotals, sort: Int): Double = when (sort) { 1 -> totals.totalTokens.knownSum.toDouble(); 2 -> totals.requests.toDouble(); else -> totals.cost.knownAmount }
private fun statsHasPrice(draft: TokenStatsPriceDraft): Boolean = if (draft.billingMode == BillingMode.COUNT) (draft.pricePerRequest ?: 0.0) > 0.0 else listOf(draft.inputPricePerMillion, draft.cachedInputPricePerMillion, draft.cacheWritePricePerMillion, draft.outputPricePerMillion).any { (it ?: 0.0) > 0.0 }
private fun statsRate(value: Double?): String = value?.let { formatTokenStatsPrice(it) } ?: "—"
private fun statsPercent(value: Double, total: Double): String = if (total > 0.0) String.format(Locale.getDefault(), "%.1f%%", value / total * 100) else "—"
@Composable
private fun statsCacheRate(totals: TokenStatsInsightsTotals): String {
    val parts = listOf(totals.uncachedInput, totals.cachedInput, totals.cacheWrite)
    return if (parts.all { it.isFullyKnown }) statsPercent(totals.cachedInput.knownSum.toDouble(), parts.sumOf { it.knownSum.toDouble() }) else stringResource(R.string.token_insights_not_recorded)
}
