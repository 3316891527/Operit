package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline

import com.ai.assistance.operit.ui.common.markdown.ResponseProcessExpansionState
import com.ai.assistance.operit.ui.common.markdown.ResponseActivityHeader
import com.ai.assistance.operit.ui.common.markdown.responseProcessKey
import com.ai.assistance.operit.ui.common.markdown.responseProcessItemVisible
import com.ai.assistance.operit.ui.common.markdown.completedProcessEnd
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPluginRegistry
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.isWaifuReplyNode
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.isWaifuStandaloneNode
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuMessageProcessor
import androidx.compose.runtime.collectAsState
import com.ai.assistance.operit.ui.common.markdown.lazy.LocalMarkdownCardStateStore
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.rememberMessageToolExecutions
import com.ai.assistance.operit.ui.common.markdown.messageSectionsForRendering
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.LocalInsideToolGroup
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
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.CanvasExpandableHeaderRow
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.ThinkToolsRow
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.ThinkToolsXmlNodeGrouper
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.buildThinkToolsRows
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.buildWaifuProcessRows
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuProcessHeader
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuProcessToolCard
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.waifuProcessRail
import com.ai.assistance.operit.ui.common.markdown.lazy.LocalMarkdownCardNodeIndex
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.LocalToolTimingIndex
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.isVisibleChatMarkdownNode
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.thinkToolsGroupTitle
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.stream.rememberRevisableTextStream
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.toCharStream
import kotlinx.coroutines.flow.Flow

