package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FileApplyResultData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.core.tools.runtime.ToolExecutor
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 保存文件工具的精确操作快照，绑定目录内与实际文件观察同步去重。 */
internal class WorkspaceChangeRecorder(
    private val context: Context,
    private val delegate: ToolExecutor,
    private val acknowledge: (List<FileChangeRecord>) -> Unit = WorkspaceFileMonitor::acknowledgeExisting,
    storeProvider: () -> WorkspaceChangeStore = { WorkspaceChangeStore.get(context) },
    private val recordingIdentity: (WorkspaceRecordingScope) -> Any? = { WorkspaceFileMonitor.existing()?.bindingIdentity(it) },
    private val invalidate: (List<ChangeLocation>) -> Unit = { locations ->
        WorkspaceFileMonitor.invalidateExisting(locations)
    },
) : ToolExecutor {
    private val store by lazy(storeProvider)
    override fun validateParameters(tool: AITool) = delegate.validateParameters(tool)

    override fun invoke(tool: AITool): ToolResult {
        val caller = ToolExecutionManager.currentToolRuntimeContext()
        val recording = WorkspaceRecordingScope.create(caller?.workspaceChatId, caller?.workspacePath, caller?.workspaceEnv)
        val identity = recording?.let(recordingIdentity)
        if (recording == null || identity == null) return delegate.invoke(tool)
        return runBlocking(Dispatchers.IO) {
            captureOperation(tool, ActiveRecording(recording, identity), caller?.invokingToolName ?: tool.name, caller?.subagentExecutionId) { capture ->
                val result = delegate.invoke(tool)
                finish(capture, result)
                result
            }
        }
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
        val caller = ToolExecutionManager.currentToolRuntimeContext()
        val recording = WorkspaceRecordingScope.create(caller?.workspaceChatId, caller?.workspacePath, caller?.workspaceEnv)
        val identity = recording?.let(recordingIdentity)
        if (recording == null || identity == null) {
            delegate.invokeAndStream(tool).collect { emit(it) }
            return@flow
        }
        captureOperation(tool, ActiveRecording(recording, identity), caller?.invokingToolName ?: tool.name, caller?.subagentExecutionId) { capture ->
            if (capture == null) {
                delegate.invokeAndStream(tool).collect { emit(it) }
            } else {
                var pending: ToolResult? = null
                var recorded = false
                try {
                    delegate.invokeAndStream(tool).collect { result ->
                        val previous = pending
                        pending = result
                        if (previous != null) emit(previous)
                    }
                    pending?.let { terminalResult ->
                        withContext(NonCancellable + Dispatchers.IO) { finish(capture, terminalResult) }
                        recorded = true
                        emit(terminalResult)
                    }
                } finally {
                    val terminalResult = pending
                    if (!recorded && terminalResult != null && (terminalResult.result is FileApplyResultData || terminalResult.result is FileOperationData)) {
                        withContext(NonCancellable + Dispatchers.IO) { finish(capture, terminalResult) }
                    }
                }
            }
        }
    }

    private suspend fun <T> captureOperation(
        tool: AITool, recording: ActiveRecording, sourceName: String, subagentExecutionId: String?,
        execute: suspend (Capture?) -> T,
    ): T {
        val preparation = try {
            val access = WorkspaceChangeAccess(context)
            val workspace = access.location(recording.scope.path, recording.scope.environment)
            val params = tool.parameters.associate { it.name to it.value }
            val environment = params["environment"]
            val targets = when (tool.name) {
                "move_file" -> listOfNotNull(params["source"], params["destination"]).map { access.location(it, environment) }
                "copy_file" -> listOfNotNull(params["destination"]).map { access.location(it, params["dest_environment"] ?: environment) }
                "zip_files", "unzip_files", "download_file" -> listOfNotNull(params["destination"]).map { access.location(it, environment) }
                else -> listOfNotNull(params["path"]).map { access.location(it, environment) }
            }
            val roots = boundMutationLocations(workspace, targets,
                com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping.currentEmulatedStoragePath())
                .filter { access.withinWorkspace(it, workspace) }
            if (roots.isEmpty() || !isRecordingActive(recording)) null
            else {
                val identities = roots.map(access::mutationIdentity)
                Preparation(workspace, roots, store.openSnapshotLease(), identities)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("WorkspaceChanges", "无法准备绑定范围内的操作快照", error)
            null
        }
        if (preparation == null) return execute(null)
        val (workspace, roots, lease, identities) = preparation
        try {
            val captureAccess = WorkspaceChangeAccess(context, { store }, lease)
            // 挂载别名归一后只锁本次目标，无关文件不等待耗时的网络与目录操作。
            val mutation = workspaceFileMutations.acquire(roots, identities)
            try {
                val capture = try {
                    WorkspaceChangeStore.operationMutex.withLock { invalidate(roots) }
                    val before = withContext(Dispatchers.IO) { captureTree(captureAccess, roots, workspace, recording) }
                    Capture(recording, workspace, sourceName, roots, before, captureAccess, subagentExecutionId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("WorkspaceChanges", "无法采集操作前快照", error)
                    null
                }
                return execute(capture)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    WorkspaceChangeStore.operationMutex.withLock {
                        // 先释放目标，再重新排队；扫描不能消费掉仍在写入中的最后一个通知。
                        mutation.close()
                        try { invalidate(roots) } catch (error: Exception) {
                            AppLogger.e("WorkspaceChanges", "无法请求工作区收尾核对", error)
                        }
                    }
                }
            }
        } finally { lease.close() }
    }

    private data class ActiveRecording(val scope: WorkspaceRecordingScope, val identity: Any)
    private fun isRecordingActive(recording: ActiveRecording): Boolean =
        recordingIdentity(recording.scope) === recording.identity

    private data class Preparation(
        val workspace: ChangeLocation,
        val roots: List<ChangeLocation>,
        val lease: WorkspaceChangeStore.SnapshotLease,
        val identities: List<ChangeLocation>,
    )
    private data class Capture(
        val recording: ActiveRecording,
        val workspace: ChangeLocation,
        val sourceName: String,
        val roots: List<ChangeLocation>,
        val before: Map<ChangeLocation, ChangeSnapshot>,
        val access: WorkspaceChangeAccess,
        val subagentExecutionId: String?,
    )

    private suspend fun captureTree(access: WorkspaceChangeAccess, roots: List<ChangeLocation>, workspace: ChangeLocation, recording: ActiveRecording): Map<ChangeLocation, ChangeSnapshot> {
        val result = linkedMapOf<ChangeLocation, ChangeSnapshot>()
        var bytesCaptured = 0L
        suspend fun visit(location: ChangeLocation, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (!isRecordingActive(recording) || location in result || !access.withinWorkspace(location, workspace)) return
            val snapshot = access.snapshot(location, 16L * 1024 * 1024 - bytesCaptured)
            if (snapshot.blob != null) bytesCaptured += snapshot.size
            result[location] = snapshot
            if (snapshot.kind != SnapshotKind.DIRECTORY) return
            if (depth >= 32 || result.size >= 512) {
                result[location] = snapshot.copy(detail = "Directory capture limit reached")
                return
            }
            try {
                val children = access.children(location)
                for (child in children) {
                    if (result.size >= 512) {
                        result[location] = snapshot.copy(detail = "Directory capture limit reached")
                        break
                    }
                    visit(child, depth + 1)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                result[location] = snapshot.copy(detail = error.message)
            }
        }
        roots.forEach { visit(it, 0) }
        store.flushBlobs()
        return result
    }

    private suspend fun finish(capture: Capture?, result: ToolResult) {
        if (capture == null || !isRecordingActive(capture.recording) || !result.success || (result.result as? FileOperationData)?.successful == false ||
            (result.result as? FileApplyResultData)?.operation?.successful == false) return
        try {
            val after = captureTree(capture.access, capture.roots, capture.workspace, capture.recording)
            if (!isRecordingActive(capture.recording)) return
            val operationId = UUID.randomUUID().toString()
            val timestamp = WorkspaceChangeStore.nextTimestamp()
            var remainingAfterBytes = 16L * 1024 * 1024 - after.values.filter { it.blob != null }.sumOf { it.size }
            val records = (capture.before.keys + after.keys).distinct().mapNotNull { location ->
                if (!isRecordingActive(capture.recording)) return@mapNotNull null
                if (!isRecordingActive(capture.recording)) return@mapNotNull null
                // 某一侧未遍历到的路径重新读取，不能把截断扫描当作文件不存在。
                val ancestor = capture.before.entries.filter { (parent, _) ->
                    parent.backend == location.backend && changePathContains(parent.path, location.path)
                }.maxByOrNull { it.key.path.length }?.value
                val before = capture.before[location] ?: ChangeSnapshot(
                    if (ancestor?.kind == SnapshotKind.ABSENT || (ancestor?.kind == SnapshotKind.DIRECTORY && ancestor.detail == null)) SnapshotKind.ABSENT
                    else SnapshotKind.UNAVAILABLE,
                )
                val next = after[location] ?: if (!capture.access.withinWorkspace(location, capture.workspace))
                    ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "Path left the bound workspace") else capture.access.snapshot(location, remainingAfterBytes).also {
                    if (it.blob != null) remainingAfterBytes -= it.size
                }
                if (before == next && before.kind != SnapshotKind.TOO_LARGE && before.kind != SnapshotKind.UNAVAILABLE) null
                else FileChangeRecord(UUID.randomUUID().toString(), operationId, capture.recording.scope.chatId, timestamp,
                    capture.sourceName, location, before, next, subagentExecutionId = capture.subagentExecutionId)
            }
            val saved = records.map { record ->
                val content = store.content(record)
                if (reviewDiffAutomatic(content)) {
                    val diff = WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after))
                    record.copy(additions = diff.additions, deletions = diff.deletions)
                } else record
            }
            // 只发布已算好的本批记录；快照读取、网络操作和差异计算均不持有共享锁。
            WorkspaceChangeStore.operationMutex.withLock {
                if (isRecordingActive(capture.recording)) {
                    store.save(saved)
                    acknowledge(saved)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("WorkspaceChanges", "Cannot save operation snapshots", error)
        }
    }

    companion object {
        private val tools = setOf("apply_file", "create_file", "edit_file", "write_file", "write_file_binary",
            "move_file", "delete_file", "copy_file", "make_directory", "zip_files", "unzip_files", "download_file")
        fun wrap(context: Context, name: String, executor: ToolExecutor): ToolExecutor =
            if (name in tools) WorkspaceChangeRecorder(context.applicationContext, executor) else executor
    }
}
