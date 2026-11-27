package com.ai.assistance.operit.core.tools.builtin.terminal

import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/** JS 桥接用原生线程调用同步工具，显式接续调用方任务，取消才能到达命令收集器。 */
internal fun <T> runTerminalWithCallerCancellation(block: suspend CoroutineScope.() -> T): T {
    val caller = ToolExecutionManager.currentToolRuntimeContext()?.cancellationJob
    return runBlocking(caller ?: EmptyCoroutineContext, block)
}
