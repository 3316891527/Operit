package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.common.markdown.AnimatedNode
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownGroupNodeRendering
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.ui.common.markdown.MarkdownNodeGrouper
import com.ai.assistance.operit.ui.common.markdown.MarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.XmlContentRenderer
import com.ai.assistance.operit.ui.common.markdown.lazy.markdownNodeIdentityKeys
import com.ai.assistance.operit.ui.common.markdown.markdownProcessTag
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.LocalToolTimingIndex
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.ToolTimingIndex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream

class ThinkToolsXmlNodeGrouper(
    internal val showThinkingProcess: Boolean,
    private val forceExpandGroups: Boolean = false,
    private val toolCollapseMode: ToolCollapseMode = ToolCollapseMode.ALL,
    private val groupProcessNodes: Boolean = true,
) : MarkdownNodeGrouper {
    // 聊天列表可注入消息持有的展开选择，其他完整渲染入口自行持有状态。
    internal var expansionOverrides = mutableStateMapOf<String, Boolean>()

    internal var executionTimings by mutableStateOf<List<ToolCallExecution>>(emptyList())
    internal var toolTimingsLive by mutableStateOf(false)
    private var timingNodes: List<Pair<MarkdownProcessorType, String>>? = null
    private var timingRecords: List<ToolCallExecution>? = null
    private var timingLive = false
    private var timingIndex = ToolTimingIndex.Empty

    private fun timingFor(nodes: List<MarkdownNodeStable>): ToolTimingIndex {
        val snapshot = nodes.map { it.type to it.content }
        val records = executionTimings
        if (timingNodes != snapshot || timingRecords != records || timingLive != toolTimingsLive) {
            timingIndex = ToolTimingIndex.parse(nodes, records, live = toolTimingsLive)
            timingNodes = snapshot
            timingRecords = records
            timingLive = toolTimingsLive
        }
        return timingIndex
    }

    @Composable
    override fun RenderScope(nodes: List<MarkdownNodeStable>, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalToolTimingIndex provides timingFor(nodes)) { content() }
    }

    override fun group(nodes: List<MarkdownNodeStable>, rendererId: String): List<MarkdownGroupedItem> {
        // Waifu 的逻辑过程段使用专用入口，节点仍逐项懒加载。
        if (!groupProcessNodes) return nodes.indices.map { MarkdownGroupedItem.Single(it) }
        var first = -1
        var last = -1
        var hasTools = false
        nodes.forEachIndexed { index, node ->
            when (markdownProcessTag(node)) {
                "tool", "tool_result", "search" -> hasTools = true
                "think", "thinking" -> if (!showThinkingProcess) return@forEachIndexed
                else -> return@forEachIndexed
            }
            if (first == -1) first = index
            last = index
        }
        if (!hasTools) return nodes.indices.map { MarkdownGroupedItem.Single(it) }

        // 只有后续过程节点实际出现时才扩展范围，中途说明与卡片一同移入组；末尾正文始终独立。
        val nodeKeys = markdownNodeIdentityKeys(nodes)
        return buildList {
            for (index in 0 until first) add(MarkdownGroupedItem.Single(index))
            add(MarkdownGroupedItem.Group(first, last, "tool-process-${nodeKeys[first]}"))
            for (index in last + 1 until nodes.size) add(MarkdownGroupedItem.Single(index))
        }
    }

    internal fun describeGroup(
        group: MarkdownGroupedItem.Group,
        nodes: List<MarkdownNodeStable>,
        isMessageStreaming: Boolean,
    ): ThinkToolsGroupInfo {
        var toolCount = 0
        var searchCount = 0
        var thoughtCount = 0
        var allToolsReadOnly = true
        for (index in group.startIndex..group.endIndexInclusive) {
            val node = nodes[index]
            when (val tag = markdownProcessTag(node)) {
                "tool", "tool_result" -> {
                    if (tag == "tool") toolCount++
                    val name = ChatMarkupRegex.nameAttr.find(node.content.substringBefore('>'))?.groupValues?.get(1)
                    if (!isReadOnlyTool(name)) allToolsReadOnly = false
                }
                "think", "thinking" -> if (showThinkingProcess) thoughtCount++
                "search" -> searchCount++
            }
        }
        val collapseByDefault = when (toolCollapseMode) {
            ToolCollapseMode.READ_ONLY -> allToolsReadOnly && (toolCount >= 2 || searchCount > 0)
            ToolCollapseMode.ALL -> toolCount >= 2 || searchCount > 0
            ToolCollapseMode.FULL -> true
        }
        // 子流在每个 XML 块结束后移除，不能用来判断整段回复是否还在生成。
        return ThinkToolsGroupInfo(
            group = group,
            toolCount = toolCount,
            searchCount = searchCount,
            autoExpand = isMessageStreaming || !collapseByDefault,
            thoughtCount = thoughtCount,
            timing = timingFor(nodes).group(group.startIndex, group.endIndexInclusive),
            isMessageStreaming = isMessageStreaming,
        )
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
        fontSize: TextUnit,
    ) {
        val alpha = if (forceExpandGroups) 1f else animateFloatAsState(
            targetValue = if (isVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 800),
            label = "fadeIn-think-tools-$rendererId",
        ).value
        val info = describeGroup(
            group, nodes, isMessageStreaming = LocalMarkdownRenderMode.current == MarkdownRenderMode.STREAMING,
        )
        val expanded = forceExpandGroups || (expansionOverrides[group.stableKey] ?: info.autoExpand)
        val nodeRendering = LocalMarkdownGroupNodeRendering.current

        Column(modifier = modifier.padding(bottom = 4.dp).graphicsLayer { this.alpha = alpha }) {
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
                animationSpec = tween(durationMillis = if (forceExpandGroups) 0 else 300),
                label = "arrowRotation-think-tools-$rendererId",
            )
            CanvasExpandableHeaderRow(
                title = thinkToolsGroupTitle(info),
                semanticDescription = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand),
                expanded = expanded,
                titleColor = textColor.copy(alpha = 0.7f),
                rotationDegrees = rotation,
                onClick = { expansionOverrides[group.stableKey] = !expanded },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(if (forceExpandGroups) 0 else 200)),
                exit = fadeOut(animationSpec = tween(if (forceExpandGroups) 0 else 200)),
            ) {
                val width = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
                Column(modifier = width.padding(top = 2.dp, bottom = 4.dp, start = 24.dp)) {
                    val nodeKeys = markdownNodeIdentityKeys(nodes)
                    for (index in group.startIndex..group.endIndexInclusive) {
                        val node = nodes[index]
                        // 状态卡片自己的 XML 绘制器处理显示设置；这里不提前丢弃任一可见卡片。
                        if (!isVisibleChatMarkdownNode(node, showThinkingProcess, showStatusTags = true)) continue
                        val nodeKey = "node-$rendererId-${nodeKeys[index]}"
                        androidx.compose.runtime.key(nodeKey) {
                            AnimatedNode(
                                nodeKey = nodeKey,
                                node = node,
                                index = index,
                                isVisible = true,
                                textColor = textColor,
                                fontSize = fontSize,
                                onLinkClick = onLinkClick,
                                xmlRenderer = xmlRenderer,
                                xmlStream = xmlStreamResolver(index),
                                enableDialogs = nodeRendering.enableDialogs,
                                fillMaxWidth = fillMaxWidth,
                                isLastNode = isLastNode && index == group.endIndexInclusive,
                                modifier = nodeRendering.modifierFor(index),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isReadOnlyTool(toolName: String?): Boolean {
    val name = toolName?.trim()?.lowercase() ?: return false
    return name.contains("search") || name in readOnlyToolNames
}

private val readOnlyToolNames = setOf(
    "list_files", "grep_code", "grep_context", "read_file", "read_file_part", "read_file_full",
    "read_file_binary", "use_package", "find_files", "visit_web",
)
