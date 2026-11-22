package com.ai.assistance.operit.core.chat.tools

import com.ai.assistance.operit.core.tools.HiddenTerminalCommandResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.TerminalCommandResultData
import com.ai.assistance.operit.core.tools.TerminalTaskResultData
import com.ai.assistance.operit.core.tools.ToolResultData
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.core.tools.runtime.ToolExecutionLimits
import com.ai.assistance.operit.core.tools.system.terminal.TerminalOutputCompressor
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONException
import org.json.JSONObject

/** 原始执行结果保留给工具与脚本；聊天使用单独的、可追溯到原文的投影。 */
internal class TerminalOutputPresentation(private val archiveDirectory: () -> File) {
    private data class Log(
        val command: String,
        val output: String,
        val truncated: Boolean,
        val render: (String) -> ToolResultData,
    )

    fun present(result: ToolResult): ToolResultData? {
        val log = logFor(result) ?: return null
        // 只处理受限的静态日志，避免为了节省上下文再次构造超大字符串。
        if (log.output.length !in 2000..MAX_SOURCE_CHARS) return null
        val summary = TerminalOutputCompressor.compress(log.command, log.output) ?: return null
        val destination = File(archiveDirectory(), "terminal_output_${UUID.randomUUID()}.log")
        val text = buildString {
            append(summary.output)
            append("\n\n[Terminal log compressed; original returned ")
            append(if (log.truncated) "tail (already truncated by the tool)" else "output")
            append(": ").append(destination.absolutePath)
            append("\nRead with read_file_part, environment=android. UTF-8; temporary file removed by app cleanup.]")
        }
        val presented = log.render(text)
        val payload = presented.toString()
        val originalBudget = result.result.toString().length.coerceAtMost(MAX_PRESENTATION_CHARS)
        // 原文入口、JSON 转义和包装字段一起计费；保证摘要和入口不会被原有结果上限截掉。
        if (payload.length + result.error.orEmpty().length > MAX_PRESENTATION_CHARS ||
            originalBudget - payload.length < 256 || payload.length.toLong() * 5 > originalBudget.toLong() * 4
        ) return null
        archive(destination, log.output)
        return presented
    }

    private fun logFor(result: ToolResult): Log? = when (val data = result.result) {
        is TerminalCommandResultData -> Log(data.command, data.output, false) {
            data.copy(output = it)
        }
        is HiddenTerminalCommandResultData -> Log(data.command, data.output, false) {
            data.copy(output = it)
        }
        is TerminalTaskResultData -> Log(data.command, data.output, data.outputTruncated) {
            data.copy(output = it)
        }
        is StringResultData -> packageLog(result, data.toString())
        else -> null
    }

    private fun packageLog(result: ToolResult, payload: String): Log? {
        if (result.toolName !in PACKAGE_TOOLS || payload.length > MAX_SOURCE_CHARS) return null
        val root = try { JSONObject(payload) } catch (_: JSONException) { return null }
        val body = when {
            root.opt("output") is String -> root
            root.optJSONObject("data")?.opt("output") is String -> root.getJSONObject("data")
            else -> return null
        }
        val command = body.opt("command") as? String ?: return null
        val output = body.getString("output")
        // 已经落盘的短指针继续使用；不为压缩而重新加载大文件或扩大模型输入。
        if (output == "(saved_to_file)" || body.has("output_saved_to")) return null
        val truncated = body.optBoolean("outputTruncated", false) || body.optBoolean("output_truncated", false)
        return Log(command, output, truncated) {
            body.put("output", it)
            StringResultData(root.toString())
        }
    }

    private fun archive(destination: File, output: String) {
        val directory = destination.parentFile
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create terminal output archive directory: $directory")
        }
        if (!destination.createNewFile()) throw IOException("Terminal output archive already exists")
        try {
            destination.bufferedWriter(Charsets.UTF_8).use { it.write(output) }
        } catch (error: IOException) {
            destination.delete()
            throw IOException("Cannot archive original terminal output before compression", error)
        }
    }

    companion object {
        private const val MAX_SOURCE_CHARS = 256_000
        private const val MAX_PRESENTATION_CHARS = ToolExecutionLimits.MAX_SINGLE_TOOL_RESULT_MESSAGE_CHARS / 2
        private val PACKAGE_TOOLS = setOf(
            "super_admin:terminal", "super_admin:bash", "super_admin:shell",
            "super_admin:terminal_start", "super_admin:terminal_poll", "super_admin:terminal_cancel",
        )
    }
}