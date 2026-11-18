package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 每次操作单独原子落盘，内容按哈希去重，不依赖聊天气泡或当前工作区绑定。 */
internal class WorkspaceChangeStore(context: Context) {
    private val root = File(context.filesDir, "workspace_changes/v1")
    private val objects = File(root, "objects")
    private val operations = File(root, "operations")
    private val observations = File(root, "observations")
    private val json = Json { encodeDefaults = true }
    private val revisionState = MutableStateFlow(0L)
    private val statistics = object : LinkedHashMap<String, Pair<Int, Int>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Int, Int>>): Boolean = size > 512
    }
    val revision = revisionState.asStateFlow()

    @Synchronized
    fun putBlob(bytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val target = File(objects, hash)
        if (!target.exists()) writeAtomic(target, bytes)
        return hash
    }

    @Synchronized
    fun save(records: List<FileChangeRecord>) {
        if (records.isEmpty()) return
        writeAtomic(File(operations, "${records.first().operationId}.json"), json.encodeToString(records).toByteArray(Charsets.UTF_8))
        revisionState.update { it + 1 }
    }

    @Synchronized
    fun observedFiles(key: String): Map<String, ObservedWorkspaceFile>? {
        require(Regex("[0-9a-f]{64}").matches(key))
        val file = File(observations, "$key.json")
        return if (file.exists()) json.decodeFromString(file.readText(Charsets.UTF_8)) else null
    }

    @Synchronized
    fun saveObservedFiles(key: String, files: Map<String, ObservedWorkspaceFile>) {
        require(Regex("[0-9a-f]{64}").matches(key))
        writeAtomic(File(observations, "$key.json"), json.encodeToString(files).toByteArray(Charsets.UTF_8))
    }

    @Synchronized
    fun records(chatId: String? = null, directory: String? = null, backend: String? = null): List<FileChangeRecord> =
        operations.listFiles().orEmpty().filter { it.extension == "json" }.flatMap { file ->
            json.decodeFromString<List<FileChangeRecord>>(file.readText(Charsets.UTF_8))
        }.filter { record ->
            (chatId == null || record.chatId == chatId) &&
                (backend == null || record.location.backend == backend) &&
                (directory == null || changePathContains(directory, record.location.path))
        }.sortedWith(compareBy<FileChangeRecord> { it.timestamp }.thenBy { it.id })

    @Synchronized
    fun content(record: FileChangeRecord): ReviewContent = content(record.before, record.after)

    @Synchronized
    fun content(records: List<FileChangeRecord>): ReviewContent {
        require(records.isNotEmpty())
        return content(records.first().before, records.last().after)
    }

    private fun content(before: ChangeSnapshot, after: ChangeSnapshot): ReviewContent = ReviewContent(
        before = text(before), after = text(after), beforeKind = before.kind, afterKind = after.kind,
        detail = listOfNotNull(before.detail, after.detail).distinct().joinToString("\n").takeIf { it.isNotEmpty() },
    )

    private fun text(snapshot: ChangeSnapshot): String? = when (snapshot.kind) {
        SnapshotKind.ABSENT -> ""
        SnapshotKind.TEXT -> File(objects, checkNotNull(snapshot.blob)).readText(Charsets.UTF_8)
        else -> null
    }

    @Synchronized
    fun reviewFiles(records: List<FileChangeRecord>, directory: String? = null, aggregate: Boolean = false): List<ReviewFile> =
        records.groupBy { it.location.key }.map { (key, unsorted) ->
            val history = unsorted.sortedWith(compareBy<FileChangeRecord> { it.timestamp }.thenBy { it.id })
            val latest = history.last()
            val first = if (aggregate) history.first() else latest
            val before = first.before
            val after = latest.after
            val text = before.kind in setOf(SnapshotKind.TEXT, SnapshotKind.ABSENT) &&
                after.kind in setOf(SnapshotKind.TEXT, SnapshotKind.ABSENT)
            val statisticsKey = first.id + ":" + latest.id
            val savedCounts = if (!aggregate && latest.additions != null && latest.deletions != null) latest.additions to latest.deletions else null
            val counts = savedCounts ?: if (text) statistics[statisticsKey] ?: content(before, after).let { data ->
                if (reviewDiffAutomatic(data)) WorkspaceDiff.compare(checkNotNull(data.before), checkNotNull(data.after)).let { document ->
                    (document.additions to document.deletions).also { statistics[statisticsKey] = it }
                } else null
            } else null
            ReviewFile(
                id = key, location = latest.location,
                displayPath = if (directory == null) latest.location.path else latest.location.path.removePrefix(changePath(directory).trimEnd('/') + "/"),
                status = when { before.kind == SnapshotKind.ABSENT -> "A"; after.kind == SnapshotKind.ABSENT -> "D"; else -> "M" },
                additions = counts?.first ?: 0, deletions = counts?.second ?: 0,
                binary = before.kind == SnapshotKind.BINARY || after.kind == SnapshotKind.BINARY,
                records = history.asReversed(), aggregateRecords = aggregate, statisticsAvailable = counts != null,
            )
        }.sortedBy { it.displayPath }

    /** 删除只影响指定记录，批量工具产生的其他文件以及其他对话的引用继续保留。 */
    suspend fun removeRecords(ids: Set<String>): Int = operationMutex.withLock {
        withContext(Dispatchers.IO) { synchronized(this@WorkspaceChangeStore) { removeLocked(ids) } }
    }

    @Synchronized
    fun fileRecords(location: ChangeLocation, chatId: String?): List<FileChangeRecord> =
        records(chatId = chatId).filter { it.location.key == location.key }

    private fun removeLocked(ids: Set<String>): Int {
        if (ids.isEmpty()) return 0
        val files = operations.listFiles().orEmpty().filter { it.extension == "json" }
        val inventory = files.associateWith { json.decodeFromString<List<FileChangeRecord>>(it.readText(Charsets.UTF_8)) }
        val removed = inventory.values.flatten().filter { it.id in ids }
        if (removed.isEmpty()) return 0
        var changed = false
        try {
            for ((file, records) in inventory) {
                val kept = records.filterNot { it.id in ids }
                if (kept.size == records.size) continue
                if (kept.isEmpty()) {
                    AtomicFile(file).delete()
                    if (file.exists()) throw IOException("Cannot delete operation record: ${file.name}")
                } else writeAtomic(file, json.encodeToString(kept).toByteArray(Charsets.UTF_8))
                changed = true
            }
            val baselineBlobs = observations.listFiles().orEmpty().filter { it.extension == "json" }.flatMap { file ->
                json.decodeFromString<Map<String, ObservedWorkspaceFile>>(file.readText(Charsets.UTF_8)).values.mapNotNull { it.snapshot.blob }
            }
            val retained = inventory.values.flatten().filterNot { it.id in ids }
                .flatMap { listOfNotNull(it.before.blob, it.after.blob) }.toSet() + baselineBlobs
            val candidates = removed.flatMap { listOfNotNull(it.before.blob, it.after.blob) }.toSet() - retained
            // 采集与清理共用互斥锁，不能删掉进行中操作即将引用的相同内容。
            for (hash in candidates) {
                require(Regex("[0-9a-f]{64}").matches(hash)) { "Invalid snapshot object name" }
                val objectFile = File(objects, hash)
                if (objectFile.exists() && !objectFile.delete()) throw IOException("Cannot remove unused snapshot: $hash")
            }
            return removed.size
        } finally {
            if (changed) {
                statistics.clear()
                revisionState.update { it + 1 }
            }
        }
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    companion object {
        internal val operationMutex = Mutex()
        private val timestamp = AtomicLong()
        internal fun nextTimestamp(): Long = timestamp.updateAndGet { maxOf(System.currentTimeMillis(), it + 1) }
        @Volatile private var instance: WorkspaceChangeStore? = null
        fun get(context: Context): WorkspaceChangeStore = instance ?: synchronized(this) {
            instance ?: WorkspaceChangeStore(context.applicationContext).also { instance = it }
        }
    }
}
