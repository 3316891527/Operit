package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping
import com.ai.assistance.operit.ui.features.conversation.workspace.git.WorkspaceGitCommand
import com.ai.assistance.operit.ui.features.conversation.workspace.process.DepthLimitedFileObserver
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 唯一的历史观察器：每个任务只观察仍有对话绑定的工作区。 */
internal class WorkspaceFileMonitor internal constructor(
    private val context: Context,
    storeProvider: () -> WorkspaceChangeStore = { WorkspaceChangeStore.get(context) },
    private val beforeScan: suspend () -> Unit = {},
    private val originProvider: (String, ChangeLocation) -> TerminalChangeOrigin? = TerminalFileJournal::originFor,
) {
    private data class Binding(val chatId: String, val location: ChangeLocation)
    private class State(val binding: Binding, val key: String, val local: File?) {
        @Volatile var initialized = false
        @Volatile var index: TerminalObservationIndex? = null
        @Volatile var observer: DepthLimitedFileObserver? = null
        val restartObserver = AtomicBoolean(false)
        val fullScan = AtomicBoolean(true)
        val requests = Channel<Unit>(Channel.CONFLATED)
        val completions = ConcurrentLinkedQueue<CompletableDeferred<Unit>>()
        lateinit var job: Job
    }
    private val store by lazy(storeProvider)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = Mutex()
    private val states = linkedMapOf<String, State>()
    private val owners = linkedMapOf<String, String>()
    @Volatile private var activeStates: List<State> = emptyList()
    @Volatile private var closed = false
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = failures.asStateFlow()

    /** 空绑定只释放旧任务，不初始化存储、文件后端或扫描协程。 */
    suspend fun updateOwner(ownerId: String, chatId: String?, workspacePath: String?, workspaceEnv: String?) {
        val recording = WorkspaceRecordingScope.create(chatId, workspacePath, workspaceEnv)
        val binding = recording?.let {
            withContext(Dispatchers.IO) {
                Binding(it.chatId, WorkspaceChangeAccess(context).location(it.path, it.environment))
            }
        }
        lifecycle.withLock {
            if (closed) return
            owners.remove(ownerId)
            val nextKey = binding?.let(::key)
            if (nextKey != null) owners[ownerId] = nextKey
            val retired = states.values.filter { it.key !in owners.values }
            retired.forEach {
                states.remove(it.key)
                it.job.cancel()
                TerminalFileJournal.forgetBinding(it.binding.chatId, it.binding.location)
            }
            activeStates = states.values.toList()
            // 同一路径再次绑定前等待旧任务释放索引，取消切换也必须完成资源交接。
            withContext(NonCancellable) { retired.forEach { it.job.join() } }
            currentCoroutineContext().ensureActive()
            if (binding != null && nextKey != null && nextKey !in states) {
                val state = State(binding, nextKey, hostFile(binding.location))
                state.job = scope.launch(start = CoroutineStart.LAZY) { observe(state) }
                states[state.key] = state
                activeStates = states.values.toList()
                state.job.start()
            }
        }
    }
    suspend fun clearOwner(ownerId: String) = withContext(NonCancellable) { updateOwner(ownerId, null, null, null) }

    fun hasBinding(recording: WorkspaceRecordingScope): Boolean = bindingIdentity(recording) != null

    fun bindingIdentity(recording: WorkspaceRecordingScope): Any? = activeStates.firstOrNull {
        recording.matches(it.binding.chatId, it.binding.location) && it.job.isActive
    }

    /** 命令收尾只请求绑定目录核对；解绑后直接结束，不能重新启动旧观察。 */
    suspend fun reconcile(recording: WorkspaceRecordingScope, identity: Any? = null) {
        val state = activeStates.firstOrNull { recording.matches(it.binding.chatId, it.binding.location) } ?: return
        if (identity != null && state !== identity) return
        val completion = CompletableDeferred<Unit>()
        state.completions += completion
        state.fullScan.set(true)
        if (state.requests.trySend(Unit).isFailure) {
            state.completions.remove(completion)
            return
        }
        completion.await()
    }

    /** 调用方只在操作锁内标记路径，不等待目录读取或事件收集完成。 */
    fun invalidate(locations: List<ChangeLocation>) {
        for (state in activeStates) {
            val index = state.index ?: continue
            val paths = mutationObservationPaths(state.binding.location, locations)
            index.invalidate(paths)
            for (path in paths) index.enqueue(path, TerminalPendingChange(source(state)), onlyWhenUnowned = true)
            if (paths.isNotEmpty()) state.requests.trySend(Unit)
        }
    }

    fun acknowledge(records: List<FileChangeRecord>) {
        for (state in activeStates) {
            val index = state.index ?: continue
            for (record in records) {
                if (record.chatId != state.binding.chatId) continue
                val path = relativeChangePath(state.binding.location, record.location) ?: continue
                index.acknowledge(path, record.after, initializing = !state.initialized)
            }
        }
    }

    private suspend fun observe(state: State) {
        var opened = false
        try {
            val index = WorkspaceChangeStore.operationMutex.withLock {
                store.workspaceIndex(state.key).also {
                    opened = true
                    state.index = it
                    state.initialized = it.initialized()
                }
            }
            val cancellation = currentCoroutineContext()
            store.importWorkspaceBaseline(state.key) { cancellation.ensureActive() }
            WorkspaceChangeStore.operationMutex.withLock { state.initialized = index.initialized() }
            state.requests.trySend(Unit)
            while (currentCoroutineContext().isActive) {
                val request = withTimeoutOrNull(if (state.local == null) 10_000L else 60_000L) { state.requests.receive() }
                if (request == null && state.local != null && !state.restartObserver.get()) continue
                if (request != null) delay(100)
                val completions = buildList {
                    while (true) add(state.completions.poll() ?: break)
                }
                try {
                    inspect(state, index, state.fullScan.getAndSet(false) || request == null)
                } finally { completions.forEach { it.complete(Unit) } }
            }
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) {
            AppLogger.e("WorkspaceFileMonitor", "建立绑定工作区观察失败", error)
            failures.update { it + (state.binding.chatId to (error.message ?: error.javaClass.simpleName)) }
        } finally {
            withContext(NonCancellable) {
                state.requests.close()
                while (true) state.completions.poll()?.complete(Unit) ?: break
                state.observer?.stop()
                WorkspaceChangeStore.operationMutex.withLock {
                    if (opened) {
                        state.index = null
                        store.releaseWorkspaceIndex(state.key)
                    }
                }
                if (!state.job.isActive) failures.update { it - state.binding.chatId }
            }
        }
    }

    private fun source(state: State): TerminalChangeOrigin {
        originProvider(state.binding.chatId, state.binding.location)?.let { return it }
        val manual = TerminalManager.getInstance(context).terminalState.value.sessions.any {
            !it.automation && it.currentExecutingCommand?.isExecuting == true
        }
        return TerminalChangeOrigin(state.binding.chatId, if (manual) "terminal" else "filesystem", null)
    }

    private fun ensureObserver(state: State, index: TerminalObservationIndex) {
        val root = state.local ?: return
        if (state.restartObserver.getAndSet(false)) {
            state.observer?.stop()
            state.observer = null
            state.fullScan.set(true)
        }
        if (state.observer != null || !root.isDirectory) return
        state.observer = DepthLimitedFileObserver(root, Int.MAX_VALUE,
            shouldIgnore = { path, _ -> !observedWorkspacePath(path) || isHistoryStorage(File(root, path)) },
            discoverDescendantsOnEvent = false,
            onChange = { event ->
                if (event.relativePath.isEmpty()) state.restartObserver.set(true)
                try {
                    index.enqueue(event.relativePath, TerminalPendingChange(source(state), event.createdInPlace))
                    state.requests.trySend(Unit)
                } catch (error: Exception) {
                    AppLogger.e("WorkspaceFileMonitor", "工作区事件入队失败", error)
                }
            }).also { it.watchDirectory(root) }
    }

    private suspend fun inspect(state: State, index: TerminalObservationIndex, forceFull: Boolean) {
        val pending = index.takePending()
        try {
            ensureObserver(state, index)
            val selected = if (state.initialized && !forceFull && pending.isNotEmpty() && state.local != null) pending.keys else null
            index.beginScan(selected).use { scan ->
                beforeScan()
                scan(state, index, pending.keys, scan, selected)
                if (state.initialized || pending.isNotEmpty()) saveChanges(state, index, scan, pending)
                scan.flush()
                WorkspaceChangeStore.operationMutex.withLock {
                    scan.excludeActiveWrites(state.binding.location)
                    scan.commit(completeInitialization = true)
                    state.initialized = true
                }
            }
            if (index.hasPending()) state.requests.trySend(Unit)
            failures.update { it - state.binding.chatId }
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) {
            pending.forEach { (path, event) -> index.enqueue(path, event, older = true, mergeCreated = true) }
            AppLogger.e("WorkspaceFileMonitor", "读取绑定工作区变化失败", error)
            failures.update { it + (state.binding.chatId to (error.message ?: error.javaClass.simpleName)) }
        }
    }

    private suspend fun saveChanges(
        state: State, index: TerminalObservationIndex, scan: TerminalObservationIndex.Scan,
        pending: Map<String, TerminalPendingChange>,
    ) {
        val operation = UUID.randomUUID().toString()
        val timestamp = WorkspaceChangeStore.nextTimestamp()
        var afterPath: String? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = scan.differences(afterPath)
            if (page.isEmpty()) break
            val records = page.mapNotNull { difference ->
                val path = difference.path
                val events = index.pendingAncestors(path).toMutableMap()
                pending.forEach { (parent, event) -> if (parent.isEmpty() || path == parent || path.startsWith("$parent/")) events[parent] = event }
                val event = events.maxByOrNull { it.key.length }?.value
                if (!state.initialized && event == null) return@mapNotNull null
                val before = if (!state.initialized && difference.before == null && event?.createdInPlace != true)
                    ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "Workspace baseline was not ready") else difference.beforeSnapshot
                val after = difference.afterSnapshot
                if (before == after && (after.kind !in setOf(SnapshotKind.TOO_LARGE, SnapshotKind.UNAVAILABLE) ||
                        difference.before?.stamp == difference.after?.stamp)) return@mapNotNull null
                val origin = event?.origin ?: source(state)
                val record = FileChangeRecord(UUID.randomUUID().toString(), origin.executionId ?: operation,
                    state.binding.chatId, timestamp, origin.toolName,
                    state.binding.location.copy(path = changePath(state.binding.location.path + "/" + path)),
                    before, after, executionId = origin.executionId,
                    sourceKind = if (origin.toolName == "filesystem") "file" else "terminal",
                    subagentExecutionId = origin.subagentExecutionId)
                val content = store.content(record)
                path to if (reviewDiffAutomatic(content)) WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after)).let {
                    record.copy(additions = it.additions, deletions = it.deletions)
                } else record
            }
            WorkspaceChangeStore.operationMutex.withLock {
                scan.excludeActiveWrites(state.binding.location)
                records.filter { scan.isCurrent(it.first) }.map { it.second }.groupBy { it.operationId }.forEach { (id, current) ->
                    store.mergeOperation(id, current) { it }
                }
            }
            afterPath = page.last().path
            yield()
        }
    }

    private suspend fun scan(
        state: State, index: TerminalObservationIndex, forced: Set<String>,
        target: TerminalObservationIndex.Scan, paths: Set<String>?,
    ) {
        val cancellation = currentCoroutineContext()
        check(WorkspaceChangeAccess.backend(context, state.binding.location.environment) == state.binding.location.backend) { "Workspace backend changed" }
        if (state.local != null) {
            check(state.initialized || state.local.isDirectory) { "Workspace is not a readable directory" }
            scanLocalWorkspace(state.local, { path -> index.file(path)?.let { ObservedWorkspaceFile(it.stamp, it.snapshot) } }, forced,
                ::isHistoryStorage, { path, file -> target.put(path, TerminalObservedFile(file.stamp, file.snapshot)) },
                { snapshotBytes(it, target) }, paths, checkCancelled = { cancellation.ensureActive() },
                onDirectory = { state.observer?.watchDirectory(it) })
        } else {
            if (state.binding.location.environment !in setOf("linux", "android")) {
                scanProviderWorkspace(state, target)
                return
            }
            val command = WorkspaceGitCommand(context, state.binding.location.path, state.binding.location.environment, state.binding.location.backend)
            try {
                command.forEachInventoryFile { path, stamp ->
                    cancellation.ensureActive()
                    val previous = index.file(path)
                    val size = stamp.substringAfter(':').substringBefore(':').toLong()
                    val snapshot = if (previous?.stamp == stamp && !anyObservationAncestor(path, forced::contains)) previous.snapshot
                        else if (size > CHANGE_SNAPSHOT_LIMIT) ChangeSnapshot(SnapshotKind.TOO_LARGE, size)
                        else snapshotBytes(command.workspaceFileBytes(state.binding.location.path.trimEnd('/') + "/" + path), target)
                    target.put(path, TerminalObservedFile(stamp, snapshot))
                }
            } finally { withContext(NonCancellable) { command.close() } }
        }
    }

    private suspend fun scanProviderWorkspace(state: State, target: TerminalObservationIndex.Scan) {
        val access = WorkspaceChangeAccess(context, { store })
        suspend fun visit(location: ChangeLocation, prefix: String) {
            currentCoroutineContext().ensureActive()
            for (entry in access.directoryEntries(location)) {
                val path = prefix + entry.name
                if (!observedWorkspacePath(path)) continue
                val child = location.copy(path = changePath(location.path + "/" + entry.name))
                if (entry.isDirectory) visit(child, "$path/") else {
                    val snapshot = access.snapshot(child)
                    check(snapshot.kind != SnapshotKind.UNAVAILABLE && snapshot.kind != SnapshotKind.ABSENT) { snapshot.detail ?: "Cannot read workspace file" }
                    snapshot.blob?.let(target::pinBlob)
                    target.put(path, TerminalObservedFile("", snapshot))
                }
            }
        }
        visit(state.binding.location, "")
    }

    private fun snapshotBytes(bytes: ByteArray, scan: TerminalObservationIndex.Scan): ChangeSnapshot = if (bytes.size > CHANGE_SNAPSHOT_LIMIT)
        ChangeSnapshot(SnapshotKind.TOO_LARGE, bytes.size.toLong()) else ChangeSnapshot(
            if (decodeChangeText(bytes) == null) SnapshotKind.BINARY else SnapshotKind.TEXT,
            bytes.size.toLong(), store.putBlob(bytes, scan))

    private fun isHistoryStorage(file: File): Boolean = changePathContains(File(context.filesDir, "workspace_changes").path, file.absolutePath)

    private fun hostFile(location: ChangeLocation): File? = when (location.backend) {
        "android" -> File(location.path)
        "linux:local" -> File(PRootMountMapping.mapLinuxPathToHostPath(location.path,
            File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu"), context.filesDir.absolutePath,
            context.applicationInfo.dataDir, context.packageName,
            context.getSharedPreferences("terminal_settings", Context.MODE_PRIVATE).getBoolean("chroot_enabled", false)))
        else -> null
    }

    private fun key(binding: Binding): String = MessageDigest.getInstance("SHA-256")
        .digest((binding.chatId + "\u0000" + binding.location.key).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    internal suspend fun close() {
        closed = true
        lifecycle.withLock {
            activeStates = emptyList()
            owners.clear()
            states.clear()
            scope.cancel()
        }
        scope.coroutineContext.job.join()
        if (instance === this) instance = null
    }

    companion object {
        @Volatile private var instance: WorkspaceFileMonitor? = null
        fun existing(): WorkspaceFileMonitor? = instance
        fun acknowledgeExisting(records: List<FileChangeRecord>) { instance?.acknowledge(records) }
        fun invalidateExisting(locations: List<ChangeLocation>) { instance?.invalidate(locations) }
        fun get(context: Context): WorkspaceFileMonitor = instance ?: synchronized(this) {
            instance ?: WorkspaceFileMonitor(context.applicationContext).also { instance = it }
        }
    }
}
