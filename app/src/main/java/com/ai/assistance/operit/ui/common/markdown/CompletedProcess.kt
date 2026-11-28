package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

/** Keep the entire answer after the last activity block, including headings and paragraphs. */
internal fun completedProcessEnd(
    nodes: List<MarkdownNodeStable>,
    collapseEnabled: Boolean = true,
    streaming: Boolean = false,
): Int {
    if (!collapseEnabled || streaming) return -1
    val end = nodes.indexOfLast { node ->
        if (node.type != MarkdownProcessorType.XML_BLOCK) false
        else {
            val tag = ChatMarkupRegex.extractOpeningTagName(node.content)?.lowercase()
            tag == "think" || tag == "thinking" || tag == "search" ||
                ChatMarkupRegex.isToolTagName(tag) || ChatMarkupRegex.isToolResultTagName(tag)
        }
    }
    if (end < 0) return -1
    // A tool-only or interrupted response must stay visible rather than become an empty answer.
    val answerStart = (end + 1 until nodes.size).firstOrNull { index ->
        val node = nodes[index]
        val tag = if (node.type == MarkdownProcessorType.XML_BLOCK) {
            ChatMarkupRegex.extractOpeningTagName(node.content)?.lowercase()
        } else null
        tag != "meta" && tag != "status" &&
            (node.content.isNotBlank() || node.children.any { child -> child.content.isNotBlank() })
    } ?: return -1
    return answerStart - 1
}

/** Preserve an entire item if it also contains any part of the final answer. */
internal fun responseProcessItemVisible(itemEnd: Int, processEnd: Int, expanded: Boolean): Boolean =
    processEnd < 0 || expanded || itemEnd > processEnd

/** 总用时只接管完整覆盖的组，原索引继续用于卡片标识和 XML 子流。 */
internal fun flattenGroupsCoveredByResponseProcess(
    groups: List<MarkdownGroupedItem>,
    nodes: List<MarkdownNodeStable>,
    processEnd: Int,
): List<MarkdownGroupedItem> {
    if (processEnd < 0) return groups
    return buildList {
        for (item in groups) {
            if (item is MarkdownGroupedItem.Group && item.endIndexInclusive <= processEnd) {
                for (index in item.startIndex.coerceAtLeast(0)..item.endIndexInclusive.coerceAtMost(nodes.lastIndex)) {
                    // 原分组只渲染 XML 成员，跳过吸收进组范围的空白。
                    if (nodes[index].type == MarkdownProcessorType.XML_BLOCK) {
                        add(MarkdownGroupedItem.Single(index))
                    }
                }
            } else {
                add(item)
            }
        }
    }
}

internal fun responseProcessKey(timestamp: Long, variant: Int): String =
    "single-process-$timestamp/$variant"