internal data class ChatTimelineEntry(
    val key: String,
    val messageIndex: Int,
    val slice: ChatMessageSlice? = null,
    val waifuProcess: Boolean = false,
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
    val standaloneContent: Boolean = false,
    val shellFirst: Boolean = first,
    val shellLast: Boolean = last,
    val waifuReplyStart: Boolean = first,
    val waifuAvatarAfterStandalone: Boolean = false,
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
    showStatusTags: Boolean,
    toolCollapseMode: ToolCollapseMode,
    waifuEnabled: Boolean = false,
): List<ChatTimelineEntry> {
    val context = LocalContext.current
    val preferences = remember(context) { DisplayPreferencesManager.getInstance(context) }
    val collapseCompletedProcess by preferences.collapseCompletedProcess.collectAsState(initial = true)
    val xmlRegistryVersion by XmlRenderPluginRegistry.changeVersion.collectAsState()
    val occurrences = mutableMapOf<Long, Int>()
    val entriesByMessage = buildList<List<ChatTimelineEntry>> {
        messages.forEachIndexed { index, message ->
            val occurrence = occurrences[message.timestamp] ?: 0
            occurrences[message.timestamp] = occurrence + 1
            val messageKey = "$chatId/${message.timestamp}/$occurrence/${message.selectedVariantIndex}"
            key(messageKey) {
                if (message.sender != "ai") {
                    add(remember(messageKey, index) { listOf(ChatTimelineEntry(messageKey, index)) })
                } else {
                    val isWaifuProcess = waifuEnabled && remember(message.content) {
                        WaifuMessageProcessor.isProcessOnlyMessage(message.content)
                    }
                    val state = remember { StreamMarkdownRendererState() }
                    val appearance = remember { ChatMessageAppearance() }
                    val cardValues = rememberSaveable(saver = CardValueSaver) { mutableStateMapOf<String, Any?>() }
                    val overrides = rememberSaveable(saver = ExpansionSaver) { mutableStateMapOf<String, Boolean>() }
                    val grouper = remember(showThinkingProcess, toolCollapseMode, isWaifuProcess) {
                        ThinkToolsXmlNodeGrouper(
                            showThinkingProcess,
                            toolCollapseMode = toolCollapseMode,
                            groupProcessNodes = !isWaifuProcess,
                        ).also { it.expansionOverrides = overrides }
                    }
                    val timingSnapshot = rememberMessageToolExecutions(message)
                    grouper.executionTimings = timingSnapshot
                    val stream = rememberRevisableTextStream(message.contentStream)
                    if (stream != null) {
                        val charStream = remember(stream) { stream.toCharStream() }
                        StreamMarkdownRenderer(markdownStream = charStream, state = state, renderContent = false)
                    } else {
                        // 已恢复的片段直接生成节点，工具结果不再进入全文 Markdown 扫描。
                        val sections = remember(message.content, message.sections) { messageSectionsForRendering(message) }
                        StreamMarkdownRenderer(sections = sections, state = state, renderContent = false)
                    }
                    val nodes = state.renderNodes.toList()
                    // Waifu 使用专用过程入口，不叠加普通消息的整段回复折叠标题。
                    val boundary = completedProcessEnd(nodes, collapseCompletedProcess && !isWaifuProcess, stream != null)
                    val processEnd = if (boundary >= 0 && nodes.take(boundary + 1).any {
                        isVisibleChatMarkdownNode(it, showThinkingProcess, showStatusTags)
                    }) boundary else -1
                    val processId = responseProcessKey(message.timestamp, message.selectedVariantIndex)
                    val processExpanded = ResponseProcessExpansionState.isExpanded(chatId, processId)
                    // 隐藏协议不触发拆块；原始节点仍用于分组键和 XML 子流索引。
                    val visibleNodes = remember(nodes, showThinkingProcess, showStatusTags) {
                        nodes.filter { isVisibleChatMarkdownNode(it, showThinkingProcess, showStatusTags) }
                    }
                    val nodeKeys = remember(nodes) { markdownNodeIdentityKeys(nodes) }
                    val cardStore = remember(nodeKeys, cardValues) { MarkdownCardStateStore(nodeKeys, cardValues) }
                    val standaloneNodes = remember(nodes, waifuEnabled, xmlRegistryVersion) {
                        if (waifuEnabled) nodes.indices.filter { isWaifuStandaloneNode(nodes[it], XmlRenderPluginRegistry::supports) }.toSet()
                        else emptySet()
                    }
                    var wasSplit by remember(waifuEnabled) { mutableStateOf(false) }
                    // 混合消息也按节点去除特殊块的外壳，正文继续使用原聊天样式。
                    val leadingProcess = waifuEnabled && visibleNodes.firstOrNull()?.let { !isWaifuReplyNode(it) } == true
                    val contentNeedsSplit = wasSplit || shouldSplitChatMessage(visibleNodes) || standaloneNodes.isNotEmpty() || leadingProcess
                    val split = contentNeedsSplit || processEnd >= 0 || isWaifuProcess
                    SideEffect { wasSplit = contentNeedsSplit }
                    val mode = if (stream == null) MarkdownRenderMode.STATIC else MarkdownRenderMode.STREAMING
                    val groups = remember(nodes, grouper) { grouper.group(nodes, messageKey) }
                    // 自动展开由整段消息的渲染模式驱动，XML 子流仅负责卡片内容。
                    val expansionSnapshot = overrides.toMap()
                    val rows = remember(
                        split, groups, nodes, grouper, mode, expansionSnapshot, timingSnapshot,
                        showThinkingProcess, showStatusTags, isWaifuProcess,
                    ) {
                        if (isWaifuProcess) {
                            buildWaifuProcessRows(nodes, expansionSnapshot["waifu-process"] ?: true, showThinkingProcess)
                        } else if (split && nodes.isNotEmpty()) {
                            buildThinkToolsRows(
                                groups, nodes, expansionSnapshot,
                                showThinkingProcess = showThinkingProcess,
                                showStatusTags = showStatusTags,
                            ) { group ->
                                grouper.describeGroup(
                                    group, nodes,
                                    isMessageStreaming = mode == MarkdownRenderMode.STREAMING,
                                )
                            }
                        } else {
                            emptyList()
                        }
                    }
                    val fragments = remember(
                        state, nodes, visibleNodes, grouper, mode, rows, cardStore,
                        processEnd, processId, processExpanded, message.waitDurationMs, message.outputDurationMs,
                    ) {
                        // Preserve original node indices, card ownership and small lazy rows.
                        val shownRows = rows.filter { row ->
                            val end = when (row) {
                                is ThinkToolsRow.Header -> row.info.group.endIndexInclusive
                                is ThinkToolsRow.Content -> row.index
                                is ThinkToolsRow.WaifuHeader -> nodes.lastIndex
                                is ThinkToolsRow.WaifuTool -> row.call.resultIndex ?: row.call.index
                            }
                            responseProcessItemVisible(end, processEnd, processExpanded)
                        }
                        when {
                            visibleNodes.isEmpty() -> emptyList()
                            processEnd >= 0 -> listOf(
                                ChatMarkdownFragment(
                                    state, nodes, grouper, mode, null, cardStore,
                                    CompletedResponseProcessHeader(
                                        message.waitDurationMs + message.outputDurationMs,
                                        processExpanded,
                                        { ResponseProcessExpansionState.toggle(chatId, processId) },
                                    ),
                                )
                            ) + shownRows.map { row ->
                                ChatMarkdownFragment(state, nodes, grouper, mode, row, cardStore)
                            }
                            rows.isEmpty() -> listOf(ChatMarkdownFragment(state, nodes, grouper, mode, null, cardStore))
                            else -> rows.map { row -> ChatMarkdownFragment(state, nodes, grouper, mode, row, cardStore) }
                        }
                    }
                    val entries = remember(messageKey, index, fragments, split, state, grouper, appearance, standaloneNodes, isWaifuProcess, waifuEnabled) {
                        fun standaloneAt(index: Int) = (fragments.getOrNull(index)?.row as? ThinkToolsRow.Content)?.let { it.index in standaloneNodes } == true
                        val replyBlock = if (isWaifuProcess) -1 else fragments.indexOfFirst { fragment ->
                            fragment.processHeader == null && when (val row = fragment.row) {
                                null -> nodes.any(::isWaifuReplyNode)
                                is ThinkToolsRow.Content -> isWaifuReplyNode(nodes[row.index])
                                else -> false
                            }
                        }
                        // 独立块后的正文已另起外壳，头像也应归这段正文；标题仍只属于首个回复片段。
                        var awaitingReplyAvatar = false
                        fragments.mapIndexed { blockIndex, fragment ->
                            val standaloneContent = standaloneAt(blockIndex)
                            val avatarAfterStandalone = awaitingReplyAvatar && !standaloneContent &&
                                (fragment.row as? ThinkToolsRow.Content)?.let { row ->
                                    val node = row.node ?: nodes[row.index]
                                    node.type != MarkdownProcessorType.HTML_BREAK && isWaifuReplyNode(node)
                                } == true
                            if (standaloneContent && waifuEnabled && !isWaifuProcess) {
                                awaitingReplyAvatar = true
                            } else if (avatarAfterStandalone) {
                                awaitingReplyAvatar = false
                            }
                            val blockKey = if (blockIndex == 0) "first" else fragment.row!!.key
                            ChatTimelineEntry(
                                key = "$messageKey/$blockKey",
                                messageIndex = index,
                                waifuProcess = isWaifuProcess,
                                slice = ChatMessageSlice(
                                    first = blockIndex == 0,
                                    last = blockIndex == fragments.lastIndex,
                                    split = split,
                                    fragment = fragment,
                                    state = state,
                                    grouper = grouper,
                                    appearance = appearance,
                                    standaloneContent = standaloneContent,
                                    waifuReplyStart = blockIndex == replyBlock,
                                    waifuAvatarAfterStandalone = avatarAfterStandalone,
                                    // 独立卡片两侧的正文各自闭合外壳，消息首尾身份仍用于标题、统计和操作。
                                    shellFirst = blockIndex == 0 || standaloneAt(blockIndex - 1),
                                    shellLast = blockIndex == fragments.lastIndex || standaloneAt(blockIndex + 1),
                                ),
                            )
                        }
                    }
                    add(entries)
                }
            }
        }
    }
    // 单条消息的节点更新时复用其他消息的条目，无关重组不重新拼接整条时间线。
    return remember(entriesByMessage) { entriesByMessage.flatten() }
}

