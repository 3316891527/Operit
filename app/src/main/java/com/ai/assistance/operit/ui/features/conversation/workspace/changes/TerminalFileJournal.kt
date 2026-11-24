package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 终端只提供已绑定工作区的来源身份，不再创建观察根、基线或独立扫描。 */
internal class TerminalFileJournal internal constructor(
    private val monitorProvider: () -> WorkspaceFileMonitor? = WorkspaceFileMonitor::existing,
) {
    internal class Execution(val origin: TerminalChangeOrigin, val workspace: WorkspaceRecordingScope, val identity: Any)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executions = ConcurrentHashMap<String, Execution>()
    private val interactive = ConcurrentHashMap<String, Execution>()
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = failures.asStateFlow()

    fun begin(origin: TerminalChangeOrigin, workspacePath: String?, workspaceEnv: String?): Execution? {
        val workspace = WorkspaceRecordingScope.create(origin.chatId, workspacePath, workspaceEnv) ?: return null
        val identity = monitorProvider()?.bindingIdentity(workspace) ?: return null
        val execution = Execution(origin, workspace, identity)
        executions[checkNotNull(origin.executionId)] = execution
        return execution
    }

    /** 完成事件直接返回，绑定目录的核对由唯一观察器在后台执行。 */
    fun finish(execution: Execution): Job = scope.launch {
        try {
            if (executions[execution.origin.executionId] === execution) monitorProvider()?.reconcile(execution.workspace)
        }
        finally { executions.remove(execution.origin.executionId, execution) }
    }

    fun interactiveInput(sessionId: String, origin: TerminalChangeOrigin, workspacePath: String?, workspaceEnv: String?) {
        val workspace = WorkspaceRecordingScope.create(origin.chatId, workspacePath, workspaceEnv)
        val identity = workspace?.let { monitorProvider()?.bindingIdentity(it) }
        if (workspace == null || identity == null) {
            interactive.remove(sessionId)
            return
        }
        interactive[sessionId] = Execution(origin, workspace, identity)
    }
    fun closeSession(sessionId: String) { interactive.remove(sessionId) }

    internal fun origin(chatId: String, location: ChangeLocation): TerminalChangeOrigin? = terminalChangeOrigin(
        (executions.values + interactive.values).filter { it.workspace.matches(chatId, location) }.map { it.origin },
    )

    private fun forget(chatId: String, location: ChangeLocation) {
        executions.entries.removeAll { it.value.workspace.matches(chatId, location) }
        interactive.entries.removeAll { it.value.workspace.matches(chatId, location) }
    }

    internal suspend fun close() {
        scope.cancel()
        scope.coroutineContext.job.join()
        executions.clear()
        interactive.clear()
        if (instance === this) instance = null
    }

    companion object {
        @Volatile private var instance: TerminalFileJournal? = null
        fun forgetBinding(chatId: String, location: ChangeLocation) { instance?.forget(chatId, location) }
        fun originFor(chatId: String, location: ChangeLocation): TerminalChangeOrigin? = instance?.origin(chatId, location)
        @Suppress("UNUSED_PARAMETER")
        fun get(context: Context): TerminalFileJournal = instance ?: synchronized(this) {
            instance ?: TerminalFileJournal().also { instance = it }
        }
    }
}
