package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.markdownListMarker

internal val LocalMarkdownDocumentPath = compositionLocalOf<String?> { null }

/** 块容器只移除自身语法，内部继续使用相同的 Markdown 解析和链接处理。 */
@Composable
internal fun MarkdownContainerRenderer(
    node: MarkdownNodeStable,
    textColor: Color,
    fontSize: TextUnit,
    onLinkClick: ((String) -> Unit)?,
    xmlRenderer: XmlContentRenderer,
    enableDialogs: Boolean,
) {
    val renderBody: @Composable (String, Modifier) -> Unit = { content, modifier ->
        StreamMarkdownRenderer(content = content, modifier = modifier, textColor = textColor,
            fontSize = fontSize, onLinkClick = onLinkClick, xmlRenderer = xmlRenderer,
            enableDialogs = enableDialogs)
    }
    when (node.type) {
        MarkdownProcessorType.BLOCK_QUOTE -> {
            val body = node.content.lines().joinToString("\n") { it.replaceFirst(Regex("^ {0,3}> ?"), "") }
            Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(4.dp)) {
                renderBody(body, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        MarkdownProcessorType.ORDERED_LIST, MarkdownProcessorType.UNORDERED_LIST -> {
            val lines = node.content.trimEnd('\n').lines()
            val marker = markdownListMarker(lines.first()) ?: return
            val indentToRemove = marker.indent + 2
            val body = lines.first().drop(marker.prefixLength) + if (lines.size == 1) "" else "\n" +
                lines.drop(1).joinToString("\n") { line -> line.drop(line.takeWhile { it == ' ' }.length.coerceAtMost(indentToRemove)) }
            Row(Modifier.fillMaxWidth().padding(start = (marker.indent * 6).dp, top = 1.dp, bottom = 1.dp)) {
                if (marker.checked != null) {
                    Checkbox(checked = marker.checked, onCheckedChange = null,
                        modifier = Modifier.padding(end = 6.dp).size(22.dp))
                } else {
                    Text(text = marker.number?.let { "$it." } ?: "•", color = textColor,
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 18.dp).padding(end = 6.dp))
                }
                renderBody(body, Modifier.weight(1f))
            }
        }
        MarkdownProcessorType.DEFINITION_LIST -> {
            val lines = node.content.lines()
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                renderBody("**${lines.first()}**", Modifier)
                val definition = lines.drop(1).joinToString("\n") {
                    if (it.startsWith(": ")) "\n" + it.drop(2) else it.removePrefix("  ")
                }.trim()
                renderBody(definition, Modifier.padding(start = 20.dp))
            }
        }
        MarkdownProcessorType.FOOTNOTE -> {
            val parts = node.content.split('\n', limit = 3)
            if (parts.size != 3) return
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${parts[1]}.", color = textColor, modifier = Modifier.padding(end = 8.dp))
                renderBody(parts[2] + " [↩](#fnref-${parts[0]})", Modifier.weight(1f))
            }
        }
        MarkdownProcessorType.HTML_BLOCK -> {
            HtmlPreviewRenderer(
                code = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" + node.content,
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 2.dp),
            )
        }
        else -> error("Unsupported Markdown container: ${node.type}")
    }
}