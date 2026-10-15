package com.ai.assistance.operit.ui.common.a2ui

/** 卡片 JSON 只参与显示，不能被旧 XML 工具检测管线解释为工具调用。 */
internal object CardFences {
    private val opening = Regex("^[ \\t]{0,3}(`{3,}|~{3,})([^`~]*)$")

    private fun ranges(content: String): List<IntRange> {
        if (!content.contains("a2ui")) return emptyList()
        val lines = content.splitToSequence('\n').toList()
        val spans = mutableListOf<IntRange>()
        var index = 0
        var offset = 0
        while (index < lines.size) {
            val header = opening.matchEntire(lines[index].removeSuffix("\r"))
            if (header == null) {
                offset += lines[index++].length + 1
                continue
            }
            val delimiter = header.groupValues[1]
            val closing = Regex("^[ \\t]{0,3}" + Regex.escape(delimiter.first().toString()) + "{" + delimiter.length + ",}[ \\t]*\\r?$")
            val end = (index + 1 until lines.size).firstOrNull { closing.matches(lines[it]) }
            val last = end ?: lines.lastIndex
            val next = (offset + (index..last).sumOf { lines[it].length + 1 }).coerceAtMost(content.length)
            if (header.groupValues[2].trim() in setOf("a2ui", "a2ui-ref")) spans.add(offset until next)
            offset = next
            index = last + 1
        }
        return spans
    }

    /** 保留字符位置和换行，使工具调用的位置仍对应原始回复。 */
    fun forTools(content: String): String {
        val ranges = ranges(content)
        if (ranges.isEmpty()) return content
        val chars = content.toCharArray()
        ranges.forEach { range -> range.forEach { index ->
            if (chars[index] != '\n' && chars[index] != '\r') chars[index] = ' '
        } }
        return chars.concatToString()
    }

    data class Protected(val text: String, private val cards: List<Pair<String, String>>) {
        fun restore(processed: String): String {
            var result = processed
            cards.forEach { (token, source) ->
                check(token in result) { "Card placeholder was changed by tool normalization" }
                result = result.replace(token, source)
            }
            return result
        }
    }

    /** XML 标准化可能改变其他文本的长度，临时占位符使卡片仍可精确恢复。 */
    fun protect(content: String): Protected {
        val cards = mutableListOf<Pair<String, String>>()
        var text = content
        ranges(content).asReversed().forEach { range ->
            val token = "[operit-card-${java.util.UUID.randomUUID()}]"
            check(token !in content)
            cards.add(token to content.substring(range))
            text = text.replaceRange(range, token)
        }
        return Protected(text, cards)
    }
}