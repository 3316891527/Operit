package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity

internal fun insightsComparisonValidity(current: TokenStatsTimeRange?, other: TokenStatsTimeRange): TokenStatsComparisonValidity = when {
    current == null -> TokenStatsComparisonValidity.NO_CURRENT_RANGE
    current == other -> TokenStatsComparisonValidity.IDENTICAL
    current.startMs < other.endMs && other.startMs < current.endMs -> TokenStatsComparisonValidity.OVERLAPPING
    current.durationMs != other.durationMs -> TokenStatsComparisonValidity.DIFFERENT_DURATION
    else -> TokenStatsComparisonValidity.AVAILABLE
}

internal fun insightsKnownSum(vararg values: Long?): Long? {
    if (values.any { it == null || it < 0L }) return null
    return values.fold(0L) { sum, value -> TokenCostCalculator.saturatedAdd(sum, requireNotNull(value)) }
}

internal fun List<TokenStatsModelEntity>.insightsPrices(): TokenPriceSettingsSnapshot {
    val rows = filter { it.hasPriceSetting() }
    return TokenPriceSettingsSnapshot(
        rows.filter { it.configId.isEmpty() }.associate { "${it.provider}:${it.model}" to it.toModelPriceSettings() },
        rows.filter { it.configId.isNotEmpty() }.associate { tokenPriceConfigKey("${it.provider}:${it.model}", it.configId) to it.toModelPriceSettings() },
    )
}

internal fun insightsPricing(record: TokenUsageRecordEntity, prices: TokenPriceSettingsSnapshot): ResolvedTokenPricing {
    val key = normalizeProviderModel(record.providerModel)
    return TokenPriceResolver.resolve(key, prices.settingFor(key, record.configId))
}

internal fun insightsPeak(record: TokenUsageRecordEntity, pricing: ResolvedTokenPricing): TokenStatsPeakPeriod = when {
    pricing.billingMode == BillingMode.COUNT || !pricing.peakPricingEnabled -> TokenStatsPeakPeriod.NOT_ENABLED
    record.occurredAtMs == null || record.requestCount != 1L -> TokenStatsPeakPeriod.UNKNOWN
    TokenPricingRules.isPeakTime(record.occurredAtMs, pricing.peakSchedule, pricing.weekendOffPeakPricingEnabled, pricing.holidayOffPeakPricingEnabled) -> TokenStatsPeakPeriod.PEAK
    else -> TokenStatsPeakPeriod.OFF_PEAK
}

internal fun insightsLongContext(record: TokenUsageRecordEntity, pricing: ResolvedTokenPricing): Boolean? {
    if (pricing.billingMode == BillingMode.COUNT || !pricing.longContextPricingEnabled) return false
    if (record.occurredAtMs == null || record.requestCount != 1L) return null
    val tokens = insightsKnownSum(record.uncachedInputTokens, record.cachedInputTokens, record.outputTokens) ?: return null
    val threshold = pricing.longContextThreshold ?: return false
    return tokens >= threshold
}

internal fun insightsCost(record: TokenUsageRecordEntity, pricing: ResolvedTokenPricing, params: TokenStatsQueryParams): TokenStatsCostSummary =
    if (record.configId == TokenUsageRepository.DETACHED_CONFIG_ID) {
        TokenCostCalculator.zeroCost(params.targetCurrency, params.manualRate, record.requestCount)
    } else {
        TokenCostCalculator.currentCost(listOf(record), pricing, params.targetCurrency, params.manualRate)
    }

