package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 条件区和结果共用一个滚动容器，小屏不会被高级条件挤掉结果。 */
@Composable
internal fun WorkspaceSearchDialog(
    root: WorkspaceLocation,
    settings: WorkspaceBrowserSettings,
    onDismiss: () -> Unit,
    selected: List<WorkspaceSearchEntry> = emptyList(),
    onOpen: (WorkspaceSearchHit) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dialogWidth = (configuration.screenWidthDp.dp * 0.88f).coerceAtMost(420.dp)
    val dialogHeight = (configuration.screenHeightDp.dp * 0.72f).coerceAtMost(560.dp)
    val scope = rememberCoroutineScope()
    val repository = remember(context) { WorkspaceSearchRepository(context.applicationContext) }
    var name by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(WorkspaceMatchMode.TEXT) }
    var type by rememberSaveable { mutableStateOf(WorkspaceSearchType.ALL) }
    var caseSensitive by rememberSaveable { mutableStateOf(false) }
    var recursive by rememberSaveable { mutableStateOf(true) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var contentRegex by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var minimum by rememberSaveable { mutableStateOf("") }
    var maximum by rememberSaveable { mutableStateOf("") }
    var after by rememberSaveable { mutableStateOf("") }
    var before by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableStateOf(WorkspaceSearchProgress()) }
    var running by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var revision by remember { mutableIntStateOf(0) }

    fun size(value: String): Long? = value.trim().takeIf(String::isNotEmpty)?.let {
        Math.multiplyExact(it.toLong().also { number -> require(number >= 0) }, 1024L)
    }
    fun date(value: String, end: Boolean): Long? = value.trim().takeIf(String::isNotEmpty)?.let {
        val day = LocalDate.parse(it)
        (if (end) day.plusDays(1) else day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    fun start() {
        val options = try {
            WorkspaceSearchOptions(name, content, mode, contentRegex, !caseSensitive, recursive, hidden, type,
                size(minimum), size(maximum), date(after, false), date(before, true)).also {
                it.validate()
                workspaceNamePattern(it.name, it.mode, it.ignoreCase)
                if (it.contentRegex) workspaceNamePattern(it.content, WorkspaceMatchMode.REGEX, it.ignoreCase)
            }
        } catch (failure: IllegalArgumentException) {
            error = context.getString(R.string.workspace_browser_invalid_conditions, failure.localizedMessage.orEmpty())
            return
        } catch (failure: java.time.DateTimeException) {
            error = context.getString(R.string.workspace_browser_date_error)
            return
        } catch (failure: ArithmeticException) {
            error = context.getString(R.string.workspace_browser_invalid_conditions, failure.localizedMessage.orEmpty())
            return
        }
        val request = ++revision
        val previousJob = job
        previousJob?.cancel()
        error = null
        stopped = false
        progress = WorkspaceSearchProgress()
        running = true
        job = scope.launch {
            try {
                previousJob?.join()
                withContext(Dispatchers.IO) {
                    repository.beginSearch()
                    WorkspaceSearchEngine(if (selected.isEmpty()) repository else WorkspaceSelectedSearchSource(repository, root, selected)).search(root, options, settings) { update ->
                        withContext(Dispatchers.Main) { if (request == revision) progress = update }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                if (request == revision) error = failure.localizedMessage ?: failure.javaClass.simpleName
            } finally {
                if (request == revision) running = false
            }
        }
    }
    fun close() { job?.cancel(); onDismiss() }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.width(dialogWidth).heightIn(max = dialogHeight), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.workspace_browser_search), style = MaterialTheme.typography.titleLarge)
                Text("${root.environment}: ${root.path}", maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = !running,
                            label = { Text(stringResource(R.string.workspace_browser_search_name)) }, singleLine = true)
                    }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WorkspaceMatchMode.entries.forEach { option ->
                                val label = when (option) {
                                    WorkspaceMatchMode.TEXT -> R.string.workspace_browser_match_text
                                    WorkspaceMatchMode.GLOB -> R.string.workspace_browser_match_glob
                                    WorkspaceMatchMode.REGEX -> R.string.workspace_browser_match_regex
                                }
                                FilterChip(mode == option, { mode = option }, enabled = !running,
                                    label = { Text(stringResource(label)) })
                            }
                        }
                    }
                    item {
                        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), enabled = !running,
                            label = { Text(stringResource(R.string.workspace_browser_search_content)) }, singleLine = true)
                    }
                    item {
                        WorkspaceSearchCheck(R.string.workspace_browser_recursive, recursive, !running) { recursive = it }
                        if (content.isNotEmpty()) WorkspaceSearchCheck(R.string.workspace_browser_content_regex,
                            contentRegex, !running) { contentRegex = it }
                        TextButton(onClick = { advanced = !advanced }) {
                            Text(stringResource(R.string.workspace_browser_advanced))
                        }
                    }
                    if (advanced) {
                        item {
                            WorkspaceSearchCheck(R.string.workspace_browser_case_sensitive, caseSensitive, !running) { caseSensitive = it }
                            WorkspaceSearchCheck(R.string.workspace_browser_include_hidden, hidden, !running) { hidden = it }
                        }
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                WorkspaceSearchType.entries.forEach { option ->
                                    val label = when (option) {
                                        WorkspaceSearchType.ALL -> R.string.workspace_browser_type_all
                                        WorkspaceSearchType.FILES -> R.string.workspace_browser_type_files
                                        WorkspaceSearchType.DIRECTORIES -> R.string.workspace_browser_type_directories
                                    }
                                    FilterChip(type == option, { type = option }, enabled = !running,
                                        label = { Text(stringResource(label)) })
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(minimum, { minimum = it }, Modifier.weight(1f), enabled = !running,
                                    label = { Text(stringResource(R.string.workspace_browser_min_size)) }, singleLine = true)
                                OutlinedTextField(maximum, { maximum = it }, Modifier.weight(1f), enabled = !running,
                                    label = { Text(stringResource(R.string.workspace_browser_max_size)) }, singleLine = true)
                            }
                        }
                        item {
                            OutlinedTextField(after, { after = it }, Modifier.fillMaxWidth(), enabled = !running,
                                label = { Text(stringResource(R.string.workspace_browser_modified_from)) },
                                placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
                            OutlinedTextField(before, { before = it }, Modifier.fillMaxWidth(), enabled = !running,
                                label = { Text(stringResource(R.string.workspace_browser_modified_to)) },
                                placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
                        }
                    }
                    item {
                        Text(stringResource(R.string.workspace_browser_search_progress, progress.hits.size, progress.visited),
                            style = MaterialTheme.typography.titleSmall)
                        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (stopped) Text(stringResource(R.string.workspace_browser_search_stopped))
                        if (progress.unreadable > 0 || progress.skippedContents > 0) Text(
                            stringResource(R.string.workspace_browser_search_skipped, progress.unreadable, progress.skippedContents),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (progress.limited) Text(stringResource(R.string.workspace_browser_result_limit), color = MaterialTheme.colorScheme.primary)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    items(progress.hits, key = { it.entry.location.key }) { hit ->
                        Row(Modifier.fillMaxWidth().clickable { job?.cancel(); onOpen(hit) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WorkspaceFileIcon(hit.entry.location, hit.entry.directory, "${hit.entry.size}:${hit.entry.modified}", hit.entry.symbolicLink)
                            Column(Modifier.weight(1f)) {
                                Text(hit.entry.name, style = MaterialTheme.typography.bodyLarge)
                                Text(hit.entry.location.path, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                hit.content?.let { Text("${it.line}: ${it.snippet}", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::close) { Text(stringResource(R.string.close)) }
                    Button(onClick = {
                        if (running) { stopped = true; job?.cancel() } else start()
                    }) { Text(stringResource(if (running) R.string.workspace_browser_stop_search else R.string.workspace_browser_search)) }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSearchCheck(label: Int, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(value, onChange, enabled = enabled)
        Text(stringResource(label))
    }
}