package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin

/** A preempted draft must never repair or execute unfinished tool arguments. */
internal suspend fun retainBeforeUnexecutedTool(content: String): String {
    val toolOpening = Regex(
        "<(?:${ChatMarkupRegex.TOOL_TAG_NAME_REGEX_SOURCE})(?=\\s|/?>|$)",
        RegexOption.IGNORE_CASE,
    )
    // Mask code samples before XML matching so examples never become execution boundaries.
    // Every UTF-16 offset stays unchanged and still addresses the original response text.
    val searchable = maskSamplingCodeSamples(content)
    var offset = 0
    var cut: Int? = null
    searchable.stream().splitBy(listOf(StreamXmlPlugin(includeTagsInOutput = true))).collect { group ->
        val text = buildString { group.stream.collect { append(it) } }
        if (cut == null) {
            val localCut = when (group.tag) {
                is StreamXmlPlugin ->
                    if (ChatMarkupRegex.isToolTagName(ChatMarkupRegex.extractOpeningTagName(text)))
                        text.indexOf('<') else null
                null -> toolOpening.find(text)?.range?.first
                else -> null
            }
            if (localCut != null) cut = offset + localCut
        }
        // Drain every group: abandoning a nested stream leaves its producer suspended.
        offset += text.length
    }
    return cut?.let(content::take) ?: content
}

private fun maskSamplingCodeSamples(content: String): String {
    val opening = Regex("(?m)^ {0,3}(`{3,}|~{3,})[^\\r\\n]*(?:\\r?\\n|$)|`+")
    val masked = StringBuilder(content)
    var cursor = 0
    while (cursor < content.length) {
        val marker = opening.find(content, cursor) ?: break
        val fence = marker.groups[1]?.value
        val end = if (fence != null) {
            val closing = Regex("(?m)^ {0,3}${Regex.escape(fence.first().toString())}{${fence.length},}[ \\t]*(?:\\r?\\n|$)")
            closing.find(content, marker.range.last + 1)?.range?.last?.plus(1) ?: content.length
        } else {
            val ticks = marker.value
            val closing = Regex("(?<!`)${Regex.escape(ticks)}(?!`)")
            closing.find(content, marker.range.last + 1)?.range?.last?.plus(1) ?: content.length
        }
        for (index in marker.range.first until end) {
            if (masked[index] != '\n' && masked[index] != '\r') masked.setCharAt(index, ' ')
        }
        cursor = end
    }
    return masked.toString()
}
