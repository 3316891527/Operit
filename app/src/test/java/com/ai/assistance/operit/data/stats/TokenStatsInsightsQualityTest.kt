package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenStatsInsightsQualityTest {
    @Test
    fun tpsUsesPostFirstTokenTimeAndAllowsKnownZeroOutput() {
        assertEquals(25.0, requireNotNull(tokenStatsOutputTokensPerSecond(row)), 0.0)
        assertEquals(0.0, requireNotNull(tokenStatsOutputTokensPerSecond(row.copy(outputTokens = 0))), 0.0)
        assertEquals(20.0, requireNotNull(tokenStatsOutputTokensPerSecond(row.copy(firstTokenLatencyMs = 0))), 0.0)
    }

    @Test
    fun tpsRequiresSuccessfulIndividualFactAndCompleteValidTiming() {
        val ineligible = listOf(
            row.copy(status = null), row.copy(status = "COMPLETED"), row.copy(status = "FAILED"),
            row.copy(status = "CANCELLED"), row.copy(hasUsageFact = false), row.copy(requestCount = 2),
            row.copy(isLegacyAggregate = true), row.copy(outputTokens = null), row.copy(outputTokens = -1),
            row.copy(firstTokenLatencyMs = null), row.copy(firstTokenLatencyMs = -1),
            row.copy(durationMs = null), row.copy(durationMs = 200), row.copy(durationMs = 199),
        )
        ineligible.forEach { assertNull(tokenStatsOutputTokensPerSecond(it)) }
    }

    @Test
    fun ratioColorsUseInclusiveExactThresholdsAndNeutralUnknowns() {
        assertLevels(TokenStatsRatioKind.CACHE_READ,
            0.0 to TokenStatsRatioLevel.RED, 0.4999 to TokenStatsRatioLevel.RED,
            0.50 to TokenStatsRatioLevel.YELLOW, 0.89 to TokenStatsRatioLevel.YELLOW,
            0.8999 to TokenStatsRatioLevel.YELLOW, 0.90 to TokenStatsRatioLevel.GREEN)
        assertLevels(TokenStatsRatioKind.REQUEST_SUCCESS,
            0.0 to TokenStatsRatioLevel.RED, 0.6999 to TokenStatsRatioLevel.RED,
            0.70 to TokenStatsRatioLevel.YELLOW, 0.8999 to TokenStatsRatioLevel.YELLOW,
            0.9499 to TokenStatsRatioLevel.YELLOW, 0.95 to TokenStatsRatioLevel.GREEN)
        for (kind in TokenStatsRatioKind.entries) {
            assertLevels(kind, null to TokenStatsRatioLevel.UNKNOWN, Double.NaN to TokenStatsRatioLevel.UNKNOWN,
                1.01 to TokenStatsRatioLevel.UNKNOWN, -0.01 to TokenStatsRatioLevel.UNKNOWN)
        }
    }

    @Test
    fun unavailableRatiosStayUnknownButKnownZerosAreValid() {
        val known = TokenStatsTokenAggregate(100L, 1L, 0L, 1L)
        val zero = known.copy(knownSum = 0L)
        val totals = TokenStatsInsightsTotals(
            1L, 1L, 0L, 0L, known, zero, zero, zero, known, row.cost, mapOf("FAILED" to 1L), 0L,
        )
        assertEquals(0.0, requireNotNull(tokenStatsCacheReadRatio(totals)), 0.0)
        assertEquals(0.0, requireNotNull(tokenStatsSuccessRatio(totals)), 0.0)
        assertNull(tokenStatsCacheReadRatio(totals.copy(uncachedInput = zero)))
        assertNull(tokenStatsCacheReadRatio(totals.copy(cacheWrite = zero.copy(unknownEventCount = 1L))))
        assertNull(tokenStatsSuccessRatio(totals.copy(requests = 0L, statusCounts = emptyMap())))
        assertNull(tokenStatsSuccessRatio(totals.copy(statusCounts = emptyMap(), unknownStatusRequests = 1L)))
        assertNull(tokenStatsSuccessRatio(totals.copy(statusCounts = mapOf("UNKNOWN" to 1L))))
    }

    private fun assertLevels(kind: TokenStatsRatioKind, vararg cases: Pair<Double?, TokenStatsRatioLevel>) {
        cases.forEach { (ratio, level) -> assertEquals(level, tokenStatsRatioLevel(ratio, kind)) }
    }

    private val row = TokenStatsRequestRow(
        id = "sample", requestId = "sample", occurredAtMs = 1L, configId = "priced", provider = "OPENAI",
        model = "quality-synthetic", requestCount = 1L, hasUsageFact = true, isLegacyAggregate = false,
        status = "SUCCESS", functionType = "CHAT", executionModel = null,
        firstTokenLatencyMs = 200L, durationMs = 1000L, successfulAttempt = 1, observedAttemptNumbers = "1",
        uncachedInputTokens = 0L, cachedInputTokens = 0L, cacheWriteTokens = 0L, totalInputTokens = 0L,
        outputTokens = 20L, totalTokens = 20L,
        cost = TokenStatsCostSummary(PricingCurrency.CNY, 0.001, 0L, 1L, 7.0, emptyMap()),
        peakPeriod = TokenStatsPeakPeriod.NOT_ENABLED, longContext = false,
    )
}