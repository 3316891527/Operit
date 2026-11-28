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
        db: SupportSQLiteDatabase, id: String?, output: Long?, input: Long = 0L,
        count: Long = 1L, config: String = "rated",
    ) = db.execSQL(
        "INSERT INTO token_usage_records VALUES (NULL, ?, NULL, ?, ?, 'OPENAI', 'quality-synthetic', ?, ?, 0, 0, ?, ?)",
        arrayOf(id, peak, config, count, input, input, output),
    )
}