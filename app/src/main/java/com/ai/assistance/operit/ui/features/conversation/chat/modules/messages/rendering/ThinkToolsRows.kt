package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.ToolTimingSummary
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.toolTimingTitle
import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.ui.common.markdown.lazy.markdownNodeIdentityKeys
import com.ai.assistance.operit.ui.common.markdown.lazy.splitPlainMarkdownNode
import com.ai.assistance.operit.ui.common.markdown.responseProcessItemVisible
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuProcessItem
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.waifuProcessItems
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

internal data class ThinkToolsGroupInfo(
    val group: MarkdownGroupedItem.Group,
    val toolCount: Int,
    val searchCount: Int,
    val autoExpand: Boolean,
    val thoughtCount: Int = 0,
    val timing: ToolTimingSummary = ToolTimingSummary(emptyList()),
    val isMessageStreaming: Boolean = false,
)

internal sealed interface ThinkToolsRow {
    val key: String

    data class Header(val info: ThinkToolsGroupInfo, val expanded: Boolean) : ThinkToolsRow {
        override val key: String get() = "header-${info.group.stableKey}"
    }

    data class WaifuHeader(val toolCount: Int, val thoughtCount: Int, val expanded: Boolean) : ThinkToolsRow {
        override val key: String get() = "waifu-process"
    }

    data class WaifuTool(val call: WaifuProcessItem.Tool, override val key: String) : ThinkToolsRow

    data class Content(
        val index: Int,
        val inGroup: Boolean,
        override val key: String,
        val node: MarkdownNodeStable? = null,
        val waifuProcess: Boolean = false,
        val groupEnd: Int? = null,
    ) : ThinkToolsRow
}

/** 过程标题和配对工具仍展开为独立懒加载行，长过程不会集中测量全部内容。 */
internal fun buildWaifuProcessRows(
    nodes: List<MarkdownNodeStable>,
    expanded: Boolean,
    showThinkingProcess: Boolean,
): List<ThinkToolsRow> {
    val process = waifuProcessItems(nodes, showThinkingProcess)
    if (process.items.isEmpty()) return emptyList()
    val nodeKeys = markdownNodeIdentityKeys(nodes)
    // 单独一个思考自身已经带原文预览和折叠入口，外层总标题会造成重复嵌套。
    if (process.thoughtCount == 1 && process.items.size == 1) {
        val item = process.items.single()
        return listOf(ThinkToolsRow.Content(item.index, false, nodeKeys[item.index]))
    }
    return buildList {
        add(ThinkToolsRow.WaifuHeader(process.toolCount, process.thoughtCount, expanded))
        if (expanded) process.items.forEach { item ->
            when (item) {
                is WaifuProcessItem.Content -> add(ThinkToolsRow.Content(item.index, true, nodeKeys[item.index], waifuProcess = true))
                is WaifuProcessItem.Tool -> add(ThinkToolsRow.WaifuTool(item, nodeKeys[item.index]))
            }
        }
    }
}

/** 组标题和组内节点属于同一滚动区域，单个大组也不会退化为一个超高条目。 */
internal fun buildThinkToolsRows(
    groups: List<MarkdownGroupedItem>,
    nodes: List<MarkdownNodeStable>,
    overrides: Map<String, Boolean>,
    showThinkingProcess: Boolean = true,
    showStatusTags: Boolean = true,
    describeGroup: (MarkdownGroupedItem.Group) -> ThinkToolsGroupInfo,
): List<ThinkToolsRow> = buildList {
    val nodeKeys = markdownNodeIdentityKeys(nodes)
    fun addContent(index: Int, groupEnd: Int? = null) {
        val node = nodes[index]
        if (!isVisibleChatMarkdownNode(node, showThinkingProcess, showStatusTags)) return
        val inGroup = groupEnd != null
        // 批次间的长说明也拆为独立可复用行，纳入分组不会改变原节点索引或片段键。
        if (node.type == MarkdownProcessorType.PLAIN_TEXT) {
            splitPlainMarkdownNode(node).forEachIndexed { piece, chunk ->
                if (isVisibleChatMarkdownNode(chunk, showThinkingProcess, showStatusTags)) {
                    add(ThinkToolsRow.Content(index, inGroup, "${nodeKeys[index]}/part-$piece", chunk, groupEnd = groupEnd))
                }
            }
        } else {
            add(ThinkToolsRow.Content(index, inGroup, nodeKeys[index], groupEnd = groupEnd))
        }
    }
    for (item in groups) {
        when (item) {
            is MarkdownGroupedItem.Single -> addContent(item.index)
            is MarkdownGroupedItem.Group -> {
                val info = describeGroup(item)
                val expanded = overrides[item.stableKey] ?: info.autoExpand
                add(ThinkToolsRow.Header(info, expanded))
                if (expanded) {
                    for (index in item.startIndex..item.endIndexInclusive) addContent(index, item.endIndexInclusive)
                }
            }
        }
    }
}

/** 总用时收起时隐藏过程前缀；跨边界的组仍保有自己的入口。 */
internal fun visibleResponseProcessRows(
    rows: List<ThinkToolsRow>,
    processEnd: Int,
    expanded: Boolean,
    lastNodeIndex: Int,
): List<ThinkToolsRow> {
    if (processEnd < 0 || expanded) return rows
    return rows.filter { row ->
        val end = when (row) {
            is ThinkToolsRow.Header -> row.info.group.endIndexInclusive
            is ThinkToolsRow.Content -> row.groupEnd ?: row.index
            is ThinkToolsRow.WaifuHeader -> lastNodeIndex
            is ThinkToolsRow.WaifuTool -> row.call.resultIndex ?: row.call.index
        }
        responseProcessItemVisible(end, processEnd, expanded)
    }
}

@Composable
internal fun thinkToolsGroupTitle(info: ThinkToolsGroupInfo): String = toolTimingTitle(
    summary = info.timing,
    thoughtCount = info.thoughtCount,
    searchCount = info.searchCount,
    isMessageStreaming = info.isMessageStreaming,
    toolCount = info.toolCount,
)
