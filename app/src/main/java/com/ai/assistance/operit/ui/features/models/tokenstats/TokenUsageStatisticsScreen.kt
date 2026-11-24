package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import com.ai.assistance.operit.data.stats.*
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import java.time.ZoneId

/** 沿用评审过的 V2 层级；共用筛选和计价，让四页能追溯同一批真实请求。 */
@Composable
fun TokenUsageStatisticsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val viewModel: TokenUsageStatisticsViewModel = viewModel(factory = TokenUsageStatisticsViewModel.Factory(context))
    val state by viewModel.state.collectAsState()
    val message by viewModel.actionMessage.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showReset by rememberSaveable { mutableStateOf(false) }
    var showRange by rememberSaveable { mutableStateOf(false) }
    var showCompareRange by rememberSaveable { mutableStateOf(false) }
    var priceEditor by remember { mutableStateOf<InsightsPriceEditor?>(null) }
    var priceReset by remember { mutableStateOf<TokenStatsPriceSetting?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) viewModel.export(uri) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var configurationSearch by rememberSaveable { mutableStateOf("") }
    var listMetric by rememberSaveable { mutableStateOf(TokenStatsInsightMetric.COST) }
    val scroll = rememberLazyListState()
    LaunchedEffect(Unit) { viewModel.loadForEntry() }
    LaunchedEffect(message) {
        message?.let {
            if (it.isError) OKToast.error(context, it.text, OKToast.LENGTH_SHORT).show()
            else OKToast.success(context, it.text, OKToast.LENGTH_SHORT).show()
            viewModel.consumeActionMessage()
        }
    }
    LaunchedEffect(state.tab, state.requests?.pageIndex) { scroll.scrollToItem(0) }

    TokenStatsColorsProvider {
        CustomScaffold(containerColor = LocalTokenStatsColors.current.pageBackground, contentColor = LocalTokenStatsColors.current.pageContent) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), state = scroll,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item("title") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBackPressed) { Text(stringResource(R.string.token_insights_back)) }
                        Text(stringResource(R.string.token_insights_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(enabled = !state.loading && !state.exporting && !state.clearing, onClick = { exporter.launch("operit-usage.csv") }) {
                            if (state.exporting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Download, contentDescription = stringResource(R.string.token_insights_export))
                        }
                    }
                }
                item("summary") {
                    if (state.loading || state.clearing) InsightsCard { Text(stringResource(R.string.token_insights_loading)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    else if (state.errorMessage == null) InsightsOverviewHero(state)
                }
                item("actions") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = !state.clearing && !state.exporting, onClick = { showReset = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.token_insights_clear_all), color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(enabled = !state.clearing, onClick = { showSettings = true }) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.token_insights_settings))
                        }
                    }
                }
                item("date") {
                    InsightsPeriodControls(state, viewModel.zone, onQuick = viewModel::selectQuickRange, onRange = { showRange = true },
                        onPrevious = viewModel::comparePrevious, onCompare = { showCompareRange = true }, onDisableCompare = { viewModel.setComparison(null) }, onGranularity = viewModel::setGranularity)
                }
                item("tabs") {
                    val labels = listOf(R.string.token_insights_overview, R.string.token_insights_models, R.string.token_insights_configurations, R.string.token_insights_requests)
                    TabRow(selectedTabIndex = state.tab.ordinal, containerColor = LocalTokenStatsColors.current.pageBackground) {
                        TokenStatsTab.entries.forEachIndexed { index, tab -> Tab(selected = state.tab == tab, onClick = { viewModel.selectTab(tab) }, text = { Text(stringResource(labels[index])) }) }
                    }
                }
                item("filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showFilters = true }, enabled = !state.clearing) { Text(stringResource(R.string.token_insights_filters)) }
                            if (state.filter.copy(range = null) != TokenStatsInsightsFilter()) TextButton(onClick = viewModel::resetFilters) { Text(stringResource(R.string.token_insights_reset_filters)) }
                        }
                        InsightsActiveFilters(state)
                    }
                }
                if (state.loading || state.clearing) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.errorMessage?.let { error -> item("error") {
                    InsightsCard { Text(error); TextButton(onClick = { viewModel.load(rebuildCatalog = true) }) { Text(stringResource(R.string.token_insights_retry)) } }
                } }
                if (state.overview != null && !state.clearing && !state.loading && state.errorMessage == null) {
                    when (state.tab) {
                        TokenStatsTab.OVERVIEW -> {
                            item("trend") { InsightsTrend(state, viewModel.zone) { viewModel.showRequests(range = it) } }
                            item("costComposition") { InsightsComposition(state, cost = true) }
                            item("tokenComposition") { InsightsComposition(state, cost = false) }
                            item("quality") { InsightsQuality(state) }
                            item("sources") { InsightsBreakdowns(state, peak = false) { if (it != null) viewModel.showRequests(functionType = it) } }
                            item("executionModels") { InsightsExecutionModels(state) { viewModel.showRequests(executionModel = it) } }
                            item("peak") { InsightsBreakdowns(state, peak = true) { if (it != null) viewModel.showRequests(peak = TokenStatsPeakPeriod.valueOf(it)) } }
                            item("budget") { InsightsBudget(state) }
                        }
                        TokenStatsTab.MODELS -> {
                            item("modelSearch") {
                                InsightsListControls(modelSearch, R.string.token_insights_search_models, listMetric, { modelSearch = it }, { listMetric = it })
                            }
                            val models = state.overview?.models.orEmpty().filter { it.key.orEmpty().contains(modelSearch, ignoreCase = true) }.sortedByDescending { insightsListValue(it.totals, listMetric) }
                            if (models.isEmpty()) item { InsightsEmpty() }
                            items(models, key = { "model:${it.key}" }) { model -> InsightsModelCard(model, state) { viewModel.showRequests(model = it) } }
                        }
                        TokenStatsTab.CONFIGURATIONS -> {
                            item("configurationSearch") {
                                InsightsListControls(configurationSearch, R.string.token_insights_search_configurations, listMetric, { configurationSearch = it }, { listMetric = it })
                            }
                            val configurations = state.overview?.configurations.orEmpty().filter { (it.configId.isBlank() || it.configId in state.configurationNames) && listOf(state.configurationNames[it.configId].orEmpty(), it.provider, it.model).any { name -> name.contains(configurationSearch, ignoreCase = true) } }.sortedByDescending { insightsListValue(it.totals, listMetric) }
                            item { Text(stringResource(R.string.token_insights_configuration_visibility_hint), style = MaterialTheme.typography.bodySmall, color = LocalTokenStatsColors.current.pageSupportingContent) }
                            if (configurations.isEmpty()) item { InsightsEmpty() }
                            items(configurations, key = { "config:${it.configId}\u001f${it.provider}\u001f${it.model}" }) { config ->
                                InsightsConfigurationCard(config, state,
                                    onRequests = { viewModel.showRequests(model = config.model, configId = config.configId) },
                                    onEdit = { existing, draft, name -> priceEditor = InsightsPriceEditor(existing, draft, name) },
                                    onReset = { priceReset = it })
                            }
                        }
                        TokenStatsTab.REQUESTS -> {
                            item("requestSearch") {
                                Text(stringResource(R.string.token_insights_export_note), style = MaterialTheme.typography.bodySmall)
                                var search by rememberSaveable(state.filter.search) { mutableStateOf(state.filter.search) }
                                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.token_insights_search)) }, singleLine = true,
                                    trailingIcon = { TextButton(onClick = { viewModel.setFilter(state.filter.copy(search = search)) }) { Text(stringResource(R.string.token_insights_apply)) } })
                                val sorts = listOf(R.string.token_insights_sort_newest, R.string.token_insights_sort_oldest, R.string.token_insights_sort_tokens, R.string.token_insights_sort_cost, R.string.token_insights_sort_duration)
                                InsightsChips(sorts.mapIndexed { i, label -> stringResource(label) to (TokenStatsRequestSort.entries[i] == state.requestSort) }) { viewModel.setRequestSort(TokenStatsRequestSort.entries[it]) }
                            }
                            if (state.requestsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            val page = state.requests
                            if (page != null) {
                                if (page.historicalAggregateRequests > 0) item { Text(stringResource(R.string.token_insights_legacy_aggregate_note, page.historicalAggregateRequests), style = MaterialTheme.typography.bodySmall) }
                                if (page.rows.isEmpty()) item { InsightsEmpty() }
                                items(page.rows, key = { "request:${it.id}" }) { row -> InsightsRequestCard(row, state, viewModel.zone) { viewModel.showDetail(row.id) } }
                                item("pagination") {
                                    val count = ((page.totalRows + page.pageSize - 1) / page.pageSize).coerceAtLeast(1)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.token_insights_page, page.pageIndex + 1, count), style = MaterialTheme.typography.labelMedium)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            TextButton(enabled = page.pageIndex > 0 && !state.requestsLoading && !state.loading, onClick = { viewModel.requestPage(page.pageIndex - 1) }) { Text(stringResource(R.string.token_insights_previous)) }
                                            TextButton(enabled = (page.pageIndex + 1L) * page.pageSize < page.totalRows && !state.requestsLoading && !state.loading, onClick = { viewModel.requestPage(page.pageIndex + 1) }) { Text(stringResource(R.string.token_insights_next)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showSettings) TokenStatsInsightsSettingsDialog(state, onSave = { currency, rate, unit, increaseRed, budget ->
            viewModel.saveSettings(currency, rate, unit, increaseRed, budget); showSettings = false
        }, onDismiss = { showSettings = false })
        if (showFilters) TokenStatsInsightsFilterDialog(state, onApply = { viewModel.setFilter(it); showFilters = false }, onDismiss = { showFilters = false })
        if (showReset) TokenStatsInsightsResetDialog(onConfirm = { showReset = false; viewModel.clearUsageRecords() }, onDismiss = { showReset = false })
        if (showRange) TokenStatsDateRangeDialog(zone = viewModel.zone, maxRangeDays = TokenUsageStatisticsViewModel.MAX_CUSTOM_RANGE_DAYS,
            initialRange = state.currentRange, onConfirm = viewModel::setCustomRange, onDismiss = { showRange = false })
        if (showCompareRange) TokenStatsDateRangeDialog(zone = viewModel.zone, maxRangeDays = TokenUsageStatisticsViewModel.MAX_CUSTOM_RANGE_DAYS,
            initialRange = state.comparisonRange, onConfirm = { start, end -> viewModel.setComparison(TokenStatsTimeRange(start, end)); true }, onDismiss = { showCompareRange = false })
        priceEditor?.let { target -> PriceSettingsDialog(target.existing, target.draft, target.name, onSave = viewModel::savePrice, onDismiss = { priceEditor = null }) }
        priceReset?.let { setting -> AlertDialog(onDismissRequest = { priceReset = null }, title = { Text(stringResource(R.string.token_insights_reset_price)) },
            text = { Text(stringResource(R.string.token_insights_reset_price_warning)) }, confirmButton = { TextButton(onClick = { viewModel.deletePrice(setting); priceReset = null }) { Text(stringResource(R.string.token_insights_reset_price)) } },
            dismissButton = { TextButton(onClick = { priceReset = null }) { Text(stringResource(R.string.token_insights_cancel)) } }) }
        if (state.detailLoading) AlertDialog(onDismissRequest = viewModel::dismissDetail, text = { CircularProgressIndicator() }, confirmButton = { TextButton(onClick = viewModel::dismissDetail) { Text(stringResource(R.string.token_insights_close)) } })
        state.detail?.let { row -> InsightsRequestDetail(row, state, viewModel.zone, viewModel::dismissDetail) }
    }
}

