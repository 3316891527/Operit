package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TokenBillingMultiplierTest {
    @Test
    fun `token discount applies once to both calculation paths and native currency`() {
        val price = pricing(billingMultiplier = 0.1)
        val aggregate = TokenUsageModelAggregateRow(
            provider = "OPENAI", model = "test-model", configId = "config", requests = 1L,
            uncachedInputTokens = 3_229L, uncachedInputKnown = 1L,
            cachedInputTokens = 173_312L, cachedInputKnown = 1L,
            cacheWriteTokens = 0L, cacheWriteKnown = 1L,
            totalInputTokens = 176_541L, totalInputKnown = 1L,
            outputTokens = 925L, outputKnown = 1L,
        )
        val costs = listOf(
            TokenCostCalculator.currentCost(aggregate, price, PricingCurrency.CNY, 7.0),
            TokenCostCalculator.currentCost(listOf(record()), price, PricingCurrency.CNY, 7.0),
        )
        val relay = TokenCostCalculator.currentCost(listOf(record()), price, PricingCurrency.USD, 7.0)
        val decimalPrices = price.copy(
            inputPricePerMillion = 0.2,
            cachedInputPricePerMillion = 0.02,
            outputPricePerMillion = 1.0,
            billingMultiplier = 1.0,
        )
        val alternative = TokenCostCalculator.currentCost(listOf(record()), decimalPrices, PricingCurrency.USD, 7.0)
        assertEquals(0.00330392, relay.knownAmount, 1e-12)
        assertEquals(0.00503704, alternative.knownAmount, 1e-12)
        // 固定同币种比较，仅缓存单价差 0.01 美元/百万即可解释小计差值。
        assertEquals(173_312 * 0.01 / 1_000_000, alternative.knownAmount - relay.knownAmount, 1e-12)
        costs.forEach { cost ->
            assertEquals(0.00330392 * 7.0, cost.knownAmount, 1e-12)
            assertEquals(0.00330392, cost.originalCurrencyAmounts.getValue(PricingCurrency.USD), 1e-12)
            assertEquals(0L, cost.unknownContributionCount)
        }
    }

    @Test
    fun `count discount applies once and ignores token rules`() {
        val price = pricing(billingMultiplier = 0.1).copy(
            billingMode = BillingMode.COUNT,
            pricePerRequest = 0.25,
            longContextPricingEnabled = true,
            longContextThreshold = 1L,
            longContextInputMultiplier = 2.0,
            peakPricingEnabled = true,
            peakInputMultiplier = 3.0,
        )
        val row = TokenUsageModelAggregateRow(
            provider = "OPENAI", model = "test-model", configId = "config", requests = 4L,
            uncachedInputTokens = 0L, uncachedInputKnown = 0L, cachedInputTokens = 0L, cachedInputKnown = 0L,
            cacheWriteTokens = 0L, cacheWriteKnown = 0L, totalInputTokens = 0L, totalInputKnown = 0L,
            outputTokens = 0L, outputKnown = 0L,
        )
        val costs = listOf(
            TokenCostCalculator.currentCost(row, price, PricingCurrency.USD, 7.0),
            TokenCostCalculator.currentCost(List(4) { record() }, price, PricingCurrency.USD, 7.0),
        )
        costs.forEach { cost ->
            assertEquals(0.1, cost.knownAmount, 1e-12)
            assertEquals(0.1, cost.originalCurrencyAmounts.getValue(PricingCurrency.USD), 1e-12)
            assertEquals(4L, cost.totalContributionCount)
            assertEquals(0L, cost.unknownContributionCount)
        }
    }

    @Test
    fun `cost composition retains the discount on every token component and count fee`() {
        val time = LocalDateTime.parse("2026-08-03T10:00")
            .atZone(TokenPricingRules.PRICING_ZONE).toInstant().toEpochMilli()
        val usage = record().copy(occurredAtMs = time, cacheWriteTokens = 11L, totalInputTokens = 176_552L)
        val price = pricing(billingMultiplier = 0.1).copy(
            cacheWritePricePerMillion = 3.0,
            peakPricingEnabled = true,
            peakInputMultiplier = 3.0,
            longContextPricingEnabled = true,
            longContextThreshold = 1L,
            longContextInputMultiplier = 2.0,
        )
        val params = TokenStatsQueryParams(targetCurrency = PricingCurrency.USD)
        val tokenPieces = insightsCostComposition(usage, price, params)
        assertEquals(0.0038748, tokenPieces.uncachedInput, 1e-12)
        assertEquals(0.00173312, tokenPieces.cachedInput, 1e-12)
        assertEquals(0.0000033, tokenPieces.cacheWrite, 1e-12)
        assertEquals(0.000925, tokenPieces.output, 1e-12)
        assertEquals(0.0, tokenPieces.unallocatedInput, 1e-12)
        assertEquals(0.00653622, insightsCost(usage, price, params).knownAmount, 1e-12)

        val countPrice = price.copy(billingMode = BillingMode.COUNT, pricePerRequest = 0.25)
        val countPieces = insightsCostComposition(usage, countPrice, params)
        assertEquals(0.025, countPieces.perRequest, 1e-12)
        assertEquals(insightsCost(usage, countPrice, params).knownAmount, countPieces.perRequest, 0.0)
        assertEquals(0.0, countPieces.uncachedInput + countPieces.cachedInput + countPieces.cacheWrite + countPieces.output, 0.0)
    }

    @Test
    fun `zero billing is free without pretending missing usage is known`() {
        val price = pricing(billingMultiplier = 0.0)
        val cost = TokenCostCalculator.currentCost(
            listOf(record(), record().copy(outputTokens = null)), price, PricingCurrency.USD, 7.0,
        )
        assertEquals(0.0, cost.knownAmount, 0.0)
        assertEquals(emptyMap<PricingCurrency, Double>(), cost.originalCurrencyAmounts)
        assertEquals(1L, cost.unknownContributionCount)
        assertEquals(2L, cost.totalContributionCount)
    }

    @Test
    fun `resolver uses configuration multiplier including explicit one without changing base prices`() {
        val key = "OPENAI:test-model"
        val settings = TokenPriceSettingsSnapshot(
            providerModels = mapOf(key to ModelPriceSettings(inputPricePerMillion = 2.0, billingMultiplier = 0.1)),
            configs = mapOf(
                tokenPriceConfigKey(key, "neutral") to ModelPriceSettings(billingMultiplier = 1.0),
                tokenPriceConfigKey(key, "surcharge") to ModelPriceSettings(billingMultiplier = 1.5),
            ),
        )
        assertEquals(1.0, TokenPriceResolver.resolve(key, null).billingMultiplier, 0.0)
        assertEquals(PricingSource.BUILT_IN, TokenPriceResolver.resolve(key, ModelPriceSettings()).source)
        mapOf("no-config-setting" to 0.1, "neutral" to 1.0, "surcharge" to 1.5).forEach { (id, multiplier) ->
            val resolved = TokenPriceResolver.resolve(key, settings.settingFor(key, id))
            assertEquals(multiplier, resolved.billingMultiplier, 0.0)
            assertEquals(2.0, resolved.inputPricePerMillion, 0.0)
            assertEquals(PricingSource.USER, resolved.source)
        }
        assertEquals(PricingSource.USER, TokenPriceResolver.resolve(key, ModelPriceSettings(billingMultiplier = 0.1)).source)
    }

    @Test
    fun `nonfinite or negative multipliers are rejected in pricing models`() {
        listOf(-0.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { ModelPriceSettings(billingMultiplier = invalid) }
            assertThrows(IllegalArgumentException::class.java) { pricing(billingMultiplier = invalid) }
        }
    }

    private fun pricing(billingMultiplier: Double) = ResolvedTokenPricing(
        billingMode = BillingMode.TOKEN, currency = PricingCurrency.USD,
        inputPricePerMillion = 2.0, cachedInputPricePerMillion = 0.1,
        cacheWritePricePerMillion = 0.0, outputPricePerMillion = 10.0,
        pricePerRequest = 0.0, source = PricingSource.USER, billingMultiplier = billingMultiplier,
    )

    private fun record() = TokenUsageRecordEntity(
        occurredAtMs = 1_000L, configId = "config", provider = "OPENAI", model = "test-model",
        requestCount = 1L, uncachedInputTokens = 3_229L, cachedInputTokens = 173_312L,
        cacheWriteTokens = 0L, totalInputTokens = 176_541L, outputTokens = 925L,
    )
}
