package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
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
    private val snapshotReader = WorkspaceSnapshotReader(objects)
    private val operations = File(root, "operations")
    private val observations = File(root, "observations")
    private val terminalObservations = File(root, "terminal-observations")
    // 工作区清单和旧终端清单迁移各自串行，终端基线本体由磁盘索引持有。
    private val baselineIo = Any()
    private val terminalBaselineIo = Any()
    private val terminalIndexes = linkedMapOf<String, TerminalObservationIndex>()
    private val json = Json { encodeDefaults = true }
    private val revisionState = MutableStateFlow(0L)
    private val statistics = object : LinkedHashMap<String, Pair<Int, Int>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Int, Int>>): Boolean = size > 512
    }
    val revision = revisionState.asStateFlow()
    private var trackedRevision = -1L
    private var trackedChat: String? = null
    private var trackedLocations = emptyList<ChangeLocation>()

    /** 终端核对本对话记录过的本地文件，包含 Android 与 Ubuntu 共用的存储。 */
    @Synchronized
    fun trackedLocalFiles(chatId: String): List<ChangeLocation> {
        if (chatId.isBlank()) return emptyList()
        if (trackedChat != chatId || trackedRevision != revisionState.value) {
            trackedLocations = records(chatId).asSequence()
                .filter { it.location.backend == "linux:local" || it.location.backend == "android" }
                .associateBy { it.location.historyLocation().key }.values
                .filter { it.after.kind == SnapshotKind.TEXT || it.after.kind == SnapshotKind.BINARY }
                .map { it.location }
            trackedRevision = revisionState.value
            trackedChat = chatId
        }
        return trackedLocations
    }

    @Synchronized
    fun putBlob(bytes: ByteArray, terminalScan: TerminalObservationIndex.Scan? = null): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        // 冷启动会计算许多小文件的哈希，逐字节创建格式化器的开销会落到命令启动路径。
        val alphabet = "0123456789abcdef"
        val hex = CharArray(digest.size * 2)
        for ((index, byte) in digest.withIndex()) {
            val value = byte.toInt() and 255
            hex[2 * index] = alphabet[value ushr 4]
            hex[2 * index + 1] = alphabet[value and 15]
        }
        val hash = String(hex)
        val target = File(objects, hash)
        if (!target.exists()) writeAtomic(target, bytes)
        terminalScan?.pinBlob(hash)
        return hash
    }

    @Synchronized
    fun save(records: List<FileChangeRecord>) {
        if (records.isEmpty()) return
        writeAtomic(File(operations, "${records.first().operationId}.json"), json.encodeToString(records).toByteArray(Charsets.UTF_8))
        revisionState.update { it + 1 }
    }

    /** 长任务每批保存完整操作清单，同一次执行不会丢掉先前批次的其他文件。 */
    @Synchronized
    fun replaceOperation(operationId: String, records: List<FileChangeRecord>) {
        require(java.util.UUID.fromString(operationId).toString() == operationId)
        require(records.all { it.operationId == operationId })
        if (records.isEmpty()) {
            val file = File(operations, "$operationId.json")
            if (!file.exists()) return
            AtomicFile(file).delete()
            check(!file.exists()) { "Cannot remove empty terminal operation" }
        } else {
            writeAtomic(File(operations, "$operationId.json"), json.encodeToString(records).toByteArray(Charsets.UTF_8))
        }
        statistics.clear()
        revisionState.update { it + 1 }
    }

    @Synchronized
    fun operationRecords(operationId: String): List<FileChangeRecord> {
        require(java.util.UUID.fromString(operationId).toString() == operationId)
        val file = File(operations, "$operationId.json")
        return if (file.exists()) json.decodeFromString(file.readText(Charsets.UTF_8)) else emptyList()
    }

    @Synchronized
    fun terminalIndex(key: String): TerminalObservationIndex {
        require(Regex("[0-9a-f]{64}").matches(key))
        return terminalIndexes.getOrPut(key) {
            TerminalObservationIndex(AndroidTerminalIndexDatabase(File(terminalObservations, "$key.sqlite")))
        }
    }

    @Synchronized
    fun releaseTerminalIndex(key: String) { terminalIndexes.remove(key)?.close() }

    /** 迁移成功后才移除旧清单；异常保留源文件并由日志层报告。 */
    fun importTerminalBaseline(key: String, checkCancelled: () -> Unit) {
        val index = terminalIndex(key)
        synchronized(terminalBaselineIo) {
            val file = File(terminalObservations, "$key.json")
            if (!index.legacyImported()) {
                if (file.exists()) index.beginScan().use { scan ->
                    readLegacyTerminalBaseline(file, scan::put, scan::unreadable, checkCancelled)
                    checkCancelled()
                    scan.commit(imported = true)
                } else index.markLegacyImported()
            }
            if (file.exists()) {
                AtomicFile(file).delete()
                if (file.exists()) throw IOException("Cannot remove migrated terminal baseline")
            }
        }
    }

    fun observedFiles(key: String): Map<String, ObservedWorkspaceFile>? = synchronized(baselineIo) {
        require(Regex("[0-9a-f]{64}").matches(key))
        val file = File(observations, "$key.json")
        if (file.exists()) json.decodeFromString(file.readText(Charsets.UTF_8)) else null
    }

    fun saveObservedFiles(key: String, files: Map<String, ObservedWorkspaceFile>) = synchronized(baselineIo) {
        require(Regex("[0-9a-f]{64}").matches(key))
        writeAtomic(File(observations, "$key.json"), json.encodeToString(files).toByteArray(Charsets.UTF_8))
    }

    @Synchronized
    fun records(chatId: String? = null, directory: String? = null, backend: String? = null): List<FileChangeRecord> =
        operations.listFiles().orEmpty().asSequence().filter { it.extension == "json" }.flatMap { file ->
            json.decodeFromString<List<FileChangeRecord>>(file.readText(Charsets.UTF_8)).asSequence()
        }.filter { record ->
            val inScope = if (directory == null) {
                backend == null || record.location.backend == backend
            } else {
                val scope = ChangeLocation(directory, record.location.environment, backend ?: record.location.backend)
                relativeChangePath(scope, record.location) != null
            }
            (chatId == null || record.chatId == chatId) && inScope
        }.sortedWith(compareBy<FileChangeRecord> { it.timestamp }.thenBy { it.id }).toList()

    @Synchronized
    fun content(record: FileChangeRecord): ReviewContent = content(record.before, record.after)

    @Synchronized
    fun content(records: List<FileChangeRecord>): ReviewContent {
        require(records.isNotEmpty())
        return content(records.first().before, records.last().after)
    }

    private fun content(before: ChangeSnapshot, after: ChangeSnapshot): ReviewContent = snapshotReader.content(before, after)

    @Synchronized
    fun reviewFiles(
        records: List<FileChangeRecord>, directory: ChangeLocation? = null, aggregate: Boolean = false,
    ): List<ReviewFile> = snapshotReader.readableRecords(records).groupBy { it.location.historyLocation().key }.map { (key, unsorted) ->
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
                displayPath = if (directory == null) latest.location.path else checkNotNull(relativeChangePath(directory, latest.location)),
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
    fun fileRecords(location: ChangeLocation, chatId: String?): List<FileChangeRecord> {
        val key = location.historyLocation().key
        return records(chatId = chatId).filter { it.location.historyLocation().key == key }
    }

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
            val workspaceBaselineBlobs = observations.listFiles().orEmpty().filter { it.extension == "json" }.flatMap { file ->
                json.decodeFromString<Map<String, ObservedWorkspaceFile>>(file.readText(Charsets.UTF_8)).values.mapNotNull { it.snapshot.blob }
            }
            val retained = inventory.values.flatten().filterNot { it.id in ids }
                .flatMap { listOfNotNull(it.before.blob, it.after.blob) }.toSet() + workspaceBaselineBlobs
            val possible = removed.flatMap { listOfNotNull(it.before.blob, it.after.blob) }.toSet() - retained
            val terminalRetained = referencedTerminalBlobs(possible)
            val candidates = possible - terminalRetained
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

    /** 仅查询待删哈希，不再为垃圾回收反序列化三十万条终端基线。 */
    private fun referencedTerminalBlobs(candidates: Set<String>): Set<String> {
        if (candidates.isEmpty()) return emptySet()
        val retained = mutableSetOf<String>()
        synchronized(terminalBaselineIo) {
            for (file in terminalObservations.listFiles().orEmpty().filter { it.extension == "json" }) {
                readLegacyTerminalBaseline(file, { _, observed ->
                    observed.snapshot.blob?.takeIf { it in candidates }?.let(retained::add)
                })
            }
        }
        for (file in terminalObservations.listFiles().orEmpty().filter { it.extension == "sqlite" }) {
            val index = terminalIndex(file.nameWithoutExtension)
            for (hash in candidates) if (hash !in retained && index.containsBlob(hash)) retained += hash
        }
        return retained
    }

    private fun writeAtomic(file: File, bytes: ByteArray) = writeAtomic(file) { it.write(bytes) }

    private fun writeAtomic(file: File, write: (FileOutputStream) -> Unit) {
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            write(stream)
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
