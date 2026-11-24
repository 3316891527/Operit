package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.Closeable

internal interface TerminalObservationReader {
    fun file(path: String): TerminalObservedFile?
    fun isUnreadable(path: String): Boolean
}

internal interface TerminalObservationWriter {
    fun put(path: String, file: TerminalObservedFile)
    fun unreadable(path: String)
    fun preserveSubtree(path: String)
}

internal data class TerminalPendingChange(val origin: TerminalChangeOrigin?, val createdInPlace: Boolean = false)

internal data class TerminalObservedFile(val stamp: String, val snapshot: ChangeSnapshot)

internal fun TerminalObservationReader.snapshot(path: String): ChangeSnapshot = file(path)?.snapshot
    ?: if (isUnreadable(path)) ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "Previous observation could not read this path")
    else ChangeSnapshot(SnapshotKind.ABSENT)

/** 只保留固定数量的查询结果。扫描、旧基线和冷启动覆盖路径均保存在磁盘。 */
internal class TerminalObservationIndex(private val database: TerminalIndexDatabase, private val beforeStageCommit: (Set<String>) -> Unit = {}) : TerminalObservationReader, Closeable {
    companion object {
        const val PAGE_SIZE = 128
        const val CACHE_SIZE = 256
        private const val COLUMNS = "stamp,kind,size,blob,detail"
    }

