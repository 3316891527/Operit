package com.ai.assistance.operit.data.stats

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 直接验证生产查询的合并、去重和数据库分页，不复制另一套统计 SQL。 */
@RunWith(AndroidJUnit4::class)
class TokenStatsInsightsSqlTest {
    private fun database(): SQLiteDatabase = SQLiteDatabase.create(null).apply {
        execSQL("CREATE TABLE token_usage_records (id INTEGER PRIMARY KEY, requestId TEXT UNIQUE, importKey TEXT, occurredAtMs INTEGER, configId TEXT, provider TEXT, model TEXT, requestCount INTEGER, uncachedInputTokens INTEGER, cachedInputTokens INTEGER, cacheWriteTokens INTEGER, totalInputTokens INTEGER, outputTokens INTEGER)")
        execSQL("CREATE TABLE token_request_logs (requestId TEXT PRIMARY KEY, startedAtMs INTEGER, configId TEXT, provider TEXT, model TEXT, status TEXT, functionType TEXT, executionModel TEXT, firstTokenLatencyMs INTEGER, durationMs INTEGER, successfulAttempt INTEGER, observedAttemptNumbers TEXT, uncachedInputTokens INTEGER, cachedInputTokens INTEGER, cacheWriteTokens INTEGER, totalInputTokens INTEGER, outputTokens INTEGER)")
        execSQL("INSERT INTO token_request_logs VALUES ('r1',100,'c','OPENAI','m','SUCCESS','CHAT',NULL,10,30,1,'1',90,0,0,90,10)")
        execSQL("INSERT INTO token_usage_records VALUES (1,'r1',NULL,100,'c','OPENAI','m',1,90,0,0,90,10)")
        execSQL("INSERT INTO token_request_logs VALUES ('r2',200,'c','OPENAI','m','CANCELLED','CHAT',NULL,NULL,40,NULL,'1',NULL,NULL,NULL,NULL,NULL)")
        execSQL("INSERT INTO token_usage_records VALUES (2,NULL,NULL,300,'old','OPENAI','m',1,80,0,0,80,20)")
        execSQL("INSERT INTO token_usage_records VALUES (3,NULL,'legacy-cumulative:OPENAI:m',NULL,'','OPENAI','m',50,800,0,NULL,800,200)")
    }

    @Test fun successfulFactAndLogAreNotDoubleCountedAndCumulativeIsNotARequest() {
        database().use { db ->
            db.rawQuery(TokenStatsInsightsService.UNIFIED + " SELECT COUNT(*), SUM(requestCount), SUM(hasUsageFact), SUM(isLegacyAggregate) FROM unified", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4, cursor.getLong(0))
                assertEquals(53, cursor.getLong(1))
                assertEquals(3, cursor.getLong(2))
                assertEquals(1, cursor.getLong(3))
            }
            db.rawQuery(TokenStatsInsightsService.UNIFIED + " SELECT id FROM unified WHERE isLegacyAggregate=0 ORDER BY occurredAtMs DESC,id DESC LIMIT 1 OFFSET 1", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("r2", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test fun halfOpenTimeFilterAndUnknownOldStatusStayAccurate() {
        database().use { db ->
            val query = TokenStatsInsightsService.filtered(TokenStatsInsightsFilter(range = TokenStatsTimeRange(100, 300)), TokenStatsQueryParams())
            db.rawQuery(query.sql, query.args.map { it.toString() }.toTypedArray()).use { cursor ->
                assertEquals(2, cursor.count)
            }
            db.rawQuery(TokenStatsInsightsService.UNIFIED + " SELECT status,functionType,durationMs FROM unified WHERE id='history:2'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }

    @Test fun selectionsAreBoundAndAnEmptySelectionReturnsNoRows() {
        database().use { db ->
            val query = TokenStatsInsightsService.filtered(TokenStatsInsightsFilter(models = setOf("m' OR 1=1 --")), TokenStatsQueryParams())
            assertFalse(query.sql.contains("m' OR 1=1 --"))
            db.rawQuery(query.sql, query.args.map { it.toString() }.toTypedArray()).use { assertEquals(0, it.count) }
            val empty = TokenStatsInsightsService.filtered(TokenStatsInsightsFilter(models = emptySet()), TokenStatsQueryParams())
            db.rawQuery(empty.sql, emptyArray()).use { assertEquals(0, it.count) }
        }
    }
    @Test fun executionModelFilterUsesRecordedModelWithoutInventingFunctionType() {
        database().use { db ->
            db.execSQL("UPDATE token_request_logs SET functionType=NULL, executionModel=? WHERE requestId='r2'", arrayOf("worker-model"))
            val query = TokenStatsInsightsService.filtered(TokenStatsInsightsFilter(executionModels = setOf("worker-model")), TokenStatsQueryParams())
            assertFalse(query.sql.contains("worker-model"))
            db.rawQuery(query.sql, query.args.map { it.toString() }.toTypedArray()).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("r2", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("functionType")))
                assertFalse(cursor.moveToNext())
            }
            val empty = TokenStatsInsightsService.filtered(TokenStatsInsightsFilter(executionModels = emptySet()), TokenStatsQueryParams())
            db.rawQuery(empty.sql, emptyArray()).use { assertEquals(0, it.count) }
        }
    }
}
