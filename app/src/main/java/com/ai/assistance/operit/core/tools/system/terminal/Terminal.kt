package com.ai.assistance.operit.core.tools.system.terminal

import android.content.Context
import android.os.Build
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.SessionDirectoryEvent
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.data.TerminalState
import com.ai.assistance.operit.terminal.provider.type.HiddenExecResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager
import com.ai.assistance.operit.terminal.provider.type.TerminalType
import com.ai.assistance.operit.terminal.provider.filesystem.LocalFileSystemProvider
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.TerminalFileJournal
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.TerminalChangeOrigin
import java.util.UUID

/**
 * 终端管理器
 * 提供应用程序级别的终端服务管理和访问
 */
@RequiresApi(Build.VERSION_CODES.O)
class Terminal internal constructor(
    private val terminalManager: TerminalManager,
    private val fileJournal: TerminalFileJournal? = null,
) {

    companion object {
        @Volatile
        private var INSTANCE: Terminal? = null

        fun getInstance(context: Context): Terminal {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Terminal(TerminalManager.getInstance(context.applicationContext), TerminalFileJournal.get(context)).also { INSTANCE = it }
            }
        }

        private const val TAG = "Terminal"
    }


    // 从 TerminalManager 暴露状态和事件流
    val commandEvents: SharedFlow<CommandExecutionEvent> = terminalManager.commandExecutionEvents
    val directoryEvents: SharedFlow<SessionDirectoryEvent> = terminalManager.directoryChangeEvents
    val terminalState: StateFlow<TerminalState> = terminalManager.terminalState
    val sessions = terminalManager.sessions
    val currentSessionId = terminalManager.currentSessionId
    val currentDirectory = terminalManager.currentDirectory
    val isInteractiveMode = terminalManager.isInteractiveMode
    val interactivePrompt = terminalManager.interactivePrompt
    val isFullscreen = terminalManager.isFullscreen

    /**
     * 初始化终端管理器
     */
    suspend fun initialize(): Boolean {
        return terminalManager.initializeEnvironment()
    }

    /**
     * 销毁终端管理器
     */
    fun destroy() {
        try {
            terminalManager.cleanup()
        } finally {
            runBlocking(Dispatchers.IO) { fileJournal?.close() }
        }
    }

    /**
     * 创建新的终端会话 - 同步等待初始化完成
     */
    suspend fun createSession(title: String? = null, automation: Boolean = false): String {
        AppLogger.d(TAG, "Creating new terminal session and waiting for initialization")
        val newSession = terminalManager.createNewSession(title, automation)
        AppLogger.d(TAG, "Session ${newSession.id} initialized successfully")
        return newSession.id
    }
    
    /**
     * 切换到指定会话
     */
    fun switchToSession(sessionId: String) {
        terminalManager.switchToSession(sessionId)
    }

    /**
     * 关闭终端会话
     */
    fun closeSession(sessionId: String) {
        terminalManager.closeSession(sessionId)
        fileJournal?.closeSession(sessionId)
    }

    /**
     * 执行命令并等待其完成（不切换当前会话）
     */
    suspend fun executeCommand(sessionId: String, command: String): String? =
        executeCommandFlow(sessionId, command).last().outputChunk

    suspend fun executeHiddenCommand(
        command: String,
        executorKey: String = "default",
        timeoutMs: Long = 120000L
    ): HiddenExecResult {
        val origin = executionOrigin(UUID.randomUUID().toString())
        val local = terminalManager.getFileSystemProvider() is LocalFileSystemProvider
        val observation = if (local) fileJournal?.begin(origin) else null
        try {
            return terminalManager.executeHiddenCommand(command, executorKey, timeoutMs)
        } finally {
            if (observation != null) fileJournal?.finish(observation)
        }
    }

    /**
     * 执行命令 - Flow版本
     * 返回命令执行过程中的所有事件，直到命令完成
     */
    fun executeCommandFlow(
        sessionId: String,
        command: String,
        commandId: String = UUID.randomUUID().toString(),
        onCancellationOutcome: (com.ai.assistance.operit.terminal.command.CommandCancellationOutcome) -> Unit = {},
    ): Flow<CommandExecutionEvent> = observedCommandFlow(sessionId, command, commandId, null, onCancellationOutcome)

    internal fun timedCommandFlow(sessionId: String, command: String, timeoutMs: Long): Flow<CommandExecutionEvent> =
        observedCommandFlow(sessionId, command, UUID.randomUUID().toString(), timeoutMs) {}

    private fun observedCommandFlow(
        sessionId: String, command: String, commandId: String, timeoutMs: Long?,
        onCancellationOutcome: (com.ai.assistance.operit.terminal.command.CommandCancellationOutcome) -> Unit,
    ): Flow<CommandExecutionEvent> {
        // 在创建 Flow 时冻结调用者，托管任务换协程收集后仍保留原始工具身份。
        val origin = executionOrigin(commandId)
        return flow {
            val session = terminalState.value.sessions.firstOrNull { it.id == sessionId }
            val journal = fileJournal?.takeIf { session?.terminalType == TerminalType.LOCAL }
            val prepareStarted = System.nanoTime()
            if (journal != null) AppLogger.d(TAG, "终端命令 $commandId 开始观察准备，会话=$sessionId")
            val observation = journal?.begin(origin, session?.currentDirectory)
            val executionStarted = System.nanoTime()
            var completion: CommandExecutionEvent? = null
            val execute: suspend () -> Unit = {
                terminalCommandFlow(
                    events = commandEvents, sessionId = sessionId, commandId = commandId,
                    submit = {
                        if (observation != null) AppLogger.d(TAG, "终端命令 $commandId 提交，观察准备=${(executionStarted - prepareStarted) / 1_000_000}ms")
                        terminalManager.sendCommandToSession(sessionId, command, commandId)
                    },
                    cancel = { terminalManager.cancelCommand(sessionId, commandId) },
                    onCancellationOutcome = onCancellationOutcome,
                ).collect { event ->
                    if (event.isCompleted) completion = event else emit(event)
                }
            }
            try {
                // 只对命令从提交到退出计时。历史采集用时不能覆盖进程已经给出的退出码。
                if (timeoutMs == null) execute() else withTimeout(timeoutMs) { execute() }
            } finally {
                if (journal != null && observation != null) {
                    val finishStarted = System.nanoTime()
                    AppLogger.d(TAG, "终端命令 $commandId 执行阶段结束，完成事件=${completion != null}，耗时=${(finishStarted - executionStarted) / 1_000_000}ms")
                    journal.finish(observation)
                }
            }
            completion?.let { emit(it) }
        }
    }

    private fun executionOrigin(commandId: String): TerminalChangeOrigin {
        val caller = ToolExecutionManager.currentToolRuntimeContext()
        return TerminalChangeOrigin(caller?.workspaceChatId.orEmpty(), caller?.invokingToolName ?: "terminal", commandId,
            subagentExecutionId = caller?.subagentExecutionId)
    }

    suspend fun cancelCommand(sessionId: String, commandId: String) =
        terminalManager.cancelCommand(sessionId, commandId)

    /**
     * 发送输入到当前会话
     */
    fun sendInput(sessionId: String, input: String) {
        val session = terminalState.value.sessions.firstOrNull { it.id == sessionId }
        val caller = ToolExecutionManager.currentToolRuntimeContext()
        val origin = TerminalChangeOrigin(caller?.workspaceChatId.orEmpty(), caller?.invokingToolName ?: "terminal", null,
            subagentExecutionId = caller?.subagentExecutionId)
        if (session?.terminalType == TerminalType.LOCAL && input.isNotEmpty()) runBlocking(Dispatchers.IO) {
            fileJournal?.interactiveInput(sessionId, origin, session.currentDirectory)
        }
        terminalManager.sendInput(input, sessionId)
    }

    /**
     * 发送中断信号 (Ctrl+C)
     */
    fun sendInterruptSignal(sessionId: String) {
        terminalManager.sendInterruptSignal(sessionId)
    }

    /**
     * 检查服务是否已连接 (现在总是返回 true)
     */
    fun isConnected(): Boolean {
        return true
    }
}