    private val cache = object : LinkedHashMap<String, TerminalObservedFile?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TerminalObservedFile?>): Boolean = size > CACHE_SIZE
    }
    private var generation = 0L
    private val scans = mutableSetOf<Long>()
    private val pendingPins = mutableMapOf<Long, MutableSet<String>>()

    init {
        database.transaction {
            database.execute("CREATE TABLE IF NOT EXISTS files (generation INTEGER NOT NULL,path TEXT NOT NULL COLLATE BINARY,stamp TEXT NOT NULL,kind TEXT NOT NULL,size INTEGER NOT NULL,blob TEXT,detail TEXT,PRIMARY KEY(generation,path)) WITHOUT ROWID")
            database.execute("CREATE INDEX IF NOT EXISTS file_blobs ON files(blob) WHERE blob IS NOT NULL")
            database.execute("CREATE TABLE IF NOT EXISTS unreadable (generation INTEGER NOT NULL,path TEXT NOT NULL COLLATE BINARY,PRIMARY KEY(generation,path)) WITHOUT ROWID")
            database.execute("CREATE TABLE IF NOT EXISTS touched (path TEXT NOT NULL COLLATE BINARY PRIMARY KEY) WITHOUT ROWID")
            database.execute("CREATE TABLE IF NOT EXISTS preparing (generation INTEGER NOT NULL,blob TEXT NOT NULL,PRIMARY KEY(generation,blob)) WITHOUT ROWID")
            database.execute("CREATE INDEX IF NOT EXISTS preparing_blobs ON preparing(blob)")
            database.execute("CREATE TABLE IF NOT EXISTS events (path TEXT NOT NULL COLLATE BINARY PRIMARY KEY,chat TEXT,tool TEXT,execution TEXT,subagent TEXT,created INTEGER NOT NULL) WITHOUT ROWID")
            database.execute("DELETE FROM events")
            database.execute("CREATE TABLE IF NOT EXISTS metadata (name TEXT NOT NULL PRIMARY KEY,value TEXT NOT NULL) WITHOUT ROWID")
            // 进程已结束的暂存代次没有发布，不能作为下一次扫描的可靠基线。
            database.execute("DELETE FROM files WHERE generation<>0")
            database.execute("DELETE FROM unreadable WHERE generation<>0")
            database.execute("DELETE FROM preparing")
            database.execute("DELETE FROM touched")
        }
    }

    @Synchronized
    override fun file(path: String): TerminalObservedFile? {
        if (cache.containsKey(path)) return cache[path]
        return readFile(0, path).also { cache[path] = it }
    }

    @Synchronized
    override fun isUnreadable(path: String): Boolean = unreadableAncestors(0, path).isNotEmpty()

    @Synchronized
    fun unreadableSample(limit: Int = 3): List<String> = database.query(
        "SELECT path FROM unreadable WHERE generation=0 ORDER BY path LIMIT ?", listOf(limit.toString()),
    ).use { rows -> buildList { while (rows.next()) add(checkNotNull(rows.string(0))) } }

    @Synchronized
    fun hasTouchedAncestor(path: String): Boolean = database.query(
        "SELECT 1 FROM touched WHERE path IN (${ancestors(path).joinToString(",") { "?" }}) LIMIT 1", ancestors(path),
    ).use { it.next() }

    /** 通知合并和待处理清单落盘，文件事件风暴也不持有无界路径映射。 */
    @Synchronized
    fun enqueue(path: String, change: TerminalPendingChange, onlyWhenUnowned: Boolean = false, older: Boolean = false, mergeCreated: Boolean = false) {
        val previous = database.query("SELECT chat,tool,execution,subagent,created FROM events WHERE path=?", listOf(path)).use { if (it.next()) pendingRow(it, 0) else null }
        if (onlyWhenUnowned && previous?.origin != null) return
        val origin = when {
            previous?.origin == null -> change.origin
            change.origin == null -> previous.origin
            else -> previous.origin.merge(change.origin)
        }
        val merged = TerminalPendingChange(origin, if (mergeCreated) change.createdInPlace || previous?.createdInPlace == true else if (older) change.createdInPlace else previous?.createdInPlace ?: change.createdInPlace)
        if (previous == merged) return
        database.execute("INSERT OR REPLACE INTO events(path,chat,tool,execution,subagent,created) VALUES(?,?,?,?,?,?)",
            listOf(path, origin?.chatId, origin?.toolName, origin?.executionId, origin?.subagentExecutionId, if (merged.createdInPlace) 1 else 0))
    }

    @Synchronized
    fun hasPending(): Boolean = database.query("SELECT 1 FROM events LIMIT 1").use { it.next() }

    @Synchronized
    fun hasOwnedPendingAncestor(path: String): Boolean {
        val names = ancestors(path)
        return database.query("SELECT 1 FROM events WHERE chat IS NOT NULL AND path IN (" + names.joinToString(",") { "?" } + ") LIMIT 1", names).use { it.next() }
    }

    @Synchronized
    fun pendingAncestors(path: String): Map<String, TerminalPendingChange> {
        val names = ancestors(path)
        return database.query("SELECT path,chat,tool,execution,subagent,created FROM events WHERE path IN (" + names.joinToString(",") { "?" } + ")", names).use { rows ->
            buildMap { while (rows.next()) put(checkNotNull(rows.string(0)), pendingRow(rows, 1)) }
        }
    }

    @Synchronized
    fun takePending(): Map<String, TerminalPendingChange> = database.transaction {
        val pending = database.query("SELECT path,chat,tool,execution,subagent,created FROM events ORDER BY path LIMIT ?", listOf(PAGE_SIZE.toString())).use { rows ->
            buildMap { while (rows.next()) put(checkNotNull(rows.string(0)), pendingRow(rows, 1)) }
        }
        for (path in pending.keys) database.execute("DELETE FROM events WHERE path=?", listOf(path))
        pending
    }

    private fun pendingRow(row: TerminalIndexRows, offset: Int): TerminalPendingChange {
        val chat = row.string(offset)
        val origin = if (chat == null) null else TerminalChangeOrigin(chat, checkNotNull(row.string(offset + 1)), row.string(offset + 2), row.string(offset + 3))
        return TerminalPendingChange(origin, row.long(offset + 4) != 0L)
    }

    @Synchronized
    fun beginScan(paths: Set<String>? = null): Scan {
        val selected = selectedObservationPaths(paths)
        val id = ++generation
        scans += id
        return Scan(id, selected, paths == null)
    }

    @Synchronized
    fun count(): Long = database.query("SELECT count(*) FROM files WHERE generation=0").use { it.next(); it.long(0) }

    @Synchronized
    fun cachedEntries(): Int = cache.size

    @Synchronized
    fun containsBlob(hash: String): Boolean = pendingPins.values.any { hash in it } || database.query(
        "SELECT 1 FROM files WHERE blob=? UNION ALL SELECT 1 FROM preparing WHERE blob=? LIMIT 1", listOf(hash, hash),
    ).use { it.next() }

    @Synchronized
    fun legacyImported(): Boolean = database.query("SELECT 1 FROM metadata WHERE name='legacy-imported'").use { it.next() }

    @Synchronized
    fun initialized(): Boolean = database.query("SELECT 1 FROM metadata WHERE name='initialized'").use { it.next() }

    @Synchronized
    fun markLegacyImported() = database.execute("INSERT OR REPLACE INTO metadata(name,value) VALUES('legacy-imported','1')")

    /** 文件工具推进单一路径，不创建或复制全盘映射。调用方持有操作锁。 */
    fun acknowledge(path: String, snapshot: ChangeSnapshot, initializing: Boolean) {
        if (snapshot.kind == SnapshotKind.DIRECTORY) return
        beginScan(setOf(path)).use { scan ->
            if (snapshot.kind != SnapshotKind.ABSENT) scan.put(path, TerminalObservedFile("", snapshot))
            scan.commit(touch = initializing)
        }
    }

    @Synchronized
    override fun close() {
        check(scans.isEmpty()) { "Terminal scans are still active" }
        cache.clear()
        database.close()
    }

    internal data class Difference(
        val path: String, val before: TerminalObservedFile?, val after: TerminalObservedFile?,
        val beforeSnapshot: ChangeSnapshot, val afterSnapshot: ChangeSnapshot,
    )

    inner class Scan internal constructor(val id: Long, val paths: List<String>, private val full: Boolean) : TerminalObservationWriter, Closeable {
        private val buffer = ArrayList<Pair<String, TerminalObservedFile>>(PAGE_SIZE)
        private var ended = false

        override fun put(path: String, file: TerminalObservedFile) {
            check(!ended)
            buffer += path to file
            if (buffer.size >= PAGE_SIZE) flush()
        }

        fun flush() {
            // 内容锁必须在基线锁外取得，清理路径按同一顺序核对引用。
            val captured = synchronized(this@TerminalObservationIndex) { pendingPins[id]?.toSet().orEmpty() }
            beforeStageCommit(captured)
            synchronized(this@TerminalObservationIndex) {
                val pins = pendingPins[id].orEmpty()
                if (buffer.isEmpty() && pins.isEmpty()) return
                database.transaction {
                    for (hash in pins) database.execute("INSERT OR IGNORE INTO preparing(generation,blob) VALUES(?,?)", listOf(id, hash))
                    for ((path, file) in buffer) database.execute(
                        "INSERT OR REPLACE INTO files(generation,path,stamp,kind,size,blob,detail) VALUES(?,?,?,?,?,?,?)",
                        listOf(id, path, file.stamp, file.snapshot.kind.name, file.snapshot.size, file.snapshot.blob, file.snapshot.detail),
                    )
                }
                buffer.clear()
                pendingPins.remove(id)
            }
        }

        override fun unreadable(path: String) = synchronized(this@TerminalObservationIndex) {
            check(!ended)
            database.execute("INSERT OR REPLACE INTO unreadable(generation,path) VALUES(?,?)", listOf(id, path))
        }

        override fun preserveSubtree(path: String) {
            flush()
            synchronized(this@TerminalObservationIndex) {
                val scope = subtree("path", path)
                database.execute("INSERT OR REPLACE INTO files SELECT ?,path,$COLUMNS FROM files WHERE generation=0 AND ${scope.first}", listOf(id) + scope.second)
            }
        }

        /** 内容落盘到基线行之前，用磁盘租约保护在途 blob，避免常驻哈希集合。 */
        fun pinBlob(hash: String) {
            val full = synchronized(this@TerminalObservationIndex) { (pendingPins[id]?.size ?: 0) >= PAGE_SIZE }
            if (full) flush()
            synchronized(this@TerminalObservationIndex) {
                check(!ended)
                pendingPins.getOrPut(id) { linkedSetOf() }.add(hash)
            }
        }

        /** 每页最多固定数量的差异；查询游标关闭后再交给日志层保存，避免锁顺序反转。 */
        fun differences(afterPath: String? = null): List<Difference> {
            flush()
            return synchronized(this@TerminalObservationIndex) {
                val scope = scope("path", paths)
                val boundary = if (afterPath == null) "" else " AND path>?"
                val arguments = listOf(id.toString()) + listOfNotNull(afterPath) + scope.second +
                    listOfNotNull(afterPath) + PAGE_SIZE.toString() + id.toString()
                database.query(
                    "SELECT p.path,b.stamp,b.kind,b.size,b.blob,b.detail,n.stamp,n.kind,n.size,n.blob,n.detail FROM " +
                        "(SELECT path FROM files WHERE generation=?$boundary UNION SELECT path FROM files WHERE generation=0 AND ${scope.first}$boundary ORDER BY path LIMIT ?) p " +
                        "LEFT JOIN files b ON b.generation=0 AND b.path=p.path LEFT JOIN files n ON n.generation=? AND n.path=p.path " +
                        "ORDER BY p.path COLLATE BINARY", arguments,
                ).use { rows -> buildList {
                    while (rows.next()) {
                        val path = checkNotNull(rows.string(0))
                        val before = rowFile(rows, 1)
                        val after = rowFile(rows, 6)
                        val oldSnapshot = before?.snapshot ?: absentSnapshot(isUnreadable(path))
                        val nextSnapshot = after?.snapshot ?: absentSnapshot(
                            unreadableAncestors(id, path).isNotEmpty() || unreadableAncestors(0, path).any { !inScope(it, paths) })
                        add(Difference(path, before, after, oldSnapshot, nextSnapshot))
                    }
                } }
            }
        }

        fun commit(touch: Boolean = false, completeInitialization: Boolean = false, imported: Boolean = false) {
            flush()
            synchronized(this@TerminalObservationIndex) {
                check(!ended)
                val scope = scope("path", paths)
                val untouched = "NOT EXISTS(SELECT 1 FROM touched t WHERE t.path='' OR t.path=f.path OR (f.path>=t.path||'/' AND f.path<t.path||'0'))"
                database.transaction {
                    for (table in listOf("files", "unreadable")) {
                        if (full) {
                            database.execute("DELETE FROM $table WHERE generation=0 AND path IN (SELECT f.path FROM $table f WHERE f.generation=0 AND $untouched)")
                            val columns = if (table == "files") "path,$COLUMNS" else "path"
                            database.execute("INSERT OR REPLACE INTO $table SELECT 0,$columns FROM $table f WHERE generation=? AND $untouched", listOf(id))
                        } else {
                            database.execute("DELETE FROM $table WHERE generation=0 AND ${scope.first}", scope.second)
                            val columns = if (table == "files") "path,$COLUMNS" else "path"
                            database.execute("INSERT OR REPLACE INTO $table SELECT 0,$columns FROM $table WHERE generation=?", listOf(id))
                        }
                    }
                    if (touch) for (path in paths) database.execute("INSERT OR IGNORE INTO touched(path) VALUES(?)", listOf(path))
                    if (completeInitialization) {
                        database.execute("DELETE FROM touched")
                        database.execute("INSERT OR REPLACE INTO metadata(name,value) VALUES('initialized','1')")
                    }
                    if (imported) markLegacyImported()
                    removeStage()
                }
                cache.clear()
                scans -= id
                ended = true
            }
        }

        override fun close() = synchronized(this@TerminalObservationIndex) {
            if (!ended) {
                buffer.clear()
                try { database.transaction { removeStage() } }
                finally { scans -= id; ended = true }
            }
        }

        private fun removeStage() {
            pendingPins.remove(id)
            database.execute("DELETE FROM files WHERE generation=?", listOf(id))
            database.execute("DELETE FROM unreadable WHERE generation=?", listOf(id))
            database.execute("DELETE FROM preparing WHERE generation=?", listOf(id))
        }
    }

    private fun readFile(generation: Long, path: String): TerminalObservedFile? = database.query(
        "SELECT $COLUMNS FROM files WHERE generation=? AND path=?", listOf(generation.toString(), path),
    ).use { if (it.next()) rowFile(it, 0) else null }

    private fun unreadableAncestors(generation: Long, path: String): List<String> {
        val ancestors = ancestors(path)
        return database.query("SELECT path FROM unreadable WHERE generation=? AND path IN (${ancestors.joinToString(",") { "?" }})",
            listOf(generation.toString()) + ancestors).use { rows -> buildList { while (rows.next()) add(checkNotNull(rows.string(0))) } }
    }

    private fun rowFile(row: TerminalIndexRows, offset: Int): TerminalObservedFile? {
        val stamp = row.string(offset) ?: return null
        return TerminalObservedFile(stamp, ChangeSnapshot(SnapshotKind.valueOf(checkNotNull(row.string(offset + 1))),
            row.long(offset + 2), row.string(offset + 3), row.string(offset + 4)))
    }

    private fun absentSnapshot(unreadable: Boolean) = if (unreadable)
        ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "Previous observation could not read this path") else ChangeSnapshot(SnapshotKind.ABSENT)

    private fun ancestors(path: String): List<String> = buildList {
        anyObservationAncestor(path) { add(it); false }
    }

    private fun inScope(path: String, paths: List<String>): Boolean = paths.any { it.isEmpty() || path == it || path.startsWith("$it/") }

    private fun scope(column: String, paths: List<String>): Pair<String, List<String>> {
        val parts = paths.map { subtree(column, it) }
        return parts.joinToString(" OR ", "(", ")") { it.first } to parts.flatMap { it.second }
    }

    private fun subtree(column: String, path: String): Pair<String, List<String>> = if (path.isEmpty()) "1" to emptyList()
        else "($column=? OR ($column>=? AND $column<?))" to listOf(path, "$path/", "${path}0")
}