@Composable
private fun InsightsPeriodControls(state: TokenStatsUiState, zone: ZoneId, onQuick: (TokenStatsQuickRange) -> Unit, onRange: () -> Unit, onPrevious: () -> Unit, onCompare: () -> Unit, onDisableCompare: () -> Unit, onGranularity: (TokenStatsInsightsGranularity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val labels = listOf(R.string.token_insights_today, R.string.token_insights_yesterday, R.string.token_insights_week, R.string.token_insights_month, R.string.token_insights_this_month, R.string.token_insights_all_time)
        InsightsChips(labels.mapIndexed { index, id -> stringResource(id) to (TokenStatsQuickRange.entries[index] == state.quickRange) }) { onQuick(TokenStatsQuickRange.entries[it]) }
        TextButton(onClick = onRange) { Text(state.currentRange?.let { insightsDate(it.startMs, zone, "yyyy/MM/dd") + " — " + insightsDate(it.endMs - 1, zone, "yyyy/MM/dd") } ?: stringResource(R.string.token_insights_all_time)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = state.currentRange != null, onClick = onPrevious) { Text(stringResource(R.string.token_insights_previous_period)) }
            TextButton(enabled = state.currentRange != null, onClick = onCompare) { Text(stringResource(R.string.token_insights_custom_compare)) }
            if (state.comparisonRange != null) TextButton(onClick = onDisableCompare) { Text(stringResource(R.string.token_insights_comparison_off)) }
        }
        val granularities = TokenStatsInsightsGranularity.entries
        val granularityLabels = listOf(R.string.token_insights_auto, R.string.token_insights_hourly, R.string.token_insights_daily, R.string.token_insights_weekly, R.string.token_insights_monthly)
        InsightsChips(granularityLabels.mapIndexed { i, id -> stringResource(id) to (state.granularity == granularities[i]) }) { onGranularity(granularities[it]) }
        state.comparisonRange?.let { Text(stringResource(R.string.token_insights_compare) + " · " + insightsDate(it.startMs, zone, "yyyy/MM/dd") + " — " + insightsDate(it.endMs - 1, zone, "yyyy/MM/dd"), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun InsightsActiveFilters(state: TokenStatsUiState) {
    val names = buildList {
        state.filter.models?.let { addAll(it) }
        state.filter.configIds?.forEach { add(insightsConfigurationName(it, state)) }
        state.filter.functionTypes?.forEach { add(insightsFunction(it)) }
        state.filter.executionModels?.forEach { add(it) }
        state.filter.statuses?.forEach { add(insightsStatus(it)) }
        state.filter.peakPeriods?.forEach { add(insightsPeakName(it)) }
        if (state.filter.search.isNotBlank()) add(state.filter.search)
    }
    if (names.isNotEmpty()) Text(names.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = LocalTokenStatsColors.current.pageSupportingContent)
    if (listOf(state.filter.models, state.filter.configIds, state.filter.functionTypes, state.filter.statuses, state.filter.peakPeriods, state.filter.executionModels).any { it != null && it.isEmpty() }) Text(stringResource(R.string.token_insights_select_none), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun InsightsEmpty() { InsightsCard { Text(stringResource(R.string.token_insights_empty), color = LocalTokenStatsColors.current.cardSupportingContent) } }

@Composable
private fun InsightsRequestDetail(row: TokenStatsRequestRow, state: TokenStatsUiState, zone: ZoneId, onDismiss: () -> Unit) {
    val unknown = stringResource(R.string.token_insights_not_recorded)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.token_insights_details)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(row.model, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(row.id, style = MaterialTheme.typography.labelSmall)
                InsightsKeyValue(stringResource(R.string.token_insights_started_at), row.occurredAtMs?.let { insightsDate(it, zone, "yyyy/MM/dd HH:mm:ss") } ?: unknown)
                InsightsKeyValue(stringResource(R.string.token_insights_configurations), insightsConfigurationName(row.configId, state))
                InsightsKeyValue(stringResource(R.string.token_insights_status), insightsStatus(row.status))
                InsightsKeyValue(stringResource(R.string.token_insights_source), insightsFunction(row.functionType))
                if (row.executionModel != null) InsightsKeyValue(stringResource(R.string.token_insights_execution_model), row.executionModel)
                InsightsKeyValue(stringResource(R.string.token_insights_first_token), row.firstTokenLatencyMs?.let { "$it ms" } ?: unknown)
                InsightsKeyValue(stringResource(R.string.token_insights_duration), row.durationMs?.let { "$it ms" } ?: unknown)
                InsightsKeyValue(stringResource(R.string.token_insights_observed_attempts), row.observedAttemptNumbers ?: unknown)
                InsightsKeyValue(stringResource(R.string.token_insights_successful_attempt), row.successfulAttempt?.toString() ?: unknown)
                HorizontalDivider()
                listOf(R.string.token_insights_uncached to row.uncachedInputTokens, R.string.token_insights_cached to row.cachedInputTokens, R.string.token_insights_cache_write to row.cacheWriteTokens, R.string.token_insights_output to row.outputTokens, R.string.token_insights_tokens to row.totalTokens).forEach { (label, count) ->
                    InsightsKeyValue(stringResource(label), count?.let { formatTokenCount(it, state.tokenDisplayUnit) } ?: unknown)
                }
                InsightsKeyValue(stringResource(R.string.token_insights_cost), insightsCost(row.cost), accent = true)
                if (!row.hasUsageFact) Text(stringResource(R.string.token_insights_usage_unconfirmed), style = MaterialTheme.typography.bodySmall)
                InsightsKeyValue(stringResource(R.string.token_insights_peak_usage), insightsPeakName(row.peakPeriod))
                InsightsKeyValue(stringResource(R.string.token_insights_long_context), row.longContext?.takeIf { row.hasUsageFact }?.let { stringResource(if (it) R.string.token_insights_applied else R.string.token_insights_not_applied) } ?: unknown)
                if (row.configId.isBlank() || row.configId in state.configurationNames) {
                val draft = priceDraftForIdentity(row.configId, row.provider, row.model, state.priceSettings)
                HorizontalDivider()
                Text(stringResource(R.string.token_insights_pricing_now), style = MaterialTheme.typography.labelMedium)
                InsightsPricingRules(draft)
                if (row.hasUsageFact && row.configId != TokenUsageRepository.DETACHED_CONFIG_ID) {
                    val record = TokenUsageRecordEntity(occurredAtMs = row.occurredAtMs, configId = row.configId, provider = row.provider, model = row.model, requestCount = row.requestCount, uncachedInputTokens = row.uncachedInputTokens, cachedInputTokens = row.cachedInputTokens, cacheWriteTokens = row.cacheWriteTokens, totalInputTokens = row.totalInputTokens, outputTokens = row.outputTokens)
                    val pricing = TokenPriceResolver.resolve("${row.provider}:${row.model}", draft.toInsightsModelSettings())
                    val composition = insightsCostComposition(record, pricing, state.params)
                    val amounts = listOf(R.string.token_insights_uncached to composition.uncachedInput, R.string.token_insights_cached to composition.cachedInput, R.string.token_insights_cache_write to composition.cacheWrite, R.string.token_insights_output to composition.output, R.string.token_insights_per_request to composition.perRequest, R.string.token_insights_unallocated_input to composition.unallocatedInput)
                    amounts.forEach { (label, amount) -> InsightsKeyValue(stringResource(label), insightsMoney(amount, state.targetCurrency)) }
                }
                }
                Text(stringResource(R.string.token_insights_attempt_note), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.token_insights_close)) } })
}

private data class InsightsPriceEditor(val existing: TokenStatsPriceSetting?, val draft: TokenStatsPriceDraft, val name: String)
private fun TokenStatsPriceDraft.toInsightsModelSettings() = ModelPriceSettings(billingMode, currency, inputPricePerMillion, cachedInputPricePerMillion, cacheWritePricePerMillion, outputPricePerMillion, pricePerRequest, peakPricingEnabled, weekendOffPeakPricingEnabled, holidayOffPeakPricingEnabled, peakSchedule, peakInputMultiplier, peakCachedInputMultiplier, peakCacheWriteMultiplier, peakOutputMultiplier, longContextPricingEnabled, longContextThreshold, longContextInputMultiplier, longContextCachedInputMultiplier, longContextCacheWriteMultiplier, longContextOutputMultiplier)

@Composable
private fun InsightsListControls(query: String, label: Int, metric: TokenStatsInsightMetric, onQuery: (String) -> Unit, onMetric: (TokenStatsInsightMetric) -> Unit) {
    Column {
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(label)) })
        val labels = listOf(R.string.token_insights_cost, R.string.token_insights_tokens, R.string.token_insights_request_count)
        InsightsChips(labels.mapIndexed { index, id -> stringResource(id) to (metric == TokenStatsInsightMetric.entries[index]) }) { onMetric(TokenStatsInsightMetric.entries[it]) }
    }
}
private fun insightsListValue(totals: TokenStatsInsightsTotals, metric: TokenStatsInsightMetric): Double = when (metric) {
    TokenStatsInsightMetric.COST -> totals.cost.knownAmount
    TokenStatsInsightMetric.TOKENS -> totals.totalTokens.knownSum.toDouble()
    TokenStatsInsightMetric.REQUESTS -> totals.requests.toDouble()
}
