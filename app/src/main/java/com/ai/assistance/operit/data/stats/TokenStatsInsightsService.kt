package com.ai.assistance.operit.data.stats

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlin.coroutines.coroutineContext

/** 读取时只保留当前行、分组累计与趋势桶；明细始终由数据库 LIMIT 返回。 */
object TokenStatsInsightsService {
    suspend fun overview(
        context: Context,
        filter: TokenStatsInsightsFilter,
        params: TokenStatsQueryParams,
        zone: ZoneId,
        granularity: TokenStatsInsightsGranularity = TokenStatsInsightsGranularity.AUTO,
        comparisonRange: TokenStatsTimeRange? = null,
    ): TokenStatsInsightsOverview = access(context) { db, prices, check ->
        val current = aggregate(db, prices, filter, params, zone, granularity, check)
        val comparison = comparisonRange?.let { range ->
            val validity = insightsComparisonValidity(filter.range, range)
            val other = if (validity == TokenStatsComparisonValidity.AVAILABLE) {
                val comparisonBounds = comparisonBucketBounds(
                    requireNotNull(filter.range), range, current.buckets.map { it.startMs to it.endMs },
                )
                aggregate(db, prices, filter.copy(range = range), params, zone, current.granularity, check, comparisonBounds)
            } else null
            TokenStatsInsightsComparison(validity, range, other?.totals, other?.buckets.orEmpty())
        }
        current.copy(comparison = comparison)
    }

    suspend fun requestPage(
        context: Context,
        filter: TokenStatsInsightsFilter,
        params: TokenStatsQueryParams,
        sort: TokenStatsRequestSort = TokenStatsRequestSort.NEWEST,
        pageIndex: Int = 0,
        pageSize: Int = 30,
    ): TokenStatsRequestPage {
        require(pageIndex >= 0 && pageSize in 1..100)
        return access(context) { db, prices, check ->
            val query = filtered(filter, params)
            // Token 排序使用读取后的归一化值，不能让旧缓存写入空值改变已知总量排序。
            val derived = sort == TokenStatsRequestSort.COST_DESC || sort == TokenStatsRequestSort.TOKENS_DESC || filter.peakPeriods != null
            if (derived) {
                derivedPage(db, query, prices, filter, params, sort, pageIndex, pageSize, check)
            } else {
                val history = scalar(db, query, "COALESCE(SUM(CASE WHEN isLegacyAggregate = 1 THEN requestCount ELSE 0 END), 0)")
                val count = scalar(db, query, "COUNT(*)", "isLegacyAggregate = 0")
                val sql = query.sql + " AND isLegacyAggregate = 0 ORDER BY " + orderBy(sort) + " LIMIT ? OFFSET ?"
                val rows = mutableListOf<TokenStatsRequestRow>()
                db.query(SimpleSQLiteQuery(sql, (query.args + listOf(pageSize, pageIndex.toLong() * pageSize)).toTypedArray())).use { cursor ->
                    while (cursor.moveToNext()) { check(); rows += read(cursor, prices, params) }
                }
                TokenStatsRequestPage(rows, count, pageIndex, pageSize, history)
            }
        }
    }

    /** 同一数据库快照内按时间倒序流式导出；回调必须同步消费当前行。 */
    suspend fun exportRequests(context: Context, filter: TokenStatsInsightsFilter, params: TokenStatsQueryParams, onRow: (TokenStatsRequestRow) -> Unit): Long = access(context) { db, prices, check ->
        val query = filtered(filter, params)
        var count = 0L
        db.query(SimpleSQLiteQuery(query.sql + " AND isLegacyAggregate = 0 ORDER BY occurredAtMs DESC, id DESC", query.args.toTypedArray())).use { cursor ->
            while (cursor.moveToNext()) {
                check()
                val row = read(cursor, prices, params)
                if (filter.peakPeriods != null && row.peakPeriod !in filter.peakPeriods) continue
                onRow(row)
                count++
            }
        }
        count
    }

