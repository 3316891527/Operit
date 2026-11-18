package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping
import com.ai.assistance.operit.ui.features.conversation.workspace.process.DepthLimitedFileObserver
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock

/** 共享终端的文件事件日志；观察范围、执行来源与工作区绑定相互独立。 */
internal class TerminalFileJournal internal constructor(
    private val context: Context,
    private val rootsProvider: () -> List<TerminalObservationRoot> = { localTerminalObservationRoots(context) },
    private val store: WorkspaceChangeStore = WorkspaceChangeStore.get(context),
    private val recorded: (List<FileChangeRecord>) -> Unit = WorkspaceFileMonitor::acknowledgeExisting,
) {
    private class State(val root: TerminalObservationRoot, val key: String) {
        var baseline = TerminalObservationBaseline()
        var initialized = false
        var observer: DepthLimitedFileObserver? = null
        val pending = linkedMapOf<String, TerminalChangeOrigin?>()
        var needsSave = false
        @Volatile var excludedRoots: List<File> = emptyList()
    }
    internal class Execution(val origin: TerminalChangeOrigin) {
        val operationId: String = UUID.randomUUID().toString()
        var finished = false
    }
    private val excludedDirectories = listOf(
        File(context.filesDir, "workspace_changes"), context.cacheDir, File(context.filesDir, "logs"),
        File(OperitPaths.downloadsDir(), "Operit/packageLogs"),
        File(context.applicationInfo.dataDir, "databases"), File(context.applicationInfo.dataDir, "shared_prefs"),
        File(context.applicationInfo.dataDir, "code_cache"), File(context.applicationInfo.dataDir, "no_backup"),
    ).map { it.canonicalFile }
    private val barrier = lazy { FileObservationBarrier(File(context.cacheDir, "terminal-file-events")) }
    private suspend fun awaitEvents() = barrier.value.await()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = linkedMapOf<String, State>()
    @Volatile private var prepared = false
    @Volatile private var observedStates: List<State> = emptyList()
    private val executions = ConcurrentHashMap<String, Execution>()
    private val interactive = ConcurrentHashMap<String, TerminalChangeOrigin>()
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = failures.asStateFlow()

    init {
        scope.launch {
            for (signal in signals) {
                delay(150)
                WorkspaceChangeStore.operationMutex.withLock { inspectPending() }
            }
        }
    }

    suspend fun begin(origin: TerminalChangeOrigin): Execution = withContext(Dispatchers.IO) {
        val execution = Execution(origin)
        try {
            if (prepared) awaitEvents()
            WorkspaceChangeStore.operationMutex.withLock {
                prepare()
                inspectPending()
                persistBaselines()
                executions[checkNotNull(origin.executionId)] = execution
                prepared = true
            }
            val unreadable = states.values.flatMap { state ->
                state.baseline.unreadable.map { File(state.root.file, it).path }
            }
            if (unreadable.isEmpty()) failures.update { it - origin.chatId }
            else report(origin.chatId, java.io.IOException("Cannot observe: " + unreadable.take(3).joinToString()))
        } catch (timeout: TimeoutCancellationException) { report(origin.chatId, timeout) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { report(origin.chatId, error) }
        execution
    }

    suspend fun finish(execution: Execution) = withContext(NonCancellable + Dispatchers.IO) {
        if (execution.finished) return@withContext
        try {
            // 保留执行来源直到先前文件通知全部入队，避免终端完成后的通知变成未知来源。
            awaitEvents()
            WorkspaceChangeStore.operationMutex.withLock {
                inspectPending()
                persistBaselines()
                execution.finished = true
            }
        } catch (error: Exception) { report(execution.origin.chatId, error) }
        finally { execution.finished = true; executions.remove(execution.origin.executionId, execution) }
    }

    suspend fun interactiveInput(sessionId: String, origin: TerminalChangeOrigin) = withContext(Dispatchers.IO) {
        try {
            if (prepared) awaitEvents()
            WorkspaceChangeStore.operationMutex.withLock {
                prepare()
                inspectPending()
                persistBaselines()
                interactive[sessionId] = origin
                prepared = true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { report(origin.chatId, error) }
    }

    fun closeSession(sessionId: String) { interactive.remove(sessionId) }

    /** 文件工具已保存的内容直接推进相应物理文件基线，不能再次算作终端操作。调用方持有操作锁。 */
    fun acknowledge(records: List<FileChangeRecord>) {
        for (record in records) {
            val file = hostFile(record.location) ?: continue
            val state = states.values.filter { changePathContains(it.root.file.path, file.path) }
                .maxByOrNull { it.root.file.path.length } ?: continue
            if (!state.initialized || excluded(state, file)) continue
            val path = state.root.file.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
            val next = state.baseline.files.toMutableMap()
            when (record.after.kind) {
                SnapshotKind.ABSENT -> next.keys.removeAll { it == path || it.startsWith("$path/") }
                SnapshotKind.DIRECTORY -> Unit
                else -> next[path] = TerminalObservedFile("", record.after)
            }
            state.baseline = state.baseline.copy(files = next)
            store.pinTerminalBaseline(state.key, state.baseline)
            state.needsSave = true
        }
    }

    internal fun ownsActiveFile(file: File): Boolean = source() != null && observedStates.any { state ->
        changePathContains(state.root.file.path, file.absolutePath) && !excluded(state, file)
    }

    private fun source(): TerminalChangeOrigin? = terminalChangeOrigin(
        executions.values.map { it.origin }.ifEmpty { interactive.values.toList() },
    )

    private fun prepare() {
        val roots = rootsProvider()
        check(roots.isNotEmpty()) { "No accessible local terminal observation roots" }
        for (root in roots) {
            val key = MessageDigest.getInstance("SHA-256").digest((root.location.key + "\u0000" + root.file.path)
                .toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            states.getOrPut(key) { State(root, key) }
        }
        for (state in states.values) {
            state.excludedRoots = states.values.filter { it !== state && it.root.file.path != state.root.file.path &&
                changePathContains(state.root.file.path, it.root.file.path) }.map { it.root.file }
        }
        for (state in states.values) {
            if (state.initialized) continue
            val saved = store.terminalBaseline(state.key) ?: TerminalObservationBaseline()
            state.observer ?: DepthLimitedFileObserver(state.root.file, Int.MAX_VALUE,
                shouldIgnore = { path, _ -> excluded(state, File(state.root.file, path)) },
                onChange = { event ->
                    val origin = source()
                    synchronized(state) {
                        val previous = state.pending[event.relativePath]
                        state.pending[event.relativePath] = if (previous != null && origin != null) previous.merge(origin) else origin ?: previous
                    }
                    signals.trySend(Unit)
                }).also { state.observer = it; it.start() }
            state.baseline = scanTerminalFiles(state.root.file, saved, excluded = { excluded(state, it) }, snapshotBytes = ::snapshotBytes)
            store.pinTerminalBaseline(state.key, state.baseline)
            state.initialized = true
            state.needsSave = true
            // 初次快照只建立基线；启动前的文件不归入本次终端创建记录。
        }
        observedStates = states.values.toList()
    }

    private fun inspectPending() {
        for (state in states.values) {
            if (!state.initialized) continue
            val pending = synchronized(state) { state.pending.toMap().also { state.pending.clear() } }
            if (pending.isEmpty()) continue
            try {
                val previous = state.baseline
                val next = scanTerminalFiles(state.root.file, previous, pending.keys,
                    excluded = { excluded(state, it) }, snapshotBytes = ::snapshotBytes)
                val changed = (previous.files.keys + next.files.keys).filter { path ->
                    val after = next.snapshot(path)
                    previous.snapshot(path) != after ||
                        (after.kind in setOf(SnapshotKind.TOO_LARGE, SnapshotKind.UNAVAILABLE) &&
                            previous.files[path]?.stamp != next.files[path]?.stamp)
                }
                val grouped = linkedMapOf<TerminalChangeOrigin, MutableList<FileChangeRecord>>()
                for (path in changed) {
                    if (excluded(state, File(state.root.file, path))) continue
                    val origin = pending.entries.filter { (parent, _) -> parent.isEmpty() || parent == path || path.startsWith("$parent/") }
                        .maxByOrNull { it.key.length }?.value ?: continue
                    val location = state.root.location.copy(path = changePath(state.root.location.path + "/" + path))
                    val record = FileChangeRecord(UUID.randomUUID().toString(), origin.executionId ?: UUID.randomUUID().toString(),
                        origin.chatId, WorkspaceChangeStore.nextTimestamp(), origin.toolName, location,
                        previous.snapshot(path), next.snapshot(path), executionId = origin.executionId, sourceKind = "terminal")
                    grouped.getOrPut(origin) { mutableListOf() }.add(record)
                }
                for ((origin, changes) in grouped) save(origin, changes)
                state.baseline = next
                store.pinTerminalBaseline(state.key, next)
                state.needsSave = true
                if (next.unreadable.isNotEmpty()) {
                    val origin = terminalChangeOrigin(pending.values.filterNotNull())
                    if (origin != null) report(origin.chatId, java.io.IOException("Cannot observe: " + next.unreadable.take(3).joinToString()))
                }
            } catch (error: Exception) {
                synchronized(state) { pending.forEach { (path, origin) -> state.pending.putIfAbsent(path, origin) } }
                pending.values.filterNotNull().map { it.chatId }.distinct().forEach { report(it, error) }
            }
        }
    }

    private fun save(origin: TerminalChangeOrigin, changes: List<FileChangeRecord>) {
        val execution = origin.executionId?.let { executions[it] }
        if (execution == null) {
            val operation = UUID.randomUUID().toString()
            val saved = changes.map { statistics(it.copy(operationId = operation)) }
            store.save(saved)
            recorded(saved)
            return
        }
        // 长任务可逐批更新界面，同一次执行的同一文件保留最早 before 和最新 after。
        val next = store.operationRecords(execution.operationId).associateBy { it.location.key }.toMutableMap()
        for (record in changes) {
            val prior = next[record.location.key]
            val merged = if (prior == null) record.copy(operationId = execution.operationId) else record.copy(
                id = prior.id, operationId = execution.operationId, timestamp = prior.timestamp, before = prior.before)
            val comparable = merged.before.kind !in setOf(SnapshotKind.TOO_LARGE, SnapshotKind.UNAVAILABLE)
            if (comparable && merged.before == merged.after) next.remove(record.location.key)
            else next[record.location.key] = statistics(merged)
        }
        store.replaceOperation(execution.operationId, next.values.toList())
        recorded(changes)
    }

    private fun statistics(record: FileChangeRecord): FileChangeRecord {
        val content = store.content(record)
        return if (reviewDiffAutomatic(content)) WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after)).let {
            record.copy(additions = it.additions, deletions = it.deletions)
        } else record
    }

    private fun snapshotBytes(bytes: ByteArray): ChangeSnapshot = when {
        bytes.size > CHANGE_SNAPSHOT_LIMIT -> ChangeSnapshot(SnapshotKind.TOO_LARGE, bytes.size.toLong())
        decodeChangeText(bytes) == null -> ChangeSnapshot(SnapshotKind.BINARY, bytes.size.toLong(), store.putBlob(bytes))
        else -> ChangeSnapshot(SnapshotKind.TEXT, bytes.size.toLong(), store.putBlob(bytes))
    }

    private fun persistBaselines() {
        for (state in states.values.filter { it.needsSave }) {
            store.saveTerminalBaseline(state.key, state.baseline)
            state.needsSave = false
        }
    }

    private fun excluded(state: State, file: File): Boolean {
        val path = file.absolutePath
        val relative = state.root.file.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
        if (relative.split('/').any { it == ".git" }) return true
        if (excludedDirectories.any { changePathContains(it.path, path) }) return true
        if (state.excludedRoots.any { changePathContains(it.path, path) }) return true
        if (state.root.location.path == "/") {
            val relative = state.root.file.toPath().relativize(file.toPath()).toString().substringBefore('/')
            if (relative in setOf("dev", "proc", "sys")) return true
        }
        return false
    }

    private fun hostFile(location: ChangeLocation): File? = when (location.backend) {
        "android" -> File(location.path).canonicalFile
        "linux:local" -> File(PRootMountMapping.mapLinuxPathToHostPath(location.path,
            File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu"), context.filesDir.path,
            context.applicationInfo.dataDir, context.packageName,
            context.getSharedPreferences("terminal_settings", Context.MODE_PRIVATE).getBoolean("chroot_enabled", false))).canonicalFile
        else -> null
    }

    internal suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        WorkspaceChangeStore.operationMutex.withLock {
            try {
                persistBaselines()
            } finally {
                states.values.forEach { it.observer?.stop() }
                states.clear()
                observedStates = emptyList()
                executions.clear()
                interactive.clear()
                signals.close()
                scope.cancel()
                if (barrier.isInitialized()) barrier.value.close()
                if (instance === this@TerminalFileJournal) instance = null
            }
        }
    }

    private fun report(chatId: String, error: Exception) {
        AppLogger.e("TerminalFileJournal", "终端文件变化采集失败", error)
        failures.update { it + (chatId to (error.message ?: error.javaClass.simpleName)) }
    }

    companion object {
        @Volatile private var instance: TerminalFileJournal? = null
        fun acknowledgeExisting(records: List<FileChangeRecord>) { instance?.acknowledge(records) }
        fun observesActiveFile(file: File): Boolean = instance?.ownsActiveFile(file) == true
        fun get(context: Context): TerminalFileJournal = instance ?: synchronized(this) {
            instance ?: TerminalFileJournal(context.applicationContext).also { instance = it }
        }
    }
}
