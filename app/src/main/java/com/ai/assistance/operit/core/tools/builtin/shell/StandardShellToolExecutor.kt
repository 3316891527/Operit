package com.ai.assistance.operit.core.tools.builtin.shell

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.tools.ADBResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.system.shell.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.core.tools.protocol.ToolValidationResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager
import kotlin.coroutines.EmptyCoroutineContext

/** 执行一次性 Android Shell 工具调用，沿用当前授权并保留取消信号。 */
open class StandardShellToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ShellToolExecutor"
        private const val DEFAULT_TIMEOUT = 30_000L
    }

    fun invoke(tool: AITool): ToolResult {
        // JS 原生桥接在独立线程调用同步入口，显式继承发起工具的取消任务。
        val parent = ToolExecutionManager.currentToolRuntimeContext()?.cancellationJob
        return runBlocking(parent ?: EmptyCoroutineContext) { invokeSuspend(tool) }
    }

    internal suspend fun invokeSuspend(tool: AITool): ToolResult {
        // 先校验命令与期限，非法参数不能启动进程。
        val validationResult = validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = validationResult.errorMessage
            )
        }

        val command = tool.parameters.find { it.name == "command" }?.value ?: ""
        val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: DEFAULT_TIMEOUT

        return try {
            val result = AndroidShellExecutor.executeToolShellCommand(command, timeoutMs)

            if (result.success) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                ADBResultData(
                                        command = command,
                                        output = result.stdout,
                                        exitCode = result.exitCode
                                )
                )
            } else {
                // 失败时保留已产生的输出和执行错误。
                val errorOutput =
                        if (result.stderr.isNotEmpty()) {
                            "${result.stderr.trim()}\n${result.stdout.trim()}"
                        } else {
                            result.stdout.trim()
                        }

                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Android Shell command execution failed (exit code: ${result.exitCode}): $errorOutput"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing Android Shell command", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Android Shell command execution failed: ${e.message}"
            )
        }
    }

    /** 校验一次性 Shell 工具的参数。 */
    fun validateParameters(tool: AITool): ToolValidationResult {
        val command = tool.parameters.find { it.name == "command" }?.value
        val timeout = tool.parameters.find { it.name == "timeout_ms" }?.value

        return when {
            timeout != null && (timeout.toLongOrNull()?.let { it > 0 } != true) ->
                ToolValidationResult(valid = false, errorMessage = "timeout_ms must be a positive integer")
            command.isNullOrBlank() -> {
                ToolValidationResult(valid = false, errorMessage = "Command parameter is required")
            }
            else -> {
                val dangerReason = ShellCommandSafety.validate(command)
                if (dangerReason != null) {
                    ToolValidationResult(
                            valid = false,
                            errorMessage = "Potentially dangerous command detected: $dangerReason"
                    )
                } else {
                    ToolValidationResult(valid = true)
                }
            }
        }
    }
}
