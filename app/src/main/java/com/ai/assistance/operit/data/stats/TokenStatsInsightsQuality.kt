package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.model.TokenRequestLogEntity

/** 全筛选范围的运行质量。每项使用独立的有效样本数；空值表示无有效样本。 */
data class TokenStatsInsightsQuality(
    val averageFirstTokenLatencyMs: Double? = null,
    val firstTokenLatencySamples: Long = 0L,
    val p95DurationMs: Long? = null,
    val durationSamples: Long = 0L,
    /** 目标币种同总览费用；完整定价的历史批次按所含请求数加权。 */
    val averagePricedRequestCost: Double? = null,
    val pricedRequestSamples: Long = 0L,
    val averageSuccessfulTokensPerSecond: Double? = null,
    val successfulTokensPerSecondSamples: Long = 0L,
)

internal val TokenStatsRequestRow.isIndividualRequest: Boolean
    get() = requestCount == 1L && !isLegacyAggregate

/**
 * 单次成功请求在首字后的输出速度，单位为 Token/秒。只接受实际用量事实和已知非负输出，
 * 且首字时间必须已知、非负并小于完整耗时；未知不是零，已知的零输出仍是有效样本。
 * 总览对有效请求的各自速度取算术平均，详情复用同一口径。
 */
fun tokenStatsOutputTokensPerSecond(row: TokenStatsRequestRow): Double? {
    if (!row.isIndividualRequest || !row.hasUsageFact || row.status != TokenRequestLogEntity.SUCCESS) return null
    val output = row.outputTokens?.takeIf { it >= 0L } ?: return null
    val first = row.firstTokenLatencyMs?.takeIf { it >= 0L } ?: return null
    val end = row.durationMs?.takeIf { it > first } ?: return null
    return output.toDouble() * 1000.0 / (end - first).toDouble()
}

internal fun tokenStatsCacheReadRatio(totals: TokenStatsInsightsTotals): Double? {
    val parts = listOf(totals.uncachedInput, totals.cachedInput, totals.cacheWrite)
    if (parts.any { !it.isFullyKnown || it.knownSum < 0L }) return null
    val input = parts.sumOf { it.knownSum.toDouble() }
    if (input == 0.0) return null
    return totals.cachedInput.knownSum.toDouble() / input
}

internal fun tokenStatsSuccessRatio(totals: TokenStatsInsightsTotals): Double? {
    if (totals.requests <= 0L || totals.unknownStatusRequests != 0L || totals.statusCounts.keys.any { it !in knownRequestStatuses }) return null
    return (totals.statusCounts[TokenRequestLogEntity.SUCCESS] ?: 0L).toDouble() / totals.requests
}

private val knownRequestStatuses = setOf(
    TokenRequestLogEntity.SUCCESS,
    TokenRequestLogEntity.FAILED,
    TokenRequestLogEntity.CANCELLED,
    TokenRequestLogEntity.COMPLETED,
)

internal enum class TokenStatsRatioKind(val greenFrom: Double, val yellowFrom: Double) {
    CACHE_READ(0.90, 0.50),
    REQUEST_SUCCESS(0.95, 0.70),
}

internal enum class TokenStatsRatioLevel { UNKNOWN, GREEN, YELLOW, RED }

internal fun tokenStatsRatioLevel(ratio: Double?, kind: TokenStatsRatioKind): TokenStatsRatioLevel = when {
    ratio == null || ratio !in 0.0..1.0 -> TokenStatsRatioLevel.UNKNOWN
    ratio >= kind.greenFrom -> TokenStatsRatioLevel.GREEN
    ratio >= kind.yellowFrom -> TokenStatsRatioLevel.YELLOW
    else -> TokenStatsRatioLevel.RED
}
