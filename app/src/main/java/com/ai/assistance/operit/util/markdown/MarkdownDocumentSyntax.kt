package com.ai.assistance.operit.util.markdown

import java.util.Locale

internal enum class MarkdownSourceKind { NATIVE, QUOTE, ORDERED_ITEM, UNORDERED_ITEM, DEFINITION, HTML, FOOTNOTE }
internal data class MarkdownSourceBlock(val kind: MarkdownSourceKind, val content: String)
internal data class MarkdownDocumentSource(val body: String, val footnotes: List<MarkdownSourceBlock>)

/** 全文语法先建立定义，再展开引用；代码、公式与 XML 范围始终保持原文。 */
internal object MarkdownDocumentSyntax {
    private val definition = Regex("^ {0,3}\\[([^]\\n]+)]:[ \\t]*(.*)$")
    private val quote = Regex("^ {0,3}> ?")
    private val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
    private val htmlStart = Regex("^ {0,3}<([A-Za-z][A-Za-z0-9:-]*)(?:[ \\t/>]|$)")
    private val htmlBlocks = setOf("address", "article", "aside", "blockquote", "details", "dialog", "div", "dl", "fieldset", "figure", "figcaption", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "html", "iframe", "main", "nav", "ol", "p", "pre", "section", "style", "summary", "table", "ul")
    private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    private fun label(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /** 避免完整消息复用未完成全文解析的流式节点。 */
    fun needsDocumentPass(source: String): Boolean = source.contains(Regex("(?m)^(?: {0,3}(?:>|[-+*] |[0-9]+[.)] |\\[[^]\\n]+]:|<|\\[TOC]|\\[toc]|: )| {2,}[-+*] )")) ||
        source.contains(Regex("(?m)^ {0,3}(?:=+|-+)[ \\t]*$")) || source.contains("[^") || source.contains("][") || source.contains(Regex("<(?:/?(?:b|strong|i|em|u|mark|sub|sup)>|https?://|mailto:)"))

    /** 受保护范围按行确定，避免示例代码或工具卡片中的定义污染正文。 */
    private fun tagDepth(line: String, tag: String): Int = Regex("<(/?)" + Regex.escape(tag) + "(?=[\\s/>])[^>]*>", RegexOption.IGNORE_CASE)
        .findAll(line).fold(0) { depth, match -> depth + if (match.value.endsWith("/>")) 0 else if (match.groupValues[1] == "/") -1 else 1 }

    private fun opaqueLines(lines: List<String>): IntArray {
        val result = IntArray(lines.size)
        var fenceMarker: String? = null
        var mathEnd: String? = null
        var xmlTag: String? = null
        var xmlDepth = 0
        val inlineTags = setOf("b", "strong", "i", "em", "u", "ins", "s", "del", "strike", "mark", "sub", "sup", "span", "a", "br")
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            val marker = fenceMarker
            if (marker != null) {
                result[index] = 1
                if (trimmed.length >= marker.length && trimmed.all { it == marker.first() }) fenceMarker = null
                continue
            }
            val end = mathEnd
            if (end != null) {
                result[index] = 1
                if (trimmed == end) mathEnd = null
                continue
            }
            val xml = xmlTag
            if (xml != null) {
                result[index] = 1
                xmlDepth += tagDepth(line, xml)
                if (xmlDepth <= 0) xmlTag = null
                continue
            }
            val opening = fence.find(line)
            if (opening != null) { result[index] = 1; fenceMarker = opening.groupValues[1]; continue }
            if (trimmed == "$$" || trimmed == "\\[") {
                result[index] = 1
                mathEnd = if (trimmed == "$$") "$$" else "\\]"
                continue
            }
            val tag = htmlStart.find(line)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
            if (tag != null && tag !in inlineTags) {
                result[index] = 2
                xmlDepth = tagDepth(line, tag)
                if (tag !in voidTags && xmlDepth > 0) xmlTag = tag
            }
        }
        return result
    }

