// SPDX-License-Identifier: AGPL-3.0-only
package com.ai.assistance.operit.ui.features.conversation.workspace.git

/**
 * 从 Operit2 change_review/git.ts 移植 NUL 协议解析与重命名配对。
 * 来源提交 1a05a94，原文件按 AGPL-3.0 发布，详见同目录 SOURCE.md。
 */
internal data class GitStatusEntry(val path: String, val status: String, val originalPath: String? = null, val untracked: Boolean = false)
internal data class GitLineStats(val additions: Int, val deletions: Int, val binary: Boolean)

internal object WorkspaceGitParser {
    fun status(text: String): List<GitStatusEntry> {
        val records = text.split('\u0000')
        val result = mutableListOf<GitStatusEntry>()
        var index = 0
        while (index < records.size) {
            val record = records[index++]
            if (record.isEmpty()) continue
            require(record.length >= 4 && record[2] == ' ') { "Invalid Git status record" }
            val flags = record.take(2)
            val renamed = flags.any { it == 'R' || it == 'C' }
            val original = if (renamed) records.getOrNull(index++).also { require(!it.isNullOrEmpty()) } else null
            val status = when {
                flags == "??" -> "A"
                flags == "AA" || flags == "DD" || 'U' in flags -> "U"
                renamed -> "R"
                'A' in flags -> "A"
                'D' in flags -> "D"
                'T' in flags -> "T"
                else -> "M"
            }
            result += GitStatusEntry(record.drop(3), status, original, flags == "??")
        }
        return result
    }

    fun nameStatus(text: String): List<GitStatusEntry> {
        val records = text.split('\u0000')
        val result = mutableListOf<GitStatusEntry>()
        var index = 0
        while (index < records.size) {
            val flag = records[index++]
            if (flag.isEmpty()) continue
            require(Regex("[ACDMRTUXB][0-9]*").matches(flag)) { "Invalid Git name-status record" }
            val original = if (flag[0] == 'R' || flag[0] == 'C') records.getOrNull(index++).also { require(!it.isNullOrEmpty()) } else null
            val path = records.getOrNull(index++)
            require(!path.isNullOrEmpty()) { "Missing Git name-status path" }
            result += GitStatusEntry(path, if (original == null) flag.take(1) else "R", original)
        }
        return result
    }

    fun numstat(text: String): Map<String, GitLineStats> {
        val records = text.split('\u0000')
        val result = linkedMapOf<String, GitLineStats>()
        var index = 0
        while (index < records.size) {
            val raw = records[index++]
            if (raw.isEmpty()) continue
            val parts = raw.split('\t', limit = 3)
            require(parts.size == 3) { "Invalid Git numstat record" }
            val path = if (parts[2].isEmpty()) {
                require(!records.getOrNull(index++).isNullOrEmpty()) { "Missing Git rename source" }
                records.getOrNull(index++)
            } else parts[2]
            require(!path.isNullOrEmpty()) { "Missing Git numstat path" }
            val binary = parts[0] == "-" || parts[1] == "-"
            result[path] = if (binary) GitLineStats(0, 0, true) else GitLineStats(parts[0].toInt(), parts[1].toInt(), false)
        }
        return result
    }

    /** 跨出绑定目录的重命名只保留目录内的一侧，不读取另一侧的内容。 */
    fun restrict(entries: List<GitStatusEntry>, prefix: String): List<GitStatusEntry> = entries.mapNotNull { entry ->
        fun contains(path: String) = prefix.isEmpty() || path.startsWith(prefix)
        val inside = contains(entry.path)
        val originalInside = entry.originalPath?.let(::contains) == true
        when {
            inside && (entry.originalPath == null || originalInside) -> entry
            inside -> entry.copy(status = "A", originalPath = null)
            originalInside -> entry.copy(path = checkNotNull(entry.originalPath), status = "D", originalPath = null)
            else -> null
        }
    }
}
