package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping
import com.ai.assistance.operit.ui.features.conversation.workspace.process.DepthLimitedFileObserver
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
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
        var setupFailure: Exception? = null
        var observer: DepthLimitedFileObserver? = null
        val pending = linkedMapOf<String, TerminalChangeOrigin?>()
        var needsSave = false
        @Volatile var excludedRoots: List<File> = emptyList()
    }
    internal class Execution(val origin: TerminalChangeOrigin) {
        val operationId: String = UUID.randomUUID().toString()
        var finished = false
        internal var tracked = emptyList<ChangeLocation>()
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

    suspend fun begin(origin: TerminalChangeOrigin): Execution {
        val execution = Execution(origin)
        try {
            withContext(Dispatchers.IO) {
                if (prepared) awaitEventsFor(origin.chatId)
                WorkspaceChangeStore.operationMutex.withLock {
                    prepare()
                    execution.tracked = store.trackedLinuxFiles(origin.chatId)
                    queueTrackedChanges(execution.tracked, source())
                    inspectPending()
                    executions[checkNotNull(origin.executionId)] = execution
                    prepared = true
                    val setupError = states.values.firstNotNullOfOrNull { it.setupFailure }
                    val unreadable = states.values.asSequence().flatMap { state ->
                        state.baseline.unreadable.asSequence().map { File(state.root.file, it).path }
                    }.take(3).toList()
                    if (setupError != null) report(origin.chatId, setupError)
                    else if (unreadable.isNotEmpty()) report(origin.chatId, IOException("Cannot observe: " + unreadable.joinToString()))
                    else failures.update { it - origin.chatId }
                }
            }
        } catch (cancelled: CancellationException) {
            // 切回调用线程时也可能取消，必须释放已经登记但尚未提交的执行。
            executions.remove(origin.executionId, execution)
            throw cancelled
        } catch (error: Exception) { report(origin.chatId, error) }
        return execution
    }

    private suspend fun awaitEventsFor(chatId: String) {
        try {
            awaitEvents()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            report(chatId, error)
        } catch (error: Exception) { report(chatId, error) }
    }

    suspend fun finish(execution: Execution) = withContext(NonCancellable + Dispatchers.IO) {
        if (execution.finished) return@withContext
        try {
            awaitEventsFor(execution.origin.chatId)
            WorkspaceChangeStore.operationMutex.withLock {
                if (executions[execution.origin.executionId] === execution) {
                    queueTrackedChanges(execution.tracked, source())
                }
                inspectPending()
                persistBaselines()
            }
        } catch (error: Exception) { report(execution.origin.chatId, error) }
        finally { execution.finished = true; executions.remove(execution.origin.executionId, execution) }
    }

    /** 核对当前对话已经记录过的文件；通知与退出检查共用同一份待处理路径和基线。 */
    private fun queueTrackedChanges(locations: List<ChangeLocation>, origin: TerminalChangeOrigin?) {
        for (location in locations) {
            val state = observedStates.filter { it.root.location.backend == location.backend &&
                changePathContains(it.root.location.path, location.path) }.maxByOrNull { it.root.location.path.length } ?: continue
            val name = location.path.removePrefix(state.root.location.path.trimEnd('/') + "/")
            if (name.isEmpty() || name.split('/').any { it == ".." }) continue
            val file = File(state.root.file, name)
            if (excluded(state, file)) continue
            val path = file.toPath()
            var parent = path.parent
            while (parent != null && parent != state.root.file.toPath() && !Files.isSymbolicLink(parent)) parent = parent.parent
            if (parent != state.root.file.toPath()) continue
            val old = state.baseline.files[name]
            val differs = try {
                val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                !attrs.isDirectory && (old?.stamp != terminalFileStamp(attrs) || old.snapshot.kind == SnapshotKind.UNAVAILABLE)
            } catch (_: NoSuchFileException) { old != null }
            catch (_: IOException) { true }
            if (differs) synchronized(state) {
                // 已有文件事件优先保留写入发生时的归属。
                if (name !in state.pending) state.pending[name] = origin
            }
        }
    }

    suspend fun interactiveInput(sessionId: String, origin: TerminalChangeOrigin) = withContext(Dispatchers.IO) {
        try {
            if (prepared) awaitEventsFor(origin.chatId)
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
            try {
                initializeState(state)
                state.setupFailure = null
            } catch (failure: Exception) {
                state.observer?.stop()
                state.observer = null
                state.setupFailure = failure
            }
        }
        observedStates = states.values.filter { it.initialized }
    }

    private fun initializeState(state: State) {
        val saved = store.terminalBaseline(state.key) ?: TerminalObservationBaseline()
        val observer = DepthLimitedFileObserver(state.root.file, Int.MAX_VALUE,
            shouldIgnore = { path, _ -> excluded(state, File(state.root.file, path)) },
            onChange = { event ->
                val origin = source()
                synchronized(state) {
                    val previous = state.pending[event.relativePath]
                    state.pending[event.relativePath] = if (previous != null && origin != null) previous.merge(origin) else origin ?: previous
                }
                signals.trySend(Unit)
            })
        state.observer = observer
        state.baseline = scanTerminalFiles(state.root.file, saved, excluded = { excluded(state, it) },
            onDirectory = { observer.watchDirectory(it) }, snapshotBytes = ::snapshotBytes)
        store.pinTerminalBaseline(state.key, state.baseline)
        state.initialized = true
        state.needsSave = true
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
                val candidates = if (pending.keys.all { it in previous.files && it in next.files }) pending.keys
                    else previous.files.keys + next.files.keys
                val changed = candidates.filter { path ->
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
        // 所有物理根已经规范化。逐项拆分并拼接完整路径会放大每个文件的遍历成本。
        fun contains(root: File) = path == root.path || path.startsWith(root.path + File.separator)
        if (excludedDirectories.any(::contains) || state.excludedRoots.any(::contains)) return true
        if (state.root.location.path == "/") {
            val first = relative.substringBefore('/')
            if (first == "dev" || first == "proc" || first == "sys") return true
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
