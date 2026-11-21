package com.ai.assistance.operit.ui.features.conversation.chat.subagent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.SubagentMessage
import com.ai.assistance.operit.core.agent.SubagentTask
import com.ai.assistance.operit.core.agent.isActive
import com.ai.assistance.operit.core.chat.protocol.protocolMetaDisplayText
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.common.markdown.lazy.LocalMarkdownRenderFragment
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

/** 子会话使用独立工具卡，不把主聊天的大卡片或原始 XML 嵌进任务窗口。 */
@Composable
internal fun SubagentConversation(task: SubagentTask, modifier: Modifier = Modifier) {
    val messages = remember(task.messages, task.currentOutput, task.assistantTimestamp, task.executionId) {
        task.messages + if (task.currentOutput.isBlank()) emptyList() else listOf(
            SubagentMessage(id = task.assistantTimestamp.toString(), role = "assistant", content = task.currentOutput,
                executionId = task.executionId, createdAt = task.assistantTimestamp),
        )
    }
    val blocks = messages.associate { message ->
        val running = task.status.isActive && message.id == task.assistantTimestamp.toString()
        message.id to key(message.id) {
            // 每个历史消息单独缓存；新增流式片段只重新解析当前助手消息。
            remember(message.content, message.role, running) {
                if (message.role == "assistant") subagentTranscriptBlocks(message.content, running) else emptyList()
            }
        }
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
        item(key = "hint") {
            Text(stringResource(R.string.subagent_child_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        messages.forEach { message ->
            item(key = "${message.id}:label") {
                val label = when (message.role) {
                    "user" -> R.string.subagent_main_agent
                    "summary" -> R.string.subagent_context_background
                    "agent" -> R.string.subagent_agent_messages
                    else -> R.string.subagent_helper
                }
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (message.role == "assistant") {
                blocks.getValue(message.id).forEach { block ->
                    item(key = "${message.id}:block:${block.index}") { TranscriptBlock(block) }
                }
            } else {
                item(key = "${message.id}:text") { TranscriptMarkdown(message.content) }
            }
            if (message.role == "user") item(key = "${message.id}:delivery") {
                Text(stringResource(if (message.pending) R.string.subagent_pending_message else R.string.subagent_read_message),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SubagentTranscriptContent(raw: String, running: Boolean) {
    val blocks = remember(raw, running) { subagentTranscriptBlocks(raw, running) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> key(block.index) { TranscriptBlock(block) } }
    }
}

@Composable
private fun TranscriptBlock(block: SubagentTranscriptBlock) {
    when (block) {
        is SubagentTranscriptBlock.Prose -> TranscriptMarkdown(block.content)
        is SubagentTranscriptBlock.Reasoning -> {
            var expanded by rememberSaveable { mutableStateOf(false) }
            Column {
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        stringResource(if (expanded) R.string.common_collapse else R.string.common_expand), Modifier.size(18.dp))
                    Text(stringResource(R.string.thinking_process), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expanded) BoundedToolText(block.content)
            }
        }
        is SubagentTranscriptBlock.Tool -> SubagentToolCard(block)
    }
}

@Composable
private fun TranscriptMarkdown(content: String) {
    val display = remember(content) { protocolMetaDisplayText(content) }
    if (display.isBlank()) return
    SelectionContainer {
        // 任务窗口拥有自己的文本，不能复用打开它的主聊天消息片段。
        CompositionLocalProvider(LocalMarkdownRenderFragment provides null) {
            MarkdownTextComposable(display, MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodySmall.fontSize)
        }
    }
}

@Composable
private fun SubagentToolCard(tool: SubagentTranscriptBlock.Tool) {
    var expanded by rememberSaveable { mutableStateOf(tool.phase == SubagentToolPhase.RUNNING || tool.phase == SubagentToolPhase.PREPARING) }
    var showParameters by rememberSaveable { mutableStateOf(false) }
    val active = tool.phase == SubagentToolPhase.RUNNING || tool.phase == SubagentToolPhase.PREPARING
    val failed = tool.phase == SubagentToolPhase.FAILED
    val status = when (tool.phase) {
        SubagentToolPhase.PREPARING -> R.string.subagent_tool_preparing
        SubagentToolPhase.RUNNING -> R.string.subagent_status_running
        SubagentToolPhase.COMPLETED -> R.string.subagent_status_completed
        SubagentToolPhase.FAILED -> R.string.subagent_status_failed
        SubagentToolPhase.STOPPED -> R.string.subagent_tool_no_result
        SubagentToolPhase.RETURNED -> R.string.subagent_tool_returned
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    stringResource(if (expanded) R.string.common_collapse else R.string.common_expand), Modifier.size(18.dp))
                Text(tool.name.ifBlank { stringResource(R.string.subagent_tool_call_label) }, Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium)
                SubagentStatusBadge(stringResource(status), running = active,
                    completed = tool.phase == SubagentToolPhase.COMPLETED, failed = failed)
            }
            if (expanded) Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(7.dp))
                        .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))) {
                        listOf(false, true).forEach { parameters ->
                            val chosen = showParameters == parameters
                            Surface(onClick = { showParameters = parameters }, shape = RectangleShape,
                                modifier = Modifier.weight(1f).fillMaxHeight().semantics { selected = chosen },
                                color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                contentColor = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(stringResource(if (parameters) R.string.subagent_tool_parameters else R.string.subagent_tool_output),
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    val body = if (showParameters) tool.parameters else tool.output
                    if (!body.isNullOrEmpty()) {
                        val clipboard = LocalClipboardManager.current
                        IconButton(onClick = { clipboard.setText(AnnotatedString(body)) }) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.subagent_tool_copy), Modifier.size(16.dp))
                        }
                    }
                }
                if (showParameters) {
                    if (tool.parameters.isEmpty()) Text(stringResource(R.string.subagent_tool_empty_parameters), style = MaterialTheme.typography.bodySmall)
                    else key("parameters") { BoundedToolText(tool.parameters) }
                } else if (tool.output == null) {
                    Text(stringResource(if (active) R.string.subagent_tool_waiting_output else R.string.subagent_tool_no_result),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else key("output") {
                    if (tool.output.isEmpty()) Text(stringResource(R.string.subagent_tool_empty_output), style = MaterialTheme.typography.bodySmall)
                    else BoundedToolText(tool.output)
                }
            }
        }
    }
}

@Composable
private fun BoundedToolText(content: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        // 内容完整保留，只约束显示视口；流式增长不重置内部滚动位置。
        Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(10.dp)) {
            SelectionContainer {
                Text(content, fontFamily = FontFamily.Monospace, fontSize = 12.sp, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 用紧凑状态胶囊区分运行、完成和失败，跟随当前明暗主题保持对比度。 */
@Composable
internal fun SubagentStatusBadge(label: String, running: Boolean = false, completed: Boolean = false, failed: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val tint = when {
        failed -> scheme.error
        completed -> if (scheme.surface.luminance() < 0.5f) Color(0xFF9ED593) else Color(0xFF467D3B)
        running -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = 0.10f), contentColor = tint) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            when {
                running -> CircularProgressIndicator(Modifier.size(10.dp), color = tint, strokeWidth = 1.4.dp)
                completed -> Icon(Icons.Default.CheckCircle, null, Modifier.size(12.dp), tint = tint)
                failed -> Icon(Icons.Default.ErrorOutline, null, Modifier.size(12.dp), tint = tint)
            }
            Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
        }
    }
}
