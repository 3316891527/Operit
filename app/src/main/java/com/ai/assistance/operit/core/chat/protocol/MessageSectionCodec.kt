package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.util.toolmarkup.ToolResultMarkup
import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.core.chat.protocol.ThinkingMarkup
import com.ai.assistance.operit.data.model.MessageSection

/**
 * 旧标记字符串和 sections 的双向转换。
 * 仅在旧记录导入和运行时协议边界转换，持久化使用 sections。
 */
object MessageSectionCodec {
    private val structuralTag = Regex(
        """<(think(?:ing)?|search|status|meta|""" +
            ChatMarkupRegex.TOOL_RESULT_TAG_NAME_REGEX_SOURCE + "|" +
            ChatMarkupRegex.TOOL_TAG_NAME_REGEX_SOURCE +
            """)\b""",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun parse(content: String): List<MessageSection> = parseContent(content, includeIncomplete = false)

    /** 仅供流式界面投影；未闭合工具仍是工具片段，原始请求与持久化解析不变。 */
    fun parseForDisplay(content: String): List<MessageSection> = parseContent(content, includeIncomplete = true)

    private fun parseContent(content: String, includeIncomplete: Boolean): List<MessageSection> {
        if (content.isEmpty()) {
            return emptyList()
        }
        val sections = mutableListOf<MessageSection>()
        var cursor = 0
        while (cursor < content.length) {
            val match = findNextStructuralTag(content, cursor) ?: break
            if (match.range.first > cursor) {
                appendText(sections, content.substring(cursor, match.range.first))
            }
            val tagName = match.groupValues[1]
            val blockEnd = findBlockEnd(content, match.range.first, tagName)
            if (blockEnd < 0) {
                val tail = content.substring(match.range.first)
                if (includeIncomplete) sections.add(parseBlock(tagName, tail, complete = false, forDisplay = true))
                else appendText(sections, tail)
                cursor = content.length
                break
            }
            val raw = content.substring(match.range.first, blockEnd)
            sections.add(parseBlock(tagName, raw, forDisplay = includeIncomplete))
            cursor = blockEnd
        }
        if (cursor < content.length) {
            appendText(sections, content.substring(cursor))
        }
        return mergeText(sections)
    }

    fun render(sections: List<MessageSection>): String {
        return sections.joinToString(separator = "") { section ->
            when (section) {
                is MessageSection.Text -> section.content
                is MessageSection.Thinking -> section.markup
                is MessageSection.ToolCall -> section.raw.ifEmpty { renderToolCall(section) }
                is MessageSection.ToolResult -> section.raw.ifEmpty { renderToolResult(section) }
                is MessageSection.Protocol -> section.raw.ifEmpty {
                    "<meta provider=\"${section.provider}\">${section.payload}</meta>"
                }
                is MessageSection.Search -> section.raw
                is MessageSection.Status -> section.raw
            }
        }
    }

    /** 搜索索引不是消息来源，不包含协议负载和 XML 外壳。 */
    fun searchText(sections: List<MessageSection>): String = sections.joinToString("") { section ->
        when (section) {
            is MessageSection.Text -> section.content
            is MessageSection.Thinking -> section.content
            is MessageSection.ToolCall -> section.name + " " + section.params.values.joinToString(" ")
            is MessageSection.ToolResult -> section.name + " " + section.content
            is MessageSection.Protocol -> ""
            is MessageSection.Search -> section.raw
            is MessageSection.Status -> section.raw
        }
    }

    private val textBoundary = Regex("(`+|(?m)^[ \\t]{0,3}~{3,})|<([A-Za-z][A-Za-z0-9_.:-]*)\\b")

    /** 逐段扫描正文，避免反复寻找同一个远处标签；工具和思考正文按外层边界读取。 */
    private fun findNextStructuralTag(content: String, start: Int): MatchResult? {
        var scan = start
        while (scan < content.length) {
            val boundary = textBoundary.find(content, scan) ?: return null
            val delimiter = boundary.groupValues[1].trimStart()
            if (delimiter.isNotEmpty()) {
                val markerStart = boundary.range.first
                var backslashStart = markerStart
                while (backslashStart > 0 && content[backslashStart - 1] == '\\') backslashStart--
                if ((markerStart - backslashStart) % 2 == 1) {
                    scan = boundary.range.last + 1
                    continue
                }
                val lineStart = content.lastIndexOf('\n', markerStart - 1) + 1
                val prefix = content.substring(lineStart, markerStart)
                val isFence = delimiter.length >= 3 && prefix.length <= 3 && prefix.all { it == ' ' }
                if (isFence) {
                    val bodyStart = content.indexOf('\n', boundary.range.last + 1)
                    if (bodyStart < 0) return null
                    val closingFence = Regex(
                        "^[ \\t]{0,3}" + Regex.escape(delimiter.first().toString()) +
                            "{" + delimiter.length + ",}[ \\t]*\\r?$",
                        RegexOption.MULTILINE,
                    ).find(content, bodyStart + 1)
                    scan = closingFence?.range?.last?.plus(1) ?: return null
                } else {
                    // 行内代码不跨行。跨全文寻找结束反引号会把后续工具和协议吞成普通正文。
                    val spanStart = boundary.range.last + 1
                    val lineEnd = content.indexOfAny(charArrayOf('\r', '\n'), spanStart)
                        .let { if (it < 0) content.length else it }
                    val end = Regex("(?<!`)" + Regex.escape(delimiter) + "(?!`)")
                        .toPattern().matcher(content).region(spanStart, lineEnd)
                    scan = if (end.find()) end.end() else spanStart
                }
                continue
            }
            val tagName = boundary.groupValues[2]
            val normalized = tagName.lowercase()
            if (ThinkingMarkup.isThinkingTag(normalized) || (normalized == "search" || normalized == "status" || normalized == "meta") ||
                ChatMarkupRegex.isToolTagName(normalized) || ChatMarkupRegex.isToolResultTagName(normalized)) {
                return structuralTag.find(content, boundary.range.first)
            }
            val end = findBlockEnd(content, boundary.range.first, tagName)
            scan = if (end >= 0) end else boundary.range.last + 1
        }
        return null
    }

    private fun parseBlock(tagName: String, raw: String, complete: Boolean = true, forDisplay: Boolean = false): MessageSection {
        val normalized = tagName.lowercase()
        return when {
            ThinkingMarkup.isThinkingTag(normalized) ->
                MessageSection.Thinking(ThinkingMarkup.body(raw)).also { it.markup = raw }
            normalized == "search" -> MessageSection.Search(raw)
            normalized == "status" -> MessageSection.Status(raw)
            normalized == "meta" -> parseProtocol(raw)
            ChatMarkupRegex.isToolResultTagName(normalized) -> parseToolResult(raw, complete, forDisplay)
            ChatMarkupRegex.isToolTagName(normalized) -> parseToolCall(raw, complete, forDisplay)
            else -> MessageSection.Text(raw)
        }
    }

    private fun parseToolCall(raw: String, complete: Boolean, forDisplay: Boolean): MessageSection {
        val name = ChatMarkupRegex.nameAttr.find(raw.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val body = if (complete) extractBody(raw) else raw.substringAfter('>', "")
        val params = linkedMapOf<String, String>()
        ChatMarkupRegex.toolParamPattern.findAll(body).forEach { match ->
            params[match.groupValues[1]] = unescapeXml(if (forDisplay) match.groupValues[2] else match.groupValues[2].trim())
        }
        if (!complete) {
            // 参数正文正在生成时也可展示；不补闭合标签，不将展示投影写回原文。
            val opening = Regex("""<param\s+name="([^"]+)">""").findAll(body).lastOrNull()
            if (opening != null) {
                val value = body.substring(opening.range.last + 1)
                if (!value.contains("</param>")) params[opening.groupValues[1]] = unescapeXml(value)
            }
        }
        return MessageSection.ToolCall(name = name, params = params, raw = raw)
    }

    private fun parseToolResult(raw: String, complete: Boolean, forDisplay: Boolean): MessageSection {
        val opening = raw.substringBefore('>')
        val name = ChatMarkupRegex.nameAttr.find(opening)?.groupValues?.getOrNull(1).orEmpty()
        val status = ChatMarkupRegex.statusAttr.find(opening)?.groupValues?.getOrNull(1).orEmpty()
        val body = if (complete) extractBody(raw) else raw.substringAfter('>', "")
        val content = if (complete) ToolResultMarkup.contentFromBody(body)
            else incompleteResultContent(raw, body)
        return MessageSection.ToolResult(
            name = name,
            status = status,
            content = if (forDisplay) content else content.trim(),
            raw = raw,
        )
    }

    /** 去掉尚在到达的外层封装，正文中的同名标签示例仍保留。 */
    private fun incompleteResultContent(raw: String, body: String): String {
        if ("<content>".startsWith(body)) return ""
        if (!body.startsWith("<content>")) return body
        val content = body.removePrefix("<content>")
        val end = content.lastIndexOf("</content>")
        if (end < 0) return content
        val tail = content.substring(end + "</content>".length)
        val tagName = raw.substringAfter('<').substringBefore(' ').substringBefore('>')
        return if ("</$tagName>".startsWith(tail)) content.substring(0, end) else content
    }

    private fun parseProtocol(raw: String): MessageSection {
        val provider =
            Regex("""\bprovider\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(raw.substringBefore('>'))
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        return MessageSection.Protocol(
            provider = provider,
            payload = extractBody(raw).trim(),
            raw = raw,
        )
    }

    private fun renderToolCall(section: MessageSection.ToolCall): String {
        val params = section.params.entries.joinToString(separator = "") { (name, value) ->
            "<param name=\"$name\">${escapeXml(value)}</param>"
        }
        return "<tool name=\"${section.name}\">$params</tool>"
    }

    private fun renderToolResult(section: MessageSection.ToolResult): String {
        val status = if (section.status.isEmpty()) "" else " status=\"${section.status}\""
        return "<tool_result name=\"${section.name}\"$status><content>${section.content}</content></tool_result>"
    }

    private fun findBlockEnd(
        content: String,
        start: Int,
        tagName: String,
    ): Int {
        val openEnd = content.indexOf('>', start)
        if (openEnd < 0) {
            return -1
        }
        if (content.startsWith("/>", openEnd - 1)) {
            return openEnd + 1
        }
        val normalized = tagName.lowercase()
        if (ThinkingMarkup.isThinkingTag(normalized)) {
            return ThinkingMarkup.blockAt(content, start)?.end ?: -1
        }
        val close = "</$tagName>"
        val closeAt = content.indexOf(close, openEnd + 1, ignoreCase = true)
        return if (closeAt < 0) -1 else closeAt + close.length
    }

    private fun extractBody(raw: String): String {
        val openEnd = raw.indexOf('>')
        val closeStart = raw.lastIndexOf("</")
        if (openEnd < 0 || closeStart <= openEnd) {
            return ""
        }
        return raw.substring(openEnd + 1, closeStart)
    }

    private fun appendText(sections: MutableList<MessageSection>, value: String) {
        if (value.isNotEmpty()) {
            sections.add(MessageSection.Text(value))
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
    }

    private fun mergeText(sections: List<MessageSection>): List<MessageSection> {
        val merged = mutableListOf<MessageSection>()
        sections.forEach { section ->
            val previous = merged.lastOrNull()
            if (section is MessageSection.Text && previous is MessageSection.Text) {
                merged[merged.lastIndex] = MessageSection.Text(previous.content + section.content)
            } else {
                merged.add(section)
            }
        }
        return merged
    }
}
