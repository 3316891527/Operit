package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal data class WorkspaceHistoryQuery(
    val chatId: String? = null,
    val directory: ChangeLocation? = null,
    val backend: String? = null,
    val from: Long? = null,
    val until: Long? = null,
    val search: String = "",
    val file: ChangeLocation? = null,
    val through: HistoryRecordCursor? = null,
)
internal data class HistoryRecordCursor(val timestamp: Long, val id: String)
internal data class WorkspaceHistorySummary(val first: FileChangeRecord, val latest: FileChangeRecord, val count: Int)
internal data class WorkspaceHistoryPage(val files: List<ReviewFile>, val total: Int, val offset: Int)
internal data class WorkspaceRecordPage(val records: List<FileChangeRecord>, val total: Int, val offset: Int)
internal data class WorkspaceRecordRemoval(val query: WorkspaceHistoryQuery, val count: Int)

/** 记录正文逐条编码；查询游标和返回页均有界，不复制整次执行或整段历史。调用方串行使用共用连接。 */
internal class WorkspaceHistoryIndex(private val database: TerminalIndexDatabase, private val storage: String) {
    companion object { const val PAGE_SIZE = 128 }
    private val json = Json { encodeDefaults = true }

    init {
        database.transaction {
            // 大 payload 随 rowid 追加，随机 UUID 只进入小型唯一索引，避免反复改写正文所在页。
            database.execute("CREATE TABLE IF NOT EXISTS operation_records (id TEXT NOT NULL UNIQUE,operation TEXT NOT NULL,chat TEXT NOT NULL,time INTEGER NOT NULL,backend TEXT NOT NULL,path TEXT NOT NULL,history_backend TEXT NOT NULL,history_path TEXT NOT NULL,path_search TEXT NOT NULL,history_search TEXT NOT NULL,before_blob TEXT,after_blob TEXT,after_kind TEXT NOT NULL,payload TEXT NOT NULL)")
            database.execute("CREATE INDEX IF NOT EXISTS operation_paths ON operation_records(history_backend,history_path,time,id)")
            database.execute("CREATE INDEX IF NOT EXISTS operation_chat_paths ON operation_records(chat,history_backend,history_path,time,id)")
            database.execute("CREATE INDEX IF NOT EXISTS operation_times ON operation_records(chat,time,id)")
            database.execute("CREATE INDEX IF NOT EXISTS operation_members ON operation_records(operation,history_backend,history_path)")
            database.execute("CREATE INDEX IF NOT EXISTS operation_original_paths ON operation_records(backend,path)")
            database.execute("CREATE INDEX IF NOT EXISTS operation_before_blobs ON operation_records(before_blob) WHERE before_blob IS NOT NULL")
            database.execute("CREATE INDEX IF NOT EXISTS operation_after_blobs ON operation_records(after_blob) WHERE after_blob IS NOT NULL")
            database.execute("CREATE TABLE IF NOT EXISTS operation_imports (source TEXT PRIMARY KEY) WITHOUT ROWID")
        }
    }

