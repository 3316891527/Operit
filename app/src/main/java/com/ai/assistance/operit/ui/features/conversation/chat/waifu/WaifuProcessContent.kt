package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex

/** 过程段只由连续的过程 XML 组成，正文、代码和插件卡片都保留各自的边界。 */
internal object WaifuProcessContent {
    val tags = setOf("think", "thinking", "tool", "tool_result")
    val xmlToken = Regex(
        """<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?]]>|</?([A-Za-z][A-Za-z0-9_]*)(?=\s|/?>)(?:[^<>"']|"[^"]*"|'[^']*')*>"""
    )

    /** 同名标签按深度配对，属性、注释和 CDATA 中的标签文本不参与配对。 */
    fun blockEnd(content: String, start: Int): Int? {
        val opening = xmlToken.find(content, start)
            ?.takeIf { it.range.first == start && !it.value.startsWith("</") }
            ?: return null
        val rawTagName = opening.groupValues[1]
        if (rawTagName.isEmpty()) return null
        if (opening.value.endsWith("/>")) return opening.range.last + 1
        var depth = 1
        xmlToken.findAll(content, opening.range.last + 1).forEach { match ->
            if (match.groupValues[1] != rawTagName) return@forEach
            if (match.value.startsWith("</")) {
                depth--
                if (depth == 0) return match.range.last + 1
            } else if (!match.value.endsWith("/>")) {
                depth++
            }
        }
        return null
    }

    /** 输入已去掉隐藏协议节点；未闭合的过程 XML 仍属于正在增长的同一段。 */
    fun isProcessOnly(renderable: String): Boolean {
        var cursor = 0
        var foundProcess = false
        while (cursor < renderable.length) {
            while (cursor < renderable.length && renderable[cursor].isWhitespace()) cursor++
            if (cursor == renderable.length) break
            val opening = xmlToken.find(renderable, cursor)
                ?.takeIf { it.range.first == cursor && !it.value.startsWith("</") }
                ?: return false
            val tagName = ChatMarkupRegex.normalizeToolLikeTagName(opening.groupValues[1])
            if (tagName !in tags) return false
            foundProcess = true
            cursor = blockEnd(renderable, cursor) ?: renderable.length
        }
        return foundProcess
    }

    data class Part(val content: String, val isProcess: Boolean)

    /** 自闭合过程标签在原生 Markdown 中属于纯文本，仍须保留它与真实正文的边界。 */
    fun splitPlainText(content: String): List<Part> {
        val parts = mutableListOf<Part>()
        var cursor = 0
        var index = 0
        var codeDelimiterLength = 0
        while (index < content.length) {
            if (content[index] == '`') {
                var slashStart = index
                while (slashStart > 0 && content[slashStart - 1] == '\\') slashStart--
                if ((index - slashStart) % 2 == 0) {
                    var end = index + 1
                    while (end < content.length && content[end] == '`') end++
                    val runLength = end - index
                    if (codeDelimiterLength == 0) codeDelimiterLength = runLength
                    else if (codeDelimiterLength == runLength) codeDelimiterLength = 0
                    index = end
                    continue
                }
            }
            if (content[index] == '<' && codeDelimiterLength == 0) {
                val opening = xmlToken.find(content, index)?.takeIf {
                    it.range.first == index && !it.value.startsWith("</")
                }
                val tagName = opening?.groupValues?.get(1)?.let(ChatMarkupRegex::normalizeToolLikeTagName)
                if (tagName in tags) {
                    if (cursor < index) parts += Part(content.substring(cursor, index), false)
                    val end = blockEnd(content, index) ?: content.length
                    parts += Part(content.substring(index, end), true)
                    cursor = end
                    index = end
                    continue
                }
            }
            index++
        }
        if (cursor < content.length) parts += Part(content.substring(cursor), false)
        return parts
    }

    fun mergeConsecutive(segments: List<String>): List<String> {
        val merged = mutableListOf<String>()
        val process = StringBuilder()
        fun flushProcess() {
            if (process.isNotEmpty()) {
                merged += process.toString()
                process.clear()
            }
        }
        for (segment in segments) {
            if (segment.isBlank()) continue
            if (isProcessOnly(segment)) {
                if (process.isNotEmpty()) process.append('\n')
                process.append(segment)
            } else {
                flushProcess()
                merged += segment
            }
        }
        flushProcess()
        return merged
    }
}
