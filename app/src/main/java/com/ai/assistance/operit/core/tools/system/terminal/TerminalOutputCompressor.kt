package com.ai.assistance.operit.core.tools.system.terminal

internal object TerminalOutputCompressor {
    data class Summary(val output: String, val omittedLines: Int, val strategy: String)

    private enum class Tool(val strategy: String) {
        GRADLE("gradle-progress"),
        MAVEN("maven-downloads"),
        CARGO("cargo-progress"),
        NODE("node-test-passes"),
        PYTEST("pytest-passes"),
        GO("go-test-passes"),
    }

    private val sgr = Regex("\u001B\\[[0-9;]*m")
    private val commandToken = Regex("[A-Za-z0-9_./:=+@%,-]+")
    private val diagnostic = Regex(
        "error|warn|fail|exception|assert|traceback|panic|fatal|stacktrace|错误|警告|失败|异常|断言|^[ew]:[ \\t]",
        RegexOption.IGNORE_CASE,
    )
    private val jsonArrayStart = Regex("\\[\\s*(?:[\\[\\{\\\"0-9-]|true|false|null|$)")
    private val csvHeader = Regex("[\\p{L}\\p{N}_.:-]+(?:[ \\t]*,[ \\t]*[\\p{L}\\p{N}_.:-]+)+")
    private val machineOptions = listOf(
        "json", "format", "porcelain", "message-format", "xml", "csv", "junit",
        "report", "output", "result", "list", "collect-only", "tap", "null",
    )
    private val gradleTask = Regex("(?:build|check|test(?:[A-Z][A-Za-z0-9]*)?|assemble(?:[A-Z][A-Za-z0-9]*)?|compile(?:[A-Z][A-Za-z0-9]*)?)")
    private val progress = mapOf(
        Tool.GRADLE to listOf(
            Regex("> Task :[A-Za-z0-9_.:-]+(?: (?:UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED))?"),
        ),
        Tool.MAVEN to listOf(
            Regex("\\[INFO] (?:Downloading|Downloaded) from [A-Za-z0-9_.-]+: https?://\\S+(?: \\([0-9.,]+ [kMGT]?B at [0-9.,]+ [kMGT]?B/s\\))?"),
        ),
        Tool.CARGO to listOf(
            Regex(" {0,4}(?:Compiling|Checking|Downloaded) [A-Za-z0-9_-]+ v[0-9][A-Za-z0-9.+-]*(?: \\([^\\r\\n]+\\))?"),
            Regex("test [A-Za-z0-9_:]+ \\.\\.\\. ok"),
        ),
        Tool.NODE to listOf(
            Regex("ok [0-9]+ - \\S[^\\r\\n]*"),
            Regex("PASS[ \\t]+\\S+\\.(?:test|spec)\\.[cm]?[jt]sx?(?: \\([0-9.]+ s\\))?"),
        ),
        Tool.PYTEST to listOf(
            Regex("[A-Za-z0-9_./-]+\\.py \\.+ +\\[ *[0-9]{1,3}%]"),
            Regex("[A-Za-z0-9_./-]+\\.py(?:::[^\\s]+)+ PASSED(?: +\\[ *[0-9]{1,3}%])?"),
        ),
        Tool.GO to listOf(
            Regex("--- PASS: [A-Za-z0-9_/]+ \\([0-9.]+s\\)"),
            Regex("ok[ \\t]+[A-Za-z0-9._~/-]+[ \\t]+(?:[0-9.]+s|\\(cached\\))"),
        ),
    )

