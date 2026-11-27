package com.ai.assistance.operit.data.stats

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.dao.TokenUsageDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.TokenRequestLogEntity
import com.ai.assistance.operit.data.model.TokenStatsRecordingStateEntity
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room owner for successful formal-inference usage and pricing. */
class TokenUsageRepository private constructor(
    context: Context,
    private val databaseProvider: () -> AppDatabase,
    private val statsPreferences: TokenStatsPreferences,
    alreadyInitialized: Boolean,
) {
    private constructor(context: Context) : this(
        context,
        { AppDatabase.getDatabase(context.applicationContext) },
        TokenStatsPreferences(context.applicationContext),
        false,
    )

    /** 隔离回归使用独立数据库及偏好；模拟已完成旧统计迁移后的真实读写。 */
    internal constructor(
        context: Context,
        database: AppDatabase,
        preferences: TokenStatsPreferences,
    ) : this(context, { database }, preferences, true)

    companion object {
        private const val TAG = "TokenUsageRepository"
        const val DETACHED_CONFIG_ID = "__operit_deleted_config__"
        @Volatile
        private var instance: TokenUsageRepository? = null
        private val databaseAccessMutex = Mutex()

        fun getInstance(context: Context): TokenUsageRepository =
            instance ?: synchronized(this) {
                instance ?: TokenUsageRepository(context.applicationContext).also { instance = it }
            }

        /** Prevent Room access while a restore replaces database files. */
        suspend fun <T> withDatabaseAccess(block: suspend () -> T): T =
            databaseAccessMutex.withLock { block() }

        suspend fun <T> withDatabaseRestore(block: suspend () -> T): T =
            withDatabaseAccess { block() }
    }

    private val appContext = context.applicationContext
    private val legacyDataSource by lazy { ApiPreferences.getInstance(appContext) }
    private val importMutex = Mutex()

    @Volatile
    private var initializationComplete = alreadyInitialized

    suspend fun ensureInitialized() {
        if (initializationComplete) return
        importMutex.withLock {
            if (initializationComplete) return
            if (statsPreferences.importedAtMs() == null) {
                val snapshot = legacyDataSource.readTokenStatsMigrationSnapshot()
                withDatabaseAccess {
                    val database = databaseProvider()
                    database.withTransaction {
                        val dao = database.tokenUsageDao()
                        dao.insertRecords(
                            snapshot.totals.map { total ->
                                TokenUsageRecordEntity(
                                    importKey = "legacy-cumulative:${total.provider}:${total.model}",
                                    occurredAtMs = null,
                                    configId = "",
                                    provider = total.provider,
                                    model = total.model,
                                    requestCount = total.requestCount,
                                    uncachedInputTokens =
                                        (total.inputTokens - total.cachedInputTokens).coerceAtLeast(0L),
                                    cachedInputTokens = total.cachedInputTokens,
                                    totalInputTokens = total.inputTokens,
                                    outputTokens = total.outputTokens,
                                )
                            }
                        )
                        snapshot.prices.forEach { price ->
                            val current = dao.getStatsModel("", price.provider, price.model)
                                ?: TokenStatsModelEntity("", price.provider, price.model)
                            dao.upsertStatsModel(
                                current.copy(
                                    billingMode = price.settings.billingMode?.name,
                                    currency = price.settings.currency?.name,
                                    inputPricePerMillion = price.settings.inputPricePerMillion,
                                    cachedInputPricePerMillion = price.settings.cachedInputPricePerMillion,
                                    cacheWritePricePerMillion = price.settings.cacheWritePricePerMillion,
                                    outputPricePerMillion = price.settings.outputPricePerMillion,
                                    pricePerRequest = price.settings.pricePerRequest,
                                )
                            )
                        }
                    }
                }
                statsPreferences.completeMigration(
                    importedAtMs = System.currentTimeMillis(),
                    releasedUsdToCnyRate = snapshot.usdToCnyRate,
                )
                AppLogger.i(TAG, "Imported ${snapshot.totals.size} cumulative totals and ${snapshot.prices.size} price settings")
            }
            legacyDataSource.clearMigratedTokenStatsData()
            initializationComplete = true
        }
    }

    internal suspend fun <T> withDao(block: suspend (TokenUsageDao) -> T): T {
        ensureInitialized()
        return withDatabaseAccess { block(databaseProvider().tokenUsageDao()) }
    }

    /** 查询层复用初始化与恢复互斥，不另开数据库连接。 */
    internal suspend fun <T> withDatabase(block: suspend (AppDatabase) -> T): T {
        ensureInitialized()
        return withDatabaseAccess { block(databaseProvider()) }
    }

    suspend fun beginRequest(): Long = withDatabase { database ->
        database.withTransaction {
            val dao = database.tokenRequestLogDao()
            dao.initializeState(TokenStatsRecordingStateEntity())
            dao.recordingEpoch()
        }
    }

    /** 请求日志与成功用量只提交一次；清空前开始的请求不再写回。 */
    suspend fun recordRequest(
        log: TokenRequestLogEntity,
        successFact: TokenUsageRecordEntity?,
        epoch: Long,
    ): Boolean = withDatabase { database ->
        require(successFact == null ||
            (log.status == TokenRequestLogEntity.SUCCESS && successFact.requestId == log.requestId))
        database.withTransaction {
            val dao = database.tokenRequestLogDao()
            dao.initializeState(TokenStatsRecordingStateEntity())
            if (dao.recordingEpoch() != epoch) return@withTransaction false
            if (dao.insertLog(log) == -1L) return@withTransaction false
            if (successFact != null) database.tokenUsageDao().insertRecord(successFact)
            true
        }
    }

    suspend fun record(record: TokenUsageRecordEntity) {
        withDao { dao -> dao.insertRecord(record) }
    }
    suspend fun detachUsageFromConfig(configId: String) {
        val trimmedId = configId.trim()
        if (trimmedId.isEmpty()) return
        ensureInitialized()
        withDatabaseAccess {
            val database = databaseProvider()
            database.withTransaction {
                val dao = database.tokenUsageDao()
                dao.detachRecordsFromConfig(
                    configId = trimmedId,
                    detachedConfigId = DETACHED_CONFIG_ID,
                )
                database.tokenRequestLogDao().detachFromConfig(trimmedId, DETACHED_CONFIG_ID)
                dao.deleteStatsModelsByConfigId(trimmedId)
            }
        }
    }

    /** Read all scopes: clearing a detail list must not depend on the current date/model filter. */
    suspend fun usedConfigurationIds(): List<String> = withDao { it.getUsedConfigurationIds() }

    suspend fun clearUsageRecords() {
        withDatabase { database ->
            database.withTransaction {
                val logDao = database.tokenRequestLogDao()
                logDao.initializeState(TokenStatsRecordingStateEntity())
                val nextEpoch = Math.addExact(logDao.recordingEpoch(), 1L)
                database.tokenUsageDao().clearUsageRecords()
                logDao.clearLogs()
                logDao.setRecordingEpoch(nextEpoch)
            }
        }
    }
}
