package com.ai.assistance.operit.data.stats

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.TokenRequestLogEntity
import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 运行真实 Repository 清空事务；数据库在内存，迁移偏好位于独立临时目录。 */
@RunWith(AndroidJUnit4::class)
class TokenUsageRepositoryEpochTest {
    @Test
    fun clearingRejectsOldRequestsAndPreservesPricesAndMigrationMarker() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "token-epoch-test-" + UUID.randomUUID())
        check(directory.mkdirs())
        val dataStoreJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dataStoreJob + Dispatchers.IO),
            produceFile = { File(directory, "isolated.preferences_pb") },
        )
        val preferences = TokenStatsPreferences(context, store)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            preferences.completeMigration(importedAtMs = 123456L, releasedUsdToCnyRate = 6.8)
            val repository = TokenUsageRepository(context, database, preferences)
            val price = TokenStatsModelEntity(
                configId = "config", provider = "OPENAI", model = "model",
                currency = "USD", inputPricePerMillion = 2.5, peakPricingEnabled = true,
                longContextPricingEnabled = true, longContextThreshold = 256000L,
            )
            database.tokenUsageDao().upsertStatsModel(price)
            database.tokenUsageDao().insertRecord(
                TokenUsageRecordEntity(
                    importKey = "legacy-cumulative:OPENAI:model", occurredAtMs = null,
                    configId = "", provider = "OPENAI", model = "model", requestCount = 5L,
                    totalInputTokens = 100L, outputTokens = 20L,
                )
            )
            val oldEpoch = repository.beginRequest()
            assertEquals(0L, oldEpoch)
            assertTrue(repository.recordRequest(log("before-clear"), fact("before-clear"), oldEpoch))
            assertEquals(2L, count(database, "token_usage_records"))
            assertEquals(1L, count(database, "token_request_logs"))

            repository.clearUsageRecords()
            val newEpoch = repository.beginRequest()
            assertEquals(oldEpoch + 1L, newEpoch)
            assertEquals(0L, count(database, "token_usage_records"))
            assertEquals(0L, count(database, "token_request_logs"))
            assertEquals(listOf(price), database.tokenUsageDao().getAllStatsModels())
            assertEquals(123456L, preferences.importedAtMs())
            assertEquals(6.8, preferences.loadRateWithEstimate().first, 0.0)

            assertFalse(repository.recordRequest(log("late-old"), fact("late-old"), oldEpoch))
            assertFalse(repository.recordRequest(log("late-cancelled").copy(status = TokenRequestLogEntity.CANCELLED), null, oldEpoch))
            assertEquals(0L, count(database, "token_usage_records"))
            assertEquals(0L, count(database, "token_request_logs"))
            assertTrue(repository.recordRequest(log("after-clear"), fact("after-clear"), newEpoch))
            assertFalse(repository.recordRequest(log("after-clear"), fact("after-clear"), newEpoch))
            assertEquals(1L, count(database, "token_usage_records"))
            assertEquals(1L, count(database, "token_request_logs"))

            repository.clearUsageRecords()
            val finalEpoch = repository.beginRequest()
            assertEquals(newEpoch + 1L, finalEpoch)
            assertEquals(0L, count(database, "token_usage_records"))
            assertEquals(0L, count(database, "token_request_logs"))
            assertFalse(repository.recordRequest(log("late-second"), fact("late-second"), newEpoch))
            assertTrue(repository.recordRequest(log("final"), fact("final"), finalEpoch))
            assertEquals(listOf(price), database.tokenUsageDao().getAllStatsModels())
            assertEquals(123456L, preferences.importedAtMs())
        } finally {
            database.close()
            dataStoreJob.cancelAndJoin()
            check(directory.deleteRecursively())
        }
    }

    private fun count(database: AppDatabase, table: String): Long =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM " + table).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun fact(id: String) = TokenUsageRecordEntity(
        requestId = id, occurredAtMs = 100L, configId = "config", provider = "OPENAI",
        model = "model", requestCount = 1L, totalInputTokens = 10L, outputTokens = 2L,
    )

    private fun log(id: String) = TokenRequestLogEntity(
        requestId = id, startedAtMs = 100L, endedAtMs = 120L, durationMs = 20L, firstTokenLatencyMs = 5L,
        configId = "config", provider = "OPENAI", model = "model", functionType = "CHAT",
        executionModel = null, status = TokenRequestLogEntity.SUCCESS, successfulAttempt = 1,
        observedAttemptNumbers = "1", usageAttempt = 1, uncachedInputTokens = null, cachedInputTokens = null,
        cacheWriteTokens = null, totalInputTokens = 10L, outputTokens = 2L, reasoningTokens = null,
        reasoningIncludedInOutput = true, cacheWriteSeparateBilling = false, usageSource = "test-api",
    )
}
