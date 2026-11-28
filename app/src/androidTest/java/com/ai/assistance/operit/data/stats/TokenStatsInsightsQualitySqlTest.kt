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
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenStatsInsightsQualitySqlTest {
    private val peak = Instant.parse("2026-11-23T02:00:00Z").toEpochMilli()
    private val prices = TokenPriceSettingsSnapshot(
        mapOf("OPENAI:quality-synthetic" to ModelPriceSettings(
            billingMode = BillingMode.TOKEN, currency = PricingCurrency.USD,
            inputPricePerMillion = 1_000_000.0, cachedInputPricePerMillion = 0.0,
            cacheWritePricePerMillion = 0.0, outputPricePerMillion = 1_000_000.0,
            peakPricingEnabled = true, peakSchedule = listOf(TokenPeakTimeRange(9 * 60, 18 * 60)),
            weekendOffPeakPricingEnabled = false, holidayOffPeakPricingEnabled = false,
        )),
        emptyMap(),
    )
    private val params = TokenStatsQueryParams(PricingCurrency.CNY, manualRate = 2.0)

    @Test
    fun overviewUsesFullFiltersAndIndependentSamples() = withDatabase { db ->
        // 共 20 个有效耗时样本；P95 既不是最大值，也不是去重后的分位数。
        for (i in 1..17) log(db, "fail-$i", duration = i * 10L)
        log(db, "a", first = 100, duration = 900, status = "SUCCESS")
        fact(db, "a", output = 8)
        log(db, "b", first = 400, duration = 900, status = "SUCCESS")
        fact(db, "b", output = 0)
        log(db, "outlier", duration = 10_000)
        log(db, "no-duration", first = 0, duration = null)
        log(db, "invalid", first = -1, duration = -1)
        log(db, "partial-cost", status = "SUCCESS", duration = null)
        fact(db, "partial-cost", output = null, input = 3)
        log(db, "other-scope", time = peak + 10L * 3_600_000L, duration = 99_000)

        val filter = TokenStatsInsightsFilter(peakPeriods = setOf(TokenStatsPeakPeriod.PEAK))
        val page = TokenStatsInsightsService.derivedPage(
            db, TokenStatsInsightsService.filtered(filter, params), prices, filter, params,
            TokenStatsRequestSort.DURATION_DESC, 0, 1, {},
        )
        assertEquals("outlier", page.rows.single().id)
        assertEquals(23L, page.totalRows)
        val quality = quality(db, filter)
        assertEquals(3L, quality.firstTokenLatencySamples)
        assertEquals(500.0 / 3.0, requireNotNull(quality.averageFirstTokenLatencyMs), 1e-9)
        assertEquals(20L, quality.durationSamples)
        assertEquals(900L, requireNotNull(quality.p95DurationMs))
        assertEquals(2L, quality.pricedRequestSamples)
        assertEquals(8.0, requireNotNull(quality.averagePricedRequestCost), 1e-9)
        assertEquals(2L, quality.successfulTokensPerSecondSamples)
        assertEquals(5.0, requireNotNull(quality.averageSuccessfulTokensPerSecond), 1e-9)

        val single = quality(db, filter.copy(search = "outlier"))
        assertEquals(1L, single.durationSamples)
        assertEquals(10_000L, requireNotNull(single.p95DurationMs))
        assertEquals(TokenStatsInsightsQuality(), quality(db, filter.copy(statuses = emptySet())))
    }

    @Test
    fun ratioCoverageUsesFullFilteredOverviewAndOnlyCompleteSuccessfulUsageSplits() = withDatabase { db ->
        log(db, "complete-a", duration = 1, status = "SUCCESS")
        fact(db, "complete-a", output = 1L, input = 30L, cached = 60L, write = 10L, totalInput = 100L)
        log(db, "complete-b", duration = 2, status = "SUCCESS")
        fact(db, "complete-b", output = 1L, input = 15L, cached = 0L, write = 5L, totalInput = 20L)
        // 两条缺失字段不同，即使两条都记录了总输入，也不能拼出一组完整拆分。
        log(db, "missing-cached", duration = 3, status = "SUCCESS")
        fact(db, "missing-cached", output = 1L, input = 500L, cached = null, write = 60L, totalInput = 700L)
        log(db, "missing-uncached", duration = 4, status = "SUCCESS")
        fact(db, "missing-uncached", output = 1L, input = null, cached = 500L, write = 0L, totalInput = 600L)
        log(db, "negative-write", duration = 5, status = "SUCCESS")
        fact(db, "negative-write", output = 1L, input = 20L, cached = 500L, write = -1L, totalInput = 520L)
        log(db, "success-without-fact", duration = 6, status = "SUCCESS")
        log(db, "failed", duration = 7)
        log(db, "cancelled", duration = 8, status = "CANCELLED")
        log(db, "completed", duration = 9, status = "COMPLETED")
        log(db, "unrecognized", duration = 10, status = "FUTURE_STATUS")
        // 完整的诊断用量也不能代替账本事实。
        db.execSQL("UPDATE token_request_logs SET uncachedInputTokens = 10, cachedInputTokens = 1000, cacheWriteTokens = 0")
        fact(db, null, output = 1L, count = 3L, input = 80L, cached = 40L, write = 10L, totalInput = 130L)
        fact(db, null, output = 1L, count = 4L, input = 1_000L, write = null)
        log(db, "outside", duration = 11, status = "SUCCESS", time = peak + 1L)
        fact(db, "outside", output = 1L, cached = 20_000L, totalInput = 20_000L, time = peak + 1L)

        val filter = TokenStatsInsightsFilter(range = TokenStatsTimeRange(peak, peak + 1L))
        val page = TokenStatsInsightsService.derivedPage(
            db, TokenStatsInsightsService.filtered(filter, params), prices, filter, params,
            TokenStatsRequestSort.DURATION_DESC, 0, 1, {},
        )
        assertEquals("unrecognized", page.rows.single().id)
        assertEquals(10L, page.totalRows)
        assertEquals(7L, page.historicalAggregateRequests)

        val totals = TokenStatsInsightsService.aggregate(
            db, prices, filter, params, ZoneId.of("UTC"), TokenStatsInsightsGranularity.DAILY, {},
        ).totals
        assertEquals(17L, totals.requests)
        val statuses = tokenStatsSuccessCoverage(totals)
        assertEquals(6L, statuses.successfulRequests)
        assertEquals(9L, statuses.knownStatusRequests)
        assertEquals(8L, statuses.unknownStatusRequests)
        assertEquals(6.0 / 9.0, requireNotNull(statuses.ratio), 1e-9)
        val cache = requireNotNull(totals.cacheReadCoverage)
        assertEquals(5L, cache.completeRequests)
        assertEquals(7L, cache.missingRequests)
        assertEquals(100.0, cache.cachedInputTokens, 0.0)
        assertEquals(250.0, cache.totalInputTokens, 0.0)
        assertEquals(0.40, requireNotNull(cache.ratio), 1e-9)
    }

    @Test
    fun historicalCostIsWeightedByRequestsAndDetachedZeroIsExcluded() = withDatabase { db ->
        log(db, "individual", status = "SUCCESS", duration = null)
        fact(db, "individual", output = 8)
        fact(db, null, output = 10, count = 2L)
        fact(db, null, output = 100, config = TokenUsageRepository.DETACHED_CONFIG_ID)
        val quality = quality(db)
        assertEquals(3L, quality.pricedRequestSamples)
        assertEquals(12.0, requireNotNull(quality.averagePricedRequestCost), 1e-9)
        assertEquals(0L, quality.successfulTokensPerSecondSamples)
    }

    @Test
    fun cancellationDuringScanOrPercentileDropsTempTable() = withDatabase { db ->
        log(db, "one", duration = 11)
        log(db, "two", duration = 22)
        // 第 3 次检查在请求扫描内，第 5 次在有序耗时频数扫描内。
        for (cancelAt in listOf(3, 5)) {
            var checks = 0
            try {
                quality(db) { if (++checks == cancelAt) throw CancellationException("合成取消") }
                fail("未传播取消")
            } catch (_: CancellationException) {
                assertEquals(cancelAt, checks)
            }
            db.query("SELECT name FROM sqlite_temp_master WHERE name = 'token_insights_quality_duration'").use {
                assertFalse(it.moveToFirst())
            }
            assertEquals(22L, requireNotNull(quality(db).p95DurationMs))
        }
    }

    private fun quality(
        db: SupportSQLiteDatabase,
        filter: TokenStatsInsightsFilter = TokenStatsInsightsFilter(),
        check: () -> Unit = {},
    ): TokenStatsInsightsQuality = TokenStatsInsightsService.aggregate(
        db, prices, filter, params, ZoneId.of("UTC"), TokenStatsInsightsGranularity.DAILY, check,
    ).quality

    private fun withDatabase(block: (SupportSQLiteDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE token_usage_records (id INTEGER PRIMARY KEY, requestId TEXT UNIQUE, importKey TEXT, occurredAtMs INTEGER, configId TEXT, provider TEXT, model TEXT, requestCount INTEGER, uncachedInputTokens INTEGER, cachedInputTokens INTEGER, cacheWriteTokens INTEGER, totalInputTokens INTEGER, outputTokens INTEGER)")
                        db.execSQL("CREATE TABLE token_request_logs (requestId TEXT PRIMARY KEY, startedAtMs INTEGER, configId TEXT, provider TEXT, model TEXT, status TEXT, functionType TEXT, executionModel TEXT, firstTokenLatencyMs INTEGER, durationMs INTEGER, successfulAttempt INTEGER, observedAttemptNumbers TEXT, uncachedInputTokens INTEGER, cachedInputTokens INTEGER, cacheWriteTokens INTEGER, totalInputTokens INTEGER, outputTokens INTEGER)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        ).use { helper ->
            val db = helper.writableDatabase
            db.beginTransactionNonExclusive()
            try { block(db) } finally { db.endTransaction() }
        }
    }

    private fun log(
        db: SupportSQLiteDatabase, id: String, first: Long? = null, duration: Long?,
        status: String = "FAILED", time: Long = peak,
    ) = db.execSQL(
        "INSERT INTO token_request_logs VALUES (?, ?, 'rated', 'OPENAI', 'quality-synthetic', ?, 'CHAT', NULL, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
        arrayOf(id, time, status, first, duration),
    )

    private fun fact(
        db: SupportSQLiteDatabase, id: String?, output: Long?, input: Long? = 0L,
        count: Long = 1L, config: String = "rated", cached: Long? = 0L, write: Long? = 0L,
        totalInput: Long? = input, time: Long = peak,
    ) = db.execSQL(
        "INSERT INTO token_usage_records VALUES (NULL, ?, NULL, ?, ?, 'OPENAI', 'quality-synthetic', ?, ?, ?, ?, ?, ?)",
        arrayOf(id, time, config, count, input, cached, write, totalInput, output),
    )
}