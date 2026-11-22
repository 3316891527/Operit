package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping
import com.ai.assistance.operit.ui.features.conversation.workspace.process.DepthLimitedFileObserver
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.io.IOException
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
    private val preparationDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val commandPreparationWaitMs: Long = 50,
) {
    private data class PendingChange(val origin: TerminalChangeOrigin?, val createdInPlace: Boolean = false)

    private class State(val root: TerminalObservationRoot, val key: String) {
        var baseline = TerminalObservationBaseline()
        var initialized = false
        var setupFailure: Exception? = null
        var observer: DepthLimitedFileObserver? = null
        val pending = linkedMapOf<String, PendingChange>()
        // 冷启动期间已核对的路径覆盖后台扫描结果，包括已经删除的路径。
        val startupPaths = linkedSetOf<String>()
        var needsSave = false
        @Volatile var excludedRoots: List<File> = emptyList()
    }
    private data class BaselineWrite(val state: State, val baseline: TerminalObservationBaseline)

    internal class Execution(val origin: TerminalChangeOrigin) {
        val operationId: String = UUID.randomUUID().toString()
        @Volatile var acceptsEvents = true
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
    private var preparation: Job? = null
    @Volatile private var closed = false
    @Volatile private var prepared = false
    @Volatile private var observedStates: List<State> = emptyList()
    private val executions = ConcurrentHashMap<String, Execution>()
    private val interactive = ConcurrentHashMap<String, TerminalChangeOrigin>()
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val persistSignals = Channel<Unit>(Channel.CONFLATED)
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = failures.asStateFlow()

    init {
        scope.launch {
            for (signal in signals) {
                delay(150)
                WorkspaceChangeStore.operationMutex.withLock { inspectPending() }
            }
        }
        scope.launch {
            for (signal in persistSignals) {
                delay(150)
                val writes = WorkspaceChangeStore.operationMutex.withLock { takeBaselineWrites() }
                val failed = writeBaselines(writes)
                if (failed.isNotEmpty()) WorkspaceChangeStore.operationMutex.withLock {
                    failed.forEach { it.needsSave = true }
                }
            }
        }
    }

    suspend fun begin(origin: TerminalChangeOrigin, workingDirectory: String? = null): Execution {
        val execution = Execution(origin)
        val alreadyWatching = prepared
        val observation = prepareForUse(origin.chatId, workingDirectory)
        val snapshots = scope.async {
            if (!observation.await()) return@async emptyList()
            if (alreadyWatching) awaitEventsFor(origin.chatId)
            val captureContext = currentCoroutineContext()
            WorkspaceChangeStore.operationMutex.withLock {
                if (closed) return@withLock emptyList()
                val tracked = store.trackedLocalFiles(origin.chatId)
                captureContext.ensureActive()
                queueTrackedPaths(tracked, source(), watchParent = true)
                inspectPending { captureContext.ensureActive() }
                tracked
            }
        }
        try {
            // 固定等待窗口内完成命令前快照。观察器初始化独立在后台继续。
            execution.tracked = withTimeoutOrNull(commandPreparationWaitMs) { snapshots.await() }.orEmpty()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { report(origin.chatId, error) }
        finally { snapshots.cancel() }
        val id = checkNotNull(origin.executionId)
        executions[id] = execution
        if (closed) executions.remove(id, execution)
        return execution
    }

    private fun prepareForUse(chatId: String, workingDirectory: String?): Deferred<Boolean> = scope.async {
        try {
            WorkspaceChangeStore.operationMutex.withLock {
                if (closed) return@withLock false
                prepareObservers()
                watchWorkingDirectory(workingDirectory)
                prepared = true
                prepareBaselinesInBackground()
                val error = states.values.firstNotNullOfOrNull { it.setupFailure }
                val unreadable = states.values.asSequence().flatMap { state ->
                    state.baseline.unreadable.asSequence().map { File(state.root.file, it).path }
                }.take(3).toList()
                if (error != null) report(chatId, error)
                else if (unreadable.isNotEmpty()) report(chatId, IOException("Cannot observe: " + unreadable.joinToString()))
                else failures.update { it - chatId }
                true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            report(chatId, error)
            false
        }
    }

    private suspend fun awaitEventsFor(chatId: String) {
        try {
            awaitEvents()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            report(chatId, error)
        } catch (error: Exception) { report(chatId, error) }
    }

    /** 完成事件直接交给调用方，文件记录由日志协程继续收集。 */
    fun finish(execution: Execution): Job = scope.launch {
        val started = System.nanoTime()
        try {
            awaitEventsFor(execution.origin.chatId)
            val origin = source()
            execution.acceptsEvents = false
            val captureContext = currentCoroutineContext()
            WorkspaceChangeStore.operationMutex.withLock {
                if (executions[execution.origin.executionId] === execution) {
                    queueTrackedPaths(execution.tracked, origin)
                }
                inspectPending { captureContext.ensureActive() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { report(execution.origin.chatId, error) }
        finally {
            executions.remove(execution.origin.executionId, execution)
            AppLogger.d("TerminalFileJournal", "终端命令 ${execution.origin.executionId} 观察收尾=${(System.nanoTime() - started) / 1_000_000}ms")
        }
    }

    /** 以观察根的实际相对路径核对当前对话的文件，前后读取使用同一份快照逻辑。 */
    private fun queueTrackedPaths(locations: List<ChangeLocation>, origin: TerminalChangeOrigin?, watchParent: Boolean = false) {
        for (location in locations) {
            val target = observedStates.mapNotNull { state ->
                relativeChangePath(state.root.location, location)?.let { state to it }
            }.minByOrNull { (_, path) -> path.length } ?: continue
            val (state, path) = target
            val file = File(state.root.file, path)
            if (path.isEmpty() || excluded(state, file)) continue
            if (watchParent && !state.initialized) file.parentFile?.let { state.observer?.watchDirectory(it) }
            synchronized(state) {
                // 已有明确来源的通知保留归属，空来源的通知也参与本次实际内容对比。
                val pending = state.pending[path]
                if (pending?.origin == null) state.pending[path] = PendingChange(origin, pending?.createdInPlace == true)
            }
        }
    }

    suspend fun interactiveInput(sessionId: String, origin: TerminalChangeOrigin, workingDirectory: String? = null) {
        val observation = prepareForUse(origin.chatId, workingDirectory)
        withTimeoutOrNull(commandPreparationWaitMs) { observation.await() }
        interactive[sessionId] = origin
        if (closed) interactive.remove(sessionId, origin)
    }

    fun closeSession(sessionId: String) { interactive.remove(sessionId) }

    /** 文件工具已保存的内容直接推进相应物理文件基线，不能再次算作终端操作。调用方持有操作锁。 */
    fun acknowledge(records: List<FileChangeRecord>) {
        for (record in records) {
            val file = hostFile(record.location) ?: continue
            val state = states.values.filter { changePathContains(it.root.file.path, file.path) }
                .maxByOrNull { it.root.file.path.length } ?: continue
            if (state.observer == null || excluded(state, file)) continue
            val path = state.root.file.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
            val next = state.baseline.files.toMutableMap()
            when (record.after.kind) {
                SnapshotKind.ABSENT -> next.keys.removeAll { it == path || it.startsWith("$path/") }
                SnapshotKind.DIRECTORY -> Unit
                else -> next[path] = TerminalObservedFile("", record.after)
            }
            updateBaseline(state, state.baseline.copy(files = next), setOf(path))
        }
    }

    internal fun ownsActiveFile(file: File): Boolean = source() != null && observedStates.any { state ->
        if (!changePathContains(state.root.file.path, file.absolutePath) || excluded(state, file)) return@any false
        if (file.parentFile?.let { state.observer?.isWatchingDirectory(it) } == true) return@any true
        // 终端已有父目录事件时，会在事件批次中采集整棵新子树。
        val path = file.absolutePath.removePrefix(state.root.file.path).trimStart('/')
        synchronized(state) {
            anyObservationAncestor(path) { state.pending[it]?.origin != null }
        }
    }

    private fun source(): TerminalChangeOrigin? = terminalChangeOrigin(
        executions.values.filter { it.acceptsEvents }.map { it.origin }.ifEmpty { interactive.values.toList() },
    )

    private fun prepareObservers() {
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
            if (state.observer != null) continue
            try {
                startObserver(state)
                state.setupFailure = null
            } catch (failure: Exception) {
                state.observer?.stop()
                state.observer = null
                state.setupFailure = failure
            }
        }
        observedStates = states.values.filter { it.observer != null }
    }

    private fun startObserver(state: State) {
        val observer = DepthLimitedFileObserver(state.root.file, Int.MAX_VALUE,
            shouldIgnore = { path, _ -> excluded(state, File(state.root.file, path)) },
            discoverDescendantsOnEvent = false,
            onChange = { event ->
                val origin = source()
                synchronized(state) {
                    val previous = state.pending[event.relativePath]
                    val previousOrigin = previous?.origin
                    val mergedOrigin = if (previousOrigin != null && origin != null) previousOrigin.merge(origin) else origin ?: previousOrigin
                    state.pending[event.relativePath] = PendingChange(mergedOrigin, previous?.createdInPlace ?: event.createdInPlace)
                }
                signals.trySend(Unit)
            })
        state.observer = observer
        // 这里只注册固定数量的目录，递归注册和基线读取由准备任务负责。
        observer.watchDirectory(state.root.file)
        val directories = when (state.root.location.path) {
            "/" -> listOf("root", "home", "tmp")
            "/sdcard" -> listOf("Download", "Documents")
            else -> emptyList()
        }
        directories.forEach { observer.watchDirectory(File(state.root.file, it)) }
    }

    private fun watchWorkingDirectory(workingDirectory: String?) {
        if (workingDirectory == null || !workingDirectory.startsWith('/')) return
        val location = ChangeLocation(workingDirectory, "linux", "linux:local")
        val (state, path) = observedStates.mapNotNull { state ->
            relativeChangePath(state.root.location, location)?.let { state to it }
        }.minByOrNull { (_, path) -> path.length } ?: return
        if (!state.initialized) state.observer?.watchDirectory(File(state.root.file, path))
    }

    private fun prepareBaselinesInBackground() {
        if (preparation?.isActive == true) return
        val unprepared = observedStates.filterNot { it.initialized }
        if (unprepared.isEmpty()) return
        preparation = scope.launch(preparationDispatcher) {
            for (state in unprepared) initializeBaseline(state)
        }
    }

    private suspend fun initializeBaseline(state: State) {
        val observer = checkNotNull(state.observer)
        val coroutineContext = currentCoroutineContext()
        val started = System.nanoTime()
        try {
            val saved = store.terminalBaseline(state.key) ?: TerminalObservationBaseline()
            val scanned = scanTerminalFiles(state.root.file, saved,
                excluded = { excluded(state, it) }, onDirectory = { observer.watchDirectory(it) },
                checkCancelled = { coroutineContext.ensureActive() }, snapshotBytes = { snapshotBytes(it, state.key) })
            // 先处理扫描期间已到达的事件，防止初次扫描读到写入后的内容并覆盖原快照。
            awaitEvents()
            WorkspaceChangeStore.operationMutex.withLock {
                if (closed) return@withLock
                inspectPending(state)
                val baseline = if (state.startupPaths.isEmpty()) scanned else {
                    val touched = state.startupPaths
                    val files = scanned.files.toMutableMap()
                    files.keys.removeAll { anyObservationAncestor(it, touched::contains) }
                    files.putAll(state.baseline.files)
                    val unreadable = scanned.unreadable.filterNot { anyObservationAncestor(it, touched::contains) }
                    TerminalObservationBaseline(files, (unreadable + state.baseline.unreadable).toSet())
                }
                state.initialized = true
                state.startupPaths.clear()
                state.setupFailure = null
                updateBaseline(state, baseline)
            }
            AppLogger.d("TerminalFileJournal", "终端观察初始化 ${state.root.location.path} 条目=${scanned.files.size} 耗时=${(System.nanoTime() - started) / 1_000_000}ms")
        } catch (error: Exception) {
            coroutineContext.ensureActive()
            WorkspaceChangeStore.operationMutex.withLock { state.setupFailure = error }
            AppLogger.e("TerminalFileJournal", "终端观察初始化失败 ${state.root.location.path}", error)
        } finally {
            store.releasePreparingTerminalBlobs(state.key)
        }
    }

    private fun updateBaseline(state: State, baseline: TerminalObservationBaseline, paths: Set<String> = emptySet()) {
        if (!state.initialized) state.startupPaths.addAll(paths)
        if (baseline === state.baseline) return
        state.baseline = baseline
        store.pinTerminalBaseline(state.key, baseline)
        if (state.initialized) {
            state.needsSave = true
            persistSignals.trySend(Unit)
        }
    }

    private fun inspectPending(checkCancelled: () -> Unit = {}) {
        observedStates.forEach { inspectPending(it, checkCancelled) }
    }

    private fun inspectPending(state: State, checkCancelled: () -> Unit = {}) {
        checkCancelled()
        val pending = synchronized(state) { state.pending.toMap().also { state.pending.clear() } }
        if (pending.isEmpty()) return
        try {
            val previous = state.baseline
            val observer = checkNotNull(state.observer)
            val next = scanTerminalFiles(state.root.file, previous, pending.keys,
                excluded = { excluded(state, it) }, onDirectory = { observer.watchDirectory(it) },
                checkCancelled = checkCancelled, snapshotBytes = { snapshotBytes(it) })
            val grouped = linkedMapOf<TerminalChangeOrigin, MutableList<FileChangeRecord>>()
            if (pending.values.any { it.origin != null }) {
                val candidates = if (pending.keys.all { it in previous.files && it in next.files }) pending.keys else
                    (previous.files.keys.asSequence() + next.files.keys.asSequence())
                        .filter { anyObservationAncestor(it, pending::containsKey) }.toSet()
                for (path in candidates) {
                    checkCancelled()
                    if (excluded(state, File(state.root.file, path))) continue
                    var closest: PendingChange? = null
                    var createdInPlace = false
                    anyObservationAncestor(path) { ancestor ->
                        val event = pending[ancestor] ?: return@anyObservationAncestor false
                        if (closest == null) closest = event
                        createdInPlace = createdInPlace || event.createdInPlace
                        createdInPlace
                    }
                    val origin = closest?.origin ?: continue
                    val previousSnapshot = previous.snapshot(path)
                    val after = next.snapshot(path)
                    if (previousSnapshot == after &&
                        (after.kind !in setOf(SnapshotKind.TOO_LARGE, SnapshotKind.UNAVAILABLE) ||
                            previous.files[path]?.stamp == next.files[path]?.stamp)) continue
                    // 冷启动尚未读过的原文件没有可靠快照。明确的 CREATE 仍可记录为新建。
                    val before = if (!state.initialized && !createdInPlace && previousSnapshot.kind == SnapshotKind.ABSENT &&
                        !anyObservationAncestor(path, state.startupPaths::contains)) {
                        ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "Observation baseline was not ready")
                    } else previousSnapshot
                    val location = state.root.location.copy(path = changePath(state.root.location.path + "/" + path))
                    val record = FileChangeRecord(UUID.randomUUID().toString(), origin.executionId ?: UUID.randomUUID().toString(),
                        origin.chatId, WorkspaceChangeStore.nextTimestamp(), origin.toolName, location,
                        before, after, executionId = origin.executionId, sourceKind = "terminal",
                        subagentExecutionId = origin.subagentExecutionId)
                    grouped.getOrPut(origin) { mutableListOf() }.add(record)
                }
            }
            checkCancelled()
            for ((origin, changes) in grouped) save(origin, changes)
            updateBaseline(state, next, pending.keys)
            if (next.unreadable.isNotEmpty()) {
                val origin = terminalChangeOrigin(pending.values.mapNotNull { it.origin })
                if (origin != null) report(origin.chatId, IOException("Cannot observe: " + next.unreadable.take(3).joinToString()))
            }
        } catch (error: Exception) {
            synchronized(state) { pending.forEach { (path, change) -> state.pending.putIfAbsent(path, change) } }
            if (error is CancellationException) {
                signals.trySend(Unit)
                throw error
            }
            pending.values.mapNotNull { it.origin?.chatId }.distinct().forEach { report(it, error) }
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
        val next = store.operationRecords(execution.operationId).associateBy { it.location.historyLocation().key }.toMutableMap()
        for (record in changes) {
            val key = record.location.historyLocation().key
            val prior = next[key]
            val merged = if (prior == null) record.copy(operationId = execution.operationId) else record.copy(
                id = prior.id, operationId = execution.operationId, timestamp = prior.timestamp, before = prior.before)
            val comparable = merged.before.kind !in setOf(SnapshotKind.TOO_LARGE, SnapshotKind.UNAVAILABLE)
            if (comparable && merged.before == merged.after) next.remove(key)
            else next[key] = statistics(merged)
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

    private fun snapshotBytes(bytes: ByteArray, preparingKey: String? = null): ChangeSnapshot = when {
        bytes.size > CHANGE_SNAPSHOT_LIMIT -> ChangeSnapshot(SnapshotKind.TOO_LARGE, bytes.size.toLong())
        decodeChangeText(bytes) == null -> ChangeSnapshot(SnapshotKind.BINARY, bytes.size.toLong(), store.putBlob(bytes, preparingKey))
        else -> ChangeSnapshot(SnapshotKind.TEXT, bytes.size.toLong(), store.putBlob(bytes, preparingKey))
    }

    /** 锁内只取清单引用，序列化和磁盘 I/O 不占用终端采集锁。 */
    private fun takeBaselineWrites(): List<BaselineWrite> = states.values.filter { it.needsSave }.map { state ->
        state.needsSave = false
        store.pinWritingTerminalBaseline(state.key, state.baseline)
        BaselineWrite(state, state.baseline)
    }

    private fun writeBaselines(writes: List<BaselineWrite>): List<State> = buildList {
        for ((state, baseline) in writes) {
            try {
                store.saveTerminalBaseline(state.key, baseline)
            } catch (error: Exception) {
                add(state)
                AppLogger.e("TerminalFileJournal", "终端观察基线写入失败 ${state.root.location.path}", error)
            } finally {
                store.pinWritingTerminalBaseline(state.key, null)
            }
        }
    }

    private fun excluded(state: State, file: File): Boolean {
        val path = file.absolutePath
        val relative = path.removePrefix(state.root.file.path).trimStart('/')
        if (relative == ".git" || relative.startsWith(".git/") || relative.endsWith("/.git") || "/.git/" in relative) return true
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

    internal suspend fun close(): Unit = withContext(NonCancellable + Dispatchers.IO) {
        WorkspaceChangeStore.operationMutex.withLock { closed = true }
        scope.coroutineContext.job.cancelAndJoin()
        val writes = WorkspaceChangeStore.operationMutex.withLock {
            val baselines = takeBaselineWrites()
            states.values.forEach { it.observer?.stop() }
            states.clear()
            observedStates = emptyList()
            executions.clear()
            interactive.clear()
            signals.close()
            persistSignals.close()
            if (barrier.isInitialized()) barrier.value.close()
            baselines
        }
        writeBaselines(writes)
        if (instance === this@TerminalFileJournal) instance = null
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