    fun put(record: FileChangeRecord, importing: Boolean = false) {
        val history = record.location.historyLocation(storage)
        database.execute("INSERT OR ${if (importing) "IGNORE" else "REPLACE"} INTO operation_records(id,operation,chat,time,backend,path,history_backend,history_path,path_search,history_search,before_blob,after_blob,after_kind,payload) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            listOf(record.id, record.operationId, record.chatId, record.timestamp, record.location.backend, record.location.path,
                history.backend, history.path, foldSearch(record.location.path), foldSearch(history.path), record.before.blob, record.after.blob, record.after.kind.name, json.encodeToString(record)))
    }

    fun record(id: String, query: WorkspaceHistoryQuery? = null): FileChangeRecord? {
        val filter = query?.let { filter(it, readable = true) } ?: ("1" to emptyList())
        return database.query("SELECT payload FROM operation_records r WHERE r.id=? AND " + filter.first, listOf(id) + filter.second).use {
            if (it.next()) decode(it, 0) else null
        }
    }

    fun operationFile(operation: String, location: ChangeLocation): FileChangeRecord? {
        val target = location.historyLocation(storage)
        return database.query("SELECT payload FROM operation_records WHERE operation=? AND history_backend=? AND history_path=? ORDER BY time,id LIMIT 1",
            listOf(operation, target.backend, target.path)).use { if (it.next()) decode(it, 0) else null }
    }

    fun mergeOperation(operationId: String, records: List<FileChangeRecord>, statistics: (FileChangeRecord) -> FileChangeRecord) {
        for (record in records) {
            val prior = operationFile(operationId, record.location)
            val merged = if (prior == null) record.copy(operationId = operationId) else record.copy(
                id = prior.id, operationId = operationId, timestamp = prior.timestamp, before = prior.before)
            val comparable = merged.before.kind !in setOf(SnapshotKind.TOO_LARGE, SnapshotKind.UNAVAILABLE)
            if (comparable && merged.before == merged.after) { if (prior != null) delete(prior.id) }
            else put(statistics(merged))
        }
    }

    fun operationPage(operation: String, offset: Int): List<FileChangeRecord> = database.query(
        "SELECT payload FROM operation_records WHERE operation=? ORDER BY time,id LIMIT ? OFFSET ?",
        listOf(operation, PAGE_SIZE.toString(), offset.toString()),
    ).use { rows -> buildList { while (rows.next()) add(decode(rows, 0)) } }

    fun delete(id: String) = database.execute("DELETE FROM operation_records WHERE id=?", listOf(id))
    fun deleteOperation(operation: String) = database.execute("DELETE FROM operation_records WHERE operation=?", listOf(operation))

    /** 首页产出入口只读取最新一条，不能因记录版本更新而重建完整对话清单。 */
    fun latestRecord(chat: String): FileChangeRecord? = database.query("SELECT payload FROM operation_records WHERE chat=? ORDER BY time DESC,id DESC LIMIT 1", listOf(chat)).use {
        if (it.next()) decode(it, 0) else null
    }

    fun latestTimestamp(chat: String): Long? = database.query("SELECT time FROM operation_records WHERE chat=? ORDER BY time DESC,id DESC LIMIT 1", listOf(chat)).use {
        if (it.next()) it.long(0) else null
    }

    fun count(query: WorkspaceHistoryQuery, readable: Boolean = false): Int {
        val filter = filter(query, readable = readable)
        return database.query("SELECT count(*) FROM operation_records r WHERE ${filter.first}", filter.second).use { it.next(); it.long(0).toInt() }
    }

    fun latestCursor(query: WorkspaceHistoryQuery): HistoryRecordCursor? {
        val filter = filter(query)
        return database.query("SELECT time,id FROM operation_records r WHERE ${filter.first} ORDER BY time DESC,id DESC LIMIT 1", filter.second).use {
            if (it.next()) HistoryRecordCursor(it.long(0), checkNotNull(it.string(1))) else null
        }
    }

    fun recordsPage(query: WorkspaceHistoryQuery, offset: Int = 0, ascending: Boolean = false, readable: Boolean = false): List<FileChangeRecord> {
        require(offset >= 0)
        val filter = filter(query, readable = readable)
        val order = if (ascending) "ASC" else "DESC"
        return database.query("SELECT payload FROM operation_records r WHERE ${filter.first} ORDER BY time $order,id $order LIMIT ? OFFSET ?",
            filter.second + listOf(PAGE_SIZE.toString(), offset.toString())).use { rows -> buildList { while (rows.next()) add(decode(rows, 0)) } }
    }

    fun summaryCount(query: WorkspaceHistoryQuery): Int {
        val filter = filter(query, readable = true)
        return database.query("SELECT count(*) FROM (SELECT 1 FROM operation_records r WHERE ${filter.first} GROUP BY history_backend,history_path)", filter.second).use {
            it.next(); it.long(0).toInt()
        }
    }

    fun summaries(query: WorkspaceHistoryQuery, offset: Int): List<WorkspaceHistorySummary> {
        require(offset >= 0)
        val filter = filter(query, readable = true)
        val first = filter(query, "f", readable = true)
        val last = filter(query, "l", readable = true)
        val sql = "SELECT count(*)," +
            "(SELECT payload FROM operation_records f WHERE f.history_backend=r.history_backend AND f.history_path=r.history_path AND ${first.first} ORDER BY f.time,f.id LIMIT 1)," +
            "(SELECT payload FROM operation_records l WHERE l.history_backend=r.history_backend AND l.history_path=r.history_path AND ${last.first} ORDER BY l.time DESC,l.id DESC LIMIT 1) " +
            "FROM operation_records r WHERE ${filter.first} GROUP BY r.history_backend,r.history_path ORDER BY r.history_path COLLATE BINARY,r.history_backend COLLATE BINARY LIMIT ? OFFSET ?"
        return database.query(sql, first.second + last.second + filter.second + listOf(PAGE_SIZE.toString(), offset.toString())).use { rows ->
            buildList { while (rows.next()) add(WorkspaceHistorySummary(decode(rows, 1), decode(rows, 2), rows.long(0).toInt())) }
        }
    }

    /** 基线核对只返回当前页的位置，执行对象不再持有整段对话的文件清单。 */
    fun trackedPage(chat: String, offset: Int): List<ChangeLocation> = database.query(
        "SELECT r.payload FROM operation_records r WHERE r.chat=? AND r.backend IN ('android','linux:local') AND r.after_kind IN ('TEXT','BINARY') " +
            "AND NOT EXISTS(SELECT 1 FROM operation_records n WHERE n.chat=r.chat AND n.history_backend=r.history_backend AND n.history_path=r.history_path AND (n.time>r.time OR (n.time=r.time AND n.id>r.id))) " +
            "ORDER BY r.history_path,r.history_backend LIMIT ? OFFSET ?", listOf(chat, PAGE_SIZE.toString(), offset.toString()),
    ).use { rows -> buildList { while (rows.next()) add(decode(rows, 0).location) } }

    fun referenced(hashes: Set<String>): Set<String> = buildSet {
        for (hash in hashes) {
            if (database.query("SELECT 1 FROM operation_records WHERE before_blob=? UNION ALL SELECT 1 FROM operation_records WHERE after_blob=? LIMIT 1", listOf(hash, hash)).use { it.next() }) add(hash)
        }
    }

    /** 单文件事务保证损坏或取消不会留下半份迁移；提交标记避免重启后复活已删除的记录。 */
    fun importLegacy(directory: File, checkCancelled: () -> Unit = {}) {
        if (!directory.exists()) return
        Files.newDirectoryStream(directory.toPath(), "*.json").use { paths ->
            for (path in paths) {
                checkCancelled()
                val source = path.fileName.toString()
                val imported = database.query("SELECT 1 FROM operation_imports WHERE source=?", listOf(source)).use { it.next() }
                if (!imported) database.transaction {
                    JsonReader(Files.newBufferedReader(path, Charsets.UTF_8)).use { reader ->
                        reader.beginArray()
                        while (reader.hasNext()) {
                            checkCancelled()
                            // 旧数组中每次只解码一个小记录，内容正文仍然只通过哈希引用。
                            val record = json.decodeFromString<FileChangeRecord>(JsonParser.parseReader(reader).toString())
                            require(record.operationId + ".json" == source) { "Legacy operation identity mismatch: $source" }
                            put(record, importing = true)
                        }
                        reader.endArray()
                        check(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing operation history content" }
                    }
                    checkCancelled()
                    database.execute("INSERT INTO operation_imports(source) VALUES(?)", listOf(source))
                }
                checkCancelled()
                Files.delete(path)
            }
        }
    }

    private fun decode(rows: TerminalIndexRows, column: Int): FileChangeRecord = json.decodeFromString(checkNotNull(rows.string(column)))

    private fun foldSearch(value: String): String = buildString(value.length) { value.forEach { append(it.uppercaseChar().lowercaseChar()) } }

    private fun filter(query: WorkspaceHistoryQuery, alias: String = "r", readable: Boolean = false): Pair<String, List<String>> {
        val parts = mutableListOf<String>()
        val values = mutableListOf<String>()
        fun clause(sql: String, vararg arguments: String) { parts += sql; values.addAll(arguments) }
        query.chatId?.let { clause("$alias.chat=?", it) }
        query.from?.let { clause("$alias.time>=?", it.toString()) }
        query.until?.let { clause("$alias.time<?", it.toString()) }
        query.through?.let { clause("($alias.time<? OR ($alias.time=? AND $alias.id<=?))", it.timestamp.toString(), it.timestamp.toString(), it.id) }
        val file = query.file?.historyLocation(storage)
        if (file != null) clause("$alias.history_backend=? AND $alias.history_path=?", file.backend, file.path)
        query.directory?.let { directory ->
            val history = directory.historyLocation(storage)
            fun subtree(column: String, path: String): String = if (path == "/") "$column>=? AND $column<?" else "($column=? OR ($column>=? AND $column<?))"
            fun arguments(path: String): List<String> = if (path == "/") listOf("/", "0") else listOf(path, "$path/", "${path}0")
            parts += "(($alias.history_backend=? AND ${subtree("$alias.history_path", history.path)}) OR ($alias.backend=? AND ${subtree("$alias.path", directory.path)}))"
            values += history.backend
            values += arguments(history.path)
            values += directory.backend
            values += arguments(directory.path)
        }
        if (query.directory == null && query.backend != null) clause("$alias.backend=?", query.backend)
        if (query.search.isNotBlank()) {
            val escaped = foldSearch(query.search.trim()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val directory = query.directory
            val column = if (directory == null) "$alias.history_search" else {
                val history = directory.historyLocation(storage)
                values += history.backend
                values += history.path
                values += if (history.path == "/") "/" else history.path + "/"
                values += if (history.path == "/") "0" else history.path + "0"
                values += history.path.trimEnd('/')
                values += directory.path.trimEnd('/')
                "CASE WHEN $alias.history_backend=? AND ($alias.history_path=? OR ($alias.history_path>=? AND $alias.history_path<?)) " +
                    "THEN substr($alias.history_search,length(?)+2) ELSE substr($alias.path_search,length(?)+2) END"
            }
            clause("$column LIKE ? ESCAPE '\\'", "%$escaped%")
        }
        if (readable) for (column in listOf("before_blob", "after_blob")) {
            parts += "NOT EXISTS(SELECT 1 FROM snapshot_objects o WHERE o.hash=$alias.$column AND o.live=0)"
        }
        return parts.joinToString(" AND ").ifEmpty { "1" } to values
    }
}
