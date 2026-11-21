package com.ai.assistance.operit.util.markdown

import java.net.URI
import java.util.Locale

/** 标准链接目标与可选标题共用解析，避免把标题误当成地址。 */
data class MarkdownDestination(val url: String, val title: String? = null) {
    companion object {
        fun parse(source: String): MarkdownDestination? {
            val text = source.trim()
            if (text.isEmpty()) return MarkdownDestination("")
            var cursor = 0
            val url = StringBuilder()
            if (text.startsWith('<')) {
                cursor++
                while (cursor < text.length && text[cursor] != '>') {
                    if (text[cursor] == '\\' && cursor + 1 < text.length) cursor++
                    url.append(text[cursor++])
                }
                if (cursor >= text.length) return null
                cursor++
            } else {
                var depth = 0
                while (cursor < text.length) {
                    val char = text[cursor]
                    if (char.isWhitespace() && depth == 0) break
                    if (char == '\\' && cursor + 1 < text.length) {
                        url.append(text[cursor + 1]); cursor += 2; continue
                    }
                    if (char == '(') depth++
                    if (char == ')') depth--
                    if (depth < 0) return null
                    url.append(char); cursor++
                }
                if (depth != 0) return null
            }
            val remainder = text.substring(cursor).trim()
            if (remainder.isEmpty()) return MarkdownDestination(url.toString())
            val opener = remainder.first()
            val closer = if (opener == '(') ')' else opener
            if (opener !in setOf('"', '\'', '(') || remainder.length < 2 || remainder.last() != closer) return null
            return MarkdownDestination(url.toString(), remainder.substring(1, remainder.lastIndex))
        }
    }
}

data class MarkdownListMarker(val indent: Int, val prefixLength: Int, val number: String?, val checked: Boolean?)

fun markdownListMarker(line: String): MarkdownListMarker? {
    val match = Regex("^( *)(?:([-+*])|([0-9]{1,9})[.)])([ \\t]+)(.*)$").matchEntire(line) ?: return null
    val body = match.groupValues[5]
    val task = Regex("^\\[([ xX])]([ \\t]+|$)").find(body)
    return MarkdownListMarker(
        indent = match.groupValues[1].length,
        prefixLength = match.range.last + 1 - body.length + (task?.value?.length ?: 0),
        number = match.groupValues[3].ifEmpty { null },
        checked = task?.let { it.groupValues[1] != " " },
    )
}

enum class MarkdownColumnAlignment { LEFT, CENTER, RIGHT }

/** 切列表格时保护代码范围，反斜杠只消费被转义的竖线。 */
fun markdownTableCells(line: String): List<String> {
    val text = line.trim()
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var cursor = if (text.startsWith('|')) 1 else 0
    var codeFence = 0
    while (cursor < text.length) {
        val char = text[cursor]
        if (char == '\\' && cursor + 1 < text.length) {
            if (text[cursor + 1] == '|') {
                cell.append('|'); cursor += 2; continue
            }
            cell.append(char).append(text[cursor + 1]); cursor += 2; continue
        }
        if (char == '`') {
            val start = cursor
            while (cursor < text.length && text[cursor] == '`') cursor++
            val count = cursor - start
            if (codeFence == 0) codeFence = count else if (codeFence == count) codeFence = 0
            cell.append(text, start, cursor)
            continue
        }
        if (char == '|' && codeFence == 0) {
            cells += cell.toString().trim().replace(Regex("(?i)<br\\s*/?>"), "\n")
            cell.setLength(0)
        } else cell.append(char)
        cursor++
    }
    if (cell.isNotEmpty() || !text.endsWith('|')) cells += cell.toString().trim().replace(Regex("(?i)<br\\s*/?>"), "\n")
    return cells
}

fun markdownTableAlignments(line: String): List<MarkdownColumnAlignment>? {
    val cells = markdownTableCells(line)
    if (cells.isEmpty() || cells.any { !it.matches(Regex(":?-{3,}:?")) }) return null
    return cells.map { when {
        it.startsWith(':') && it.endsWith(':') -> MarkdownColumnAlignment.CENTER
        it.endsWith(':') -> MarkdownColumnAlignment.RIGHT
        else -> MarkdownColumnAlignment.LEFT
    } }
}

fun markdownHeadingSlug(title: String): String = title.lowercase(Locale.ROOT)
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
    .trim().replace(Regex("[ \\t]+"), "-")

/** 工作区 Android 文件位置只参与相对资源解析，不改写已有 scheme。 */
fun resolveMarkdownResource(url: String, documentPath: String?): String {
    if (documentPath == null || url.isEmpty() || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(url) || url.startsWith("//")) return url
    val base = URI("file", "", documentPath, null)
    val resolved = base.resolve(URI(url.replace(" ", "%20"))).normalize()
    return URI("file", "", resolved.path, resolved.query, resolved.fragment).toASCIIString()
}

fun markdownFootnoteId(label: String): String = label.trim().lowercase(Locale.ROOT)
    .toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 255) }
