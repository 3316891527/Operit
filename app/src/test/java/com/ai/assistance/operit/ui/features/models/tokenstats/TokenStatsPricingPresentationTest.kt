package com.ai.assistance.operit.ui.features.models.tokenstats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.stats.BillingMode
import com.ai.assistance.operit.data.stats.TokenStatsPriceDraft
import com.ai.assistance.operit.data.stats.TokenStatsPriceScope
import com.ai.assistance.operit.data.stats.TokenStatsPriceSetting
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TokenStatsPricingPresentationTest {
    @Test
    fun editingSmallPricesPreservesAllStoredDigits() {
        val values = listOf(0.00000001, 0.0000000049, 0.00330392, 12.123456789, 0.0)
        values.forEach { value ->
            assertEquals(value, formatTokenStatsPrice(value).toDouble(), 0.0)
        }
        assertEquals("0.0000000049", formatTokenStatsPrice(0.0000000049))
    }

    @Test
    fun identityRulesDoNotClutterFormula() {
        assertEquals("", tokenStatsFormulaMultiplierSuffix(listOf("峰谷" to 1.0, "长上下文" to 1.0)))
        assertEquals(" × 1.5 (长上下文)", tokenStatsFormulaMultiplierSuffix(listOf("峰谷" to 1.0, "长上下文" to 1.5)))
    }

    @Test
    fun discountedAndFreeFactorsRemainVisible() {
        assertEquals(" × 0.1 (峰谷) × 0 (长上下文)", tokenStatsFormulaMultiplierSuffix(listOf("峰谷" to 0.1, "长上下文" to 0.0)))
    }

    @Test
    fun priceDraftRetainsUnifiedMultiplierInFormulaPricing() {
        val draft = TokenStatsPriceDraft(
            scope = TokenStatsPriceScope.CONFIG,
            configId = "current",
            provider = "OPENAI",
            model = "custom",
            billingMode = BillingMode.TOKEN,
            currency = PricingCurrency.USD,
            billingMultiplier = 0.1,
        )
        assertEquals(0.1, draft.statsModelSettings().billingMultiplier, 0.0)
    }

    @Test
    fun providerMultiplierIsUsedWhenConfigurationPriceDoesNotExist() {
        val draft = priceDraftForIdentity("current", "OPENAI", "custom", listOf(setting(null, 0.1)))
        assertEquals(0.1, draft.billingMultiplier, 0.0)
    }

    @Test
    fun explicitConfigurationOneOverridesProviderDiscount() {
        val draft = priceDraftForIdentity("current", "OPENAI", "custom", listOf(setting(null, 0.1), setting("current", 1.0)))
        assertEquals(1.0, draft.billingMultiplier, 0.0)
    }

    @Test
    fun configurationDiscountSurvivesPriceEditorRoundTrip() {
        val draft = priceDraftForIdentity("current", "OPENAI", "custom", listOf(setting("current", 0.1)))
        assertEquals(0.1, draft.billingMultiplier, 0.0)
        assertEquals(0.1, draft.statsModelSettings().billingMultiplier, 0.0)
    }

    @Test
    fun requestAmountsUseEightDecimalsInBothLocales() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("$0.00330392", insightsMoney(0.00330392, PricingCurrency.USD))
            assertEquals("¥0.00503704", insightsMoney(0.00503704, PricingCurrency.CNY))
            Locale.setDefault(Locale.GERMANY)
            assertEquals("$0,00330392", insightsMoney(0.00330392, PricingCurrency.USD))
        } finally {
            Locale.setDefault(before)
        }
    }

    private fun setting(configId: String?, multiplier: Double) = TokenStatsPriceSetting(
        scope = if (configId == null) TokenStatsPriceScope.PROVIDER_MODEL else TokenStatsPriceScope.CONFIG,
        providerModel = "OPENAI:custom", provider = "OPENAI", model = "custom", configId = configId,
        billingMode = BillingMode.TOKEN, currency = PricingCurrency.USD,
        inputPricePerMillion = 2.0, cachedInputPricePerMillion = 0.1, cacheWritePricePerMillion = 0.0,
        outputPricePerMillion = 10.0, pricePerRequest = null,
        peakPricingEnabled = false, weekendOffPeakPricingEnabled = true, holidayOffPeakPricingEnabled = true,
        peakSchedule = null, peakInputMultiplier = 1.0, peakCachedInputMultiplier = 1.0,
        peakCacheWriteMultiplier = 1.0, peakOutputMultiplier = 1.0,
        longContextPricingEnabled = false, longContextThreshold = null,
        longContextInputMultiplier = 1.0, longContextCachedInputMultiplier = 1.0,
        longContextCacheWriteMultiplier = 1.0, longContextOutputMultiplier = 1.0,
        billingMultiplier = multiplier,
    )
}
