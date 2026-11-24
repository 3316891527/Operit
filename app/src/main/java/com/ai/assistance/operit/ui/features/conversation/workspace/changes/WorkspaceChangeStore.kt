package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 内容分卷与增量记录共用位置库连接，操作归属不依赖当前工作区绑定。 */
internal class WorkspaceChangeStore(
    private val root: File,
    private val openDatabase: (File, Boolean) -> TerminalIndexDatabase,
    syncDirectory: (File) -> Unit,
    private val historyStorage: String,
) {
    constructor(context: Context) : this(File(context.filesDir, "workspace_changes/v1"),
        { file, durable -> AndroidTerminalIndexDatabase(file, durable) }, ::syncSnapshotDirectory,
        com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping.currentEmulatedStoragePath())
    private val objects = File(root, "objects")
    private val blobStore = SnapshotPackStore(objects, syncDirectory = syncDirectory) { openDatabase(File(root, "objects-v2.sqlite"), true) }
    private val snapshotReader = WorkspaceSnapshotReader(blobStore)
    private val maintenance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val compactRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val operations = File(root, "operations")
    private val observations = File(root, "observations")
    private val terminalObservations = File(root, "terminal-observations")
    // 工作区清单和旧终端清单迁移各自串行，终端基线本体由磁盘索引持有。
    private val baselineIo = Any()
    private val terminalBaselineIo = Any()
    private val terminalIndexes = linkedMapOf<String, TerminalObservationIndex>()
    private val workspaceIndexes = linkedMapOf<String, TerminalObservationIndex>()
    private val json = Json { encodeDefaults = true }
    private val revisionState = MutableStateFlow(0L)
    private val statistics = object : LinkedHashMap<String, Pair<Int, Int>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Int, Int>>): Boolean = size > 512
    }
    val revision = revisionState.asStateFlow()
    private var historyIndex: WorkspaceHistoryIndex? = null
    private var operationsImported = false

    private fun <T> withHistory(read: (WorkspaceHistoryIndex) -> T): T = blobStore.withDatabase { database ->
        val index = historyIndex ?: WorkspaceHistoryIndex(database, historyStorage).also { historyIndex = it }
        if (!operationsImported) { index.importLegacy(operations); operationsImported = true }
        read(index)
    }

    init {
        // 分页删除只保留一个回收请求，不能为每页创建一个等待卷锁的协程。
        maintenance.launch {
            for (request in compactRequests) {
                val cancellation = coroutineContext
                try { blobStore.compact(checkCancelled = { cancellation.ensureActive() }) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { AppLogger.e("WorkspaceChanges", "回收快照卷空间失败，已提交索引保留", error) }
            }
        }
        // 首次使用按对象迁移旧内容，不在界面线程扫描或持有完整对象清单。
        maintenance.launch {
            val cancellation = coroutineContext
            try {
                blobStore.migrateLegacy(checkCancelled = { cancellation.ensureActive() }, onBatch = { Thread.yield() })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { AppLogger.e("WorkspaceChanges", "迁移历史快照失败，未迁移的原文件保留", error) }
        }
    }

    /** 当前页只含位置，不在执行对象或 Store 中保留对话的全量文件列表。 */
    @Synchronized
    fun trackedLocalFilesPage(chatId: String, offset: Int): List<ChangeLocation> =
        if (chatId.isBlank()) emptyList() else withHistory { it.trackedPage(chatId, offset) }

    @Synchronized
    fun putBlob(bytes: ByteArray, terminalScan: TerminalObservationIndex.Scan? = null): String {
        val hash = blobStore.put(bytes, batched = true)
        terminalScan?.pinBlob(hash)
        return hash
    }

    @Synchronized
    fun flushBlobs(hashes: Set<String> = emptySet()) {
        blobStore.flush()
        check(hashes.all(blobStore::contains)) { "Captured snapshots were not committed" }
    }

    @Synchronized
    fun save(records: List<FileChangeRecord>) {
        if (records.isEmpty()) return
        withHistory { history -> blobStore.commit { records.forEach(history::put) } }
        revisionState.update { it + 1 }
    }

    /** 每批仅查找本批路径，合并保留最早身份、时间和 before，不重读同次任务的其他文件。 */
    @Synchronized
    fun mergeOperation(operationId: String, records: List<FileChangeRecord>, statistics: (FileChangeRecord) -> FileChangeRecord) {
        require(java.util.UUID.fromString(operationId).toString() == operationId)
        if (records.isEmpty()) return
        withHistory { history -> blobStore.commit {
            history.mergeOperation(operationId, records, statistics)
        } }
        this.statistics.clear()
        revisionState.update { it + 1 }
    }

    /** 整次替换只供明确的调用方使用；终端生产路径采用 mergeOperation。 */
    @Synchronized
    fun replaceOperation(operationId: String, records: List<FileChangeRecord>) {
        require(java.util.UUID.fromString(operationId).toString() == operationId)
        require(records.all { it.operationId == operationId })
        withHistory { history -> blobStore.commit {
            history.deleteOperation(operationId)
            records.forEach(history::put)
        } }
        statistics.clear()
        revisionState.update { it + 1 }
    }

    @Synchronized
    fun operationRecords(operationId: String): List<FileChangeRecord> = withHistory { history -> buildList {
        var offset = 0
        while (true) {
            val page = history.operationPage(operationId, offset)
            if (page.isEmpty()) break
            addAll(page)
            offset += page.size
        }
    } }

    @Synchronized
    fun terminalIndex(key: String): TerminalObservationIndex {
        require(Regex("[0-9a-f]{64}").matches(key))
        return terminalIndexes.getOrPut(key) {
            TerminalObservationIndex(openDatabase(File(terminalObservations, "$key.sqlite"), false), beforeStageCommit = ::flushBlobs)
        }
    }

    @Synchronized
    fun releaseTerminalIndex(key: String) { terminalIndexes.remove(key)?.close() }

    /** 迁移成功后才移除旧清单；异常保留源文件并由日志层报告。 */
    @Synchronized
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

    @Synchronized
    fun workspaceIndex(key: String): TerminalObservationIndex {
        require(Regex("[0-9a-f]{64}").matches(key))
        return workspaceIndexes.getOrPut(key) {
            val index = TerminalObservationIndex(openDatabase(File(observations, "$key.sqlite"), false), beforeStageCommit = ::flushBlobs)
            try {
                val legacy = File(observations, "$key.json")
                if (!index.legacyImported()) {
                    if (legacy.exists()) index.beginScan().use { scan ->
                        com.google.gson.stream.JsonReader(legacy.bufferedReader(Charsets.UTF_8)).use { reader ->
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val path = reader.nextName()
                                val observed = json.decodeFromString<ObservedWorkspaceFile>(com.google.gson.JsonParser.parseReader(reader).toString())
                                scan.put(path, TerminalObservedFile(observed.stamp, observed.snapshot))
                            }
                            reader.endObject()
                            check(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT)
                        }
                        scan.commit(completeInitialization = true, imported = true)
                    } else index.markLegacyImported()
                }
                if (legacy.exists()) java.nio.file.Files.delete(legacy.toPath())
                index
            } catch (error: Exception) { index.close(); throw error }
        }
    }

    @Synchronized
    fun releaseWorkspaceIndex(key: String) { workspaceIndexes.remove(key)?.close() }

    /** 兼容明确需要列表的内部调用；历史界面和采集均使用有界分页接口。 */
    @Synchronized
    fun records(chatId: String? = null, directory: String? = null, backend: String? = null): List<FileChangeRecord> = withHistory { history ->
        val query = WorkspaceHistoryQuery(chatId, directory?.let { ChangeLocation(it, "android", backend ?: "android") }, backend)
        buildList {
            var offset = 0
            while (true) {
                val page = history.recordsPage(query, offset, ascending = true)
                if (page.isEmpty()) break
                addAll(page)
                offset += page.size
            }
        }
    }

    @Synchronized
    fun latestRecord(chatId: String): FileChangeRecord? = withHistory { it.latestRecord(chatId) }

    @Synchronized
    fun historyQuery(chatId: String?, directory: ChangeLocation?, lastTurn: Boolean, userMessages: List<Long>, search: String): WorkspaceHistoryQuery? {
        if (!lastTurn) return WorkspaceHistoryQuery(chatId = chatId, directory = directory, search = search)
        if (chatId == null) return null
        val latest = withHistory { it.latestTimestamp(chatId) } ?: return null
        val starts = userMessages.distinct().sorted()
        val from = starts.lastOrNull { it <= latest } ?: return null
        return WorkspaceHistoryQuery(chatId, directory, from = from, until = starts.firstOrNull { it > from }, search = search)
    }

    @Synchronized
    fun historyPage(query: WorkspaceHistoryQuery, offset: Int): WorkspaceHistoryPage {
        blobStore.refreshAvailability()
        return withHistory { history ->
            val total = history.summaryCount(query)
            val boundedOffset = if (total == 0) 0 else minOf(offset, ((total - 1) / WorkspaceHistoryIndex.PAGE_SIZE) * WorkspaceHistoryIndex.PAGE_SIZE)
            val files = history.summaries(query, boundedOffset).mapNotNull { summary ->
                val first = summary.first
                val latest = summary.latest
                val endpoints = if (first.id == latest.id) listOf(latest) else listOf(latest, first)
                if (snapshotReader.readableRecords(endpoints).size != endpoints.size) return@mapNotNull null
                val counts = if (summary.count == 1 && latest.additions != null && latest.deletions != null)
                    latest.additions to latest.deletions else statistics[first.id + ":" + latest.id]
                ReviewFile(
                    id = latest.location.historyLocation(historyStorage).key, location = latest.location,
                    displayPath = query.directory?.let { checkNotNull(relativeChangePath(it, latest.location, historyStorage)) } ?: latest.location.path,
                    status = when { first.before.kind == SnapshotKind.ABSENT -> "A"; latest.after.kind == SnapshotKind.ABSENT -> "D"; else -> "M" },
                    additions = counts?.first ?: 0, deletions = counts?.second ?: 0,
                    binary = first.before.kind == SnapshotKind.BINARY || latest.after.kind == SnapshotKind.BINARY,
                    records = endpoints, aggregateRecords = true, statisticsAvailable = counts != null,
                    historyQuery = query.copy(search = "", file = latest.location), historyCount = summary.count)
            }
            WorkspaceHistoryPage(files, total, boundedOffset)
        }
    }

    @Synchronized
    fun recordPage(query: WorkspaceHistoryQuery, offset: Int): WorkspaceRecordPage = withHistory { history ->
        val total = history.count(query, readable = true)
        val boundedOffset = if (total == 0) 0 else minOf(offset, ((total - 1) / WorkspaceHistoryIndex.PAGE_SIZE) * WorkspaceHistoryIndex.PAGE_SIZE)
        WorkspaceRecordPage(history.recordsPage(query, boundedOffset, readable = true), total, boundedOffset)
    }

    @Synchronized
    fun historyRecord(id: String, query: WorkspaceHistoryQuery? = null): FileChangeRecord? = withHistory { it.record(id, query) }

    @Synchronized
    fun prepareFileRemoval(location: ChangeLocation, chatId: String?): WorkspaceRecordRemoval = withHistory { history ->
        val query = WorkspaceHistoryQuery(chatId = chatId, file = location)
        val boundary = history.latestCursor(query)
        val bounded = query.copy(through = boundary)
        WorkspaceRecordRemoval(bounded, if (boundary == null) 0 else history.count(bounded))
    }

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
    ): List<ReviewFile> = snapshotReader.readableRecords(records).groupBy { it.location.historyLocation(historyStorage).key }.map { (key, unsorted) ->
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
                displayPath = if (directory == null) latest.location.path else checkNotNull(relativeChangePath(directory, latest.location, historyStorage)),
                status = when { before.kind == SnapshotKind.ABSENT -> "A"; after.kind == SnapshotKind.ABSENT -> "D"; else -> "M" },
                additions = counts?.first ?: 0, deletions = counts?.second ?: 0,
                binary = before.kind == SnapshotKind.BINARY || after.kind == SnapshotKind.BINARY,
                records = history.asReversed(), aggregateRecords = aggregate, statisticsAvailable = counts != null,
            )
        }.sortedBy { it.displayPath }

    /** 删除在操作锁内按页推进，候选哈希集合不随全部历史增长。 */
    suspend fun removeRecords(ids: Set<String>): Int = operationMutex.withLock {
        withContext(Dispatchers.IO) { synchronized(this@WorkspaceChangeStore) {
            var removed = 0
            for (page in ids.asSequence().chunked(WorkspaceHistoryIndex.PAGE_SIZE)) {
                val records = withHistory { history -> page.mapNotNull(history::record) }
                removed += removePage(records)
            }
            removed
        } }
    }

    suspend fun removeFileRecords(removal: WorkspaceRecordRemoval): Int = operationMutex.withLock {
        if (removal.count == 0) return@withLock 0
        withContext(Dispatchers.IO) {
            var removed = 0
            while (true) {
                coroutineContext.ensureActive()
                val count = synchronized(this@WorkspaceChangeStore) {
                    val page = withHistory { it.recordsPage(removal.query) }
                    removePage(page)
                }
                if (count == 0) break
                removed += count
            }
            removed
        }
    }

    @Synchronized
    fun fileRecords(location: ChangeLocation, chatId: String?): List<FileChangeRecord> = withHistory { history -> buildList {
        var offset = 0
        while (true) {
            val page = history.recordsPage(WorkspaceHistoryQuery(chatId = chatId, file = location), offset, ascending = true)
            if (page.isEmpty()) break
            addAll(page)
            offset += page.size
        }
    } }

    private fun removePage(records: List<FileChangeRecord>): Int {
        if (records.isEmpty()) return 0
        val possible = records.flatMap { listOfNotNull(it.before.blob, it.after.blob) }.toSet()
        withHistory { history -> blobStore.commit { records.forEach { history.delete(it.id) } } }
        statistics.clear()
        revisionState.update { it + 1 }
        val retained = withHistory { it.referenced(possible) }.toMutableSet()
        synchronized(baselineIo) {
            if (observations.exists()) java.nio.file.Files.newDirectoryStream(observations.toPath(), "*.json").use { files ->
                for (file in files) com.google.gson.stream.JsonReader(java.nio.file.Files.newBufferedReader(file, Charsets.UTF_8)).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName()
                        val observed = json.decodeFromString<ObservedWorkspaceFile>(com.google.gson.JsonParser.parseReader(reader).toString())
                        observed.snapshot.blob?.takeIf { it in possible }?.let(retained::add)
                    }
                    reader.endObject()
                    check(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT)
                }
            }
        }
        if (observations.exists()) java.nio.file.Files.newDirectoryStream(observations.toPath(), "*.sqlite").use { files ->
            for (file in files) {
                val key = file.fileName.toString().removeSuffix(".sqlite")
                val active = workspaceIndexes[key]
                if (active != null) {
                    for (hash in possible) if (hash !in retained && active.containsBlob(hash)) retained += hash
                } else openDatabase(file.toFile(), false).use { database ->
                    for (hash in possible) if (hash !in retained && database.query(
                        "SELECT 1 FROM files WHERE blob=? UNION ALL SELECT 1 FROM preparing WHERE blob=? LIMIT 1", listOf(hash, hash),
                    ).use { it.next() }) retained += hash
                }
            }
        }
        retained += referencedTerminalBlobs(possible - retained)
        blobStore.remove(possible - retained)
        compactRequests.trySend(Unit)
        return records.size
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
        if (terminalObservations.exists()) java.nio.file.Files.newDirectoryStream(terminalObservations.toPath(), "*.sqlite").use { files ->
            for (file in files) {
                val key = file.fileName.toString().removeSuffix(".sqlite")
                val active = terminalIndexes[key]
                if (active != null) {
                    for (hash in candidates) if (hash !in retained && active.containsBlob(hash)) retained += hash
                } else openDatabase(file.toFile(), false).use { database ->
                    for (hash in candidates) if (hash !in retained && database.query(
                        "SELECT 1 FROM files WHERE blob=? UNION ALL SELECT 1 FROM preparing WHERE blob=? LIMIT 1", listOf(hash, hash),
                    ).use { it.next() }) retained += hash
                }
            }
        }
        return retained
    }

    internal suspend fun close() = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
        maintenance.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        maintenance.coroutineContext[kotlinx.coroutines.Job]?.join()
        compactRequests.close()
        synchronized(this@WorkspaceChangeStore) {
            terminalIndexes.values.forEach { it.close() }
            workspaceIndexes.values.forEach { it.close() }
            terminalIndexes.clear()
            workspaceIndexes.clear()
            blobStore.close()
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
