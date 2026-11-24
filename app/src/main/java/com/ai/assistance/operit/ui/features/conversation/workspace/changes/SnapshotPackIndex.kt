package com.ai.assistance.operit.ui.features.conversation.workspace.changes

internal data class SnapshotPackEntry(val hash: String, val volume: String, val offset: Long, val length: Int, val live: Boolean)
internal data class SnapshotPackVolume(val name: String, val committed: Long, val dead: Long, val sealed: Boolean)

/** 复用工作区的逐行 SQL 接口，不把全部对象位置载入内存。 */
internal class SnapshotPackIndex(private val database: TerminalIndexDatabase) {
    private val cache = object : LinkedHashMap<String, SnapshotPackEntry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SnapshotPackEntry>): Boolean = size > 256
    }

    init {
        val version = database.query("PRAGMA user_version").use { rows -> check(rows.next()); rows.long(0) }
        require(version == 0L || version == 2L) { "Unsupported snapshot index version: $version" }
        database.transaction {
            database.execute("CREATE TABLE IF NOT EXISTS snapshot_volumes (name TEXT PRIMARY KEY,committed INTEGER NOT NULL,dead INTEGER NOT NULL,sealed INTEGER NOT NULL) WITHOUT ROWID")
            database.execute("CREATE TABLE IF NOT EXISTS snapshot_objects (hash TEXT PRIMARY KEY,volume TEXT NOT NULL,offset INTEGER NOT NULL,length INTEGER NOT NULL,live INTEGER NOT NULL) WITHOUT ROWID")
            database.execute("CREATE INDEX IF NOT EXISTS snapshot_object_volumes ON snapshot_objects(volume,live,hash)")
            database.execute("PRAGMA user_version=2")
        }
    }

    fun entry(hash: String): SnapshotPackEntry? {
        cache[hash]?.let { return it }
        return database.query("SELECT hash,volume,offset,length,live FROM snapshot_objects WHERE hash=?", listOf(hash)).use { rows ->
            if (rows.next()) entry(rows).also { cache[hash] = it } else null
        }
    }

    fun volume(name: String): SnapshotPackVolume? = database.query(
        "SELECT name,committed,dead,sealed FROM snapshot_volumes WHERE name=?", listOf(name),
    ).use { rows -> if (rows.next()) volume(rows) else null }

    fun volumes(after: String = ""): List<SnapshotPackVolume> = database.query(
        "SELECT name,committed,dead,sealed FROM snapshot_volumes WHERE name>? ORDER BY name LIMIT 64", listOf(after),
    ).use { rows -> buildList { while (rows.next()) add(volume(rows)) } }

    fun liveEntries(volume: String, after: String = ""): List<SnapshotPackEntry> = database.query(
        "SELECT hash,volume,offset,length,live FROM snapshot_objects WHERE volume=? AND live=1 AND hash>? ORDER BY hash LIMIT 64", listOf(volume, after),
    ).use { rows -> buildList { while (rows.next()) add(entry(rows)) } }

    fun active(): SnapshotPackVolume? = database.query(
        "SELECT name,committed,dead,sealed FROM snapshot_volumes WHERE sealed=0 LIMIT 1",
    ).use { rows -> if (rows.next()) volume(rows) else null }

    fun addVolume(volume: SnapshotPackVolume) = database.execute(
        "INSERT INTO snapshot_volumes(name,committed,dead,sealed) VALUES(?,?,?,?)",
        listOf(volume.name, volume.committed, volume.dead, if (volume.sealed) 1 else 0),
    )

    fun seal(name: String) = database.execute("UPDATE snapshot_volumes SET sealed=1 WHERE name=?", listOf(name))

    fun commit(entry: SnapshotPackEntry, end: Long, sealed: Boolean, createVolume: Boolean) =
        commitBatch(listOf(entry), end, sealed, createVolume) {}

    fun <T> commitBatch(entries: List<SnapshotPackEntry>, end: Long, sealed: Boolean, createVolume: Boolean, publish: () -> T): T {
        try {
            val result = database.transaction {
                if (entries.isNotEmpty()) {
                    val volume = entries.first().volume
                    if (createVolume) addVolume(SnapshotPackVolume(volume, end, 0, sealed))
                    for (entry in entries) database.execute("INSERT OR REPLACE INTO snapshot_objects(hash,volume,offset,length,live) VALUES(?,?,?,?,1)",
                        listOf(entry.hash, entry.volume, entry.offset, entry.length))
                    if (!createVolume) database.execute("UPDATE snapshot_volumes SET committed=?,sealed=? WHERE name=?", listOf(end, if (sealed) 1 else 0, volume))
                }
                publish()
            }
            for (entry in entries) cache[entry.hash] = entry
            return result
        } catch (error: Exception) { cache.clear(); throw error }
    }

    fun remove(hashes: Set<String>) {
        cache.clear()
        try {
            database.transaction {
                for (hash in hashes) {
                    val entry = entry(hash) ?: continue
                    if (!entry.live) continue
                    database.execute("UPDATE snapshot_objects SET live=0 WHERE hash=?", listOf(hash))
                    database.execute("UPDATE snapshot_volumes SET dead=dead+? WHERE name=?", listOf(SnapshotPackStore.RECORD_HEADER + entry.length, entry.volume))
                }
            }
        } finally { cache.clear() }
    }

    fun missingVolume(name: String) {
        cache.clear()
        database.transaction {
            // 保留墓碑，迁移中断残留的旧对象不能在用户清理卷后重新出现。
            database.execute("UPDATE snapshot_objects SET live=0 WHERE volume=?", listOf(name))
            database.execute("DELETE FROM snapshot_volumes WHERE name=?", listOf(name))
        }
    }

    fun replaceVolume(old: String, replacement: SnapshotPackVolume, relocate: () -> Unit) {
        cache.clear()
        try {
            database.transaction {
                addVolume(replacement)
                relocate()
                database.execute("DELETE FROM snapshot_volumes WHERE name=?", listOf(old))
            }
        } finally { cache.clear() }
    }

    fun relocate(entry: SnapshotPackEntry, volume: String, offset: Long) = database.execute(
        "UPDATE snapshot_objects SET volume=?,offset=? WHERE hash=? AND live=1", listOf(volume, offset, entry.hash),
    )

    private fun entry(rows: TerminalIndexRows) = SnapshotPackEntry(
        checkNotNull(rows.string(0)), checkNotNull(rows.string(1)), rows.long(2), rows.long(3).toInt(), rows.long(4) == 1L,
    )
    private fun volume(rows: TerminalIndexRows) = SnapshotPackVolume(
        checkNotNull(rows.string(0)), rows.long(1), rows.long(2), rows.long(3) == 1L,
    )
}