    fun prepare(source: String): MarkdownDocumentSource {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList()
        val opaque = opaqueLines(lines)
        val references = linkedMapOf<String, MarkdownDestination>()
        val footnotes = linkedMapOf<String, String>()
        var cursor = 0
        while (cursor < lines.size) {
            val match = if ((opaque[cursor] != 0)) null else definition.matchEntire(lines[cursor])
            if (match == null) { cursor++; continue }
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            if (key.startsWith('^')) {
                val body = StringBuilder(value)
                lines[cursor++] = ""
                while (cursor < lines.size && (lines[cursor].startsWith("    ") || lines[cursor].startsWith('\t') ||
                    (lines[cursor].isBlank() && cursor + 1 < lines.size && lines[cursor + 1].startsWith("    ")))) {
                    body.append('\n').append(lines[cursor].removePrefix("    ").removePrefix("\t"))
                    lines[cursor++] = ""
                }
                footnotes.putIfAbsent(label(key.drop(1)), body.toString())
            } else {
                val target = MarkdownDestination.parse(value)
                if (target != null) { references.putIfAbsent(label(key), target); lines[cursor] = "" }
                cursor++
            }
        }
        // Setext 是两行结构，只转换正文中的相邻行，不匹配分隔线自身或代码内容。
        for (index in 1 until lines.size) {
            if ((opaque[index] != 0) || (opaque[index - 1] != 0) || lines[index - 1].isBlank()) continue
            val underline = lines[index].trim()
            if (underline.length < 1 || !underline.all { it == '=' } && !underline.all { it == '-' }) continue
            if (markdownListMarker(lines[index - 1]) != null || quote.containsMatchIn(lines[index - 1]) || lines[index - 1].startsWith('#')) continue
            lines[index - 1] = (if (underline.first() == '=') "# " else "## ") + lines[index - 1].trim()
            lines[index] = ""
        }
        val numbers = linkedMapOf<String, Int>()
        val rewritten = lines.mapIndexed { index, line -> if ((opaque[index] != 0)) line else rewriteInline(line, references, footnotes.keys, numbers) }.toMutableList()
        val headings = mutableListOf<Triple<Int, String, String>>()
        val slugs = mutableMapOf<String, Int>()
        rewritten.forEachIndexed { index, line ->
            if ((opaque[index] != 0)) return@forEachIndexed
            val match = Regex("^ {0,3}(#{1,6}) +(.+?)[ \\t]*#*[ \\t]*$").matchEntire(line) ?: return@forEachIndexed
            val title = plainHeading(match.groupValues[2])
            val slug = markdownHeadingSlug(title)
            val count = slugs[slug] ?: 0
            slugs[slug] = count + 1
            headings += Triple(match.groupValues[1].length, title, slug + if (count == 0) "" else "-$count")
        }
        for (index in rewritten.indices) {
            if (!(opaque[index] != 0) && rewritten[index].trim().lowercase(Locale.ROOT) in setOf("[toc]", "[[toc]]")) {
                val minimumLevel = headings.minOfOrNull { it.first } ?: 1
                rewritten[index] = headings.joinToString("\n") { (level, title, slug) -> "  ".repeat(level - minimumLevel) + "- [$title](#$slug)" }
            }
        }
        val expandedFootnotes = mutableListOf<MarkdownSourceBlock>()
        var numberIndex = 0
        // 定义中的其他脚注按首次引用顺序继续收录，同时允许互相引用而不递归展开。
        while (numberIndex < numbers.size) {
            val entry = numbers.entries.elementAt(numberIndex++)
            val definitionText = footnotes.getValue(entry.key)
            val body = rewriteInline(definitionText, references, footnotes.keys, numbers)
            expandedFootnotes += MarkdownSourceBlock(MarkdownSourceKind.FOOTNOTE, "${markdownFootnoteId(entry.key)}\n${entry.value}\n$body")
        }
        return MarkdownDocumentSource(rewritten.joinToString("\n"), expandedFootnotes)
    }

