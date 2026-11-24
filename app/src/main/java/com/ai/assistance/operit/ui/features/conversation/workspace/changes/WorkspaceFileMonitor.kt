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
    private data class Binding(val chatId: String, val location: ChangeLocation)
    private class State(val binding: Binding, val key: String, val local: File?, val index: TerminalObservationIndex) {
        var initialized = false
        var baselineLoaded = false
        var pendingOperation: String? = null
        var pendingTime = 0L
        var lastScan = 0L
        var observer: DepthLimitedFileObserver? = null
        @Volatile var restartObserver = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = linkedMapOf<String, State>()
    private val owners = linkedMapOf<String, String>()
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
                    states.values.toList().filter { it.index.hasPending() }
                        .forEach { inspect(it, source()) }
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
                    }.forEach { inspect(it, source()) }
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
                        val state = State(binding, key, hostFile(binding.location)?.canonicalFile, store.workspaceIndex(key))
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

    /** 文件工具已经固化的同份快照同步进观察基线，避免随后通知重复记录。调用方持有操作锁。 */
    fun acknowledge(records: List<FileChangeRecord>) {
        for (state in states.values) {
            if (!state.baselineLoaded) continue
            for (record in records) {
                if (record.chatId != state.binding.chatId) continue
                val path = relativeChangePath(state.binding.location, record.location) ?: continue
                state.index.acknowledge(path, record.after, initializing = !state.initialized)
            }
        }
    }

    private suspend fun releaseUnusedStates() {
        val unused = states.values.filter { it.key !in owners.values }
        for (state in unused) {
            inspect(state, source())
            state.observer?.stop()
            states.remove(state.key)
            store.releaseWorkspaceIndex(state.key)
            failures.update { it - state.binding.chatId }
        }
    }

    private fun source(): String {
        // 已保存的文件工具和终端操作保留精确来源；其余事件按真实文件系统变化记录。
        val manual = TerminalManager.getInstance(context).terminalState.value.sessions.any {
            !it.automation && it.currentExecutingCommand?.isExecuting == true
        }
        return observedChangeSource(emptyList(), manual)
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
                val origin = source()
                val owned = terminalOwns(File(root, event.relativePath))
                try {
                    state.index.enqueue(event.relativePath, TerminalPendingChange(
                        TerminalChangeOrigin(state.binding.chatId, origin, null, null), owned), mergeCreated = true)
                    changes.trySend(Unit)
                } catch (error: Exception) {
                    AppLogger.e("WorkspaceFileMonitor", "工作区事件入队失败", error)
                    failures.update { it + (state.binding.chatId to (error.message ?: error.javaClass.simpleName)) }
                }
            }).also { it.start() }
    }

    private suspend fun inspect(state: State, origin: String) {
        val pending = state.index.takePending()
        try {
            if (!state.baselineLoaded) {
                state.initialized = state.index.initialized()
                state.baselineLoaded = true
            }
            ensureObserver(state)
            val selected = if (state.initialized && pending.isNotEmpty() && state.local != null) pending.keys else null
            state.index.beginScan(selected).use { scan ->
                scan(state, pending.keys, scan, selected)
                if (state.initialized) saveChanges(state, scan, origin, pending)
                scan.commit(completeInitialization = true)
            }
            state.initialized = true
            state.pendingOperation = null
            state.lastScan = System.currentTimeMillis()
            if (state.index.hasPending()) changes.trySend(Unit)
            failures.update { it - state.binding.chatId }
        } catch (cancelled: CancellationException) { restoreEvents(state, pending); throw cancelled }
        catch (error: Exception) {
            restoreEvents(state, pending)
            AppLogger.e("WorkspaceFileMonitor", "读取工作区实际文件变化失败", error)
            failures.update { it + (state.binding.chatId to (error.message ?: error.javaClass.simpleName)) }
        }
    }

    private fun restoreEvents(state: State, pending: Map<String, TerminalPendingChange>) {
        pending.forEach { (path, event) -> state.index.enqueue(path, event, older = true, mergeCreated = true) }
    }

    private fun saveChanges(state: State, scan: TerminalObservationIndex.Scan, origin: String, pending: Map<String, TerminalPendingChange>) {
        val operation = state.pendingOperation ?: UUID.randomUUID().toString().also {
            state.pendingOperation = it
            state.pendingTime = WorkspaceChangeStore.nextTimestamp()
        }
        val time = state.pendingTime
        var afterPath: String? = null
        while (true) {
            val page = scan.differences(afterPath)
            if (page.isEmpty()) break
            val records = page.mapNotNull { difference ->
                val path = difference.path
                if (difference.beforeSnapshot == difference.afterSnapshot &&
                    (difference.afterSnapshot.kind !in setOf(SnapshotKind.TOO_LARGE, SnapshotKind.UNAVAILABLE) || difference.before?.stamp == difference.after?.stamp)) return@mapNotNull null
                val events = state.index.pendingAncestors(path).toMutableMap()
                pending.forEach { (parent, event) -> if (parent.isEmpty() || path == parent || path.startsWith("$parent/")) events[parent] = event }
                if ((state.local != null && terminalOwns(File(state.local, path))) || events.values.any { it.createdInPlace }) return@mapNotNull null
                val eventSource = events.maxByOrNull { it.key.length }?.value?.origin?.toolName ?: origin
                val record = FileChangeRecord(UUID.randomUUID().toString(), operation, state.binding.chatId, time,
                    eventSource, state.binding.location.copy(path = changePath(state.binding.location.path + "/" + path)),
                    difference.beforeSnapshot, difference.afterSnapshot)
                val content = store.content(record)
                if (reviewDiffAutomatic(content)) WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after)).let {
                    record.copy(additions = it.additions, deletions = it.deletions)
                } else record
            }
            store.mergeOperation(operation, records) { it }
            afterPath = page.last().path
        }
    }

    private suspend fun scan(state: State, forced: Set<String>, target: TerminalObservationIndex.Scan, paths: Set<String>?) {
        check(WorkspaceChangeAccess.backend(context, state.binding.location.environment) == state.binding.location.backend) { "Workspace backend changed" }
        if (state.local != null) {
            check(state.initialized || state.local.isDirectory) { "Workspace is not a readable directory" }
            scanLocalWorkspace(state.local, { path -> state.index.file(path)?.let { ObservedWorkspaceFile(it.stamp, it.snapshot) } }, forced,
                ::isHistoryStorage, { path, file -> target.put(path, TerminalObservedFile(file.stamp, file.snapshot)) }, { snapshotBytes(it, target) }, paths)
        } else scanRemote(state, target)
    }

    private suspend fun scanRemote(state: State, target: TerminalObservationIndex.Scan) {
        val access = WorkspaceChangeAccess(context)
        if (state.binding.location.environment in setOf("linux", "android")) {
            val command = WorkspaceGitCommand(context, state.binding.location.path, state.binding.location.environment, state.binding.location.backend)
            try {
                command.forEachInventoryFile { path, stamp ->
                    val previous = state.index.file(path)
                    val size = stamp.substringAfter(':').substringBefore(':').toLong()
                    val snapshot = if (previous?.stamp == stamp) previous.snapshot
                        else if (size > CHANGE_SNAPSHOT_LIMIT) ChangeSnapshot(SnapshotKind.TOO_LARGE, size)
                        else snapshotBytes(command.workspaceFileBytes(state.binding.location.path.trimEnd('/') + "/" + path), target)
                    target.put(path, TerminalObservedFile(stamp, snapshot))
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
                        check(snapshot.kind != SnapshotKind.UNAVAILABLE && snapshot.kind != SnapshotKind.ABSENT) { snapshot.detail ?: "Cannot read changed file: $path" }
                        snapshot.blob?.let(target::pinBlob)
                        target.put(path, TerminalObservedFile("", snapshot))
                    }
                }
            }
            visit(state.binding.location, "")
        }
    }

    private fun snapshotBytes(bytes: ByteArray, scan: TerminalObservationIndex.Scan): ChangeSnapshot = if (bytes.size > CHANGE_SNAPSHOT_LIMIT)
        ChangeSnapshot(SnapshotKind.TOO_LARGE, bytes.size.toLong()) else ChangeSnapshot(
            if (decodeChangeText(bytes) == null) SnapshotKind.BINARY else SnapshotKind.TEXT,
            bytes.size.toLong(), store.putBlob(bytes, scan))

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
            states.values.forEach { it.observer?.stop(); store.releaseWorkspaceIndex(it.key) }
            states.clear()
            owners.clear()
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
