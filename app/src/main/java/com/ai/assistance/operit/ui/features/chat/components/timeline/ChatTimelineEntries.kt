package com.ai.assistance.operit.ui.features.chat.components.timeline

import com.ai.assistance.operit.ui.common.markdown.lazy.LocalMarkdownCardStateStore
import com.ai.assistance.operit.ui.common.markdown.lazy.MarkdownCardStateStore
import com.ai.assistance.operit.ui.common.markdown.lazy.markdownNodeIdentityKeys
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.common.markdown.AnimatedNode
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownNodeGrouper
import com.ai.assistance.operit.ui.common.markdown.MarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRendererState
import com.ai.assistance.operit.ui.common.markdown.UnifiedMarkdownCanvas
import com.ai.assistance.operit.ui.common.markdown.XmlContentRenderer
import com.ai.assistance.operit.ui.common.markdown.lazy.MarkdownRenderFragment
import com.ai.assistance.operit.ui.features.chat.components.part.CanvasExpandableHeaderRow
import com.ai.assistance.operit.ui.features.chat.components.part.ThinkToolsRow
import com.ai.assistance.operit.ui.features.chat.components.part.ThinkToolsXmlNodeGrouper
import com.ai.assistance.operit.ui.features.chat.components.part.buildThinkToolsRows
import com.ai.assistance.operit.ui.features.chat.components.part.thinkToolsGroupTitle
import com.ai.assistance.operit.ui.features.chat.components.rememberRevisableTextStream
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.toCharStream
import kotlinx.coroutines.flow.Flow

internal data class ChatTimelineEntry(
    val key: String,
    val messageIndex: Int,
    val slice: ChatMessageSlice? = null,
)

internal class ChatMessageAppearance {
    // 同一消息的各块共享头像查询，滚动时不重复访问角色数据库。
    val avatarFlows = mutableMapOf<Pair<String, String?>, Flow<String?>>()
}

internal data class ChatMessageSlice(
    val first: Boolean,
    val last: Boolean,
    val split: Boolean,
    val fragment: MarkdownRenderFragment,
    val state: StreamMarkdownRendererState,
    val grouper: ThinkToolsXmlNodeGrouper,
    val appearance: ChatMessageAppearance,
    val sections: List<MessageSection>,
)

internal val LocalChatMessageSlice = staticCompositionLocalOf<ChatMessageSlice?> { null }

private val ExpansionSaver = mapSaver<SnapshotStateMap<String, Boolean>>(
    save = { it.toMap() },
    restore = { saved ->
        mutableStateMapOf<String, Boolean>().apply {
            saved.forEach { (key, value) -> put(key, value as Boolean) }
        }
    },
)

private val CardValueSaver = mapSaver<SnapshotStateMap<String, Any?>>(
    save = { it.toMap() },
    restore = { saved -> mutableStateMapOf<String, Any?>().apply { putAll(saved) } },
)

