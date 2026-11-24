package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TokenStatsInsightsAggregationTest {
    private val params = TokenStatsQueryParams(targetCurrency = PricingCurrency.USD)
    private fun pricing() = ResolvedTokenPricing(
        billingMode = BillingMode.TOKEN, currency = PricingCurrency.USD, inputPricePerMillion = 2.0,
        cachedInputPricePerMillion = 0.5, cacheWritePricePerMillion = 3.0, outputPricePerMillion = 8.0,
        pricePerRequest = 0.0, source = PricingSource.USER,
    )
    private fun fact() = TokenUsageRecordEntity(occurredAtMs = 1_800_000_000_000L, configId = "c", provider = "OPENAI", model = "m", requestCount = 1, uncachedInputTokens = 100, cachedInputTokens = 200, cacheWriteTokens = 30, totalInputTokens = 330, outputTokens = 10)

    @Test fun comparisonRejectsOverlapIdentityAndUnequalLength() {
        val current = TokenStatsTimeRange(100, 200)
        assertEquals(TokenStatsComparisonValidity.IDENTICAL, insightsComparisonValidity(current, current))
        assertEquals(TokenStatsComparisonValidity.OVERLAPPING, insightsComparisonValidity(current, TokenStatsTimeRange(150, 250)))
        assertEquals(TokenStatsComparisonValidity.AVAILABLE, insightsComparisonValidity(current, TokenStatsTimeRange(0, 100)))
        assertEquals(TokenStatsComparisonValidity.DIFFERENT_DURATION, insightsComparisonValidity(current, TokenStatsTimeRange(0, 50)))
        assertEquals(TokenStatsComparisonValidity.NO_CURRENT_RANGE, insightsComparisonValidity(null, current))
    }

    @Test fun unknownComponentNeverBecomesZeroAndSumsSaturate() {
        assertNull(insightsKnownSum(10, null))
        assertNull(insightsKnownSum(10, -1))
        assertEquals(Long.MAX_VALUE, insightsKnownSum(Long.MAX_VALUE, 100))
    }

    @Test fun costCompositionUsesExistingCalculatorWithMultipliers() {
        val record = fact()
        val price = pricing().copy(longContextPricingEnabled = true, longContextThreshold = 100, longContextInputMultiplier = 2.0, longContextCachedInputMultiplier = 2.0, longContextCacheWriteMultiplier = 2.0, longContextOutputMultiplier = 1.5)
        val cost = insightsCost(record, price, params)
        val pieces = insightsCostComposition(record, price, params)
        assertEquals(cost.knownAmount, pieces.uncachedInput + pieces.cachedInput + pieces.cacheWrite + pieces.output + pieces.perRequest + pieces.unallocatedInput, 1e-12)
        assertTrue(insightsLongContext(record, price) == true)
    }

    @Test fun knownTotalInputIsNotFalselyAssignedToUnknownClasses() {
        val record = fact().copy(uncachedInputTokens = null, cachedInputTokens = null, cacheWriteTokens = null)
        val price = pricing().copy(inputPricePerMillion = 2.0, cachedInputPricePerMillion = 2.0, cacheWritePricePerMillion = 2.0)
        val pieces = insightsCostComposition(record, price, params)
        assertEquals(0.0, pieces.uncachedInput + pieces.cachedInput + pieces.cacheWrite, 0.0)
        assertEquals(330 * 2.0 / 1_000_000, pieces.unallocatedInput, 1e-12)
    }

    @Test fun cumulativeUsageDoesNotGetRequestLevelMultipliers() {
        val record = fact().copy(occurredAtMs = null, requestCount = 100)
        val price = pricing().copy(peakPricingEnabled = true, longContextPricingEnabled = true, longContextThreshold = 1)
        assertEquals(TokenStatsPeakPeriod.UNKNOWN, insightsPeak(record, price))
        assertNull(insightsLongContext(record, price))
    }

    @Test fun calendarBucketsCoverDstDayWithoutHoles() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 8)
        val range = TokenStatsTimeRange(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val buckets = TokenStatsInsightsService.bucketBounds(range, TokenStatsInsightsGranularity.HOURLY, zone)
        assertEquals(23, buckets.size)
        assertEquals(range.startMs, buckets.first().first)
        assertEquals(range.endMs, buckets.last().second)
        buckets.zipWithNext().forEach { (a, b) -> assertEquals(a.second, b.first) }
    }

    @Test fun diagnosticFailureTokensDoNotChangeSuccessfulUsageTotals() {
        val factRow = TokenStatsRequestRow(
            id = "fact", requestId = null, occurredAtMs = 1L, configId = "c", provider = "OPENAI", model = "m", requestCount = 1L, hasUsageFact = true, isLegacyAggregate = false,
            status = "SUCCESS", functionType = null, executionModel = null, firstTokenLatencyMs = null, durationMs = null, successfulAttempt = null, observedAttemptNumbers = null,
            uncachedInputTokens = 80L, cachedInputTokens = 10L, cacheWriteTokens = 0L, totalInputTokens = 90L, outputTokens = 10L, totalTokens = 100L,
            cost = TokenStatsCostSummary(PricingCurrency.USD, 1.0, 0L, 1L, 7.0, emptyMap()), peakPeriod = TokenStatsPeakPeriod.NOT_ENABLED, longContext = false,
        )
        val failureRow = factRow.copy(id = "failure", hasUsageFact = false, status = "FAILED", cost = factRow.cost.copy(knownAmount = 0.0, unknownContributionCount = 1L))
        val accumulator = InsightsAccumulator(params)
        accumulator.add(factRow)
        accumulator.add(failureRow)
        val totals = accumulator.result()
        assertEquals(2L, totals.requests)
        assertEquals(1L, totals.usageFactRequests)
        assertEquals(1L, totals.unconfirmedUsageRequests)
        assertEquals(100L, totals.totalTokens.knownSum)
        assertEquals(1L, totals.totalTokens.unknownEventCount)
        assertEquals(1L, totals.cost.unknownContributionCount)
    }
    @Test fun comparisonBucketsPreservePartialHourCoverage() {
        val hour = 3_600_000L
        val current = TokenStatsTimeRange(25 * hour / 2, 29 * hour / 2)
        val comparison = TokenStatsTimeRange(9 * hour, 11 * hour)
        val bounds = TokenStatsInsightsService.bucketBounds(current, TokenStatsInsightsGranularity.HOURLY, ZoneId.of("UTC"))
        val aligned = TokenStatsInsightsService.comparisonBucketBounds(current, comparison, bounds)
        assertEquals(listOf(9 * hour to 19 * hour / 2, 19 * hour / 2 to 21 * hour / 2, 21 * hour / 2 to 11 * hour), aligned)
        assertEquals(bounds.map { it.second - it.first }, aligned.map { it.second - it.first })
        assertEquals(comparison.startMs, aligned.first().first)
        assertEquals(comparison.endMs, aligned.last().second)
        aligned.zipWithNext().forEach { (a, b) -> assertEquals(a.second, b.first) }
    }
    @Test fun monthlyComparisonUsesCurrentCoverageInsteadOfOtherCalendarBoundaries() {
        val zone = ZoneId.of("UTC")
        val start = LocalDate.of(2026, 2, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 4, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val current = TokenStatsTimeRange(start, end)
        val previous = TokenStatsTimeRange(start - current.durationMs, start)
        val bounds = TokenStatsInsightsService.bucketBounds(current, TokenStatsInsightsGranularity.MONTHLY, zone)
        val calendarPrevious = TokenStatsInsightsService.bucketBounds(previous, TokenStatsInsightsGranularity.MONTHLY, zone)
        val aligned = TokenStatsInsightsService.comparisonBucketBounds(current, previous, bounds)
        assertFalse(calendarPrevious.map { it.second - it.first } == aligned.map { it.second - it.first })
        assertEquals(bounds.map { it.second - it.first }, aligned.map { it.second - it.first })
        assertEquals(previous.startMs, aligned.first().first)
        assertEquals(previous.endMs, aligned.last().second)
        aligned.zipWithNext().forEach { (a, b) -> assertEquals(a.second, b.first) }
    }
    @Test fun comparisonPreservesDstCoverageAsRealElapsedTime() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 8)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val current = TokenStatsTimeRange(start, end)
        val previous = TokenStatsTimeRange(start - current.durationMs, start)
        val bounds = TokenStatsInsightsService.bucketBounds(current, TokenStatsInsightsGranularity.DAILY, zone)
        val aligned = TokenStatsInsightsService.comparisonBucketBounds(current, previous, bounds)
        assertEquals(23 * 3_600_000L, bounds.single().second - bounds.single().first)
        assertEquals(listOf(previous.startMs to previous.endMs), aligned)
    }
    @Test fun conflictingInputProjectionKeepsCalculatorTotalAndKnownOutput() {
        val record = fact().copy(uncachedInputTokens = 0, cachedInputTokens = 200, cacheWriteTokens = 0, totalInputTokens = 100, outputTokens = 10)
        val price = pricing().copy(inputPricePerMillion = 1.0, cachedInputPricePerMillion = 1.0, cacheWritePricePerMillion = 1.0, outputPricePerMillion = 2.0)
        val total = insightsCost(record, price, params).knownAmount
        val pieces = insightsCostComposition(record, price, params)
        assertEquals(0.0, pieces.uncachedInput + pieces.cachedInput + pieces.cacheWrite, 0.0)
        assertEquals(0.00002, pieces.output, 1e-12)
        assertEquals(0.0001, pieces.unallocatedInput, 1e-12)
        assertEquals(total, pieces.output + pieces.unallocatedInput, 1e-12)
    }
    @Test fun conflictingKnownTotalDoesNotInventMissingInputClasses() {
        val record = fact().copy(uncachedInputTokens = 10, cachedInputTokens = 20, cacheWriteTokens = 0, totalInputTokens = 100)
        val price = pricing().copy(inputPricePerMillion = 1.0, cachedInputPricePerMillion = 1.0, cacheWritePricePerMillion = 1.0)
        val pieces = insightsCostComposition(record, price, params)
        assertEquals(0.0, pieces.uncachedInput + pieces.cachedInput + pieces.cacheWrite, 0.0)
        assertEquals(0.0001, pieces.unallocatedInput, 1e-12)
        assertEquals(insightsCost(record, price, params).knownAmount, pieces.output + pieces.unallocatedInput, 1e-12)
    }
    @Test fun incompleteInputBreakdownWithKnownTotalRetainsValidKnownClasses() {
        val record = fact().copy(uncachedInputTokens = 10, cachedInputTokens = null, cacheWriteTokens = null, totalInputTokens = 100)
        val price = pricing().copy(inputPricePerMillion = 1.0, cachedInputPricePerMillion = 1.0, cacheWritePricePerMillion = 1.0)
        val pieces = insightsCostComposition(record, price, params)
        assertEquals(0.00001, pieces.uncachedInput, 1e-12)
        assertEquals(0.00009, pieces.unallocatedInput, 1e-12)
        assertEquals(insightsCost(record, price, params).knownAmount, pieces.uncachedInput + pieces.output + pieces.unallocatedInput, 1e-12)
    }
}
