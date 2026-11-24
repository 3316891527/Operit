package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.lazy.rememberMarkdownCardValue
import com.ai.assistance.operit.ui.common.markdown.lazy.splitPlainMarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 所有工具共用图标和行布局，详情只在展开时进入组合。 */
@Composable
internal fun WaifuProcessToolRow(
    name: String,
    status: String,
    details: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    summary: String = "",
    body: (@Composable () -> Unit)? = null,
) {
    var expanded by rememberMarkdownCardValue("waifu-tool-expanded", false)
    val expansionDescription = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    val statusColor = if (isError) MaterialTheme.colorScheme.error else textColor.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.fillMaxWidth().padding(vertical = 3.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .semantics { stateDescription = expansionDescription }
                .clickable(role = Role.Button, onClick = { expanded = !expanded })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Build, contentDescription = null, tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            Text(
                text = name,
                color = textColor.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = status,
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 104.dp),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = expansionDescription,
                tint = textColor.copy(alpha = 0.55f),
                modifier = Modifier.size(14.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        if (summary.isNotEmpty()) {
            Text(summary, color = textColor.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
        }
        if (expanded && body != null) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) { body() }
        }
        if (expanded && body == null) {
            val clipboard = LocalClipboardManager.current
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, top = 2.dp, bottom = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(name, color = textColor, style = MaterialTheme.typography.labelMedium)
                    }
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.clickable(role = Role.Button) { clipboard.setText(AnnotatedString(details)) }
                            .padding(8.dp).size(16.dp),
                    )
                }
                Text(status, color = statusColor, style = MaterialTheme.typography.labelSmall)
                if (details.isNotEmpty()) {
                    // 日志按原文拆成有限高度的可见行，巨量结果不测量为一个超高 Text。
                    val chunks = remember(details) {
                        splitPlainMarkdownNode(MarkdownNodeStable(MarkdownProcessorType.PLAIN_TEXT, details, emptyList()), maxChars = 2048)
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(top = 4.dp)) {
                        items(chunks) { chunk ->
                            SelectionContainer {
                                Text(chunk.content, color = textColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