    suspend fun requestDetail(context: Context, id: String, params: TokenStatsQueryParams): TokenStatsRequestRow? = access(context) { db, prices, _ ->
        db.query(SimpleSQLiteQuery(UNIFIED + " SELECT * FROM unified WHERE id = ? AND isLegacyAggregate = 0 LIMIT 1", arrayOf(id))).use { cursor ->
            if (cursor.moveToFirst()) read(cursor, prices, params) else null
        }
    }

    private suspend fun <T> access(context: Context, block: (SupportSQLiteDatabase, TokenPriceSettingsSnapshot, () -> Unit) -> T): T = withContext(Dispatchers.IO) {
        val jobContext = coroutineContext
        TokenUsageRepository.getInstance(context).withDatabase { database ->
            val prices = database.tokenUsageDao().getAllStatsModels().insightsPrices()
            val db = database.openHelper.writableDatabase
            // 在同一连接上完成读快照与临时排序表，退出必释放，不形成常驻历史缓存。
            db.beginTransactionNonExclusive()
            try {
                val result = block(db, prices) { jobContext.ensureActive() }
                db.setTransactionSuccessful()
                result
            } finally { db.endTransaction() }
        }
    }

    internal data class Query(val sql: String, val args: List<Any>)

    internal fun filtered(filter: TokenStatsInsightsFilter, params: TokenStatsQueryParams): Query {
        val clauses = mutableListOf("1 = 1")
        val args = mutableListOf<Any>()
        filter.range?.let { clauses += "occurredAtMs >= ? AND occurredAtMs < ?"; args += it.startMs; args += it.endMs }
        fun selection(column: String, values: Set<String>?) {
            if (values == null) return
            if (values.isEmpty()) { clauses += "0 = 1"; return }
            clauses += column + " IN (" + values.joinToString(",") { "?" } + ")"
            args.addAll(values)
        }
        selection("model", filter.models)
        selection("configId", filter.configIds)
        selection("functionType", filter.functionTypes)
        selection("executionModel", filter.executionModels)
        selection("status", filter.statuses)
        selection("(provider || ':' || model)", params.providerModels)
        if (filter.search.isNotBlank()) {
            clauses += "(instr(lower(model), lower(?)) > 0 OR instr(lower(configId), lower(?)) > 0 OR instr(lower(id), lower(?)) > 0)"
            repeat(3) { args += filter.search.trim() }
        }
        return Query(UNIFIED + " SELECT * FROM unified WHERE " + clauses.joinToString(" AND "), args)
    }

    private fun scalar(db: SupportSQLiteDatabase, query: Query, expression: String, where: String = "1 = 1"): Long =
        db.query(SimpleSQLiteQuery("SELECT $expression FROM (" + query.sql + ") WHERE $where", query.args.toTypedArray())).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    private fun orderBy(sort: TokenStatsRequestSort): String = when (sort) {
        TokenStatsRequestSort.NEWEST -> "occurredAtMs DESC, id DESC"
        TokenStatsRequestSort.OLDEST -> "occurredAtMs ASC, id ASC"
        TokenStatsRequestSort.TOKENS_DESC -> "totalTokens IS NULL, totalTokens DESC, occurredAtMs DESC, id DESC"
        TokenStatsRequestSort.DURATION_DESC -> "durationMs IS NULL, durationMs DESC, occurredAtMs DESC, id DESC"
        TokenStatsRequestSort.COST_DESC -> "costUnknown ASC, costAmount DESC, occurredAtMs DESC, id DESC"
    }

