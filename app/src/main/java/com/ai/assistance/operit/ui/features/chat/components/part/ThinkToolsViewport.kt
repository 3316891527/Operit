package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.ui.common.markdown.MarkdownRenderMode
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

internal val LocalThinkToolsViewportHeight = compositionLocalOf { 0.dp }

internal data class ThinkToolsViewportGroup(
    val group: MarkdownGroupedItem.Group,
    val toolCount: Int,
    val searchCount: Int,
    val autoExpand: Boolean,
)

internal sealed interface ThinkToolsViewportRow {
    val key: String

    data class Header(val info: ThinkToolsViewportGroup, val expanded: Boolean) : ThinkToolsViewportRow {
        override val key: String get() = "header-${info.group.stableKey}"
    }

    data class Content(val index: Int, val inGroup: Boolean) : ThinkToolsViewportRow {
        override val key: String get() = "node-$index"
    }
}

/** 组标题和组内节点属于同一滚动区域，单个大组也不会退化为一个超高条目。 */
internal fun buildThinkToolsViewportRows(
    groups: List<MarkdownGroupedItem>,
    nodes: List<MarkdownNodeStable>,
    overrides: Map<String, Boolean>,
    describeGroup: (MarkdownGroupedItem.Group) -> ThinkToolsViewportGroup,
): List<ThinkToolsViewportRow> = buildList {
    for (item in groups) {
        when (item) {
            is MarkdownGroupedItem.Single -> {
                if (item.index in nodes.indices) add(ThinkToolsViewportRow.Content(item.index, false))
            }
            is MarkdownGroupedItem.Group -> {
                val info = describeGroup(item)
                val expanded = overrides[item.stableKey] ?: info.autoExpand
                add(ThinkToolsViewportRow.Header(info, expanded))
                if (expanded) {
                    for (index in item.startIndex..item.endIndexInclusive.coerceAtMost(nodes.lastIndex)) {
                        if (index in nodes.indices && nodes[index].type == MarkdownProcessorType.XML_BLOCK) {
                            add(ThinkToolsViewportRow.Content(index, true))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThinkToolsViewport(
    groups: List<MarkdownGroupedItem>,
    nodes: List<MarkdownNodeStable>,
    modifier: Modifier,
    textColor: Color,
    expansionOverrides: SnapshotStateMap<String, Boolean>,
    describeGroup: (MarkdownGroupedItem.Group) -> ThinkToolsViewportGroup,
    renderNode: @Composable (Int) -> Unit,
) {
    val rows = buildThinkToolsViewportRows(groups, nodes, expansionOverrides, describeGroup)
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var followTail by remember { mutableStateOf(true) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val streaming = LocalMarkdownRenderMode.current == MarkdownRenderMode.STREAMING
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp.coerceIn(240.dp, 800.dp)
    val lastKey = rows.lastOrNull()?.key
    val tailLength = nodes.lastOrNull()?.content?.length ?: 0

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (!programmaticScroll && scrolling) followTail = !canScrollForward
            }
    }
    // 跟随新增内容；用户向上阅读后暂停，回到底部才恢复。
    LaunchedEffect(streaming, rows.size, lastKey, tailLength, followTail, dragged) {
        if (streaming && followTail && !dragged && rows.isNotEmpty()) {
            programmaticScroll = true
            try {
                listState.scrollToItem(rows.lastIndex)
            } finally {
                programmaticScroll = false
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalThinkToolsViewportHeight provides maxHeight) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(end = 6.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is ThinkToolsViewportRow.Header -> {
                            val rotation by animateFloatAsState(
                                targetValue = if (row.expanded) 90f else 0f,
                                animationSpec = tween(300),
                                label = "viewport-group-arrow",
                            )
                            CanvasExpandableHeaderRow(
                                title = thinkToolsViewportTitle(row.info),
                                semanticDescription = stringResource(
                                    if (row.expanded) R.string.common_collapse else R.string.common_expand
                                ),
                                expanded = row.expanded,
                                titleColor = textColor.copy(alpha = 0.7f),
                                rotationDegrees = rotation,
                                modifier = Modifier.padding(bottom = 4.dp),
                                onClick = {
                                    expansionOverrides[row.info.group.stableKey] = !row.expanded
                                },
                            )
                        }
                        is ThinkToolsViewportRow.Content -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(
                                    start = if (row.inGroup) 24.dp else 0.dp,
                                    bottom = if (row.inGroup) 2.dp else 0.dp,
                                )
                            ) {
                                renderNode(row.index)
                            }
                        }
                    }
                }
            }
        }
        // 仅作位置提示，触摸仍交给回复内容，避免覆盖结果行的点击区域。
        Canvas(modifier = Modifier.matchParentSize()) {
            val layout = listState.layoutInfo
            if ((listState.canScrollForward || listState.canScrollBackward) && layout.totalItemsCount > 0) {
                val visible = layout.visibleItemsInfo
                val first = visible.firstOrNull()
                if (first != null) {
                    val fraction = visible.size.toFloat() / layout.totalItemsCount
                    val thumbHeight = (size.height * fraction).coerceIn(20.dp.toPx().coerceAtMost(size.height), size.height)
                    val position = first.index + (-first.offset.toFloat() / first.size.coerceAtLeast(1))
                    val progress = (position / (layout.totalItemsCount - visible.size).coerceAtLeast(1)).coerceIn(0f, 1f)
                    drawRoundRect(
                        color = textColor.copy(alpha = 0.25f),
                        topLeft = Offset(size.width - 3.dp.toPx(), (size.height - thumbHeight) * progress),
                        size = Size(2.dp.toPx(), thumbHeight),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
internal fun thinkToolsViewportTitle(info: ThinkToolsViewportGroup): String = when {
    info.group.stableKey.startsWith("tools-only-") -> stringResource(R.string.tools_group_title_with_count, info.toolCount)
    info.group.stableKey.startsWith("search-only-") -> stringResource(R.string.search_group_title)
    info.searchCount > 0 && info.toolCount > 0 -> stringResource(R.string.thinking_search_tools_group_title_with_count, info.toolCount)
    info.searchCount > 0 -> stringResource(R.string.thinking_search_group_title)
    else -> stringResource(R.string.thinking_tools_group_title_with_count, info.toolCount)
}
