package com.ai.assistance.operit.data.stats

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.collects.PricingCurrency
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 直接比较精简费用查询与完整总览，覆盖预算不可丢失的真实计价与未知贡献。 */
@RunWith(AndroidJUnit4::class)
class TokenStatsInsightsCostSummaryTest {
    private val peakTime = Instant.parse("2026-11-23T02:00:00Z").toEpochMilli()
    private val offPeakTime = Instant.parse("2026-11-23T12:00:00Z").toEpochMilli()
    private val range = TokenStatsTimeRange(peakTime, offPeakTime + 1L)
    private val prices = TokenPriceSettingsSnapshot(
        mapOf("OPENAI:perf-model" to ModelPriceSettings(
            billingMode = BillingMode.TOKEN, currency = PricingCurrency.USD,
            inputPricePerMillion = 2.0, cachedInputPricePerMillion = 0.5,
            cacheWritePricePerMillion = 3.0, outputPricePerMillion = 8.0,
            peakPricingEnabled = true, peakSchedule = listOf(TokenPeakTimeRange(9 * 60, 18 * 60)),
            peakInputMultiplier = 1.5, peakCachedInputMultiplier = 1.5,
            peakCacheWriteMultiplier = 1.5, peakOutputMultiplier = 1.5,
            longContextPricingEnabled = true, longContextThreshold = 50L,
            longContextInputMultiplier = 2.0, longContextCachedInputMultiplier = 2.0,
            longContextCacheWriteMultiplier = 2.0, longContextOutputMultiplier = 2.0,
        ), "OPENAI:perf-unpriced-nonexistent" to ModelPriceSettings(
            billingMode = BillingMode.TOKEN, currency = PricingCurrency.USD,
            inputPricePerMillion = 0.0, cachedInputPricePerMillion = 0.0,
            cacheWritePricePerMillion = 0.0, outputPricePerMillion = 0.0,
        )),
        mapOf(tokenPriceConfigKey("OPENAI:perf-model", "count-config") to ModelPriceSettings(
            billingMode = BillingMode.COUNT, currency = PricingCurrency.CNY, pricePerRequest = 0.5,
        )),
    )

    @Test
    fun budgetSummaryMatchesOverviewForScopesCurrenciesPeaksAndHistoricalUnknowns() {
        database().use { helper ->
            val db = helper.writableDatabase
            val filters = listOf(
                TokenStatsInsightsFilter(),
                TokenStatsInsightsFilter(range = range),
                TokenStatsInsightsFilter(range = range, configIds = setOf("count-config")),
                TokenStatsInsightsFilter(configIds = setOf("")),
                TokenStatsInsightsFilter(statuses = setOf("FAILED")),
                TokenStatsInsightsFilter(models = emptySet()),
            ) + TokenStatsPeakPeriod.values().map { TokenStatsInsightsFilter(peakPeriods = setOf(it)) }
            for (currency in PricingCurrency.values()) {
                val params = TokenStatsQueryParams(currency, manualRate = 6.8)
                for (filter in filters) {
                    val overview = TokenStatsInsightsService.aggregate(
                        db, prices, filter, params, ZoneId.of("UTC"), TokenStatsInsightsGranularity.AUTO, {},
                    )
                    assertEquals(overview.totals.cost, TokenStatsInsightsService.costSummary(db, prices, filter, params) {})
                }
            }
            val params = TokenStatsQueryParams(PricingCurrency.CNY, manualRate = 6.8)
            val all = TokenStatsInsightsService.costSummary(db, prices, TokenStatsInsightsFilter(), params) {}
            assertEquals(11L, all.totalContributionCount)
            assertEquals(2L, all.unknownContributionCount)
            val count = TokenStatsInsightsService.costSummary(db, prices, TokenStatsInsightsFilter(configIds = setOf("count-config")), params) {}
            assertEquals(0.5, count.knownAmount, 0.0)
            assertEquals(mapOf(PricingCurrency.CNY to 0.5), count.originalCurrencyAmounts)
            val failed = TokenStatsInsightsService.costSummary(db, prices, TokenStatsInsightsFilter(statuses = setOf("FAILED")), params) {}
            assertEquals(0.0, failed.knownAmount, 0.0)
            assertEquals(1L, failed.unknownContributionCount)
            val detached = TokenStatsInsightsService.costSummary(db, prices, TokenStatsInsightsFilter(configIds = setOf(TokenUsageRepository.DETACHED_CONFIG_ID)), params) {}
            assertEquals(0.0, detached.knownAmount, 0.0)
            assertEquals(0L, detached.unknownContributionCount)
        }
    }

    private fun database(): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE token_usage_records (id INTEGER PRIMARY KEY, requestId TEXT UNIQUE, importKey TEXT, occurredAtMs INTEGER, configId TEXT, provider TEXT, model TEXT, requestCount INTEGER, uncachedInputTokens INTEGER, cachedInputTokens INTEGER, cacheWriteTokens INTEGER, totalInputTokens INTEGER, outputTokens INTEGER)")
                        db.execSQL("CREATE TABLE token_request_logs (requestId TEXT PRIMARY KEY, startedAtMs INTEGER, configId TEXT, provider TEXT, model TEXT, status TEXT, functionType TEXT, executionModel TEXT, firstTokenLatencyMs INTEGER, durationMs INTEGER, successfulAttempt INTEGER, observedAttemptNumbers TEXT, uncachedInputTokens INTEGER, cachedInputTokens INTEGER, cacheWriteTokens INTEGER, totalInputTokens INTEGER, outputTokens INTEGER)")
                        fact(db, 1, peakTime, "token-config", "perf-model")
                        fact(db, 2, offPeakTime, "count-config", "perf-model")
                        fact(db, 3, null, "", "perf-model", 5L)
                        fact(db, 4, peakTime, TokenUsageRepository.DETACHED_CONFIG_ID, "perf-model")
                        fact(db, 5, offPeakTime, "token-config", "perf-unpriced-nonexistent")
                        // 已发布旧数据缓存写入可空，必须沿用归一化口径。
                        db.execSQL("INSERT INTO token_usage_records VALUES (6,NULL,NULL,?,'','OPENAI','perf-model',1,100,20,NULL,NULL,10)", arrayOf(offPeakTime))
                        // 失败日志只有诊断用量，不得变成预算中的成功费用。
                        db.execSQL("INSERT INTO token_request_logs VALUES ('failed',?,'token-config','OPENAI','perf-model','FAILED','CHAT',NULL,NULL,40,NULL,'1',900,100,0,1000,200)", arrayOf(peakTime))
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
    }

    private fun fact(db: SupportSQLiteDatabase, id: Int, time: Long?, config: String, model: String, requests: Long = 1L) {
        db.execSQL(
            "INSERT INTO token_usage_records VALUES (?,NULL,NULL,?,?,'OPENAI',?,?,80,20,0,100,10)",
            arrayOf(id, time, config, model, requests),
        )
    }
}
