package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPluginRegistry
import com.ai.assistance.operit.ui.common.markdown.lazy.rememberMarkdownCardValue
import com.ai.assistance.operit.ui.common.markdown.lazy.splitPlainMarkdownNode
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.FileDiff
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.FileDiffDisplay
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.LocalToolTimingIndex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 一个调用位置对应一张卡片，结果到达时更新卡片而不新增另一条工具行。 */
@Composable
internal fun WaifuProcessToolCard(
    call: WaifuProcessItem.Tool,
    nodes: List<MarkdownNodeStable>,
    textColor: Color,
    enableDialogs: Boolean,
    renderInstanceKey: String,
) {
    val request = call.requestIndex?.let { nodes[it].content }
    val result = call.resultIndex?.let { nodes[it].content }
    // 大参数只随请求变化解析，逐步到达的结果不会反复复制同一份参数。
    val requestContent = remember(request) { waifuToolContent(request, null) }
    val resultContent = remember(result) { waifuToolContent(null, result) }
    val content = resultContent.copy(
        name = if (request != null) requestContent.name else resultContent.name,
        parameters = requestContent.parameters,
    )
    val timing = call.requestIndex?.let { LocalToolTimingIndex.current.tool(it) }
    val elapsed = if (timing != null) waifuElapsedLabel(timing) else ""
    val normalizedStatus = content.status?.lowercase()
    val state = when {
        normalizedStatus == "success" -> stringResource(R.string.success)
        normalizedStatus == "error" -> stringResource(R.string.failed)
        content.status != null -> content.status
        result != null -> stringResource(R.string.tool_result_block)
        else -> stringResource(R.string.tool_call_block)
    }
    WaifuProcessToolRow(
        name = content.name.ifBlank { stringResource(R.string.unknown_tool) },
        status = listOf(state, elapsed).filter(String::isNotEmpty).joinToString(" · "),
        details = "",
        textColor = textColor,
        isError = normalizedStatus == "error",
        summary = content.parameters.lineSequence().firstOrNull().orEmpty().take(120),
        body = {
            SelectionContainer { Text(content.name, color = textColor, style = MaterialTheme.typography.labelMedium) }
            if (request != null) {
                WaifuToolRawDetails("waifu-parameters", stringResource(R.string.tool_call_block), request, textColor)
            }
            if (result != null) {
                if (content.resultClosed) {
                    WaifuSpecializedToolResult(result, content.result, textColor, enableDialogs, renderInstanceKey)
                }
                // 原始 JSON 和长日志独立折叠，不与专用展示同时铺满聊天区。
                WaifuToolRawDetails("waifu-result", stringResource(R.string.tool_result_block), content.result, textColor)
            }
        },
    )
}

private val fileDiffBody = Regex("<file-diff\\b([^>]*)>([\\s\\S]*?)</file-diff>")
private val fileDiffPath = Regex("\\bpath=\"([^\"]*)\"")
private val fileDiffDetails = Regex("\\bdetails=\"([^\"]*)\"")
private val fileDiffCdata = Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>")

/** 结果插件只接管卡片内部；外层标题、调用身份和展开状态由统一组件持有。 */
@Composable
private fun WaifuSpecializedToolResult(
    xml: String,
    body: String,
    textColor: Color,
    enableDialogs: Boolean,
    renderInstanceKey: String,
) {
    val pluginHandled = XmlRenderPluginRegistry.RenderIfMatched(
        xmlContent = xml, tagName = "tool_result", modifier = Modifier.fillMaxWidth(),
        textColor = textColor, xmlStream = null, renderInstanceKey = renderInstanceKey,
    )
    if (pluginHandled) return
    val diff = remember(body) {
        fileDiffBody.find(body)?.let { block ->
            fileDiffCdata.find(block.groupValues[2])?.let { cdata ->
                FileDiff(
                    path = fileDiffPath.find(block.groupValues[1])?.groupValues?.get(1).orEmpty(),
                    diffContent = cdata.groupValues[1],
                    details = fileDiffDetails.find(block.groupValues[1])?.groupValues?.get(1).orEmpty(),
                )
            }
        }
    }
    when {
        diff != null -> FileDiffDisplay(diff)
        body.lineSequence().any { it.trimStart().startsWith("![") } -> StreamMarkdownRenderer(
            content = body, textColor = textColor, backgroundColor = Color.Transparent, enableDialogs = enableDialogs,
        )
        body.isNotBlank() && body.length <= 800 && body.trimStart().first() !in "{[" -> {
            SelectionContainer { Text(body, color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall) }
        }
    }
    // A2UI 的 compiled_json 不在这里重新实例化；原生投递的卡片正文保留原有所有权和表单状态。
}

/** 原始调用和结果分别保留完整复制入口，只有展开的原文才拆块并进入组合。 */
@Composable
private fun WaifuToolRawDetails(slot: String, label: String, text: String, textColor: Color) {
    var expanded by rememberMarkdownCardValue(slot, false)
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label + " · " + stringResource(if (expanded) R.string.common_collapse else R.string.common_expand),
            color = textColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f).clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 6.dp),
        )
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.copy),
            tint = textColor.copy(alpha = 0.45f),
            modifier = Modifier.clickable(role = Role.Button) { clipboard.setText(AnnotatedString(text)) }.padding(6.dp).size(14.dp),
        )
    }
    if (expanded && text.isNotEmpty()) {
        val chunks = remember(text) {
            splitPlainMarkdownNode(MarkdownNodeStable(MarkdownProcessorType.PLAIN_TEXT, text, emptyList()), maxChars = 2048)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(vertical = 4.dp)) {
            items(chunks) { chunk ->
                SelectionContainer { Text(chunk.content, color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