    fun compress(command: String, output: String): Summary? {
        if (output.length < 2000) return null
        val tool = identifyTool(command) ?: return null
        val plain = sgr.replace(output, "")
        // 未知控制序列可能携带屏幕语义，不能当作静态日志改写。
        if (plain.indices.any { index ->
                val char = plain[index]
                (char < ' ' && char != '\n' && char != '\t' &&
                    !(char == '\r' && index + 1 < plain.length && plain[index + 1] == '\n')) ||
                    char in '\u007f'..'\u009f'
            }) return null
        if (looksStructured(plain)) return null

        // 连同换行符保存原行，保留行的空格、颜色及换行形式均不改写。
        val lines = output.splitToSequence('\n').toList().let { parts ->
            parts.mapIndexedNotNull { index, line ->
                when {
                    index < parts.lastIndex -> "$line\n"
                    line.isNotEmpty() -> line
                    else -> null
                }
            }
        }
        var diagnosticSection = false
        val foldable = lines.map { line ->
            val text = sgr.replace(line.removeSuffix("\n").removeSuffix("\r"), "")
            val knownProgress = progress.getValue(tool).any { it.matches(text) }
            // Gradle 任务标识符可包含 Errors 等单词，完整任务行本身不代表诊断。
            val taskIdentifier = tool == Tool.GRADLE && knownProgress
            // 诊断之后可能是断言原文或日志样本，整段后文都保留，避免误折叠样本。
            if (!taskIdentifier && (diagnostic.containsMatchIn(text) || text.trimStart().startsWith("not ok "))) {
                diagnosticSection = true
            }
            !diagnosticSection && knownProgress
        }
        val result = StringBuilder(output.length)
        var omitted = 0
        var index = 0
        while (index < lines.size) {
            if (!foldable[index]) {
                result.append(lines[index++])
                continue
            }
            var end = index + 1
            while (end < lines.size && foldable[end]) end++
            if (end - index >= 4) {
                val count = end - index - 2
                val newline = if (lines[index].endsWith("\r\n")) "\r\n" else "\n"
                result.append(lines[index])
                result.append("[已省略 ${count} 行已识别的进度或成功日志]").append(newline)
                result.append(lines[end - 1])
                omitted += count
            } else {
                for (lineIndex in index until end) result.append(lines[lineIndex])
            }
            index = end
        }
        val saved = output.length - result.length
        if (omitted == 0 || saved < 256 || saved.toLong() * 5 < output.length.toLong()) return null
        return Summary(result.toString(), omitted, tool.strategy)
    }

    private fun identifyTool(command: String): Tool? {
        // 只接受不含引号和 shell 运算的简单命令，避免猜测复合命令的输出类型。
        if (command.any { it == '\n' || it == '\r' }) return null
        val tokens = command.trim().split(Regex("[ \\t]+"))
        if (tokens.any { !commandToken.matches(it) }) return null
        val arguments = tokens.drop(1)
        if (arguments.any { argument ->
                if (!argument.startsWith('-')) false else {
                    val option = argument.trimStart('-').lowercase()
                    option.substringBefore('=') in setOf("z", "0") || machineOptions.any { option.startsWith(it) }
                }
            }) return null
        return when (tokens.first()) {
            "gradle", "gradlew", "./gradlew" ->
                if (arguments.any { gradleTask.matches(it.substringAfterLast(':')) }) Tool.GRADLE else null
            "mvn", "mvnw", "./mvnw" ->
                if (arguments.any { it in setOf("compile", "test", "package", "verify", "install") }) Tool.MAVEN else null
            "cargo" -> if (arguments.firstOrNull() in setOf("build", "test", "check")) Tool.CARGO else null
            "npm", "pnpm" -> when {
                arguments.firstOrNull() in setOf("test", "build") -> Tool.NODE
                arguments.firstOrNull() == "run" && arguments.getOrNull(1) in setOf("test", "build") -> Tool.NODE
                else -> null
            }
            "pytest" -> Tool.PYTEST
            "go" -> if (arguments.firstOrNull() == "test") Tool.GO else null
            else -> null
        }
    }

    private fun looksStructured(output: String): Boolean {
        val first = output.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart() ?: return false
        if (csvHeader.matches(first.trim())) return true
        return output.lineSequence().any { line ->
            val text = line.trimStart()
            text.startsWith('{') || text.startsWith('<') || text.startsWith('"') ||
                (text.startsWith('[') && jsonArrayStart.containsMatchIn(text)) ||
                text.startsWith("diff --git ") ||
                (text.startsWith("--- ") && !text.startsWith("--- PASS: ") &&
                    !text.startsWith("--- FAIL: ") && !text.startsWith("--- SKIP: ")) ||
                text.startsWith("+++ ") || text.startsWith("@@ ")
        }
    }
}
