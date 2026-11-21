package com.ai.assistance.operit.ui.features.conversation.chat.subagent

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.common.markdown.lazy.LocalMarkdownRenderFragment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.*
import com.ai.assistance.operit.core.chat.protocol.protocolMetaDisplayText
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun statusName(status: SubagentStatus): String = stringResource(when (status) {
    SubagentStatus.QUEUED -> R.string.subagent_status_queued
    SubagentStatus.RUNNING -> R.string.subagent_status_running
    SubagentStatus.WAITING -> R.string.subagent_status_waiting
    SubagentStatus.COMPLETED -> R.string.subagent_status_completed
    SubagentStatus.CANCELLED -> R.string.subagent_status_cancelled
    SubagentStatus.FAILED -> R.string.subagent_status_failed
    SubagentStatus.INTERRUPTED -> R.string.subagent_status_interrupted
})

/** 输入栏上方的任务托盘只负责观察与控制；任务创建始终由主代理完成。 */
@Composable
fun SubagentTaskPanel(chatId: String?) {
    val context = LocalContext.current
    val coordinator = remember { SubagentCoordinator.getInstance(context) }
    val state by coordinator.state.collectAsState()
    val tasks = state.tasks.filter { it.rootChatId == chatId }
    var expanded by remember(chatId) { mutableStateOf(true) }
    var manager by remember(chatId) { mutableStateOf(false) }
    var selected by remember(chatId) { mutableStateOf<String?>(null) }
    if (chatId == null || (tasks.isEmpty() && state.error == null)) return
    val visible = tasks.filterNot { it.archived }.sortedWith(compareByDescending<SubagentTask> { it.status.isActive }.thenByDescending { it.createdAt })
    // 只收起托盘，详情和管理窗口仍留在组合中，自动归档不会关闭正在阅读的报告。
    if (visible.isNotEmpty() || state.error != null) Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.subagent_collaboration), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.subagent_task_count, visible.count { it.status == SubagentStatus.RUNNING }, visible.count { it.status == SubagentStatus.QUEUED }, visible.count { it.status == SubagentStatus.WAITING }, visible.sumOf { it.reports.size }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { manager = true }) { Text(stringResource(R.string.subagent_tasks)) }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, stringResource(R.string.subagent_collaboration))
                }
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (expanded && visible.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                items(visible, key = { it.id }) { task -> TaskCard(task, Modifier.width(220.dp), SubagentTree.queuePosition(state.tasks, task.id)) { selected = task.id } }
            }
        }
    }
    if (manager) TaskManager(tasks, onDismiss = { manager = false }, onOpen = { selected = it; manager = false })
    selected?.let { id -> tasks.find { it.id == id }?.let { TaskDetail(it, tasks, coordinator, onSelect = { selected = it }, onDismiss = { selected = null }) } }
}

@Composable
private fun TaskCard(task: SubagentTask, modifier: Modifier = Modifier, queuePosition: Int = 0, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
            }
            Text(task.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (queuePosition > 0) Text(stringResource(R.string.subagent_queue_position, queuePosition), style = MaterialTheme.typography.labelSmall)
            Text(task.model.modelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (task.status == SubagentStatus.RUNNING) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Text(statusName(task.status), style = MaterialTheme.typography.labelSmall, color = if (task.status == SubagentStatus.FAILED || task.status == SubagentStatus.INTERRUPTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.subagent_attempt, task.attempt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val preview = task.reports.lastOrNull()?.content ?: task.events.lastOrNull()?.text.orEmpty()
            val displayPreview = remember(preview) { protocolMetaDisplayText(preview) }
            Text(displayPreview, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TaskWindow(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // 固定窗口框架，只由屏幕与输入法约束尺寸，流式正文不能再改变外层测量。
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(0.90f).height(maxHeight).imePadding(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.common_close)) }
                }
                content()
            }
        }
    }
}

