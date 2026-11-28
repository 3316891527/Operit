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
    fun cacheRateDoesNotInferCoverageFromOldTotals() {
        val known = TokenStatsTokenAggregate(100L, 1L, 0L, 1L)
        val zero = known.copy(knownSum = 0L)
        val totals = TokenStatsInsightsTotals(
            1L, 1L, 0L, 0L, known, zero, zero, zero, known, row.cost, mapOf("SUCCESS" to 1L), 0L,
        )
        assertNull(totals.cacheReadCoverage?.ratio)
    }

    @Test
    fun availabilityDistinguishesZeroRateFromNoDenominator() {
        val params = TokenStatsQueryParams(PricingCurrency.CNY, manualRate = 7.0)
        fun totals(vararg rows: TokenStatsRequestRow): TokenStatsInsightsTotals =
            InsightsAccumulator(params).apply { rows.forEach { add(it) } }.result()

        val noInput = requireNotNull(totals(row).cacheReadCoverage)
        assertEquals(1L, noInput.completeRequests)
        assertNull(noInput.ratio)
        val zeroHitRow = row.copy(uncachedInputTokens = 100L, totalInputTokens = 100L, totalTokens = 120L)
        val zeroHits = requireNotNull(totals(zeroHitRow).cacheReadCoverage)
        assertEquals(0.0, requireNotNull(zeroHits.ratio), 0.0)
        val missing = requireNotNull(totals(row.copy(cachedInputTokens = null)).cacheReadCoverage)
        assertEquals(1L, missing.missingRequests)
        assertNull(missing.ratio)

        val unknown = tokenStatsSuccessCoverage(totals(row.copy(status = null), row.copy(status = "UNKNOWN")))
        assertEquals(2L, unknown.unknownStatusRequests)
        assertNull(unknown.ratio)
        val completed = tokenStatsSuccessCoverage(totals(row.copy(status = "COMPLETED")))
        assertEquals(1L, completed.knownStatusRequests)
        assertEquals(0.0, requireNotNull(completed.ratio), 0.0)
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