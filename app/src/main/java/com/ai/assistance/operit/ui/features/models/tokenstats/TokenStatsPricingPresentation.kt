package com.ai.assistance.operit.ui.features.models.tokenstats

import com.ai.assistance.operit.data.stats.ModelPriceSettings
import com.ai.assistance.operit.data.stats.TokenStatsPriceDraft
import java.math.BigDecimal

/** 编辑和公式沿用原始单价，避免打开后保存将小额价格舍入为零。 */
internal fun formatTokenStatsPrice(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** 仅展示实际生效且不为一的规则因子，零倍率同样保留。 */
internal fun tokenStatsFormulaMultiplierSuffix(factors: List<Pair<String, Double>>): String =
    factors.filter { it.second != 1.0 }.joinToString("") { (label, factor) ->
        " × ${formatTokenStatsPrice(factor)} ($label)"
    }

internal fun TokenStatsPriceDraft.statsModelSettings() = ModelPriceSettings(
    billingMode = billingMode,
    currency = currency,
    inputPricePerMillion = inputPricePerMillion,
    cachedInputPricePerMillion = cachedInputPricePerMillion,
    cacheWritePricePerMillion = cacheWritePricePerMillion,
    outputPricePerMillion = outputPricePerMillion,
    pricePerRequest = pricePerRequest,
    peakPricingEnabled = peakPricingEnabled,
    weekendOffPeakPricingEnabled = weekendOffPeakPricingEnabled,
    holidayOffPeakPricingEnabled = holidayOffPeakPricingEnabled,
    peakSchedule = peakSchedule,
    peakInputMultiplier = peakInputMultiplier,
    peakCachedInputMultiplier = peakCachedInputMultiplier,
    peakCacheWriteMultiplier = peakCacheWriteMultiplier,
    peakOutputMultiplier = peakOutputMultiplier,
    longContextPricingEnabled = longContextPricingEnabled,
    longContextThreshold = longContextThreshold,
    longContextInputMultiplier = longContextInputMultiplier,
    longContextCachedInputMultiplier = longContextCachedInputMultiplier,
    longContextCacheWriteMultiplier = longContextCacheWriteMultiplier,
    longContextOutputMultiplier = longContextOutputMultiplier,
    billingMultiplier = billingMultiplier,
)