/** 内容准备不随条目离屏而取消，只有可见块进入布局和绘制。 */
@Composable
internal fun rememberChatTimelineEntries(
    messages: List<ChatMessage>,
    chatId: String,
    showThinkingProcess: Boolean,
    toolCollapseMode: ToolCollapseMode,
): List<ChatTimelineEntry> {
    val occurrences = mutableMapOf<Long, Int>()
    return buildList {
        messages.forEachIndexed { index, message ->
            val occurrence = occurrences[message.timestamp] ?: 0
            occurrences[message.timestamp] = occurrence + 1
            val messageKey = "$chatId/${message.timestamp}/$occurrence/${message.selectedVariantIndex}"
            key(messageKey) {
                if (message.sender != "ai") {
                    add(ChatTimelineEntry(messageKey, index))
                } else {
                    val state = remember { StreamMarkdownRendererState() }
                    val appearance = remember { ChatMessageAppearance() }
                    val cardValues = rememberSaveable(saver = CardValueSaver) { mutableStateMapOf<String, Any?>() }
                    val overrides = rememberSaveable(saver = ExpansionSaver) { mutableStateMapOf<String, Boolean>() }
                    val grouper = remember(showThinkingProcess, toolCollapseMode) {
                        ThinkToolsXmlNodeGrouper(showThinkingProcess, toolCollapseMode = toolCollapseMode).also { it.expansionOverrides = overrides }
                    }
                    val stream = rememberRevisableTextStream(message.contentStream)
                    // 静态片段由消息统一解析，流式块继续复用正在收集的节点和 XML 子流。
                    val sections = if (stream == null) {
                        remember(message.content, message.sections) { message.displaySections() }
                    } else {
                        emptyList()
                    }
                    if (stream != null) {
                        val charStream = remember(stream) { stream.toCharStream() }
                        StreamMarkdownRenderer(markdownStream = charStream, state = state, renderContent = false)
                    } else {
                        StreamMarkdownRenderer(sections = sections, state = state, renderContent = false)
                    }
                    val nodes = state.renderNodes.toList()
                    val cardStore = MarkdownCardStateStore(markdownNodeIdentityKeys(nodes), cardValues)
                    var wasSplit by remember { mutableStateOf(false) }
                    val split = wasSplit || shouldSplitChatMessage(nodes)
                    SideEffect { wasSplit = split }
                    val mode = if (stream == null) MarkdownRenderMode.STATIC else MarkdownRenderMode.STREAMING
                    val groups = remember(nodes, grouper) { grouper.group(nodes, messageKey) }
                    val rows = if (split && nodes.isNotEmpty()) {
                        buildThinkToolsRows(groups, nodes, overrides) { group ->
                            grouper.describeGroup(group, nodes) { state.xmlNodeStreams[it] }
                        }
                    } else {
                        emptyList()
                    }
                    val fragments = if (rows.isEmpty()) {
                        listOf(ChatMarkdownFragment(state, nodes, grouper, mode, null, cardStore))
                    } else {
                        rows.map { row -> ChatMarkdownFragment(state, nodes, grouper, mode, row, cardStore) }
                    }
                    fragments.forEachIndexed { blockIndex, fragment ->
                        val blockKey = if (blockIndex == 0) "first" else fragment.row!!.key
                        add(
                            ChatTimelineEntry(
                                key = "$messageKey/$blockKey",
                                messageIndex = index,
                                slice = ChatMessageSlice(
                                    first = blockIndex == 0,
                                    last = blockIndex == fragments.lastIndex,
                                    split = split,
                                    fragment = fragment,
                                    state = state,
                                    grouper = grouper,
                                    appearance = appearance,
                                    sections = sections,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 阈值只决定内容块拆分，所有分组仍允许同时展开。 */
internal fun shouldSplitChatMessage(nodes: List<MarkdownNodeStable>): Boolean =
    nodes.size >= 24 || nodes.sumOf { it.content.length.toLong() } >= 32_000L

private class ChatMarkdownFragment(
    val state: StreamMarkdownRendererState,
    val nodes: List<MarkdownNodeStable>,
    val grouper: ThinkToolsXmlNodeGrouper,
    val mode: MarkdownRenderMode,
    val row: ThinkToolsRow?,
    val cardStore: MarkdownCardStateStore,
) : MarkdownRenderFragment {
    @Composable
    override fun Render(
        modifier: Modifier,
        textColor: Color,
        fontSize: TextUnit,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        nodeGrouper: MarkdownNodeGrouper,
        enableDialogs: Boolean,
        fillMaxWidth: Boolean,
    ) {
        CompositionLocalProvider(LocalMarkdownRenderMode provides mode, LocalMarkdownCardStateStore provides cardStore) {
            Box(modifier = modifier) {
                when (val item = row) {
                    null -> UnifiedMarkdownCanvas(
                        nodes = nodes,
                        rendererId = state.rendererId,
                        nodeAnimationStates = state.nodeAnimationStates,
                        textColor = textColor,
                        fontSize = fontSize,
                        onLinkClick = onLinkClick,
                        xmlRenderer = xmlRenderer,
                        xmlStreamsByIndex = state.xmlNodeStreams,
                        nodeGrouper = grouper,
                        enableDialogs = enableDialogs,
                        fillMaxWidth = fillMaxWidth,
                    )
                    is ThinkToolsRow.Header -> {
                        val rotation by animateFloatAsState(
                            targetValue = if (item.expanded) 90f else 0f,
                            animationSpec = tween(300),
                            label = "chat-group-arrow",
                        )
                        CanvasExpandableHeaderRow(
                            title = thinkToolsGroupTitle(item.info),
                            semanticDescription = stringResource(if (item.expanded) R.string.common_collapse else R.string.common_expand),
                            expanded = item.expanded,
                            titleColor = textColor.copy(alpha = 0.7f),
                            rotationDegrees = rotation,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onClick = { grouper.expansionOverrides[item.info.group.stableKey] = !item.expanded },
                        )
                    }
                    is ThinkToolsRow.Content -> {
                        Box(Modifier.fillMaxWidth().padding(start = if (item.inGroup) 24.dp else 0.dp)) {
                            // 已出现的块离屏后重新进入时不重播淡入，避免快速滑动时闪烁。
                            AnimatedNode(
                                nodeKey = item.key,
                                node = item.node ?: nodes[item.index],
                                index = item.index,
                                isVisible = true,
                                textColor = textColor,
                                fontSize = fontSize,
                                onLinkClick = onLinkClick,
                                xmlRenderer = xmlRenderer,
                                xmlStream = state.xmlNodeStreams[item.index],
                                enableDialogs = enableDialogs,
                                fillMaxWidth = true,
                                isLastNode = item.index == nodes.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}
