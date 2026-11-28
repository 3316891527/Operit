package com.ai.assistance.operit.ui.features.models.tokenstats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.stats.*
import com.ai.assistance.operit.util.logging.AppLogger
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TokenStatsTab { OVERVIEW, MODELS, CONFIGURATIONS, REQUESTS }
enum class TokenStatsQuickRange { TODAY, YESTERDAY, WEEK, MONTH, THIS_MONTH, ALL }
enum class TokenStatsInsightMetric { COST, TOKENS, REQUESTS }

data class TokenActivityUiState(
    val loading: Boolean = true,
    val viewMode: TokenActivityViewMode = TokenActivityViewMode.DAILY,
    val rangeData: TokenActivityRangeData? = null,
    val heatmap: TokenActivityRangeData? = null,
)

data class TokenStatsUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val tab: TokenStatsTab = TokenStatsTab.OVERVIEW,
    val filter: TokenStatsInsightsFilter = TokenStatsInsightsFilter(),
    val quickRange: TokenStatsQuickRange? = TokenStatsQuickRange.MONTH,
    val overview: TokenStatsInsightsOverview? = null,
    val catalog: TokenStatsInsightsOverview? = null,
    val comparisonRange: TokenStatsTimeRange? = null,
    val granularity: TokenStatsInsightsGranularity = TokenStatsInsightsGranularity.AUTO,
    val targetCurrency: PricingCurrency = PricingCurrency.CNY,
    val tokenDisplayUnit: TokenStatsDisplayUnit = TokenStatsDisplayUnit.MILLIONS,
    val manualRate: Double = TokenCostCurrency.DEFAULT_USD_TO_CNY_RATE,
    val rateIsEstimated: Boolean = true,
    val monthlyBudgetCny: Double? = null,
    val monthlyCost: TokenStatsCostSummary? = null,
    val increaseIsRed: Boolean = true,
    val configurationNames: Map<String, String> = emptyMap(),
    val priceSettings: List<TokenStatsPriceSetting> = emptyList(),
    val requests: TokenStatsRequestPage? = null,
    val requestSort: TokenStatsRequestSort = TokenStatsRequestSort.NEWEST,
    val requestsLoading: Boolean = false,
    val detail: TokenStatsRequestRow? = null,
    val detailLoading: Boolean = false,
    val clearing: Boolean = false,
    val hiddenConfigurationIds: Set<String> = emptySet(),
    val settingsLoaded: Boolean = false,
    val settingsSaving: Boolean = false,
) {
    val params: TokenStatsQueryParams get() = TokenStatsQueryParams(targetCurrency, manualRate)
    val currentRange: TokenStatsTimeRange? get() = filter.range

    internal val visibleConfigurations: List<TokenStatsInsightsConfiguration>
        get() = overview?.configurations.orEmpty().filter { isVisibleConfiguration(it.configId) }

    internal val visibleConfigurationFilterIds: List<String>
        get() = catalog?.configurations.orEmpty().asSequence()
            .map { it.configId }
            .filter(::isVisibleConfiguration)
            .distinct()
            .toList()

    private fun isVisibleConfiguration(configId: String): Boolean =
        isTokenStatsConfigurationVisible(configId, configurationNames.keys, hiddenConfigurationIds)
}

data class TokenStatsActionMessage(val text: String, val isError: Boolean = false)

