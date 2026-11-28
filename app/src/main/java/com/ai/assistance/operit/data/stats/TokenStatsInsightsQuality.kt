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

internal val TokenStatsCacheReadCoverage.ratio: Double?
    get() = if (totalInputTokens > 0.0) cachedInputTokens / totalInputTokens else null

internal data class TokenStatsSuccessCoverage(
    val successfulRequests: Long,
    val knownStatusRequests: Long,
    val unknownStatusRequests: Long,
) {
    val ratio: Double?
        get() = if (knownStatusRequests > 0L) successfulRequests.toDouble() / knownStatusRequests else null
}

internal fun tokenStatsSuccessCoverage(totals: TokenStatsInsightsTotals): TokenStatsSuccessCoverage {
    var known = 0L
    var unknown = totals.unknownStatusRequests
    totals.statusCounts.forEach { (status, count) ->
        when (status) {
            TokenRequestLogEntity.SUCCESS, TokenRequestLogEntity.FAILED, TokenRequestLogEntity.CANCELLED,
            // 流正常结束但未确认成功：状态明确，只计入分母。
            TokenRequestLogEntity.COMPLETED -> known = TokenCostCalculator.saturatedAdd(known, count)
            else -> unknown = TokenCostCalculator.saturatedAdd(unknown, count)
        }
    }
    return TokenStatsSuccessCoverage(totals.statusCounts[TokenRequestLogEntity.SUCCESS] ?: 0L, known, unknown)
}

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
