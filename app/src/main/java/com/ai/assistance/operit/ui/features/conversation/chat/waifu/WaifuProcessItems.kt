package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.toolmarkup.ToolResultMarkup

internal sealed interface WaifuProcessItem {
    val index: Int

    data class Content(override val index: Int) : WaifuProcessItem
    data class Tool(val requestIndex: Int?, val resultIndex: Int?) : WaifuProcessItem {
        override val index: Int get() = checkNotNull(requestIndex ?: resultIndex)
    }
}

internal data class WaifuProcessItems(
    val items: List<WaifuProcessItem>,
    val toolCount: Int,
    val thoughtCount: Int,
)

/** 结果由 OrderedToolResults 按原始调用顺序发布，同名并行调用也各自占据独立位置。 */
internal fun waifuProcessItems(nodes: List<MarkdownNodeStable>, showThinkingProcess: Boolean): WaifuProcessItems {
    val items = mutableListOf<WaifuProcessItem>()
    val waiting = ArrayDeque<Int>()
    var toolCount = 0
    var thoughtCount = 0
    nodes.forEachIndexed { index, node ->
        if (node.type == MarkdownProcessorType.PLAIN_TEXT && node.content.isBlank()) return@forEachIndexed
        val tag = if (node.type == MarkdownProcessorType.XML_BLOCK) {
            ChatMarkupRegex.normalizeToolLikeTagName(ChatMarkupRegex.extractOpeningTagName(node.content))
        } else null
        when (tag) {
            "meta" -> Unit
            "think", "thinking" -> if (showThinkingProcess) {
                thoughtCount++
                items += WaifuProcessItem.Content(index)
            }
            "tool" -> {
                waiting.addLast(items.size)
                items += WaifuProcessItem.Tool(index, null)
                toolCount++
            }
            "tool_result" -> {
                if (waiting.isEmpty()) {
                    items += WaifuProcessItem.Tool(null, index)
                } else {
                    val position = waiting.removeFirst()
                    val call = items[position] as WaifuProcessItem.Tool
                    items[position] = call.copy(resultIndex = index)
                }
            }
            else -> {
                // 可见正文是配对边界，不能把后续独立结果归到前一段未完成调用。
                waiting.clear()
                items += WaifuProcessItem.Content(index)
            }
        }
    }
    return WaifuProcessItems(items, toolCount, thoughtCount)
}

internal data class WaifuToolContent(
    val name: String,
    val parameters: String,
    val result: String,
    val resultClosed: Boolean,
    val status: String?,
)

/** 只读取外层属性，参数和结果中的 XML 示例不得参与状态或名称判断。 */
internal fun waifuToolContent(request: String?, result: String?): WaifuToolContent {
    val opening = (request ?: result).orEmpty().substringBefore('>')
    val name = ChatMarkupRegex.nameAttr.find(opening)?.groupValues?.get(1).orEmpty()
    val parameters = request?.let { xml ->
        ChatMarkupRegex.toolParamPattern.findAll(xml).joinToString("\n") { match ->
            "${match.groupValues[1]}: ${match.groupValues[2].trim()}"
        }
    }.orEmpty()
    val closed = result != null && ToolResultMarkup.isCompleteBlock(result)
    val body = when {
        result == null -> ""
        closed -> ToolResultMarkup.contentFromBlock(result)
        else -> result.substringAfter('>', "")
    }
    val status = if (closed) {
        ChatMarkupRegex.statusAttr.find(checkNotNull(result).substringBefore('>'))?.groupValues?.get(1) ?: "success"
    } else null
    return WaifuToolContent(name, parameters, body, closed, status)
}
