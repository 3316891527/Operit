package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.util.ChatMarkupRegex

/**
 * 旧标记字符串和 sections 的双向转换。
 * 数据库仍保存标记字符串，读取时转成 sections。
 */
object MessageSectionCodec {
    private val structuralTag = Regex(
        """<(operit_thinking|think(?:ing)?|search|status|meta|""" +
            ChatMarkupRegex.TOOL_RESULT_TAG_NAME_REGEX_SOURCE + "|" +
            ChatMarkupRegex.TOOL_TAG_NAME_REGEX_SOURCE +
            """)\\b""",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun parse(content: String): List<MessageSection> {
        if (content.isEmpty()) {
            return emptyList()
        }
        val sections = mutableListOf<MessageSection>()
        var cursor = 0
        while (cursor < content.length) {
            val match = structuralTag.find(content, cursor) ?: break
            if (match.range.first > cursor) {
                appendText(sections, content.substring(cursor, match.range.first))
            }
            val tagName = match.groupValues[1]
            val blockEnd = findBlockEnd(content, match.range.first, tagName)
            if (blockEnd < 0) {
                appendText(sections, content.substring(match.range.first))
                cursor = content.length
                break
            }
            val raw = content.substring(match.range.first, blockEnd)
            sections.add(parseBlock(tagName, raw))
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
                is MessageSection.Thinking -> "<operit_thinking>${section.content}</operit_thinking>"
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

    private fun parseBlock(tagName: String, raw: String): MessageSection {
        val normalized = tagName.lowercase()
        return when {
            normalized == "think" || normalized == "thinking" || normalized == "operit_thinking" ->
                MessageSection.Thinking(extractBody(raw))
            normalized == "search" -> MessageSection.Search(raw)
            normalized == "status" -> MessageSection.Status(raw)
            normalized == "meta" -> parseProtocol(raw)
            ChatMarkupRegex.isToolResultTagName(normalized) -> parseToolResult(raw)
            ChatMarkupRegex.isToolTagName(normalized) -> parseToolCall(raw)
            else -> MessageSection.Text(raw)
        }
    }

    private fun parseToolCall(raw: String): MessageSection {
        val name = ChatMarkupRegex.nameAttr.find(raw.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val body = extractBody(raw)
        val params = linkedMapOf<String, String>()
        ChatMarkupRegex.toolParamPattern.findAll(body).forEach { match ->
            params[match.groupValues[1]] = unescapeXml(match.groupValues[2].trim())
        }
        return MessageSection.ToolCall(name = name, params = params, raw = raw)
    }

    private fun parseToolResult(raw: String): MessageSection {
        val opening = raw.substringBefore('>')
        val name = ChatMarkupRegex.nameAttr.find(opening)?.groupValues?.getOrNull(1).orEmpty()
        val status = ChatMarkupRegex.statusAttr.find(opening)?.groupValues?.getOrNull(1).orEmpty()
        val body = extractBody(raw)
        val content = ChatMarkupRegex.contentTag.find(body)?.groupValues?.getOrNull(1) ?: body
        return MessageSection.ToolResult(
            name = name,
            status = status,
            content = content.trim(),
            raw = raw,
        )
    }

    private fun parseProtocol(raw: String): MessageSection {
        val provider = ChatMarkupRegex.extractOpeningTagName(raw).let {
            Regex("""\\bprovider\\s*=\\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(raw.substringBefore('>'))
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }
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

    private fun findBlockEnd(content: String, start: Int, tagName: String): Int {
        val openEnd = content.indexOf('>', start)
        if (openEnd < 0) {
            return -1
        }
        if (content.startsWith("/>", openEnd - 1)) {
            return openEnd + 1
        }
        val normalized = tagName.lowercase()
        if (normalized == "think" || normalized == "thinking" || normalized == "operit_thinking") {
            return findThinkingBlockEnd(content, openEnd, normalized)
        }
        val close = "</$tagName>"
        val closeAt = content.indexOf(close, openEnd + 1, ignoreCase = true)
        return if (closeAt < 0) -1 else closeAt + close.length
    }

    private fun findThinkingBlockEnd(content: String, openEnd: Int, tagName: String): Int {
        val close = "</$tagName>"
        var searchFrom = openEnd + 1
        var chosen = -1
        while (searchFrom < content.length) {
            val closeAt = content.indexOf(close, searchFrom, ignoreCase = true)
            if (closeAt < 0) {
                break
            }
            val between = content.substring(openEnd + 1, closeAt)
            val nextStructural = structuralTag.find(between)
            val nextName = nextStructural?.groupValues?.getOrNull(1)?.lowercase()
            if (
                nextStructural != null &&
                nextName != "think" &&
                nextName != "thinking" &&
                nextName != "operit_thinking"
            ) {
                break
            }
            chosen = closeAt
            searchFrom = closeAt + close.length
        }
        return if (chosen < 0) -1 else chosen + close.length
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