private enum class TaskFilter(val label: Int) {
    ALL(R.string.subagent_filter_all),
    ACTIVE(R.string.subagent_filter_active),
    COMPLETED(R.string.subagent_filter_completed),
    ATTENTION(R.string.subagent_filter_attention),
    ARCHIVED(R.string.subagent_filter_archived),
}

@Composable
private fun TaskManager(tasks: List<SubagentTask>, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    TaskWindow(stringResource(R.string.subagent_tasks), onDismiss) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.subagent_tasks_search)) })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TaskFilter.entries) { entry -> FilterChip(filter == entry, { filter = entry }, label = { Text(stringResource(entry.label)) }) }
        }
        val visible = tasks.filter { task ->
            (task.title.contains(query, true) || task.preset.name.contains(query, true) || task.path.contains(query, true)) && when (filter) {
                TaskFilter.ALL -> !task.archived
                TaskFilter.ACTIVE -> task.status.isActive && !task.archived
                TaskFilter.COMPLETED -> task.status == SubagentStatus.COMPLETED && !task.archived
                TaskFilter.ATTENTION -> task.status in setOf(SubagentStatus.FAILED, SubagentStatus.INTERRUPTED, SubagentStatus.CANCELLED) && !task.archived
                TaskFilter.ARCHIVED -> task.archived
            }
        }.sortedByDescending { it.createdAt }
        if (visible.isEmpty()) Text(stringResource(R.string.subagent_no_matching_tasks), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { task -> TaskCard(task, Modifier.fillMaxWidth(), SubagentTree.queuePosition(tasks, task.id)) { onOpen(task.id) } }
        }
    }
}