    internal fun derivedPage(db: SupportSQLiteDatabase, query: Query, prices: TokenPriceSettingsSnapshot, filter: TokenStatsInsightsFilter, params: TokenStatsQueryParams, sort: TokenStatsRequestSort, page: Int, size: Int, check: () -> Unit): TokenStatsRequestPage {
        db.execSQL("CREATE TEMP TABLE token_insights_sort (id TEXT PRIMARY KEY, occurredAtMs INTEGER, totalTokens INTEGER, durationMs INTEGER, costAmount REAL, costUnknown INTEGER, historical INTEGER, requests INTEGER)")
        try {
            val insert = db.compileStatement("INSERT INTO token_insights_sort VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
            insert.use { statement ->
                db.query(SimpleSQLiteQuery(query.sql, query.args.toTypedArray())).use { cursor ->
                    while (cursor.moveToNext()) {
                        check()
                        val row = read(cursor, prices, params)
                        if (filter.peakPeriods != null && row.peakPeriod !in filter.peakPeriods) continue
                        statement.clearBindings()
                        statement.bindString(1, row.id)
                        row.occurredAtMs?.let { statement.bindLong(2, it) }
                        row.totalTokens?.let { statement.bindLong(3, it) }
                        row.durationMs?.let { statement.bindLong(4, it) }
                        statement.bindDouble(5, row.cost.knownAmount)
                        statement.bindLong(6, if (row.cost.unknownContributionCount > 0L) 1L else 0L)
                        statement.bindLong(7, if (row.isLegacyAggregate) 1L else 0L)
                        statement.bindLong(8, row.requestCount)
                        statement.executeInsert()
                    }
                }
            }
            val counts = db.query("SELECT COUNT(CASE WHEN historical = 0 THEN 1 END), COALESCE(SUM(CASE WHEN historical = 1 THEN requests ELSE 0 END), 0) FROM token_insights_sort").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) to cursor.getLong(1) }
            val ids = mutableListOf<String>()
            db.query(SimpleSQLiteQuery("SELECT id FROM token_insights_sort WHERE historical = 0 ORDER BY " + orderBy(sort) + " LIMIT ? OFFSET ?", arrayOf(size, page.toLong() * size))).use { cursor -> while (cursor.moveToNext()) ids += cursor.getString(0) }
            val rows = ids.mapNotNull { id ->
                check()
                db.query(SimpleSQLiteQuery(UNIFIED + " SELECT * FROM unified WHERE id = ? LIMIT 1", arrayOf(id))).use { cursor -> if (cursor.moveToFirst()) read(cursor, prices, params) else null }
            }
            return TokenStatsRequestPage(rows, counts.first, page, size, counts.second)
        } finally { db.execSQL("DROP TABLE token_insights_sort") }
    }

    private fun aggregate(db: SupportSQLiteDatabase, prices: TokenPriceSettingsSnapshot, filter: TokenStatsInsightsFilter, params: TokenStatsQueryParams, zone: ZoneId, requested: TokenStatsInsightsGranularity, check: () -> Unit, alignedBounds: List<Pair<Long, Long>>? = null): TokenStatsInsightsOverview {
        val query = filtered(filter, params)
        val extent = db.query(SimpleSQLiteQuery("SELECT MIN(occurredAtMs), MAX(occurredAtMs) FROM (" + query.sql + ")", query.args.toTypedArray())).use { cursor ->
            cursor.moveToFirst()
            if (cursor.isNull(0)) null else TokenStatsTimeRange(cursor.getLong(0), TokenCostCalculator.saturatedAdd(cursor.getLong(1), 1L))
        }
        val range = filter.range ?: extent
        val granularity = if (requested != TokenStatsInsightsGranularity.AUTO) requested else when {
            range == null -> TokenStatsInsightsGranularity.DAILY
            range.durationMs <= 2 * TokenStatsTimeRanges.DAY_MS -> TokenStatsInsightsGranularity.HOURLY
            range.durationMs <= 90 * TokenStatsTimeRanges.DAY_MS -> TokenStatsInsightsGranularity.DAILY
            range.durationMs <= 366 * TokenStatsTimeRanges.DAY_MS -> TokenStatsInsightsGranularity.WEEKLY
            else -> TokenStatsInsightsGranularity.MONTHLY
        }
        val bounds = alignedBounds ?: bucketBounds(range, granularity, zone)
        val buckets = bounds.map { InsightsAccumulator(params) }
        val totals = InsightsAccumulator(params)
        val models = linkedMapOf<String, InsightsAccumulator>()
        val configs = linkedMapOf<Triple<String, String, String>, InsightsAccumulator>()
        val functions = linkedMapOf<String?, InsightsAccumulator>()
        val executions = linkedMapOf<String, InsightsAccumulator>()
        val periods = linkedMapOf<String, InsightsAccumulator>()
        val costs = DoubleArray(6)
        db.query(SimpleSQLiteQuery(query.sql, query.args.toTypedArray())).use { cursor ->
            while (cursor.moveToNext()) {
                check()
                val row = read(cursor, prices, params)
                if (filter.peakPeriods != null && row.peakPeriod !in filter.peakPeriods) continue
                totals.add(row)
                models.getOrPut(row.model) { InsightsAccumulator(params) }.add(row)
                configs.getOrPut(Triple(row.configId, row.provider, row.model)) { InsightsAccumulator(params) }.add(row)
                functions.getOrPut(row.functionType) { InsightsAccumulator(params) }.add(row)
                row.executionModel?.let { model -> executions.getOrPut(model) { InsightsAccumulator(params) }.add(row) }
                periods.getOrPut(row.peakPeriod.name) { InsightsAccumulator(params) }.add(row)
                row.occurredAtMs?.let { time ->
                    var low = 0
                    var high = bounds.lastIndex
                    while (low <= high) {
                        val mid = (low + high) ushr 1
                        val bound = bounds[mid]
                        when { time < bound.first -> high = mid - 1; time >= bound.second -> low = mid + 1; else -> { buckets[mid].add(row); break } }
                    }
                }
                if (row.hasUsageFact) {
                    val record = record(cursor)
                    val composition = insightsCostComposition(record, insightsPricing(record, prices), params)
                    listOf(composition.uncachedInput, composition.cachedInput, composition.cacheWrite, composition.output, composition.perRequest, composition.unallocatedInput).forEachIndexed { index, value -> costs[index] += value }
                }
            }
        }
        return TokenStatsInsightsOverview(totals.result(), TokenStatsInsightsCostComposition(costs[0], costs[1], costs[2], costs[3], costs[4], costs[5]),
            models.map { TokenStatsInsightsBreakdown(it.key, it.value.result()) }.sortedByDescending { it.totals.cost.knownAmount },
            configs.map { (key, value) -> TokenStatsInsightsConfiguration(key.first, key.second, key.third, value.result()) }.sortedByDescending { it.totals.cost.knownAmount },
            functions.map { TokenStatsInsightsBreakdown(it.key, it.value.result()) }, periods.map { TokenStatsInsightsBreakdown(it.key, it.value.result()) },
            bounds.mapIndexed { index, bound -> TokenStatsInsightsBucket(bound.first, bound.second, buckets[index].result()) }, granularity, null,
            executionModels = executions.map { TokenStatsInsightsBreakdown(it.key, it.value.result()) }.sortedByDescending { it.totals.requests })
    }

    /** 比较周期逐桶平移当前覆盖区间，保持真实时长，不按日历边界重新分桶。 */
    internal fun comparisonBucketBounds(
        currentRange: TokenStatsTimeRange,
        comparisonRange: TokenStatsTimeRange,
        currentBounds: List<Pair<Long, Long>>,
    ): List<Pair<Long, Long>> {
        require(currentRange.durationMs == comparisonRange.durationMs)
        return currentBounds.map { (start, end) ->
            require(start >= currentRange.startMs && end <= currentRange.endMs && end > start)
            Math.addExact(comparisonRange.startMs, Math.subtractExact(start, currentRange.startMs)) to
                Math.addExact(comparisonRange.startMs, Math.subtractExact(end, currentRange.startMs))
        }
    }

    internal fun bucketBounds(range: TokenStatsTimeRange?, granularity: TokenStatsInsightsGranularity, zone: ZoneId): List<Pair<Long, Long>> {
        if (range == null) return emptyList()
        require(granularity != TokenStatsInsightsGranularity.AUTO)
        val local = Instant.ofEpochMilli(range.startMs).atZone(zone)
        var current = when (granularity) {
            TokenStatsInsightsGranularity.HOURLY -> local.withMinute(0).withSecond(0).withNano(0)
            TokenStatsInsightsGranularity.DAILY -> local.toLocalDate().atStartOfDay(zone)
            TokenStatsInsightsGranularity.WEEKLY -> local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone)
            TokenStatsInsightsGranularity.MONTHLY -> local.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
            TokenStatsInsightsGranularity.AUTO -> error("Explicit granularity required")
        }
        val result = mutableListOf<Pair<Long, Long>>()
        while (current.toInstant().toEpochMilli() < range.endMs) {
            val next = when (granularity) {
                TokenStatsInsightsGranularity.HOURLY -> current.plusHours(1)
                TokenStatsInsightsGranularity.DAILY -> current.plusDays(1)
                TokenStatsInsightsGranularity.WEEKLY -> current.plusWeeks(1)
                TokenStatsInsightsGranularity.MONTHLY -> current.plusMonths(1)
                TokenStatsInsightsGranularity.AUTO -> error("Explicit granularity required")
            }
            require(result.size < 4096) { "Too many trend buckets; choose a coarser granularity" }
            result += maxOf(range.startMs, current.toInstant().toEpochMilli()) to minOf(range.endMs, next.toInstant().toEpochMilli())
            current = next
        }
        return result
    }

    private fun Cursor.text(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.number(name: String): Long? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }

    private fun record(cursor: Cursor): TokenUsageRecordEntity {
        val raw = TokenUsageRecordEntity(
        occurredAtMs = cursor.number("occurredAtMs"), configId = requireNotNull(cursor.text("configId")), provider = requireNotNull(cursor.text("provider")), model = requireNotNull(cursor.text("model")), requestCount = requireNotNull(cursor.number("requestCount")),
        uncachedInputTokens = cursor.number("uncachedInputTokens"), cachedInputTokens = cursor.number("cachedInputTokens"), cacheWriteTokens = cursor.number("cacheWriteTokens"), totalInputTokens = cursor.number("totalInputTokens"), outputTokens = cursor.number("outputTokens"),
    )
        return if (cursor.number("hasUsageFact") == 1L) normalizeLegacyCacheWriteUsage(raw) else raw
    }

    private fun read(cursor: Cursor, prices: TokenPriceSettingsSnapshot, params: TokenStatsQueryParams): TokenStatsRequestRow {
        val fact = cursor.number("hasUsageFact") == 1L
        val record = record(cursor)
        val pricing = insightsPricing(record, prices)
        val totalInput = record.totalInputTokens?.takeIf { it >= 0L } ?: insightsKnownSum(record.uncachedInputTokens, record.cachedInputTokens, record.cacheWriteTokens)
        val cost = if (fact) insightsCost(record, pricing, params) else TokenStatsCostSummary(params.targetCurrency, 0.0, record.requestCount, record.requestCount, params.manualRate, emptyMap())
        return TokenStatsRequestRow(
            id = requireNotNull(cursor.text("id")), requestId = cursor.text("requestId"), occurredAtMs = record.occurredAtMs, configId = record.configId, provider = record.provider, model = record.model, requestCount = record.requestCount, hasUsageFact = fact, isLegacyAggregate = cursor.number("isLegacyAggregate") == 1L,
            status = cursor.text("status"), functionType = cursor.text("functionType"), executionModel = cursor.text("executionModel"), firstTokenLatencyMs = cursor.number("firstTokenLatencyMs"), durationMs = cursor.number("durationMs"), successfulAttempt = cursor.number("successfulAttempt")?.toInt(), observedAttemptNumbers = cursor.text("observedAttemptNumbers"),
            uncachedInputTokens = record.uncachedInputTokens, cachedInputTokens = record.cachedInputTokens, cacheWriteTokens = record.cacheWriteTokens, totalInputTokens = totalInput, outputTokens = record.outputTokens, totalTokens = insightsKnownSum(totalInput, record.outputTokens), cost = cost, peakPeriod = insightsPeak(record, pricing), longContext = if (fact) insightsLongContext(record, pricing) else null,
        )
    }

    internal val UNIFIED: String = """
        WITH raw AS (
            SELECT l.requestId AS id, l.requestId,
                CASE WHEN f.id IS NOT NULL THEN f.occurredAtMs ELSE l.startedAtMs END AS occurredAtMs,
                CASE WHEN f.id IS NOT NULL THEN f.configId ELSE l.configId END AS configId,
                CASE WHEN f.id IS NOT NULL THEN f.provider ELSE l.provider END AS provider,
                CASE WHEN f.id IS NOT NULL THEN f.model ELSE l.model END AS model,
                COALESCE(f.requestCount, 1) AS requestCount,
                CASE WHEN f.id IS NOT NULL THEN 1 ELSE 0 END AS hasUsageFact,
                CASE WHEN f.id IS NOT NULL AND (f.importKey IS NOT NULL OR f.requestCount != 1 OR f.occurredAtMs IS NULL) THEN 1 ELSE 0 END AS isLegacyAggregate,
                l.status, l.functionType, l.executionModel, l.firstTokenLatencyMs, l.durationMs, l.successfulAttempt, l.observedAttemptNumbers,
                CASE WHEN f.id IS NOT NULL THEN f.uncachedInputTokens ELSE l.uncachedInputTokens END AS uncachedInputTokens,
                CASE WHEN f.id IS NOT NULL THEN f.cachedInputTokens ELSE l.cachedInputTokens END AS cachedInputTokens,
                CASE WHEN f.id IS NOT NULL THEN f.cacheWriteTokens ELSE l.cacheWriteTokens END AS cacheWriteTokens,
                CASE WHEN f.id IS NOT NULL THEN f.totalInputTokens ELSE l.totalInputTokens END AS totalInputTokens,
                CASE WHEN f.id IS NOT NULL THEN f.outputTokens ELSE l.outputTokens END AS outputTokens
            FROM token_request_logs l LEFT JOIN token_usage_records f ON f.requestId = l.requestId
            UNION ALL
            SELECT 'history:' || f.id, f.requestId, f.occurredAtMs, f.configId, f.provider, f.model, f.requestCount, 1,
                CASE WHEN f.importKey IS NOT NULL OR f.requestCount != 1 OR f.occurredAtMs IS NULL THEN 1 ELSE 0 END,
                NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                f.uncachedInputTokens, f.cachedInputTokens, f.cacheWriteTokens, f.totalInputTokens, f.outputTokens
            FROM token_usage_records f WHERE NOT EXISTS (SELECT 1 FROM token_request_logs l WHERE l.requestId = f.requestId)
        ), unified AS (
            SELECT raw.*,
                CASE WHEN outputTokens IS NULL THEN NULL
                    WHEN totalInputTokens IS NOT NULL THEN totalInputTokens + outputTokens
                    WHEN uncachedInputTokens IS NOT NULL AND cachedInputTokens IS NOT NULL AND cacheWriteTokens IS NOT NULL
                    THEN uncachedInputTokens + cachedInputTokens + cacheWriteTokens + outputTokens ELSE NULL END AS totalTokens
            FROM raw
        )
    """.trimIndent()
}
