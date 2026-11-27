package com.ai.assistance.operit.data.stats

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 直接调用生产派生分页，确认旧记录归一化后仍由数据库按真实 Token 值分页。 */
@RunWith(AndroidJUnit4::class)
class TokenStatsInsightsDerivedPageTest {
    @Test
    fun normalizedLegacy130TokensSortBeforeKnown100Tokens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE token_usage_records (id INTEGER PRIMARY KEY, requestId TEXT UNIQUE, importKey TEXT, occurredAtMs INTEGER, configId TEXT, provider TEXT, model TEXT, requestCount INTEGER, uncachedInputTokens INTEGER, cachedInputTokens INTEGER, cacheWriteTokens INTEGER, totalInputTokens INTEGER, outputTokens INTEGER)")
                        db.execSQL("CREATE TABLE token_request_logs (requestId TEXT PRIMARY KEY, startedAtMs INTEGER, configId TEXT, provider TEXT, model TEXT, status TEXT, functionType TEXT, executionModel TEXT, firstTokenLatencyMs INTEGER, durationMs INTEGER, successfulAttempt INTEGER, observedAttemptNumbers TEXT, uncachedInputTokens INTEGER, cachedInputTokens INTEGER, cacheWriteTokens INTEGER, totalInputTokens INTEGER, outputTokens INTEGER)")
                        db.execSQL("INSERT INTO token_usage_records VALUES (1,NULL,NULL,100,'','OPENAI','m',1,100,20,NULL,NULL,10)")
                        db.execSQL("INSERT INTO token_usage_records VALUES (2,NULL,NULL,200,'c','OPENAI','m',1,80,0,0,80,20)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.use {
            val db = helper.writableDatabase
            val filter = TokenStatsInsightsFilter()
            val params = TokenStatsQueryParams()
            val query = TokenStatsInsightsService.filtered(filter, params)
            val prices = TokenPriceSettingsSnapshot(emptyMap(), emptyMap())
            val first = TokenStatsInsightsService.derivedPage(db, query, prices, filter, params, TokenStatsRequestSort.TOKENS_DESC, 0, 1) {}
            val second = TokenStatsInsightsService.derivedPage(db, query, prices, filter, params, TokenStatsRequestSort.TOKENS_DESC, 1, 1) {}
            assertEquals(2L, first.totalRows)
            assertEquals("history:1", first.rows.single().id)
            assertEquals(130L, first.rows.single().totalTokens)
            assertEquals("history:2", second.rows.single().id)
            assertEquals(100L, second.rows.single().totalTokens)
        }
    }
}
