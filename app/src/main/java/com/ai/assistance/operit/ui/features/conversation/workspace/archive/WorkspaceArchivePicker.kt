package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.*
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import kotlinx.coroutines.CancellationException

/** 列表偏好与工作区一致，只有读取成功的路径才成为当前位置。 */
@Composable
internal fun WorkspaceArchivePicker(
    initial: WorkspaceLocation,
    title: String,
    multiple: Boolean = false,
    onDismiss: () -> Unit,
    onPick: (WorkspaceLocation, List<FileBrowserClipboardItem>) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { WorkspaceSearchRepository(context.applicationContext) }
    val navigation = remember(repository, initial) { WorkspaceDirectoryNavigation(initial) { location ->
        repository.beginSearch()
        repository.children(location)
    } }
    val snapshot by navigation.snapshot.collectAsState()
    val location = snapshot.location
    val settings by remember(context) { WorkspaceBrowserPreferences(context) }.settings.collectAsState(initial = WorkspaceBrowserSettings())
    val linuxInstalled = rememberWorkspaceLinuxInstalled()
    val bookmarks by remember(context) { ApiPreferences.getInstance(context) }.safBookmarksFlow.collectAsState(initial = emptyList())
    var requested by remember { mutableStateOf(initial) }
    var selected by remember { mutableStateOf(emptyMap<String, WorkspaceSearchEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editPath by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var pickRequested by remember { mutableStateOf(false) }
    val visible = remember(snapshot.entries, settings, multiple) {
        snapshot.entries.filter { (multiple || it.directory) && !isWorkspaceEntryHidden(it.location, it.name, settings) }
            .sortedWith(compareBy<WorkspaceSearchEntry> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    fun navigate(target: WorkspaceLocation, pick: Boolean = false) {
        requested = target
        pickRequested = pick
        revision++
    }
    LaunchedEffect(revision, linuxInstalled) {
        val request = revision
        val target = requested
        val picking = pickRequested
        loading = true
        error = null
        try {
            check(workspaceEnvironment(target.environment) != "linux" || linuxInstalled) { context.getString(R.string.workspace_browser_linux_unavailable) }
            if (navigation.open(target) && picking) {
                onPick(target, selected.values.map { FileBrowserClipboardItem(it.location.path, it.location.environment, false, it.directory) })
            }
        } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
            AppLogger.e("WorkspaceArchive", "读取选择器目标目录失败：${target.environment}: ${target.path}", failure)
            error = context.getString(R.string.workspace_browser_directory_unreadable, "${target.environment}: ${target.path}")
        } finally { if (request == revision) loading = false }
    }
    fun toggle(entry: WorkspaceSearchEntry) {
        selected = if (entry.location.key in selected) selected - entry.location.key else selected + (entry.location.key to entry)
    }
    fun back() {
        if (loading && requested != location && snapshot.readable) navigate(location)
        else workspaceParent(location)?.let { navigate(it) } ?: onDismiss()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler(onBack = ::back)
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { navigate(location) }, enabled = !loading) { Icon(Icons.Default.Refresh, stringResource(R.string.file_manager_refresh)) }
                }
                Text("${location.environment}: ${location.path}", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable { editPath = true }.padding(16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { navigate(WorkspaceLocation(Environment.getExternalStorageDirectory().absolutePath, "android")) }, label = { Text("Android") })
                    if (linuxInstalled) AssistChip(onClick = { navigate(WorkspaceLocation("/", "linux")) }, label = { Text("Linux") })
                    bookmarks.forEach { bookmark ->
                        AssistChip(onClick = { navigate(WorkspaceLocation("/", "repo:${bookmark.name}")) }, label = { Text(bookmark.name) })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                key(location.key) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        if (snapshot.readable) workspaceParent(location)?.let { parent -> item {
                            WorkspaceFileRow("..", Icons.Default.FolderOpen, true, onClick = { navigate(parent) }, enabled = !loading)
                        } }
                        if (!loading && snapshot.readable && visible.isEmpty()) item { Text(stringResource(R.string.file_manager_empty_folder), Modifier.padding(24.dp)) }
                        items(visible, key = { it.location.key }) { entry ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = !loading && !entry.symbolicLink) {
                                if (entry.directory) navigate(entry.location) else toggle(entry)
                            }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                WorkspaceFileIcon(entry.location, entry.directory, "${entry.size}:${entry.modified}", entry.symbolicLink)
                                Text(entry.name, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (multiple) Checkbox(entry.location.key in selected, { toggle(entry) }, enabled = !loading && !entry.symbolicLink)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    if (multiple) Text(stringResource(R.string.workspace_selection_count, selected.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { navigate(location, pick = true) }, enabled = !loading && snapshot.readable && (!multiple || selected.isNotEmpty())) {
                        Text(stringResource(if (multiple) R.string.workspace_archive_add else R.string.workspace_archive_choose_directory))
                    }
                }
            }
        }
        if (editPath) WorkspacePathDialog(location, { editPath = false }) { editPath = false; navigate(it) }
    }
}
