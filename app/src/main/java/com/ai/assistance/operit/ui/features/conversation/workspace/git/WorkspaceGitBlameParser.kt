package com.ai.assistance.operit.ui.features.conversation.workspace.git

import com.ai.assistance.operit.ui.features.conversation.workspace.changes.DiffLineKind
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.ReviewContent
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.SnapshotKind
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceDiff
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal data class GitBlameLine(
    val commit: String, val originalLine: Int, val finalLine: Int,
    val author: String, val email: String, val authorTime: Long, val timezone: String,
    val summary: String, val path: String, val text: String, val boundary: Boolean,
    val previousCommit: String?, val previousPath: String?,
) {
    val uncommitted: Boolean get() = commit.all { it == '0' }
}

/** 只接受一行 porcelain，作者与来源路径始终取自 Git 的显式字段。 */
internal object WorkspaceGitBlameParser {
    private val header = Regex("([0-9a-fA-F]{40}|[0-9a-fA-F]{64}) ([1-9][0-9]*) ([1-9][0-9]*)(?: ([1-9][0-9]*))?")
    private val hash = Regex("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")

    fun parse(output: String): GitBlameLine {
        val lines = output.lineSequence().iterator()
        require(lines.hasNext()) { "Empty Git blame response" }
        val first = checkNotNull(header.matchEntire(lines.next())) { "Invalid Git blame header" }
        require(first.groupValues[4].isEmpty() || first.groupValues[4] == "1") { "Expected one blamed line" }
        val fields = linkedMapOf<String, String>()
        var content: String? = null
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.startsWith('\t')) { content = line.drop(1); break }
            val name = line.substringBefore(' ')
            require(name !in fields) { "Duplicate Git blame field: $name" }
            fields[name] = line.substringAfter(' ', "")
        }
        require(lines.asSequence().all { it.isEmpty() }) { "Unexpected extra Git blame records" }
        fun field(name: String): String = checkNotNull(fields[name]) { "Missing Git blame field: $name" }
        val email = field("author-mail")
        require(email.startsWith('<') && email.endsWith('>')) { "Invalid Git author email" }
        val time = field("author-time").toLong()
        Math.multiplyExact(time, 1000L)
        val timezone = field("author-tz")
        require(Regex("[+-][0-9]{4}").matches(timezone)) { "Invalid Git author timezone" }
        require(timezone.substring(1, 3).toInt() <= 23 && timezone.substring(3).toInt() < 60)
        val previous = fields["previous"]?.let { value ->
            val commit = value.substringBefore(' ')
            require(hash.matches(commit) && ' ' in value) { "Invalid Git blame parent" }
            commit.lowercase() to path(value.substringAfter(' '))
        }
        return GitBlameLine(first.groupValues[1].lowercase(), first.groupValues[2].toInt(), first.groupValues[3].toInt(),
            field("author"), email.substring(1, email.lastIndex), time, timezone, field("summary"), path(field("filename")),
            checkNotNull(content) { "Missing Git blame source text" }, "boundary" in fields, previous?.first, previous?.second)
    }

    /** Git 对控制字符与文件名使用 C 转义，按字节解码保留 UTF-8 和合法空白。 */
    fun path(raw: String): String {
        val result = if (!raw.startsWith('"')) raw else {
            require(raw.length >= 2 && raw.endsWith('"')) { "Unterminated Git path" }
            val bytes = raw.substring(1, raw.lastIndex).toByteArray(Charsets.UTF_8)
            val decoded = ByteArrayOutputStream(bytes.size)
            var index = 0
            while (index < bytes.size) {
                val value = bytes[index++].toInt() and 255
                if (value != '\\'.code) { decoded.write(value); continue }
                require(index < bytes.size) { "Incomplete Git path escape" }
                val escaped = bytes[index++].toInt() and 255
                val simple = when (escaped.toChar()) {
                    '\\' -> 92; '"' -> 34; 'a' -> 7; 'b' -> 8; 't' -> 9; 'n' -> 10; 'v' -> 11; 'f' -> 12; 'r' -> 13
                    else -> null
                }
                if (simple != null) decoded.write(simple)
                else {
                    require(escaped in '0'.code..'7'.code) { "Unknown Git path escape" }
                    var octal = escaped - '0'.code
                    var digits = 1
                    while (digits < 3 && index < bytes.size && bytes[index].toInt() in '0'.code..'7'.code) {
                        octal = octal * 8 + bytes[index++].toInt() - '0'.code
                        digits++
                    }
                    require(octal <= 255) { "Invalid Git path byte" }
                    decoded.write(octal)
                }
            }
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded.toByteArray())).toString()
        }
        return validatePath(result)
    }

    /** 原始路径与 porcelain 引号路径分开处理，文件名可以以双引号开头。 */
    fun validatePath(result: String): String {
        require(result.isNotEmpty() && '\u0000' !in result && !result.startsWith('/')) { "Invalid repository path" }
        require(result.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Git path outside repository" }
        return result
    }
}

internal const val BLAME_MAX_LINE_CHARACTERS = 8_192

/** Blame 会直接准备行映射及提交差异，保留原有限额，不能随可手动加载的审阅页一起放宽。 */
internal fun blameDiffSupported(content: ReviewContent): Boolean {
    if (!content.isText) return false
    val before = checkNotNull(content.before)
    val after = checkNotNull(content.after)
    return before.count { it == '\n' } + after.count { it == '\n' } <= 20_000 &&
        before.lineSequence().none { it.length > BLAME_MAX_LINE_CHARACTERS } &&
        after.lineSequence().none { it.length > BLAME_MAX_LINE_CHARACTERS }
}

/** 用当前编辑快照定位原有行，新增与修改行不会被误归给历史作者。 */
internal fun blameLineMapping(before: String, current: String): Map<Int, Int> {
    val old = before.replace("\r\n", "\n")
    val next = current.replace("\r\n", "\n")
    require(blameDiffSupported(ReviewContent(old, next, SnapshotKind.TEXT, SnapshotKind.TEXT))) { "Blame text exceeds review limits" }
    return WorkspaceDiff.compare(old, next).rows.filter { it.kind == DiffLineKind.CONTEXT }
        .associate { checkNotNull(it.newLine) to checkNotNull(it.oldLine) }
}
internal data class GitBlameHistory(val parents: List<String>, val message: String) {
    fun parent(line: GitBlameLine): String? {
        val previous = line.previousCommit
        if (previous != null) {
            require(previous in parents) { "Blame predecessor is not a source commit parent" }
            return previous
        }
        return parents.firstOrNull()
    }
}

/** 读取对象原始父列表，浅克隆边界不能被误认为真正的根提交。 */
internal fun blameCommitHistory(raw: String): GitBlameHistory {
    val separator = raw.indexOf("\n\n")
    require(separator >= 0) { "Invalid Git commit object" }
    val headers = raw.substring(0, separator).lines()
    val hash = Regex("[0-9a-f]{40}|[0-9a-f]{64}")
    require(headers.first().startsWith("tree ") && hash.matches(headers.first().removePrefix("tree ")))
    val parents = headers.filter { it.startsWith("parent ") }.map { it.removePrefix("parent ") }
    require(parents.all { hash.matches(it) } && parents.distinct().size == parents.size)
    return GitBlameHistory(parents, raw.substring(separator + 2).trimEnd('\n'))
}
