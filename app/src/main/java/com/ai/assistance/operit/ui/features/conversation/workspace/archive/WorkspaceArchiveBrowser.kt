package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.*
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** APK 先展示应用信息，用户选择查看后才打开归档内容。 */
@Composable
internal fun WorkspaceArchiveBrowser(
    source: WorkspaceLocation,
    onDismiss: () -> Unit,
    onOpen: (WorkspaceLocation) -> Unit,
    readOnly: Boolean = false,
) {
    var apkInfo by rememberSaveable(source.key) { mutableStateOf(source.path.substringAfterLast('.').equals("apk", true)) }
    if (apkInfo) WorkspaceApkInfoDialog(source, onDismiss, onBrowse = { apkInfo = false })
    else WorkspaceArchiveContents(source, onDismiss, onOpen, readOnly)
}

/** 归档列表共享隐藏偏好，顶部添加、下拉刷新、底部解压，解密只在读取需要时请求。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceArchiveContents(
    source: WorkspaceLocation,
    onDismiss: () -> Unit,
    onOpen: (WorkspaceLocation) -> Unit,
    readOnly: Boolean,
) {
    val context = LocalContext.current
    val store = remember(source.key) { WorkspaceArchiveStore(context) }
    val operations = remember(context) { FileBrowserOperations(context) }
    val progress by store.progress.collectAsState()
    val settings by remember(context) { WorkspaceBrowserPreferences(context) }.settings.collectAsState(initial = WorkspaceBrowserSettings())
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<WorkspaceArchiveSession?>(null) }
    var directory by rememberSaveable(source.key) { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(suspend (WorkspaceArchiveSession) -> Unit)?>(null) }
    var pendingOpening by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var openingFile by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(busy, openingFile) {
        showProgress = false
        if (busy) {
            // 小文件读取直接完成；确实需要等待时再展示可取消的真实进度。
            if (openingFile) delay(250)
            showProgress = true
        }
    }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable(directory) { mutableStateOf(false) }
    var selected by rememberSaveable(directory) { mutableStateOf(emptySet<String>()) }
    var anchor by rememberSaveable(directory) { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<Set<String>?>(null) }
    var properties by remember { mutableStateOf<Set<String>?>(null) }
    var deleting by remember { mutableStateOf<Set<String>?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf<String?>(null) }
    var extracting by remember { mutableStateOf(emptySet<String>()) }
    var adding by remember { mutableStateOf<List<FileBrowserClipboardItem>?>(null) }
    var nested by rememberSaveable(source.key) { mutableStateOf<String?>(null) }
    val canEdit = session?.kind == WorkspaceArchiveKind.ZIP && !readOnly
    val signedContainer = source.path.substringAfterLast('.').lowercase() in setOf("apk", "aab", "jar", "xapk", "apks", "apkm")
    val entries = session?.entries.orEmpty()
    val visible = remember(entries, directory, source, settings) { workspaceArchiveVisibleChildren(entries, directory, source, settings) }
    fun finishSelection() { selectionMode = false; selected = emptySet(); anchor = null }
    fun back() {
        when {
            dragging -> Unit
            busy -> job?.cancel()
            selectionMode -> finishSelection()
            directory.isNotEmpty() -> directory = directory.substringBeforeLast('/', "")
            else -> onDismiss()
        }
    }
    fun run(opening: Boolean = false, block: suspend (WorkspaceArchiveSession) -> Unit) {
        val current = session ?: return
        if (busy || loading) return
        busy = true
        openingFile = opening
        error = null
        job = scope.launch {
            try { block(current) } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
                AppLogger.e("WorkspaceArchive", "归档操作失败", failure)
                if (workspaceArchiveNeedsPassword(failure)) {
                    pendingAction = block
                    pendingOpening = opening
                    passwordInput = password
                    passwordError = if (password.isEmpty()) null else failure.localizedMessage
                    passwordDialog = true
                    error = context.getString(R.string.workspace_archive_password_required)
                } else error = failure.localizedMessage ?: failure.javaClass.simpleName
            } finally { busy = false; job = null }
        }
    }
    LaunchedEffect(source, password, revision) {
        val request = revision
        loading = true
        error = null
        var opened: WorkspaceArchiveSession? = null
        try {
            opened = store.open(source, password)
            session = opened
            loading = false
            refreshing = false
            val action = pendingAction
            pendingAction = null
            if (action != null) run(opening = pendingOpening, block = action)
            awaitCancellation()
        } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
            AppLogger.e("WorkspaceArchive", "打开归档失败", failure)
            if (workspaceArchiveNeedsPassword(failure)) {
                passwordInput = password
                passwordError = if (password.isEmpty()) null else failure.localizedMessage
                passwordDialog = true
                error = context.getString(R.string.workspace_archive_password_required)
            } else error = failure.localizedMessage ?: failure.javaClass.simpleName
        } finally {
            if (request == revision) { loading = false; refreshing = false }
            if (session === opened) session = null
            opened?.let { store.close(it) }
        }
    }
    LaunchedEffect(visible, loading) {
        if (!loading && session != null) {
            val paths = visible.map { it.path }.toSet()
            selected = selected.intersect(paths)
            if (anchor !in paths) anchor = null
        }
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler(onBack = ::back)
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::back) { Icon(Icons.Default.ArrowBack, stringResource(R.string.file_manager_return)) }
                    Text(if (directory.isEmpty()) source.path.substringAfterLast('/') else directory.substringAfterLast('/'), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (canEdit) IconButton(onClick = { picker = "add" }, enabled = !busy && !loading) {
                        Icon(Icons.Default.Add, stringResource(R.string.workspace_archive_add))
                    }
                }
                Text(source.path.substringAfterLast('/') + "!/" + directory, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (session != null && !canEdit) Text(stringResource(R.string.workspace_archive_read_only),
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loading && !refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    TextButton(onClick = { revision++ }, enabled = !busy && !loading) { Text(stringResource(R.string.workspace_archive_retry)) }
                }
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = {
                    if (!busy && !loading && !dragging) { finishSelection(); refreshing = true; revision++ }
                }, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    key(directory) {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                            if (directory.isNotEmpty()) item { WorkspaceFileRow("..", Icons.Default.FolderOpen, true,
                                onClick = { directory = directory.substringBeforeLast('/', "") }, enabled = !busy && !loading) }
                            if (!loading && session != null && visible.isEmpty()) item { Text(stringResource(R.string.file_manager_empty_folder), Modifier.padding(24.dp)) }
                            items(visible, key = { it.path }) { entry ->
                                val currentSession = session
                                WorkspaceFileRow(entry.name, if (entry.directory) Icons.Default.Folder else Icons.Default.Description,
                                    entry.directory,
                                    leading = { WorkspaceFileIcon(WorkspaceLocation(source.path + "!/" + entry.path, source.environment),
                                        entry.directory, "${currentSession?.directory?.name}:${entry.size}:${entry.modified}",
                                        symbolicLink = entry.symbolicLink,
                                        thumbnails = !busy && !loading && currentSession != null && currentSession.kind == WorkspaceArchiveKind.ZIP &&
                                            (!entry.encrypted || currentSession.password.isNotEmpty()),
                                        archiveFile = if (currentSession == null) null else ({ store.openEntry(currentSession, entry, reportProgress = false) })) },
                                    detail = if (entry.directory) "" else entry.size?.let { Formatter.formatShortFileSize(context, it) }.orEmpty(),
                                    selection = if (selectionMode) entry.path in selected else null, enabled = !busy && !loading,
                                    onClick = {
                                        when {
                                            selectionMode -> selected = if (entry.path in selected) selected - entry.path else selected + entry.path
                                            entry.directory -> directory = entry.path
                                            entry.symbolicLink -> error = context.getString(R.string.workspace_archive_link)
                                            else -> run(opening = true) { current ->
                                                val file = store.openEntry(current, entry)
                                                if (workspaceArchiveKind(file.path) != null) nested = file.path
                                                else onOpen(WorkspaceLocation(file.path, "android"))
                                            }
                                        }
                                    },
                                    onLongPress = {
                                        actions = if (selectionMode) (selected + entry.path).also { selected = it } else setOf(entry.path)
                                    },
                                    onSwipeSelect = {
                                        val range = selectWorkspaceRange(visible.map { it.path }, selected, anchor.takeIf { selectionMode }, entry.path)
                                        selected = range.selected; anchor = range.anchor; selectionMode = true
                                    }, onSwipeActiveChanged = { dragging = it },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
                Surface(tonalElevation = 2.dp) {
                    OutlinedButton(onClick = { extracting = if (selectionMode) selected else emptySet(); picker = "extract" },
                        enabled = session != null && !busy && !loading && (!selectionMode || selected.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 52.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (selectionMode) stringResource(R.string.workspace_archive_extract_selected, selected.size)
                                else stringResource(R.string.workspace_archive_extract_all))
                            Text(source.path.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (busy && showProgress) WorkspaceArchiveOperationDialog(progress, onCancel = { job?.cancel() })
        actions?.let { targets ->
            val chosen = entries.filter { it.path in targets }
            val readable = chosen.size == targets.size && chosen.none { it.directory || it.symbolicLink }
            val single = chosen.singleOrNull()
            WorkspaceFileActionMenu(
                if (targets.size == 1) targets.single().substringAfterLast('/') else stringResource(R.string.workspace_selection_count, targets.size),
                onDismiss = { actions = null },
                actions = WorkspaceFileActions(
                    extract = true,
                    archive = { actions = null; extracting = targets; picker = "extract" },
                    properties = { actions = null; properties = targets },
                    rename = if (canEdit && targets.size == 1) ({
                        actions = null; renaming = targets.single(); newName = targets.single().substringAfterLast('/')
                    }) else null,
                    delete = if (canEdit) ({ actions = null; deleting = targets }) else null,
                    openWith = if (readable && single != null) ({
                        actions = null
                        run(opening = true) { current ->
                            val file = store.openEntry(current, single)
                            operations.externalOpen(file.path, "android", false)
                        }
                    }) else null,
                    share = if (readable) ({
                        actions = null
                        run(opening = true) { current ->
                            val files = chosen.map { entry ->
                                FileBrowserClipboardItem(store.openEntry(current, entry).path, "android", false, false)
                            }
                            operations.share(files)
                        }
                    }) else null,
                ),
            )
        }
        properties?.let { targets -> WorkspaceArchiveEntryPropertiesDialog(source, entries, targets, onDismiss = { properties = null }) }
        deleting?.let { targets ->
            AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.file_manager_delete)) },
                text = { Column {
                    Text(stringResource(R.string.workspace_delete_selected_message, targets.size))
                    if (signedContainer) Text(stringResource(R.string.workspace_archive_signed))
                } }, confirmButton = { TextButton(onClick = { deleting = null; run { store.modify(it, remove = targets); finishSelection(); revision++ } }) {
                    Text(stringResource(R.string.file_manager_delete), color = MaterialTheme.colorScheme.error)
                } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
        }
        renaming?.let { path ->
            AlertDialog(onDismissRequest = { renaming = null }, title = { Text(stringResource(R.string.file_menu_rename)) },
                text = { Column {
                    OutlinedTextField(newName, { newName = it }, singleLine = true, isError = !isValidWorkspaceEntryName(newName))
                    if (signedContainer) Text(stringResource(R.string.workspace_archive_signed))
                } }, confirmButton = { TextButton(onClick = {
                    val name = newName
                    renaming = null
                    run { store.modify(it, rename = path to (path.substringBeforeLast('/', "").takeIf(String::isNotEmpty)?.plus("/").orEmpty() + name)); finishSelection(); revision++ }
                }, enabled = isValidWorkspaceEntryName(newName) && newName != path.substringAfterLast('/')) { Text(stringResource(R.string.save)) } },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } })
        }
        if (passwordDialog) WorkspaceArchivePasswordDialog(passwordInput, { passwordInput = it },
            onDismiss = { passwordDialog = false; pendingAction = null },
            onConfirm = { password = passwordInput; passwordDialog = false; revision++ }, error = passwordError)
        picker?.let { mode ->
            WorkspaceArchivePicker(WorkspaceLocation(source.path.substringBeforeLast('/', "").ifEmpty { "/" }, source.environment),
                stringResource(if (mode == "add") R.string.workspace_archive_add else R.string.workspace_archive_destination), multiple = mode == "add",
                onDismiss = { picker = null }) { destination, targets ->
                picker = null
                if (mode == "add") adding = targets
                else { val targetsToExtract = extracting; run { store.extract(it, targetsToExtract, destination) } }
            }
        }
        adding?.let { targets ->
            AlertDialog(onDismissRequest = { adding = null }, title = { Text(stringResource(R.string.workspace_archive_add)) },
                text = { Column { Text(stringResource(R.string.workspace_archive_entries, targets.size)); if (signedContainer) Text(stringResource(R.string.workspace_archive_signed)) } },
                confirmButton = { TextButton(onClick = { adding = null; run { store.modify(it, additions = targets, directory = directory); revision++ } }) { Text(stringResource(R.string.workspace_archive_add)) } },
                dismissButton = { TextButton(onClick = { adding = null }) { Text(stringResource(R.string.cancel)) } })
        }
        nested?.let { path -> WorkspaceArchiveBrowser(WorkspaceLocation(path, "android"), { nested = null }, onOpen, readOnly = true) }
    }
}