/** 每个分量仍由既有计费器应用倍率，不复制峰谷与长上下文金额公式。 */
internal fun insightsCostComposition(record: TokenUsageRecordEntity, pricing: ResolvedTokenPricing, params: TokenStatsQueryParams): TokenStatsInsightsCostComposition {
    val usage = normalizeLegacyCacheWriteUsage(record)
    val total = insightsCost(usage, pricing, params).knownAmount
    if (pricing.billingMode == BillingMode.COUNT) return TokenStatsInsightsCostComposition(0.0, 0.0, 0.0, 0.0, total, 0.0)
    fun amount(input: Double = 0.0, cached: Double = 0.0, write: Double = 0.0, output: Double = 0.0): Double =
        insightsCost(usage, pricing.copy(inputPricePerMillion = input, cachedInputPricePerMillion = cached, cacheWritePricePerMillion = write, outputPricePerMillion = output), params).knownAmount
    val input = amount(input = pricing.inputPricePerMillion)
    val cached = amount(cached = pricing.cachedInputPricePerMillion)
    val write = amount(write = pricing.cacheWritePricePerMillion)
    val output = amount(output = pricing.outputPricePerMillion)
    val inputTokens = listOf(usage.uncachedInputTokens, usage.cachedInputTokens, usage.cacheWriteTokens)
    val knownInputSum = inputTokens.filterNotNull().filter { it >= 0L }.fold(0L, TokenCostCalculator::saturatedAdd)
    val knownTotalInput = usage.totalInputTokens?.takeIf { it >= 0L }
    val conflictingInput = knownTotalInput != null && (knownInputSum > knownTotalInput ||
        (inputTokens.all { it != null && it >= 0L } && knownInputSum != knownTotalInput))
    val projected = input + cached + write + output
    // 加法顺序可能产生几个末位舍入差异，不能把正常分项因此全部判为冲突。
    val exceedsTotal = projected - total > Math.ulp(total) * 8.0
    if (conflictingInput || exceedsTotal) {
        // 冲突输入不能按比例造分项；输出仍可确定，输入只保留计价器确认的总额。
        return TokenStatsInsightsCostComposition(0.0, 0.0, 0.0, output, 0.0, (total - output).coerceAtLeast(0.0))
    }
    return TokenStatsInsightsCostComposition(input, cached, write, output, 0.0, (total - input - cached - write - output).coerceAtLeast(0.0))
}
internal class InsightsAccumulator(private val params: TokenStatsQueryParams) {
    private var requests = 0L
    private var facts = 0L
    private var unconfirmed = 0L
    private var historical = 0L
    private var unknownStatus = 0L
    private val statuses = linkedMapOf<String, Long>()
    private val sums = LongArray(5)
    private val known = LongArray(5)
    private var amount = 0.0
    private var unknownCost = 0L
    private val native = linkedMapOf<com.ai.assistance.operit.data.collects.PricingCurrency, Double>()
    fun add(row: TokenStatsRequestRow) {
        val count = row.requestCount.coerceAtLeast(0L)
        requests = TokenCostCalculator.saturatedAdd(requests, count)
        if (row.hasUsageFact) facts = TokenCostCalculator.saturatedAdd(facts, count) else unconfirmed = TokenCostCalculator.saturatedAdd(unconfirmed, count)
        if (row.isLegacyAggregate) historical = TokenCostCalculator.saturatedAdd(historical, count)
        if (row.status == null) unknownStatus = TokenCostCalculator.saturatedAdd(unknownStatus, count) else statuses[row.status] = TokenCostCalculator.saturatedAdd(statuses[row.status] ?: 0L, count)
        // 失败日志的诊断用量不进入成功事实累计，缺失仍占未知请求计数。
        if (row.hasUsageFact) {
            listOf(row.uncachedInputTokens, row.cachedInputTokens, row.cacheWriteTokens, row.outputTokens, row.totalTokens).forEachIndexed { index, value ->
                if (value != null && value >= 0L) {
                    sums[index] = TokenCostCalculator.saturatedAdd(sums[index], value)
                    known[index] = TokenCostCalculator.saturatedAdd(known[index], count)
                }
            }
        }
        amount += row.cost.knownAmount
        unknownCost = TokenCostCalculator.saturatedAdd(unknownCost, row.cost.unknownContributionCount)
        row.cost.originalCurrencyAmounts.forEach { (currency, value) -> native[currency] = (native[currency] ?: 0.0) + value }
    }
    fun result(): TokenStatsInsightsTotals {
        fun component(index: Int) = TokenStatsTokenAggregate(sums[index], known[index], (requests - known[index]).coerceAtLeast(0L), requests)
        return TokenStatsInsightsTotals(requests, facts, unconfirmed, historical, component(0), component(1), component(2), component(3), component(4),
            TokenStatsCostSummary(params.targetCurrency, amount, unknownCost, requests, params.manualRate, native.toMap()), statuses.toMap(), unknownStatus)
    }
}