    private fun plainHeading(text: String): String = text.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_`~^=]"), "").trim()

    private fun rewriteInline(text: String, references: Map<String, MarkdownDestination>, footnotes: Set<String>, numbers: MutableMap<String, Int>): String {
        val output = StringBuilder()
        var cursor = 0
        while (cursor < text.length) {
            val char = text[cursor]
            if (char == '\\' && cursor + 1 < text.length) { output.append(text, cursor, cursor + 2); cursor += 2; continue }
            if (char == '`' || char == '$') {
                val start = cursor
                while (cursor < text.length && text[cursor] == char) cursor++
                val marker = text.substring(start, cursor)
                val end = text.indexOf(marker, cursor)
                if (end >= 0) { output.append(text, start, end + marker.length); cursor = end + marker.length; continue }
                output.append(marker); continue
            }
            if (char == '<') {
                val end = text.indexOf('>', cursor + 1)
                if (end > cursor) {
                    val rawTag = text.substring(cursor + 1, end).lowercase(Locale.ROOT)
                    val tag = rawTag.removePrefix("/")
                    val replacement = when (tag) {
                        "b", "strong" -> "**"
                        "i", "em" -> "*"
                        "u", "ins" -> "__"
                        "s", "del", "strike" -> "~~"
                        "mark" -> "=="
                        "sub" -> "~"
                        "sup" -> "^"
                        else -> null
                    }
                    if (replacement != null) { output.append(replacement); cursor = end + 1; continue }
                    val address = text.substring(cursor + 1, end)
                    if (address.startsWith("https://") || address.startsWith("http://") || address.startsWith("mailto:")) {
                        output.append('[').append(address.removePrefix("mailto:")).append("](").append(address).append(')')
                        cursor = end + 1; continue
                    }
                }
            }
            if (char != '[') { output.append(char); cursor++; continue }
            val end = text.indexOf(']', cursor + 1)
            if (end < 0) { output.append(char); cursor++; continue }
            val name = text.substring(cursor + 1, end)
            if (name.startsWith('^') && label(name.drop(1)) in footnotes) {
                val key = label(name.drop(1))
                val number = numbers.getOrPut(key) { numbers.size + 1 }
                output.append("^[").append(number).append("](#fn-").append(markdownFootnoteId(key)).append(")^")
                cursor = end + 1; continue
            }
            if (end + 1 < text.length && text[end + 1] == '(') {
                // 内联链接的目标内不再展开其他标记。
                var targetEnd = end + 2
                var depth = 1
                while (targetEnd < text.length && depth > 0) {
                    if (text[targetEnd] == '\\' && targetEnd + 1 < text.length) { targetEnd += 2; continue }
                    if (text[targetEnd] == '(') depth++
                    if (text[targetEnd] == ')') depth--
                    targetEnd++
                }
                output.append(text, cursor, targetEnd); cursor = targetEnd; continue
            }
            var referenceEnd = end + 1
            val key = if (referenceEnd < text.length && text[referenceEnd] == '[') {
                val close = text.indexOf(']', referenceEnd + 1)
                if (close < 0) { output.append(char); cursor++; continue }
                val reference = text.substring(referenceEnd + 1, close).ifEmpty { name }
                referenceEnd = close + 1
                label(reference)
            } else label(name)
            val destination = references[key]
            if (destination != null) {
                output.append('[').append(name).append("](<").append(destination.url).append('>')
                destination.title?.let { output.append(" \"").append(it.replace("\"", "\\\"")).append('"') }
                output.append(')'); cursor = referenceEnd
            } else { output.append(text, cursor, end + 1); cursor = end + 1 }
        }
        return output.toString()
    }

    /** 按明确的块语法分派；普通块仍由原生流解析器处理。 */
    fun blocks(source: String): List<MarkdownSourceBlock> {
        val lines = source.split('\n')
        val opaque = opaqueLines(lines)
        val result = mutableListOf<MarkdownSourceBlock>()
        val native = StringBuilder()
        fun flush() { if (native.isNotBlank()) result += MarkdownSourceBlock(MarkdownSourceKind.NATIVE, native.toString()); native.setLength(0) }
        var cursor = 0
        while (cursor < lines.size) {
            val line = lines[cursor]
            val tag = htmlStart.find(line)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
            if (opaque[cursor] == 2 && tag in htmlBlocks) {
                flush()
                val start = cursor++
                var depth = tagDepth(line, tag!!)
                if (tag !in voidTags) while (cursor < lines.size && depth > 0) depth += tagDepth(lines[cursor++], tag)
                result += MarkdownSourceBlock(MarkdownSourceKind.HTML, lines.subList(start, cursor).joinToString("\n"))
                continue
            }
            if (!(opaque[cursor] != 0) && quote.containsMatchIn(line)) {
                flush()
                val start = cursor++
                while (cursor < lines.size && quote.containsMatchIn(lines[cursor])) cursor++
                result += MarkdownSourceBlock(MarkdownSourceKind.QUOTE, lines.subList(start, cursor).joinToString("\n"))
                continue
            }
            val marker = if ((opaque[cursor] != 0) || line.trim().matches(Regex("[-*_]{3,}"))) null else markdownListMarker(line)
            if (marker != null) {
                flush()
                val start = cursor++
                while (cursor < lines.size) {
                    val next = lines[cursor]
                    val nextMarker = markdownListMarker(next)
                    if (nextMarker != null && nextMarker.indent <= marker.indent) break
                    if (next.isBlank()) {
                        val following = lines.getOrNull(cursor + 1)
                        if (following == null || following.takeWhile { it == ' ' }.length <= marker.indent) break
                    } else if (next.takeWhile { it == ' ' }.length <= marker.indent) break
                    cursor++
                }
                result += MarkdownSourceBlock(if (marker.number == null) MarkdownSourceKind.UNORDERED_ITEM else MarkdownSourceKind.ORDERED_ITEM, lines.subList(start, cursor).joinToString("\n"))
                continue
            }
            if (!(opaque[cursor] != 0) && line.isNotBlank() && cursor + 1 < lines.size && lines[cursor + 1].startsWith(": ")) {
                flush()
                val start = cursor++
                while (cursor < lines.size && (lines[cursor].startsWith(": ") || lines[cursor].startsWith("  "))) cursor++
                result += MarkdownSourceBlock(MarkdownSourceKind.DEFINITION, lines.subList(start, cursor).joinToString("\n"))
                continue
            }
            native.append(line).append('\n'); cursor++
        }
        flush()
        return result
    }
}
