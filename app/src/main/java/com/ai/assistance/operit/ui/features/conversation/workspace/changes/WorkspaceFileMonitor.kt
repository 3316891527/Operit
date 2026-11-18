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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock

/** 监视绑定目录的真实文件，来源仅用于标注，不决定哪些文件能够进入历史。 */
internal class WorkspaceFileMonitor internal constructor(
    private val context: Context,
    private val terminalOwns: (File) -> Boolean = TerminalFileJournal::observesActiveFile,
) {
    private data class EventOrigin(val name: String, val terminalOwned: Boolean) {
        fun merge(other: EventOrigin) = EventOrigin(
            if (name == other.name) name else "filesystem", terminalOwned || other.terminalOwned,
        )
    }
    private data class Binding(val chatId: String, val location: ChangeLocation)
    private class State(val binding: Binding, val key: String, val local: File?) {
        var before: Map<String, ObservedWorkspaceFile> = emptyMap()
        var initialized = false
        var baselineLoaded = false
        var lastScan = 0L
        var observer: DepthLimitedFileObserver? = null
        @Volatile var restartObserver = false
        val dirty = linkedMapOf<String, EventOrigin>()
    }
    internal data class ToolScope(val id: String, val chatId: String?, val name: String, val command: Boolean)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = linkedMapOf<String, State>()
    private val owners = linkedMapOf<String, String>()
    private val sources = linkedMapOf<String, ToolScope>()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val store = WorkspaceChangeStore.get(context)
    private val historyRoot = File(context.filesDir, "workspace_changes").canonicalPath
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = failures.asStateFlow()

    init {
        scope.launch {
            for (signal in changes) {
                delay(150)
                WorkspaceChangeStore.operationMutex.withLock {
                    states.values.toList().filter { synchronized(it) { it.dirty.isNotEmpty() } }
                        .forEach { inspect(it, source(it)) }
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(3_000)
                WorkspaceChangeStore.operationMutex.withLock {
                    // 本地事件即时采集，定期核对元数据以恢复观察目录被整体移动的情况。
                    states.values.toList().filter {
                        it.local == null || !it.initialized || it.observer == null ||
                            System.currentTimeMillis() - it.lastScan >= 10_000
                    }.forEach { inspect(it, source(it)) }
                }
            }
        }
    }

    suspend fun updateOwner(ownerId: String, chatId: String?, workspacePath: String?, workspaceEnv: String?) = withContext(Dispatchers.IO) {
        WorkspaceChangeStore.operationMutex.withLock {
            owners.remove(ownerId)
            try {
                val binding = if (chatId.isNullOrBlank() || workspacePath.isNullOrBlank()) null else
                    Binding(chatId, WorkspaceChangeAccess(context).location(workspacePath, workspaceEnv))
                if (binding != null) {
                    val key = key(binding)
                    owners[ownerId] = key
                    if (key !in states) {
                        val state = State(binding, key, hostFile(binding.location)?.canonicalFile)
                        states[key] = state
                        inspect(state, "filesystem")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                AppLogger.e("WorkspaceFileMonitor", "建立工作区观察失败", error)
                if (chatId != null) failures.update { it + (chatId to (error.message ?: error.javaClass.simpleName)) }
            } finally { releaseUnusedStates() }
        }
    }

    suspend fun clearOwner(ownerId: String) = withContext(NonCancellable) { updateOwner(ownerId, null, null, null) }

    suspend fun beginTool(chatId: String?, name: String, command: Boolean = false): ToolScope = withContext(Dispatchers.IO) {
        WorkspaceChangeStore.operationMutex.withLock {
            states.values.filter { chatId == null || it.binding.chatId == chatId }
                .forEach { inspect(it, source(it)) }
            ToolScope(UUID.randomUUID().toString(), chatId, name, command).also { synchronized(sources) { sources[it.id] = it } }
        }
    }

    suspend fun endTool(tool: ToolScope) = withContext(NonCancellable + Dispatchers.IO) {
        WorkspaceChangeStore.operationMutex.withLock {
            try {
                states.values.filter { tool.chatId == null || it.binding.chatId == tool.chatId }
                    .forEach { inspect(it, source(it)) }
            } finally {
                synchronized(sources) { sources.remove(tool.id) }
                releaseUnusedStates()
            }
        }
    }

    /** 文件工具已经固化的同份快照同步进观察基线，避免随后通知重复记录。调用方持有操作锁。 */
    fun acknowledge(records: List<FileChangeRecord>) {
        for (state in states.values) {
            val updates = records.filter { it.chatId == state.binding.chatId && it.location.backend == state.binding.location.backend &&
                changePathContains(state.binding.location.path, it.location.path) }
            if (updates.isEmpty() || !state.baselineLoaded) continue
            val next = state.before.toMutableMap()
            for (record in updates) {
                val relative = record.location.path.removePrefix(state.binding.location.path.trimEnd('/') + "/")
                if (record.after.kind == SnapshotKind.ABSENT) next.remove(relative)
                else if (record.after.kind != SnapshotKind.DIRECTORY) next[relative] = ObservedWorkspaceFile("", record.after)
            }
            state.before = next
            store.saveObservedFiles(state.key, next)
        }
    }

    private suspend fun releaseUnusedStates() {
        val active = synchronized(sources) { sources.values.toList() }
        val unused = states.values.filter { state -> state.key !in owners.values &&
            active.none { it.chatId == null || it.chatId == state.binding.chatId } }
        for (state in unused) {
            inspect(state, source(state))
            state.observer?.stop()
            states.remove(state.key)
            failures.update { it - state.binding.chatId }
        }
    }

    private fun source(state: State): String {
        val tools = synchronized(sources) {
            val active = sources.values.filter { it.chatId == null || it.chatId == state.binding.chatId }
            val commands = active.filter { it.command }
            (if (commands.isEmpty()) active else commands).map { it.name }
        }
        val manual = TerminalManager.getInstance(context).terminalState.value.sessions.any {
            !it.automation && it.currentExecutingCommand?.isExecuting == true
        }
        return observedChangeSource(tools, manual)
    }

    private fun ensureObserver(state: State) {
        val root = state.local ?: return
        if (state.restartObserver) {
            state.observer?.stop()
            state.observer = null
            state.restartObserver = false
        }
        if (state.observer != null || !root.isDirectory) return
        state.observer = DepthLimitedFileObserver(root, Int.MAX_VALUE,
            shouldIgnore = { path, _ -> !observedWorkspacePath(path) || isHistoryStorage(File(root, path)) },
            onChange = { event ->
                if (event.relativePath.isEmpty()) state.restartObserver = true
                val origin = EventOrigin(source(state), terminalOwns(File(root, event.relativePath)))
                synchronized(state) {
                    state.dirty.merge(event.relativePath, origin) { previous, current -> previous.merge(current) }
                }
                changes.trySend(Unit)
            }).also { it.start() }
    }

    private suspend fun inspect(state: State, origin: String) {
        // 先取走本批通知；扫描期间新收到的同一路径必须留给下一批，不能一并清空。
        val pending = synchronized(state) { state.dirty.toMap().also { state.dirty.clear() } }
        try {
            if (!state.baselineLoaded) {
                val saved = store.observedFiles(state.key)
                state.before = saved.orEmpty()
                state.initialized = saved != null
                state.baselineLoaded = true
            }
            ensureObserver(state)
            val next = scan(state, pending.keys)
            if (state.initialized) saveChanges(state, next, origin, pending)
            val saveBaseline = !state.initialized || state.before != next || state.binding.chatId in failures.value
            state.initialized = true
            state.before = next
            if (saveBaseline) store.saveObservedFiles(state.key, next)
            state.lastScan = System.currentTimeMillis()
            failures.update { it - state.binding.chatId }
        } catch (cancelled: CancellationException) { restoreEvents(state, pending); throw cancelled }
        catch (error: Exception) {
            restoreEvents(state, pending)
            AppLogger.e("WorkspaceFileMonitor", "读取工作区实际文件变化失败", error)
            failures.update { it + (state.binding.chatId to (error.message ?: error.javaClass.simpleName)) }
        }
    }

    private fun restoreEvents(state: State, pending: Map<String, EventOrigin>) = synchronized(state) {
        pending.forEach { (path, source) -> state.dirty.merge(path, source) { current, previous ->
            current.merge(previous)
        } }
    }

    private fun saveChanges(state: State, next: Map<String, ObservedWorkspaceFile>, origin: String, pending: Map<String, EventOrigin>) {
        val paths = observedWorkspaceChanges(state.before, next).filterNot { path ->
            // 归属随通知保存，执行结束后处理的旧通知也不能再次生成 filesystem 记录。
            (state.local != null && terminalOwns(File(state.local, path))) || pending.any { (parent, event) ->
                event.terminalOwned && (parent.isEmpty() || path == parent || path.startsWith("$parent/"))
            }
        }
        if (paths.isEmpty()) return
        val operation = UUID.randomUUID().toString()
        val time = WorkspaceChangeStore.nextTimestamp()
        val records = paths.map { path ->
            val before = state.before[path]?.snapshot ?: ChangeSnapshot(SnapshotKind.ABSENT)
            val after = next[path]?.snapshot ?: ChangeSnapshot(SnapshotKind.ABSENT)
            val eventSource = pending[path]?.name ?: pending.entries.filter { (parent, _) ->
                parent.isEmpty() || path.startsWith("$parent/")
            }.maxByOrNull { it.key.length }?.value?.name ?: origin
            val record = FileChangeRecord(UUID.randomUUID().toString(), operation, state.binding.chatId, time,
                eventSource, state.binding.location.copy(path = changePath(state.binding.location.path + "/" + path)), before, after)
            val content = store.content(record)
            if (reviewDiffAutomatic(content)) WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after)).let {
                record.copy(additions = it.additions, deletions = it.deletions)
            } else record
        }
        store.save(records)
    }

    private suspend fun scan(state: State, forced: Set<String>): Map<String, ObservedWorkspaceFile> {
        check(WorkspaceChangeAccess.backend(context, state.binding.location.environment) == state.binding.location.backend) { "Workspace backend changed" }
        return if (state.local != null) {
            check(state.initialized || state.local.isDirectory) { "Workspace is not a readable directory: ${state.binding.location.path}" }
            observeLocalWorkspace(state.local, state.before, forced, ::isHistoryStorage, ::snapshotBytes)
        } else scanRemote(state)
    }

    private suspend fun scanRemote(state: State): Map<String, ObservedWorkspaceFile> {
        val access = WorkspaceChangeAccess(context)
        val result = linkedMapOf<String, ObservedWorkspaceFile>()
        if (state.binding.location.environment in setOf("linux", "android")) {
            val command = WorkspaceGitCommand(context, state.binding.location.path, state.binding.location.environment, state.binding.location.backend)
            try {
                val entries = command.fileInventory()
                for ((path, stamp) in entries) {
                    val previous = state.before[path]
                    val size = stamp.substringAfter(':').substringBefore(':').toLong()
                    val snapshot = if (previous?.stamp == stamp) previous.snapshot
                        else if (size > CHANGE_SNAPSHOT_LIMIT) ChangeSnapshot(SnapshotKind.TOO_LARGE, size)
                        else snapshotBytes(command.workspaceFileBytes(state.binding.location.path.trimEnd('/') + "/" + path))
                    result[path] = ObservedWorkspaceFile(stamp, snapshot)
                }
            } finally { withContext(NonCancellable) { command.close() } }
        } else {
            suspend fun visit(location: ChangeLocation, prefix: String) {
                for (entry in access.directoryEntries(location)) {
                    val path = prefix + entry.name
                    if (!observedWorkspacePath(path)) continue
                    val child = location.copy(path = changePath(location.path + "/" + entry.name))
                    if (entry.isDirectory) visit(child, "$path/") else {
                        val snapshot = access.snapshot(child)
                        check(snapshot.kind != SnapshotKind.UNAVAILABLE && snapshot.kind != SnapshotKind.ABSENT) {
                            snapshot.detail ?: "Cannot read changed file: $path"
                        }
                        result[path] = ObservedWorkspaceFile("", snapshot)
                    }
                }
            }
            visit(state.binding.location, "")
        }
        return result
    }

    private fun snapshotBytes(bytes: ByteArray): ChangeSnapshot = if (bytes.size > CHANGE_SNAPSHOT_LIMIT)
        ChangeSnapshot(SnapshotKind.TOO_LARGE, bytes.size.toLong()) else ChangeSnapshot(
            if (decodeChangeText(bytes) == null) SnapshotKind.BINARY else SnapshotKind.TEXT,
            bytes.size.toLong(), store.putBlob(bytes))

    private fun isHistoryStorage(file: File): Boolean = changePathContains(historyRoot, file.absolutePath)

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

    internal suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        WorkspaceChangeStore.operationMutex.withLock {
            states.values.forEach { it.observer?.stop() }
            states.clear()
            owners.clear()
            synchronized(sources) { sources.clear() }
            changes.close()
            scope.cancel()
            if (instance === this@WorkspaceFileMonitor) instance = null
        }
    }

    companion object {
        fun acknowledgeExisting(records: List<FileChangeRecord>) { instance?.acknowledge(records) }
        @Volatile private var instance: WorkspaceFileMonitor? = null
        fun get(context: Context): WorkspaceFileMonitor = instance ?: synchronized(this) {
            instance ?: WorkspaceFileMonitor(context.applicationContext).also { instance = it }
        }
    }
}
