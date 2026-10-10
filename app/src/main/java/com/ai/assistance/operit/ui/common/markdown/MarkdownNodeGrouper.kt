package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.stream.Stream

sealed class MarkdownGroupedItem {
    data class Single(val index: Int) : MarkdownGroupedItem()

    data class Group(
        val startIndex: Int,
        val endIndexInclusive: Int,
        val stableKey: String
    ) : MarkdownGroupedItem()
}

interface MarkdownNodeGrouper {
    fun group(nodes: List<MarkdownNodeStable>, rendererId: String): List<MarkdownGroupedItem>

    @Composable
    fun RenderGroup(
        group: MarkdownGroupedItem.Group,
        nodes: List<MarkdownNodeStable>,
        rendererId: String,
        isVisible: Boolean,
        isLastNode: Boolean,
        modifier: Modifier,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        xmlStreamResolver: (Int) -> Stream<String>?,
        fillMaxWidth: Boolean,
        fontSize: TextUnit
    )
}

/** 允许长消息分组提供有边界的按需渲染，避免整个回复形成一个超高布局。 */
interface ViewportMarkdownNodeGrouper : MarkdownNodeGrouper {
    fun shouldUseViewport(nodes: List<MarkdownNodeStable>): Boolean

    @Composable
    fun RenderViewport(
        groups: List<MarkdownGroupedItem>,
        nodes: List<MarkdownNodeStable>,
        modifier: Modifier,
        textColor: Color,
        xmlStreamResolver: (Int) -> Stream<String>?,
        renderNode: @Composable (Int) -> Unit,
    )
}

object NoopMarkdownNodeGrouper : MarkdownNodeGrouper {
    override fun group(nodes: List<MarkdownNodeStable>, rendererId: String): List<MarkdownGroupedItem> {
        return nodes.indices.map { MarkdownGroupedItem.Single(it) }
    }

    @Composable
    override fun RenderGroup(
        group: MarkdownGroupedItem.Group,
        nodes: List<MarkdownNodeStable>,
        rendererId: String,
        isVisible: Boolean,
        isLastNode: Boolean,
        modifier: Modifier,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        xmlStreamResolver: (Int) -> Stream<String>?,
        fillMaxWidth: Boolean,
        fontSize: TextUnit
    ) {
    }
}
