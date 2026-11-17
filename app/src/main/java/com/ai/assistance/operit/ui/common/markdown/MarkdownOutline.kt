package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

internal data class MarkdownHeading(val nodeIndex: Int, val level: Int, val title: String, val parentIndex: Int?)

/** 目录与正文共用已经解析的节点，不重复扫描源码，也不收录代码块里的井号。 */
internal fun markdownHeadings(nodes: List<MarkdownNodeStable>): List<MarkdownHeading> {
    val ancestors = mutableListOf<MarkdownHeading>()
    return nodes.mapIndexedNotNull { index, node ->
        if (node.type != MarkdownProcessorType.HEADER) return@mapIndexedNotNull null
        val level = node.content.trimStart().takeWhile { it == '#' }.length.coerceIn(1, 6)
        fun plainText(inline: MarkdownNodeStable): String {
            if (inline.type == MarkdownProcessorType.INLINE_CODE) return inline.content.removeSurrounding("`")
            val children = resolveNestedInlineChildren(inline)
            return if (children.isEmpty()) resolveNestedInlineText(inline) else children.joinToString("") { plainText(it) }
        }
        val title = (if (node.children.isEmpty()) node.content else node.children.joinToString("") { plainText(it) })
            .trimStart('#', ' ', '\t').trim().replace(Regex("\\s+"), " ")
        while (ancestors.isNotEmpty() && ancestors.last().level >= level) ancestors.removeAt(ancestors.lastIndex)
        MarkdownHeading(index, level, title, ancestors.lastOrNull()?.nodeIndex).also { ancestors += it }
    }
}

internal fun visibleMarkdownHeadings(headings: List<MarkdownHeading>, collapsed: Set<Int>): List<MarkdownHeading> {
    val hidden = mutableSetOf<Int>()
    return headings.filter { heading ->
        val visible = heading.parentIndex !in collapsed && heading.parentIndex !in hidden
        if (!visible) hidden += heading.nodeIndex
        visible
    }
}

@Stable
internal class MarkdownOutlineState {
    var headings by mutableStateOf<List<MarkdownHeading>>(emptyList())
        private set
    private val offsets = mutableStateMapOf<Int, Int>()

    fun updateHeadings(value: List<MarkdownHeading>) {
        if (headings == value) return
        headings = value
        offsets.keys.retainAll(value.map { it.nodeIndex }.toSet())
    }

    fun updateOffset(index: Int, top: Int) { offsets[index] = top }
    fun offset(index: Int): Int? = offsets[index]
    fun activeAt(scroll: Int): Int? = headings.lastOrNull { heading -> offsets[heading.nodeIndex]?.let { it <= scroll } == true }?.nodeIndex
}

internal val LocalMarkdownOutline = compositionLocalOf<MarkdownOutlineState?> { null }
