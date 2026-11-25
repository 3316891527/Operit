package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.stats.*
import com.ai.assistance.operit.ui.common.oktoast.OKToast

/** 页面布局、颜色和控件顺序直接对应已有设计稿，查询及计价接口保持原义。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TokenUsageStatisticsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val model: TokenUsageStatisticsViewModel = viewModel(factory = TokenUsageStatisticsViewModel.Factory(context))
    val state by model.state.collectAsState()
    val message by model.actionMessage.collectAsState()
    var settings by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var dates by rememberSaveable { mutableStateOf(false) }
    var compare by rememberSaveable { mutableStateOf(false) }
    var price by remember { mutableStateOf<StatsPriceEditor?>(null) }
    var resetPrice by remember { mutableStateOf<TokenStatsPriceSetting?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) model.export(uri) }
    val exportAction = { export.launch("operit-usage.csv") }
    val scroll = rememberLazyListState()
    LaunchedEffect(Unit) { model.loadForEntry() }
    LaunchedEffect(state.tab) { scroll.scrollToItem(0) }
    LaunchedEffect(message) {
        message?.let {
            if (it.isError) OKToast.error(context, it.text, OKToast.LENGTH_SHORT).show()
            else OKToast.success(context, it.text, OKToast.LENGTH_SHORT).show()
            model.consumeActionMessage()
        }
    }
    TokenStatsTheme {
        val p = LocalTokenStatsPalette.current
        // 根背景必须实际绘制；自定义 Scaffold 会按主题壁纸把背景变透明，不能用于此稿。
        BoxWithConstraints(Modifier.fillMaxSize().background(p.background)) {
            val compact = maxWidth < 370.dp
            val wide = maxWidth >= 900.dp
            val inset = if (compact) 15.dp else if (wide) 36.dp else 22.dp
            LazyColumn(Modifier.widthIn(max = 1240.dp).fillMaxSize().align(Alignment.TopCenter), state = scroll,
                contentPadding = PaddingValues(start = inset, end = inset, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item("brand") { StatsBrand(compact, wide) }
                item("title") {
                    Column(Modifier.padding(top = 26.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatsText(stringResource(R.string.token_insights_title), size = 28, weight = FontWeight.SemiBold)
                            Spacer(Modifier.width(9.dp))
                            Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(p.teal))
                        }
                        StatsText(stringResource(R.string.token_stats_design_page_note), size = 11, color = p.muted)
                    }
                }
                item("actions") {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatsIconAction(Icons.Default.DeleteForever, stringResource(R.string.token_insights_clear_all), { reset = true }, enabled = !state.clearing && !state.exporting)
                        StatsIconAction(Icons.Default.Tune, stringResource(R.string.token_insights_settings), { settings = true }, enabled = !state.clearing)
                    }
                }
                item("period") {
                    TokenStatsPeriodControls(state, model.zone, onDate = { dates = true }, onCompare = { compare = true }, onFilter = { filters = true },
                        onQuick = model::selectQuickRange, onRange = model::setCustomRange, onResetFilters = model::resetFilters)
                    Spacer(Modifier.height(13.dp))
                }
                stickyHeader("tabs") { StatsTabs(state.tab, model::selectTab, state.configurationNames.size, compact) }
                item("content") {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when {
                            state.clearing || state.loading && state.overview == null -> StatsPanel {
                                StatsText(stringResource(R.string.token_insights_loading), color = p.muted)
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.accent, trackColor = p.panelAlt)
                            }
                            state.errorMessage != null -> StatsPanel {
                                StatsText(requireNotNull(state.errorMessage), color = p.muted)
                                StatsAction(stringResource(R.string.token_insights_retry), { model.load(rebuildCatalog = true) })
                            }
                            state.overview != null -> {
                                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.accent, trackColor = p.panelAlt)
                                when (state.tab) {
                                    TokenStatsTab.OVERVIEW -> TokenStatsOverviewContent(state, model.zone,
                                        onGranularity = model::setGranularity,
                                        onRequests = { name, function, executor, range -> model.showRequests(model = name, functionType = function, executionModel = executor, range = range) },
                                        onModels = { model.selectTab(TokenStatsTab.MODELS) }, onExport = exportAction, onSettings = { settings = true },
                                        onRequestSort = { sort -> model.selectTab(TokenStatsTab.REQUESTS); model.setRequestSort(sort) })
                                    TokenStatsTab.MODELS -> TokenStatsModelsContent(state) { model.showRequests(model = it) }
                                    TokenStatsTab.CONFIGURATIONS -> TokenStatsConfigurationsContent(state,
                                        onRequests = { name, config -> model.showRequests(model = name, configId = config) },
                                        onEdit = { setting, draft, name -> price = StatsPriceEditor(setting, draft, name) }, onReset = { resetPrice = it })
                                    TokenStatsTab.REQUESTS -> TokenStatsRequestsContent(state, model.zone, onFilter = model::setFilter,
                                        onSort = model::setRequestSort, onPage = model::requestPage, onDetail = model::showDetail, onExport = exportAction)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (settings) TokenStatsSettingsDialog(state, onSave = { currency, rate, unit, red, budget ->
            if (model.saveSettings(currency, rate, unit, red, budget)) settings = false
        }, onDismiss = { settings = false })
        if (filters) TokenStatsFilterDialog(state, onApply = { model.setFilter(it); filters = false }, onDismiss = { filters = false })
        if (reset) TokenStatsResetDialog(onConfirm = { reset = false; model.clearUsageRecords() }, onDismiss = { reset = false })
        if (dates) TokenStatsDateDialog(state.currentRange, model.zone, onConfirm = model::setCustomRange,
            onAll = { model.selectQuickRange(TokenStatsQuickRange.ALL); dates = false }, onDismiss = { dates = false })
        if (compare) TokenStatsComparisonDialog(state.currentRange, state.comparisonRange, model.zone,
            onConfirm = { model.setComparison(it); compare = false }, onDismiss = { compare = false })
        price?.let { editor -> PriceSettingsDialog(editor.existing, editor.draft, editor.name, model::savePrice, onDismiss = { price = null }) }
        resetPrice?.let { setting -> StatsSheet("PRICING", stringResource(R.string.token_insights_reset_price), onDismiss = { resetPrice = null },
            footer = { StatsPrimaryAction(stringResource(R.string.token_insights_reset_price), { model.deletePrice(setting); resetPrice = null }) }) {
            StatsText(stringResource(R.string.token_insights_reset_price_warning), color = p.muted)
        } }
        if (state.detailLoading) StatsSheet("REQUEST DETAIL", stringResource(R.string.token_insights_details), onDismiss = model::dismissDetail) {
            CircularProgressIndicator(color = p.accent, modifier = Modifier.size(28.dp))
        }
        state.detail?.let { row -> TokenStatsRequestDialog(row, state, model.zone, onEdit = { setting, draft, name ->
            model.dismissDetail(); price = StatsPriceEditor(setting, draft, name)
        }, onDismiss = model::dismissDetail) }
    }
}

@Composable
private fun StatsBrand(compact: Boolean, wide: Boolean) {
    val p = LocalTokenStatsPalette.current
    Column {
        Row(Modifier.fillMaxWidth().height(if (wide) 80.dp else 65.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp, 30.dp).border(1.dp, p.line, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                StatsText("o", size = 27, weight = FontWeight.SemiBold, color = p.accent)
                Box(Modifier.align(Alignment.BottomEnd).padding(3.dp).size(5.dp).clip(RoundedCornerShape(3.dp)).background(p.teal))
            }
            StatsText("operit", size = 21, weight = FontWeight.SemiBold)
            if (!compact) {
                StatsText("/", size = 21, color = p.line)
                StatsText("I N S I G H T S", size = 8, color = p.muted)
            }
        }
        StatsDivider()
    }
}

@Composable
internal fun StatsIconAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalTokenStatsPalette.current
    Row(modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick).heightIn(min = 34.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = p.muted.copy(alpha = if (enabled) 1f else .33f), modifier = Modifier.size(14.dp))
        StatsText(label, size = 11, color = p.muted.copy(alpha = if (enabled) 1f else .33f), maxLines = 1)
    }
}

@Composable
private fun StatsTabs(tab: TokenStatsTab, onTab: (TokenStatsTab) -> Unit, configurationCount: Int, compact: Boolean) {
    val p = LocalTokenStatsPalette.current
    val labels = listOf(R.string.token_insights_overview, R.string.token_insights_models, R.string.token_insights_configurations, R.string.token_insights_requests)
    val icons = listOf(Icons.Default.BarChart, Icons.Default.Widgets, Icons.Default.Tune, Icons.Default.FormatListBulleted)
    Column(Modifier.fillMaxWidth().background(p.background)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            TokenStatsTab.entries.forEachIndexed { index, value ->
                Column(Modifier.weight(1f).clickable { onTab(value) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp).heightIn(min = 49.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                        Icon(icons[index], contentDescription = null, tint = if (tab == value) p.accent else p.muted, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
                        StatsText(stringResource(labels[index]), Modifier.weight(1f, fill = false), size = if (compact) 11 else 12, color = if (tab == value) p.accent else p.muted, weight = if (tab == value) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        if (!compact && value == TokenStatsTab.CONFIGURATIONS && configurationCount > 0) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(p.panelAlt).padding(horizontal = 3.dp, vertical = 1.dp)) {
                            StatsText(configurationCount.toString(), size = 8, color = p.muted)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(2.dp).background(if (tab == value) p.accent else Color.Transparent))
                }
            }
        }
        StatsDivider()
    }
}

private data class StatsPriceEditor(val existing: TokenStatsPriceSetting?, val draft: TokenStatsPriceDraft, val name: String)