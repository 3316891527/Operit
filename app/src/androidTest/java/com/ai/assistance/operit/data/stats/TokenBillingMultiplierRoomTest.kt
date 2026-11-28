package com.ai.assistance.operit.data.stats

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.db.AppDatabase
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
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenBillingMultiplierRoomTest {
    @Test
    fun migrationPreservesPricesAndUsageAndSupportsSavingAndResettingMultiplier() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "token-billing-test-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, name)
        check(directory.mkdirs())
        val dataStoreJob = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dataStoreJob + Dispatchers.IO),
                produceFile = { File(directory, "isolated.preferences_pb") },
            )
            val preferences = TokenStatsPreferences(context, store)
            val legacyPrice = TokenStatsModelEntity(
                configId = "", provider = "OPENAI", model = "test-model",
                billingMode = BillingMode.TOKEN.name, currency = PricingCurrency.USD.name,
                inputPricePerMillion = 2.0, cachedInputPricePerMillion = 0.1,
                outputPricePerMillion = 10.0, peakPricingEnabled = true,
                peakInputMultiplier = 1.5, longContextPricingEnabled = true, longContextThreshold = 128_000L,
            )
            val usage = TokenUsageRecordEntity(
                occurredAtMs = 123L, configId = "config", provider = "OPENAI", model = "test-model",
                requestCount = 1L, totalInputTokens = 30L, outputTokens = 10L,
            )
            val fixture = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                fixture.tokenUsageDao().insertRecord(usage)
                val sqlite = fixture.openHelper.writableDatabase
                sqlite.beginTransaction()
                try {
                    // 31 与 32 仅此表结构不同；重建旧表后由 Room 实际执行并校验升级。
                    sqlite.execSQL("DROP TABLE token_stats_models")
                    sqlite.execSQL(
                        """
                        CREATE TABLE token_stats_models (
                            configId TEXT NOT NULL, provider TEXT NOT NULL, model TEXT NOT NULL,
                            billingMode TEXT, currency TEXT, inputPricePerMillion REAL,
                            cachedInputPricePerMillion REAL, cacheWritePricePerMillion REAL,
                            outputPricePerMillion REAL, pricePerRequest REAL,
                            peakPricingEnabled INTEGER, peakScheduleJson TEXT,
                            peakInputMultiplier REAL, peakCachedInputMultiplier REAL,
                            peakCacheWriteMultiplier REAL, peakOutputMultiplier REAL,
                            longContextPricingEnabled INTEGER, longContextThreshold INTEGER,
                            longContextInputMultiplier REAL, longContextCachedInputMultiplier REAL,
                            longContextCacheWriteMultiplier REAL, longContextOutputMultiplier REAL,
                            weekendOffPeakPricingEnabled INTEGER, holidayOffPeakPricingEnabled INTEGER,
                            PRIMARY KEY(configId, provider, model)
                        )
                        """.trimIndent(),
                    )
                    sqlite.execSQL(
                        """
                        INSERT INTO token_stats_models (
                            configId, provider, model, billingMode, currency,
                            inputPricePerMillion, cachedInputPricePerMillion, outputPricePerMillion,
                            peakPricingEnabled, peakInputMultiplier, longContextPricingEnabled, longContextThreshold
                        ) VALUES ('', 'OPENAI', 'test-model', 'TOKEN', 'USD', 2.0, 0.1, 10.0, 1, 1.5, 1, 128000)
                        """.trimIndent(),
                    )
                    sqlite.execSQL("DROP TABLE room_master_table")
                    sqlite.version = 31
                    sqlite.setTransactionSuccessful()
                } finally {
                    sqlite.endTransaction()
                }
            } finally {
                fixture.close()
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_31_32).build()
            try {
                val dao = migrated.tokenUsageDao()
                val manager = TokenStatsSettingsManager(TokenUsageRepository(context, migrated, preferences))
                assertEquals(32, migrated.openHelper.readableDatabase.version)
                assertEquals(legacyPrice, dao.getStatsModel("", "OPENAI", "test-model"))
                assertEquals(1.0, manager.allPriceSettings().single().billingMultiplier, 0.0)

                val draft = TokenStatsPriceDraft(
                    scope = TokenStatsPriceScope.CONFIG, provider = "OPENAI", model = "test-model",
                    configId = "config", billingMode = BillingMode.COUNT, currency = PricingCurrency.USD,
                    pricePerRequest = 0.25, billingMultiplier = 0.1,
                )
                manager.savePrice(draft)
                val stored = checkNotNull(dao.getStatsModel("config", "OPENAI", "test-model"))
                val loaded = manager.allPriceSettings().single { it.configId == "config" }
                assertEquals(0.1, stored.billingMultiplier, 0.0)
                assertEquals(0.1, loaded.billingMultiplier, 0.0)
                assertEquals(0.1, stored.toModelPriceSettings().billingMultiplier, 0.0)
                assertEquals(0.25, checkNotNull(loaded.pricePerRequest), 0.0)

                try {
                    manager.savePrice(draft.copy(billingMultiplier = Double.NaN))
                    fail("Expected invalid multiplier to be rejected")
                } catch (failure: IllegalArgumentException) {
                    assertEquals("billingMultiplier must be non-negative and finite, got NaN", failure.message)
                }
                assertEquals(stored, dao.getStatsModel("config", "OPENAI", "test-model"))

                val multiplierOnly = legacyPrice.copy(
                    configId = "multiplier-only", billingMode = null, currency = null,
                    inputPricePerMillion = null, cachedInputPricePerMillion = null, outputPricePerMillion = null,
                    peakPricingEnabled = null, peakInputMultiplier = null,
                    longContextPricingEnabled = null, longContextThreshold = null, billingMultiplier = 0.2,
                )
                dao.upsertStatsModel(multiplierOnly)
                manager.resetConfigPrice("OPENAI:test-model", "config")
                assertNull(dao.getStatsModel("config", "OPENAI", "test-model"))
                assertEquals(multiplierOnly, dao.getStatsModel("multiplier-only", "OPENAI", "test-model"))
                assertEquals(0.2, manager.allPriceSettings().single { it.configId == "multiplier-only" }.billingMultiplier, 0.0)
                assertEquals(legacyPrice, dao.getStatsModel("", "OPENAI", "test-model"))
                val preservedUsage = dao.aggregateModelsForLifetime(emptyList(), allModels = true).single()
                assertEquals(1L, preservedUsage.requests)
                assertEquals(30L, preservedUsage.totalInputTokens)
                assertEquals(10L, preservedUsage.outputTokens)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
            dataStoreJob.cancelAndJoin()
            check(directory.deleteRecursively())
        }
    }
}
