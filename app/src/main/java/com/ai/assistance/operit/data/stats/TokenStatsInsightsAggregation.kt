package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.model.TokenRequestLogEntity
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

/** 只保留计数和均值；完整耗时交给调用者的临时有序表。 */
internal class InsightsQualityAccumulator(private val addDuration: (Long) -> Unit) {
    private val firstToken = SampleMean()
    private val priced = SampleMean()
    private val tps = SampleMean()
    private var durationSamples = 0L

    fun add(row: TokenStatsRequestRow) {
        if (row.isIndividualRequest) {
            row.firstTokenLatencyMs?.takeIf { it >= 0L }?.let { firstToken.add(it.toDouble()) }
            row.durationMs?.takeIf { it >= 0L }?.let {
                addDuration(it)
                durationSamples++
            }
        }
        val cost = row.cost
        // 未关联的零账目不是已定价；部分定价也不能作为完整费用样本。
        if (row.hasUsageFact && row.configId != TokenUsageRepository.DETACHED_CONFIG_ID && row.requestCount > 0L &&
            cost.totalContributionCount == row.requestCount && cost.unknownContributionCount == 0L &&
            cost.knownAmount.isFinite() && cost.knownAmount >= 0.0
        ) {
            priced.add(cost.knownAmount / row.requestCount.toDouble(), row.requestCount)
        }
        tokenStatsOutputTokensPerSecond(row)?.let { tps.add(it) }
    }

    fun result(durationAtRank: (Long) -> Long): TokenStatsInsightsQuality = TokenStatsInsightsQuality(
        averageFirstTokenLatencyMs = firstToken.value,
        firstTokenLatencySamples = firstToken.count,
        // 最近秩是 ceil(0.95 × n)；用整数表达避免乘法溢出和浮点边界误差。
        p95DurationMs = if (durationSamples > 0L) durationAtRank(durationSamples - durationSamples / 20L) else null,
        durationSamples = durationSamples,
        averagePricedRequestCost = priced.value,
        pricedRequestSamples = priced.count,
        averageSuccessfulTokensPerSecond = tps.value,
        successfulTokensPerSecondSamples = tps.count,
    )

    private class SampleMean {
        var count = 0L
            private set
        private var mean = 0.0
        val value: Double? get() = mean.takeIf { count > 0L }

        fun add(value: Double, weight: Long = 1L) {
            // 直接更新均值，不让大量有效费用累加溢出。
            val totalWeight = count.toDouble() + weight.toDouble()
            mean += (value - mean) * (weight.toDouble() / totalWeight)
            count = TokenCostCalculator.saturatedAdd(count, weight)
        }
    }
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
    private var completeCacheInputRequests = 0L
    private var missingCacheInputRequests = 0L
    private var cacheReadTokens = 0.0
    private var cacheInputTokens = 0.0
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
        // 无日志的历史事实仍是成功用量，但不能据此推断请求状态。诊断日志不进入缓存样本。
        if (count > 0L && row.hasUsageFact && (row.status == null || row.status == TokenRequestLogEntity.SUCCESS)) {
            val uncached = row.uncachedInputTokens
            val cached = row.cachedInputTokens
            val write = row.cacheWriteTokens
            if (uncached != null && uncached >= 0L && cached != null && cached >= 0L && write != null && write >= 0L) {
                completeCacheInputRequests = TokenCostCalculator.saturatedAdd(completeCacheInputRequests, count)
                // 批次用量已是合计，不乘所含请求数；转为浮点再累加，避免大额 Token 在分子和分母饱和失真。
                cacheReadTokens += cached.toDouble()
                cacheInputTokens += uncached.toDouble() + cached.toDouble() + write.toDouble()
            } else {
                missingCacheInputRequests = TokenCostCalculator.saturatedAdd(missingCacheInputRequests, count)
            }
        }
        amount += row.cost.knownAmount
        unknownCost = TokenCostCalculator.saturatedAdd(unknownCost, row.cost.unknownContributionCount)
        row.cost.originalCurrencyAmounts.forEach { (currency, value) -> native[currency] = (native[currency] ?: 0.0) + value }
    }
    fun result(): TokenStatsInsightsTotals {
        fun component(index: Int) = TokenStatsTokenAggregate(sums[index], known[index], (requests - known[index]).coerceAtLeast(0L), requests)
        return TokenStatsInsightsTotals(requests, facts, unconfirmed, historical, component(0), component(1), component(2), component(3), component(4),
            TokenStatsCostSummary(params.targetCurrency, amount, unknownCost, requests, params.manualRate, native.toMap()), statuses.toMap(), unknownStatus,
            cacheReadCoverage = TokenStatsCacheReadCoverage(completeCacheInputRequests, missingCacheInputRequests, cacheReadTokens, cacheInputTokens),
        )
    }
}