@Composable
private fun TaskDetail(task: SubagentTask, siblings: List<SubagentTask>, coordinator: SubagentCoordinator, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember(task.id) { mutableIntStateOf(0) }
    var text by remember(task.id, task.executionId) { mutableStateOf("") }
    var busy by remember(task.id) { mutableStateOf(false) }
    var error by remember(task.id) { mutableStateOf<String?>(null) }
    var confirmingStop by remember(task.id) { mutableStateOf(false) }
    var managing by remember(task.parentChatId) { mutableStateOf(false) }
    fun act(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); error = null } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { AppLogger.e("SubagentTaskPanel", "子代理操作失败", failure); error = failure.message ?: failure.javaClass.simpleName }
            finally { busy = false }
        }
    }
    TaskWindow(task.title, onDismiss = { if (!busy) onDismiss() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(task.path, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            SubagentStatusBadge(statusName(task.status), running = task.status == SubagentStatus.RUNNING,
                completed = task.status == SubagentStatus.COMPLETED,
                failed = task.status == SubagentStatus.FAILED || task.status == SubagentStatus.INTERRUPTED)
            Text(stringResource(R.string.subagent_attempt, task.attempt), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { managing = true }, enabled = !busy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AccountTree, stringResource(R.string.subagent_tasks), Modifier.size(18.dp))
            }
        }
        val navigation = siblings.filter { !it.archived || it.id == task.id }
        if (navigation.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(navigation, key = { it.id }) { sibling ->
                FilterChip(sibling.id == task.id, { if (!busy) onSelect(sibling.id) }, label = { Text(sibling.title, maxLines = 1, modifier = Modifier.widthIn(max = 180.dp), overflow = TextOverflow.Ellipsis) })
            }
        }
        val tabs = listOf(R.string.subagent_overview, R.string.subagent_process, R.string.subagent_conversation)
        TabRow(selectedTabIndex = tab) { tabs.forEachIndexed { index, label -> Tab(tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) }) } }
        key(task.id, tab) {
            if (tab == 2) SubagentConversation(task, Modifier.weight(1f).fillMaxWidth())
            else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> {
                        DetailText(stringResource(R.string.subagent_tree_path), task.path, markdown = false)
                        Text(stringResource(R.string.subagent_tree_depth, task.depth), style = MaterialTheme.typography.labelSmall)
                        if (task.status == SubagentStatus.QUEUED) Text(stringResource(R.string.subagent_queue_position, SubagentTree.queuePosition(siblings, task.id)), style = MaterialTheme.typography.bodySmall)
                        if (task.status == SubagentStatus.WAITING) Text(stringResource(R.string.subagent_waiting_targets, if (task.waitingFor.isEmpty()) stringResource(R.string.subagent_waiting_inbox) else task.waitingFor.mapNotNull { id -> siblings.find { it.id == id }?.path }.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                        DetailText(stringResource(R.string.subagent_task_goal), task.goal)
                        DetailText(stringResource(R.string.subagent_task_model), task.model.modelName, markdown = false)
                        DetailText(stringResource(R.string.subagent_task_preset), task.preset.name, markdown = false)
                        val inherited = when (task.forkTurns) {
                            "none" -> stringResource(R.string.subagent_context_none)
                            "all" -> stringResource(R.string.subagent_context_all)
                            else -> stringResource(R.string.subagent_context_turns, task.forkTurns.toInt())
                        }
                        DetailText(stringResource(R.string.subagent_context_label), inherited, markdown = false)
                        Text(stringResource(R.string.subagent_execution_limit, task.executionTimeoutMinutes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (task.error.isNotBlank()) Text(task.error, color = MaterialTheme.colorScheme.error)
                        if (task.reports.isEmpty()) Text(stringResource(R.string.subagent_no_report), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        task.reports.asReversed().forEach { report ->
                            DetailText(stringResource(R.string.subagent_report) + " · " + statusName(report.status), report.content)
                            if (report.delivered) Text(stringResource(R.string.subagent_report_delivered), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    1 -> {
                        Text(stringResource(R.string.subagent_metrics, task.toolCount, ((if (task.completedAt > 0) task.completedAt else System.currentTimeMillis()) - (if (task.startedAt > 0) task.startedAt else task.createdAt)).coerceAtLeast(0).div(1000).toInt()), style = MaterialTheme.typography.labelMedium)
                        if (task.letters.isNotEmpty()) {
                            Text(stringResource(R.string.subagent_agent_messages), style = MaterialTheme.typography.labelLarge)
                            task.letters.forEach { letter ->
                                val sender = letter.senderTaskId?.let { id -> siblings.find { it.id == id }?.path } ?: stringResource(R.string.subagent_main_agent)
                                val recipient = letter.recipientTaskId?.let { id -> siblings.find { it.id == id }?.path } ?: stringResource(R.string.subagent_main_agent)
                                DetailText(sender + " → " + recipient + " · " + stringResource(if (letter.consumed) R.string.subagent_read_message else R.string.subagent_pending_message), letter.content)
                            }
                        }
                        val toolLabels = remember(task.events, task.messages, task.currentOutput) {
                            subagentToolEventLabels(task.events.mapNotNull { it.toolName },
                                task.messages.filter { it.role == "assistant" }.map { it.content } + task.currentOutput)
                        }
                        var toolIndex = 0
                        task.events.forEach { event ->
                            val label = if (event.toolName == null) event.text else toolLabels[toolIndex++]
                            Text("• $label", style = MaterialTheme.typography.bodySmall)
                        }
                        if (task.currentOutput.isNotBlank()) DetailText(stringResource(R.string.subagent_status_running), task.currentOutput, tools = true, running = task.status.isActive)
                    }
                }
            }
        }
        HorizontalDivider()
        error?.let { Text(stringResource(R.string.subagent_action_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!task.archived) {
            OutlinedTextField(text, { if (it.length <= 10000) text = it }, Modifier.fillMaxWidth(), maxLines = 4, label = { Text(stringResource(if (task.status.isActive) R.string.subagent_supplement else R.string.subagent_followup)) }, supportingText = { Text(stringResource(R.string.subagent_supplement_hint)) }, enabled = !busy)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (task.status.isActive) TextButton(onClick = { confirmingStop = true }, enabled = !busy) { Text(stringResource(R.string.subagent_stop), color = MaterialTheme.colorScheme.error) }
                else TextButton(onClick = { act { coordinator.archive(task.rootChatId, task.id, task.executionId, true); onDismiss() } }, enabled = !busy) { Text(stringResource(R.string.subagent_archive)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { act {
                    if (task.status.isActive) coordinator.supplement(task.rootChatId, task.id, task.executionId, text)
                    else coordinator.followup(task.rootChatId, task.id, task.executionId, text)
                    text = ""
                } }, enabled = !busy && text.isNotBlank()) { Text(stringResource(if (task.status.isActive) R.string.subagent_supplement else R.string.subagent_continue)) }
            }
        } else TextButton(onClick = { act { coordinator.archive(task.rootChatId, task.id, task.executionId, false) } }, enabled = !busy) { Text(stringResource(R.string.subagent_restore)) }
    }
    if (managing) TaskManager(siblings, onDismiss = { managing = false }, onOpen = { id -> managing = false; onSelect(id) })
    if (confirmingStop) AlertDialog(onDismissRequest = { confirmingStop = false }, title = { Text(stringResource(R.string.subagent_stop)) }, text = { Text(stringResource(R.string.subagent_stop_confirm)) }, confirmButton = {
        TextButton(onClick = { confirmingStop = false; act { coordinator.stop(task.rootChatId, task.id, task.executionId) } }) { Text(stringResource(R.string.subagent_stop)) }
    }, dismissButton = { TextButton(onClick = { confirmingStop = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun DetailText(label: String, body: String, markdown: Boolean = true, enableDialogs: Boolean = true, tools: Boolean = false, running: Boolean = false) {
    // Markdown 入口不负责协议投影，先复用主聊天过滤器，原始消息继续留给保存与请求链路。
    val displayBody = remember(body) { protocolMetaDisplayText(body) }
    if (displayBody.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (tools) SubagentTranscriptContent(body, running)
        else SelectionContainer {
            if (markdown) {
                // 详情可能从时间线卡片打开，不能沿用外层消息的 Markdown 渲染片段。
                CompositionLocalProvider(LocalMarkdownRenderFragment provides null) {
                    MarkdownTextComposable(displayBody, MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodySmall.fontSize, enableDialogs = enableDialogs)
                }
            } else Text(displayBody, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 主聊天以紧凑报告卡标注来源，详细过程继续在任务详情里阅读。 */
@Composable
fun SubagentReportMessage(content: String, enableDialogs: Boolean = true) {
    val context = LocalContext.current
    val coordinator = remember { SubagentCoordinator.getInstance(context) }
    val state by coordinator.state.collectAsState()
    val task = state.tasks.find { candidate -> candidate.reports.any { content.startsWith(it.marker) } }
    var open by remember(content) { mutableStateOf(false) }
    var selectedId by remember(content) { mutableStateOf<String?>(null) }
    if (task == null) {
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) { Column(Modifier.padding(12.dp)) { DetailText(stringResource(if (content.startsWith("[Agent message ")) R.string.subagent_agent_messages else R.string.subagent_report), content, enableDialogs = enableDialogs) } }
    } else {
        val report = requireNotNull(task.reports.find { content.startsWith(it.marker) })
        val card = task.copy(status = report.status, model = task.model.copy(modelName = report.modelName), reports = listOf(report), attempt = task.reports.indexOf(report) + 1)
        TaskCard(card, Modifier.fillMaxWidth().padding(vertical = 4.dp)) { if (enableDialogs) { selectedId = task.id; open = true } }
        if (open) state.tasks.find { it.id == selectedId }?.let { selected ->
            TaskDetail(selected, state.tasks.filter { it.rootChatId == task.rootChatId }, coordinator, onSelect = { selectedId = it }, onDismiss = { open = false })
        }
    }
}