@android.annotation.SuppressLint("StaticFieldLeak")
class TokenUsageStatisticsViewModel(
    context: Context,
    private val settings: TokenStatsSettingsStore = TokenStatsPreferencesStore(context),
    val zone: ZoneId = ZoneId.systemDefault(),
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val stringResolver: (Int) -> String = { context.applicationContext.getString(it) },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val preferences = TokenStatsPreferences(appContext)
    private val manager = TokenStatsSettingsManager(appContext)
    private val repository = TokenUsageRepository.getInstance(appContext)
    private val configurations = ModelConfigManager(appContext)
    private val mutableState = MutableStateFlow(TokenStatsUiState())
    val state: StateFlow<TokenStatsUiState> = mutableState.asStateFlow()
    private val messages = MutableStateFlow<TokenStatsActionMessage?>(null)
    val actionMessage: StateFlow<TokenStatsActionMessage?> = messages.asStateFlow()
    private var loadGeneration = 0L
    private var pageGeneration = 0L
    private var detailGeneration = 0L
    private var loadJob: Job? = null
    private var pageJob: Job? = null
    private var detailJob: Job? = null
    private var rangeSaveJob: Job? = null

    fun consumeActionMessage() { messages.value = null }
    fun selectTab(tab: TokenStatsTab) { mutableState.update { it.copy(tab = tab) } }
    fun loadForEntry() { if (!state.value.settingsLoaded && loadJob?.isActive != true) load(true) }

    private suspend fun initialize() {
        if (state.value.settingsLoaded) return
        val rate = settings.loadRateWithEstimate()
        val currency = settings.loadTargetCurrency()
        val unit = settings.loadTokenDisplayUnit()
        val hiddenIds = settings.loadHiddenConfigurationIds()
        val storedRange = settings.loadTimeRange()
        val chosen = preferences.hasSelectedInsightsRange()
        val options = preferences.loadInsightsOptions()
        val range = if (chosen || storedRange != null) storedRange else quickRange(TokenStatsQuickRange.MONTH)
        mutableState.update {
            it.copy(filter = it.filter.copy(range = range), quickRange = if (chosen && storedRange == null) TokenStatsQuickRange.ALL else if (storedRange != null) null else TokenStatsQuickRange.MONTH,
                manualRate = rate.first, rateIsEstimated = rate.second, targetCurrency = currency,
                tokenDisplayUnit = unit, increaseIsRed = options.first, monthlyBudgetCny = options.second,
                hiddenConfigurationIds = hiddenIds, settingsLoaded = true)
        }
    }

    private fun cancelReads() {
        loadGeneration++; pageGeneration++; detailGeneration++
        loadJob?.cancel(); pageJob?.cancel(); detailJob?.cancel()
    }

    fun load(rebuildCatalog: Boolean = false) {
        if (state.value.clearing) return
        cancelReads()
        val generation = loadGeneration
        mutableState.update { it.copy(loading = true, requestsLoading = true, errorMessage = null, detail = null, detailLoading = false) }
        loadJob = viewModelScope.launch(dispatcher) {
            try {
                initialize()
                val snapshot = state.value
                coroutineScope {
                    val overview = async { TokenStatsInsightsService.overview(appContext, snapshot.filter, snapshot.params, zone, snapshot.granularity, snapshot.comparisonRange) }
                    val page = async { TokenStatsInsightsService.requestPage(appContext, snapshot.filter, snapshot.params, snapshot.requestSort, 0, 30) }
                    val catalogFilter = TokenStatsInsightsFilter(range = snapshot.currentRange)
                    val catalog = async {
                        if (rebuildCatalog || snapshot.catalog == null) {
                            // 无额外筛选时目录与总览完全相同，复用本次结果但不携带对比数据。
                            if (snapshot.filter == catalogFilter) overview.await().copy(comparison = null)
                            else TokenStatsInsightsService.overview(appContext, catalogFilter, snapshot.params, zone, snapshot.granularity)
                        } else snapshot.catalog
                    }
                    val monthly = async {
                        if (snapshot.monthlyBudgetCny == null) null else {
                            val monthlyFilter = TokenStatsInsightsFilter(range = monthRange())
                            when {
                                snapshot.filter == monthlyFilter -> overview.await().totals.cost
                                catalogFilter == monthlyFilter && (rebuildCatalog || snapshot.catalog == null) -> requireNotNull(catalog.await()).totals.cost
                                // 预算只需要费用，不计算趋势、分组和费用构成。
                                else -> TokenStatsInsightsService.costSummary(appContext, monthlyFilter, snapshot.params)
                            }
                        }
                    }
                    val prices = async(Dispatchers.IO) { manager.allPriceSettings() }
                    val names = async(Dispatchers.IO) { configurations.getAllConfigSummaries().associate { it.id to it.name } }
                    val result = overview.await(); val requests = page.await(); val choices = catalog.await()
                    val month = monthly.await(); val priceList = prices.await(); val configNames = names.await()
                    if (generation != loadGeneration) return@coroutineScope
                    mutableState.update { it.copy(loading = false, requestsLoading = false, overview = result, requests = requests, catalog = choices, monthlyCost = month, priceSettings = priceList, configurationNames = configNames) }
                }
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                AppLogger.e(TAG, "统计加载失败", error)
                if (generation == loadGeneration) mutableState.update { it.copy(loading = false, requestsLoading = false, errorMessage = stringResolver(R.string.token_stats_load_failed)) }
            }
        }
    }

    fun setFilter(filter: TokenStatsInsightsFilter) {
        if (state.value.clearing) return
        val changedRange = filter.range != state.value.currentRange
        mutableState.update { it.copy(filter = filter, quickRange = if (changedRange) null else it.quickRange) }
        if (changedRange) persistRange() else load()
    }
    fun resetFilters() { setFilter(TokenStatsInsightsFilter(range = state.value.currentRange)) }
    fun setGranularity(value: TokenStatsInsightsGranularity) {
        mutableState.update { it.copy(granularity = value) }; load()
    }
    fun setComparison(range: TokenStatsTimeRange?) {
        mutableState.update { it.copy(comparisonRange = range) }; load()
    }
    fun comparePrevious() {
        val range = state.value.currentRange ?: return
        val duration = range.endMs - range.startMs
        if (range.startMs < Long.MIN_VALUE + duration) return
        setComparison(TokenStatsTimeRange(range.startMs - duration, range.startMs))
    }
    fun selectQuickRange(value: TokenStatsQuickRange) {
        if (state.value.clearing) return
        mutableState.update { it.copy(quickRange = value, filter = it.filter.copy(range = quickRange(value)), comparisonRange = null) }
        persistRange()
    }
    fun setCustomRange(startMs: Long, endMs: Long): Boolean {
        val validation = validateCustomRange(startMs, endMs, zone, MAX_CUSTOM_RANGE_DAYS)
        if (validation == CustomRangeValidation.INVALID_BOUNDS || (validation == CustomRangeValidation.TOO_LONG && state.value.currentRange != safeRange(startMs, endMs))) {
            failure(if (validation == CustomRangeValidation.TOO_LONG) R.string.token_stats_custom_range_too_long else R.string.token_stats_custom_range_invalid)
            return false
        }
        setFilter(state.value.filter.copy(range = TokenStatsTimeRange(startMs, endMs)))
        return true
    }
    private fun safeRange(start: Long, end: Long): TokenStatsTimeRange? =
        if (end > start) TokenStatsTimeRange(start, end) else null

    private fun persistRange() {
        rangeSaveJob?.cancel()
        cancelReads()
        val range = state.value.currentRange
        rangeSaveJob = viewModelScope.launch(dispatcher) {
            try {
                preferences.markInsightsRangeSelected()
                settings.saveTimeRange(range)
                load(true)
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                AppLogger.e(TAG, "保存统计范围失败", error); failure(R.string.token_stats_load_failed)
            }
        }
    }
    private fun quickRange(value: TokenStatsQuickRange): TokenStatsTimeRange? {
        val today = Instant.ofEpochMilli(nowMs()).atZone(zone).toLocalDate()
        val start = when (value) {
            TokenStatsQuickRange.TODAY -> today
            TokenStatsQuickRange.YESTERDAY -> today.minusDays(1)
            TokenStatsQuickRange.WEEK -> today.minusDays(6)
            TokenStatsQuickRange.MONTH -> today.minusDays(29)
            TokenStatsQuickRange.THIS_MONTH -> today.withDayOfMonth(1)
            TokenStatsQuickRange.ALL -> return null
        }
        val end = if (value == TokenStatsQuickRange.YESTERDAY) today else today.plusDays(1)
        return TokenStatsTimeRange(start.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli())
    }
    private fun monthRange(): TokenStatsTimeRange {
        val start = Instant.ofEpochMilli(nowMs()).atZone(zone).toLocalDate().withDayOfMonth(1)
        return TokenStatsTimeRange(start.atStartOfDay(zone).toInstant().toEpochMilli(), start.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }
    fun showRequests(
        model: String? = null, configId: String? = null, range: TokenStatsTimeRange? = null,
        functionType: String? = null, peak: TokenStatsPeakPeriod? = null, executionModel: String? = null,
    ) {
        val current = state.value.filter
        selectTab(TokenStatsTab.REQUESTS)
        setFilter(current.copy(
            models = if (model != null) setOf(model) else current.models,
            configIds = if (configId != null) setOf(configId) else current.configIds,
            range = range ?: current.range,
            functionTypes = if (functionType != null) setOf(functionType) else current.functionTypes,
            peakPeriods = if (peak != null) setOf(peak) else current.peakPeriods,
            executionModels = if (executionModel != null) setOf(executionModel) else current.executionModels,
        ))
    }
    fun setRequestSort(sort: TokenStatsRequestSort) {
        mutableState.update { it.copy(requestSort = sort) }; requestPage(0)
    }
    fun requestPage(index: Int) {
        if (index < 0 || state.value.loading || state.value.clearing) return
        pageJob?.cancel()
        val generation = ++pageGeneration
        val snapshot = state.value
        mutableState.update { it.copy(requestsLoading = true) }
        pageJob = viewModelScope.launch(dispatcher) {
            try {
                val page = TokenStatsInsightsService.requestPage(appContext, snapshot.filter, snapshot.params, snapshot.requestSort, index, 30)
                if (generation == pageGeneration) mutableState.update { it.copy(requests = page, requestsLoading = false) }
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                AppLogger.e(TAG, "请求分页读取失败", error)
                if (generation == pageGeneration) { mutableState.update { it.copy(requestsLoading = false) }; failure(R.string.token_stats_load_failed) }
            }
        }
    }
    fun showDetail(id: String) {
        if (state.value.clearing) return
        detailJob?.cancel()
        val generation = ++detailGeneration
        val params = state.value.params
        mutableState.update { it.copy(detail = null, detailLoading = true) }
        detailJob = viewModelScope.launch(dispatcher) {
            try {
                val detail = TokenStatsInsightsService.requestDetail(appContext, id, params)
                if (generation == detailGeneration) mutableState.update { it.copy(detail = detail, detailLoading = false) }
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                AppLogger.e(TAG, "请求详情读取失败", error)
                if (generation == detailGeneration) { mutableState.update { it.copy(detailLoading = false) }; failure(R.string.token_stats_load_failed) }
            }
        }
    }
    fun dismissDetail() {
        detailGeneration++; detailJob?.cancel()
        mutableState.update { it.copy(detail = null, detailLoading = false) }
    }

    fun saveSettings(currency: PricingCurrency, rate: Double, unit: TokenStatsDisplayUnit, increaseIsRed: Boolean, budgetCny: Double?): Boolean {
        if (!rate.isFinite() || rate <= 0.0 || (budgetCny != null && (!budgetCny.isFinite() || budgetCny <= 0.0))) return false
        val previous = state.value
        if (!previous.settingsLoaded || previous.settingsSaving || previous.clearing) return false
        // 提交前锁定入口，避免多次点击穿插写入或用未更新的草稿重新保存。
        mutableState.update { it.copy(settingsSaving = true) }
        viewModelScope.launch(dispatcher) {
            try {
                settings.saveTargetCurrency(currency)
                settings.saveRate(rate)
                preferences.saveInsightsOptions(increaseIsRed, budgetCny)
                settings.saveTokenDisplayUnit(unit)
                mutableState.update {
                    it.copy(targetCurrency = currency, manualRate = rate, rateIsEstimated = false,
                        tokenDisplayUnit = unit, increaseIsRed = increaseIsRed, monthlyBudgetCny = budgetCny)
                }
                // Token 单位只影响展示，切换时不用重新查询用量或价格。
                if (currency != previous.targetCurrency || rate != previous.manualRate || budgetCny != previous.monthlyBudgetCny) {
                    load(true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(TAG, "保存统计设置失败", error)
                failure(R.string.token_stats_pricing_save_failed)
            } finally {
                mutableState.update { it.copy(settingsSaving = false) }
            }
        }
        return true
    }
    fun savePrice(draft: TokenStatsPriceDraft) { mutate(R.string.token_stats_pricing_save_failed) { manager.savePrice(draft) } }
    fun deletePrice(setting: TokenStatsPriceSetting) {
        mutate(R.string.token_stats_pricing_delete_failed) {
            if (setting.scope == TokenStatsPriceScope.CONFIG) manager.resetConfigPrice(setting.providerModel, requireNotNull(setting.configId))
            else manager.restoreBuiltInPrice(setting.providerModel)
        }
    }
    fun clearInvalidConfigurations() {
        mutate(R.string.token_stats_clear_invalid_configurations_failed) {
            val names = configurations.getAllConfigSummaries().associate { it.id to it.name }
            val invalid = invalidTokenStatsConfigurationIds(repository.usedConfigurationIds(), names.keys)
            val hiddenIds = settings.hideConfigurationIds(invalid)
            mutableState.update { it.copy(configurationNames = names, hiddenConfigurationIds = hiddenIds) }
        }
    }
    private fun mutate(failureResource: Int, action: suspend () -> Unit) {
        if (state.value.clearing) return
        viewModelScope.launch(dispatcher) {
            try { action(); load(true) } catch (error: CancellationException) { throw error } catch (error: Exception) { AppLogger.e(TAG, "保存统计设置失败", error); failure(failureResource) }
        }
    }
    fun clearUsageRecords() {
        if (state.value.clearing) return
        cancelReads(); rangeSaveJob?.cancel()
        mutableState.update { it.copy(clearing = true, loading = false, requestsLoading = false, detail = null, detailLoading = false) }
        viewModelScope.launch(dispatcher) {
            try {
                repository.clearUsageRecords()
                mutableState.update { it.copy(overview = null, catalog = null, requests = null, monthlyCost = null) }
            } catch (error: CancellationException) { throw error } catch (error: Exception) { AppLogger.e(TAG, "清空统计用量失败", error); failure(R.string.token_stats_usage_reset_failed) }
            finally { mutableState.update { it.copy(clearing = false) } }
            load(true)
        }
    }
    private fun failure(resource: Int) { messages.value = TokenStatsActionMessage(stringResolver(resource), true) }
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TokenUsageStatisticsViewModel(appContext) as T
    }
    companion object {
        const val MAX_CUSTOM_RANGE_DAYS = 3 * 366L
        private const val TAG = "TokenUsageStatisticsViewModel"
    }
}
