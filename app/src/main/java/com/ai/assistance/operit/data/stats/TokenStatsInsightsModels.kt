package com.ai.assistance.operit.data.stats

/** 空集合表示不选任何项，null 表示不限制；所有时间区间均为半开区间。 */
data class TokenStatsInsightsFilter(
    val range: TokenStatsTimeRange? = null,
    val models: Set<String>? = null,
    val configIds: Set<String>? = null,
    val functionTypes: Set<String>? = null,
    val statuses: Set<String>? = null,
    val peakPeriods: Set<TokenStatsPeakPeriod>? = null,
    val search: String = "",
    val executionModels: Set<String>? = null,
)

enum class TokenStatsPeakPeriod { PEAK, OFF_PEAK, NOT_ENABLED, UNKNOWN }
enum class TokenStatsInsightsGranularity { AUTO, HOURLY, DAILY, WEEKLY, MONTHLY }
enum class TokenStatsRequestSort { NEWEST, OLDEST, TOKENS_DESC, COST_DESC, DURATION_DESC }
enum class TokenStatsComparisonValidity { AVAILABLE, IDENTICAL, OVERLAPPING, DIFFERENT_DURATION, NO_CURRENT_RANGE }

/** 明细中的可空值意味着未记录，不能解释为零、成功或免费。 */
data class TokenStatsRequestRow(
    val id: String,
    val requestId: String?,
    val occurredAtMs: Long?,
    val configId: String,
    val provider: String,
    val model: String,
    val requestCount: Long,
    val hasUsageFact: Boolean,
    val isLegacyAggregate: Boolean,
    val status: String?,
    val functionType: String?,
    val executionModel: String?,
    val firstTokenLatencyMs: Long?,
    val durationMs: Long?,
    val successfulAttempt: Int?,
    val observedAttemptNumbers: String?,
    val uncachedInputTokens: Long?,
    val cachedInputTokens: Long?,
    val cacheWriteTokens: Long?,
    val totalInputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val cost: TokenStatsCostSummary,
    val peakPeriod: TokenStatsPeakPeriod,
    val longContext: Boolean?,
)

data class TokenStatsRequestPage(
    val rows: List<TokenStatsRequestRow>,
    val totalRows: Long,
    val pageIndex: Int,
    val pageSize: Int,
    val historicalAggregateRequests: Long,
)

data class TokenStatsInsightsTotals(
    val requests: Long,
    val usageFactRequests: Long,
    val unconfirmedUsageRequests: Long,
    val historicalAggregateRequests: Long,
    val uncachedInput: TokenStatsTokenAggregate,
    val cachedInput: TokenStatsTokenAggregate,
    val cacheWrite: TokenStatsTokenAggregate,
    val output: TokenStatsTokenAggregate,
    val totalTokens: TokenStatsTokenAggregate,
    val cost: TokenStatsCostSummary,
    val statusCounts: Map<String, Long>,
    val unknownStatusRequests: Long,
)

/** 无法根据已知总输入拆开的费用单列，避免伪造四类分摊。 */
data class TokenStatsInsightsCostComposition(
    val uncachedInput: Double,
    val cachedInput: Double,
    val cacheWrite: Double,
    val output: Double,
    val perRequest: Double,
    val unallocatedInput: Double,
)

data class TokenStatsInsightsBreakdown(
    val key: String?,
    val totals: TokenStatsInsightsTotals,
)

data class TokenStatsInsightsConfiguration(
    val configId: String,
    val provider: String,
    val model: String,
    val totals: TokenStatsInsightsTotals,
)

data class TokenStatsInsightsBucket(
    val startMs: Long,
    val endMs: Long,
    val totals: TokenStatsInsightsTotals,
)

data class TokenStatsInsightsComparison(
    val validity: TokenStatsComparisonValidity,
    val range: TokenStatsTimeRange,
    val totals: TokenStatsInsightsTotals?,
    val buckets: List<TokenStatsInsightsBucket> = emptyList(),
)

data class TokenStatsInsightsOverview(
    val totals: TokenStatsInsightsTotals,
    val costComposition: TokenStatsInsightsCostComposition,
    val models: List<TokenStatsInsightsBreakdown>,
    val configurations: List<TokenStatsInsightsConfiguration>,
    val functions: List<TokenStatsInsightsBreakdown>,
    val peakPeriods: List<TokenStatsInsightsBreakdown>,
    val buckets: List<TokenStatsInsightsBucket>,
    val granularity: TokenStatsInsightsGranularity,
    val comparison: TokenStatsInsightsComparison?,
    val executionModels: List<TokenStatsInsightsBreakdown> = emptyList(),
    val quality: TokenStatsInsightsQuality = TokenStatsInsightsQuality(),
)