/** 按可见节点判定拆块阈值，所有分组仍允许同时展开。 */
internal fun shouldSplitChatMessage(nodes: List<MarkdownNodeStable>): Boolean =
    nodes.size >= 24 || nodes.sumOf { it.content.length.toLong() } >= 32_000L

private data class CompletedResponseProcessHeader(
    val durationMs: Long,
    val expanded: Boolean,
    val onToggle: () -> Unit,
)

private class ChatMarkdownFragment(
    val state: StreamMarkdownRendererState,
    val nodes: List<MarkdownNodeStable>,
    val grouper: ThinkToolsXmlNodeGrouper,
    val mode: MarkdownRenderMode,
    val row: ThinkToolsRow?,
    val cardStore: MarkdownCardStateStore,
    val processHeader: CompletedResponseProcessHeader? = null,
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
            grouper.RenderScope(nodes) {
            Box(modifier = modifier) {
                if (processHeader != null) {
                    ResponseActivityHeader(
                        durationMs = processHeader.durationMs,
                        expanded = processHeader.expanded,
                        textColor = textColor,
                        onClick = processHeader.onToggle,
                    )
                } else when (val item = row) {
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
                    is ThinkToolsRow.WaifuHeader -> WaifuProcessHeader(
                        thoughtCount = item.thoughtCount,
                        toolCount = item.toolCount,
                        timing = LocalToolTimingIndex.current.group(0, nodes.lastIndex),
                        expanded = item.expanded,
                        textColor = textColor,
                        onToggle = { grouper.expansionOverrides[item.key] = !item.expanded },
                    )
                    is ThinkToolsRow.WaifuTool -> {
                        CompositionLocalProvider(
                            LocalInsideToolGroup provides true,
                            LocalMarkdownCardNodeIndex provides item.call.index,
                        ) {
                            Box(Modifier.fillMaxWidth().waifuProcessRail(textColor)) {
                                WaifuProcessToolCard(item.call, nodes, textColor, enableDialogs, state.rendererId + "/waifu/" + item.key)
                            }
                        }
                    }
                    is ThinkToolsRow.Content -> {
                        val indentation = if (item.waifuProcess) Modifier.waifuProcessRail(textColor)
                            else Modifier.padding(start = if (item.inGroup) 24.dp else 0.dp)
                        Box(Modifier.fillMaxWidth().then(indentation)) {
                            // 已出现的块离屏后重新进入时不重播淡入，避免快速滑动时闪烁。
                            CompositionLocalProvider(LocalInsideToolGroup provides item.inGroup) {
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
    }
}
