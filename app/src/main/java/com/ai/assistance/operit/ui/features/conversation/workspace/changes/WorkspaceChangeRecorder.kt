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
    private val acknowledge: (List<FileChangeRecord>) -> Unit = TerminalFileJournal::acknowledgeExisting,
) : ToolExecutor {
    override fun validateParameters(tool: AITool) = delegate.validateParameters(tool)

    override fun invoke(tool: AITool): ToolResult {
        val caller = ToolExecutionManager.currentToolRuntimeContext()
        val chatId = caller?.workspaceChatId
        if (chatId.isNullOrBlank()) return delegate.invoke(tool)
        val sourceName = caller?.invokingToolName ?: tool.name
        return runBlocking(Dispatchers.IO) {
            WorkspaceChangeStore.operationMutex.withLock {
                val capture = begin(tool, chatId, sourceName, caller?.subagentExecutionId)
                val result = delegate.invoke(tool)
                finish(capture, result)
                result
            }
        }
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
        val caller = ToolExecutionManager.currentToolRuntimeContext()
        val chatId = caller?.workspaceChatId
        if (chatId.isNullOrBlank()) {
            delegate.invokeAndStream(tool).collect { emit(it) }
            return@flow
        }
        val sourceName = caller?.invokingToolName ?: tool.name
        WorkspaceChangeStore.operationMutex.withLock {
            val capture = withContext(Dispatchers.IO) { begin(tool, chatId, sourceName, caller?.subagentExecutionId) }
            var pending: ToolResult? = null
            var recorded = false
            try {
                delegate.invokeAndStream(tool).collect { result ->
                    val previous = pending
                    pending = result
                    if (previous != null) emit(previous)
                }
                pending?.let { terminalResult ->
                    // 最终成功结果交给外层 hook 前先固化快照，避免后续备份写入混入历史。
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

    private data class Capture(
        val chatId: String,
        val sourceName: String,
        val roots: List<ChangeLocation>,
        val before: Map<ChangeLocation, ChangeSnapshot>,
        val access: WorkspaceChangeAccess,
        val subagentExecutionId: String?,
    )

    private suspend fun begin(tool: AITool, chatId: String, sourceName: String, subagentExecutionId: String?): Capture? = try {
        val access = WorkspaceChangeAccess(context)
        val params = tool.parameters.associate { it.name to it.value }
        val environment = params["environment"]
        val roots = when (tool.name) {
            "move_file" -> listOfNotNull(params["source"], params["destination"]).map { access.location(it, environment) }
            "copy_file" -> listOfNotNull(params["destination"]).map { access.location(it, params["dest_environment"] ?: environment) }
            "zip_files", "unzip_files", "download_file" -> listOfNotNull(params["destination"]).map { access.location(it, environment) }
            else -> listOfNotNull(params["path"]).map { access.location(it, environment) }
        }.distinct()
        Capture(chatId, sourceName, roots, captureTree(access, roots), access, subagentExecutionId)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AppLogger.e("WorkspaceChanges", "Cannot capture operation start", error)
        null
    }

    private suspend fun captureTree(access: WorkspaceChangeAccess, roots: List<ChangeLocation>): Map<ChangeLocation, ChangeSnapshot> {
        val result = linkedMapOf<ChangeLocation, ChangeSnapshot>()
        var bytesCaptured = 0L
        suspend fun visit(location: ChangeLocation, depth: Int) {
            if (location in result) return
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
        WorkspaceChangeStore.get(context).flushBlobs()
        return result
    }

    private suspend fun finish(capture: Capture?, result: ToolResult) {
        if (capture == null || !result.success || (result.result as? FileOperationData)?.successful == false ||
            (result.result as? FileApplyResultData)?.operation?.successful == false) return
        try {
            val after = captureTree(capture.access, capture.roots)
            val operationId = UUID.randomUUID().toString()
            val timestamp = WorkspaceChangeStore.nextTimestamp()
            var remainingAfterBytes = 16L * 1024 * 1024 - after.values.filter { it.blob != null }.sumOf { it.size }
            val records = (capture.before.keys + after.keys).distinct().mapNotNull { location ->
                // 某一侧未遍历到的路径重新读取，不能把截断扫描当作文件不存在。
                val ancestor = capture.before.entries.filter { (parent, _) ->
                    parent.backend == location.backend && changePathContains(parent.path, location.path)
                }.maxByOrNull { it.key.path.length }?.value
                val before = capture.before[location] ?: ChangeSnapshot(
                    if (ancestor?.kind == SnapshotKind.ABSENT || (ancestor?.kind == SnapshotKind.DIRECTORY && ancestor.detail == null)) SnapshotKind.ABSENT
                    else SnapshotKind.UNAVAILABLE,
                )
                val next = after[location] ?: capture.access.snapshot(location, remainingAfterBytes).also {
                    if (it.blob != null) remainingAfterBytes -= it.size
                }
                if (before == next && before.kind != SnapshotKind.TOO_LARGE && before.kind != SnapshotKind.UNAVAILABLE) null
                else FileChangeRecord(UUID.randomUUID().toString(), operationId, capture.chatId, timestamp,
                    capture.sourceName, location, before, next, subagentExecutionId = capture.subagentExecutionId)
            }
            val store = WorkspaceChangeStore.get(context)
            val saved = records.map { record ->
                val content = store.content(record)
                if (reviewDiffAutomatic(content)) {
                    val diff = WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after))
                    record.copy(additions = diff.additions, deletions = diff.deletions)
                } else record
            }
            store.save(saved)
            WorkspaceFileMonitor.acknowledgeExisting(saved)
            acknowledge(saved)
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
