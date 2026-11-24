// SPDX-License-Identifier: AGPL-3.0-only
package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.git.GitReview
import com.ai.assistance.operit.ui.features.conversation.workspace.git.WorkspaceGitRepository
import com.ai.assistance.operit.util.logging.AppLogger
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.features.conversation.workspace.links.workspaceFileOpenMessage
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.workspaceFileTypeIcon

/** 文件树与详情在手机和宽屏上共用同一布局，隐藏文件区时保留文件选择。 */
@Composable
internal fun WorkspaceChangesScreen(
    chatId: String?, directory: String?, environment: String?, conversation: Boolean, isVisible: Boolean,
    onOpenFile: suspend (ChangeLocation, ChangeOpenAction) -> Unit,
    userMessages: List<Long> = emptyList(),
    onPreviewFile: suspend (ChangeLocation) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { WorkspaceChangeStore.get(context) }
    val workspaceObservationErrors by remember(context) { WorkspaceFileMonitor.get(context).errors }.collectAsState()
    val terminalObservationErrors by remember(context) { TerminalFileJournal.get(context).errors }.collectAsState()
    val observationErrors = workspaceObservationErrors + terminalObservationErrors
    val scope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(isVisible, directory, environment, chatId) { onDispose { previewJob?.cancel(); previewError = null } }
    val revision by store.revision.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var source by rememberSaveable(directory, environment, conversation) {
        mutableStateOf(if (conversation) ChangeReviewSource.OPERATIONS else ChangeReviewSource.LAST_TURN)
    }
    var base by rememberSaveable(directory, environment) { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf(emptyList<ReviewFile>()) }
    var git by remember { mutableStateOf<GitReview?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable(directory, environment, conversation) { mutableStateOf<String?>(null) }
    val repository = remember(directory, environment, refresh) { directory?.let { WorkspaceGitRepository(context, it, environment) } }
    val historical = source == ChangeReviewSource.OPERATIONS || source == ChangeReviewSource.LAST_TURN
    // 历史记录携带快照版本，无关文件写入无需使已看过的差异和语法颜色失效。
    val cache = remember(chatId, directory, environment, source, base, if (historical) 0L else revision, refresh, isVisible) { WorkspaceReviewCache() }
    var query by rememberSaveable(directory, environment, conversation) { mutableStateOf("") }
    var historyOffset by rememberSaveable(chatId, directory, environment, source, query) { mutableIntStateOf(0) }
    var historyTotal by remember { mutableIntStateOf(0) }
    var loadedHistoryQuery by remember { mutableStateOf<WorkspaceHistoryQuery?>(null) }
    var treeSelection by remember { mutableStateOf<ReviewFile?>(null) }
    var selectLastOnLoad by remember { mutableStateOf(false) }
    val request = listOf(chatId, directory, environment, source, base, revision, refresh, userMessages, if (historical) query else null, if (historical) historyOffset else 0)
    var loadedRequest by remember { mutableStateOf<List<Any?>?>(null) }
    LaunchedEffect(isVisible, request) {
        // 外部清理不会递增记录版本，重新打开时必须核对快照并丢弃已缓存的差异。
        if (!isVisible) {
            loadedRequest = null
            return@LaunchedEffect
        }
        if (request == loadedRequest) return@LaunchedEffect
        loading = true
        error = null
        if (source == ChangeReviewSource.BRANCH || source == ChangeReviewSource.WORKING_TREE) git = null
        try {
            val loadedFiles = if (source == ChangeReviewSource.OPERATIONS || source == ChangeReviewSource.LAST_TURN) {
                git = null
                withContext(Dispatchers.IO) {
                    val workspace = if (!conversation && directory != null) WorkspaceChangeAccess(context).location(directory, environment) else null
                    val history = if ((conversation || source == ChangeReviewSource.LAST_TURN) && chatId == null || !conversation && source != ChangeReviewSource.LAST_TURN && workspace == null) null else
                        store.historyQuery(if (conversation || source == ChangeReviewSource.LAST_TURN) chatId else null, workspace,
                            source == ChangeReviewSource.LAST_TURN, userMessages, query)
                    val page = history?.let { store.historyPage(it, historyOffset) } ?: WorkspaceHistoryPage(emptyList(), 0, 0)
                    Triple(page, history, page.files)
                }.let { (page, history, result) ->
                    historyTotal = page.total
                    historyOffset = page.offset
                    loadedHistoryQuery = history
                    result
                }
            } else {
                val review = checkNotNull(repository).load(source == ChangeReviewSource.BRANCH, base)
                git = review
                review.missingBase?.let { error = context.getString(R.string.workspace_changes_base_deleted, it.removePrefix("refs/heads/").removePrefix("refs/remotes/")) }
                if (source == ChangeReviewSource.BRANCH && base == null && review.upstream != null && review.references.any { it.name == review.upstream }) base = review.upstream
                review.files
            }
            val previousTree = treeSelection?.takeIf { it.id == selectedId }
            treeSelection = if (historical && previousTree != null && loadedHistoryQuery != null) withContext(Dispatchers.IO) {
                store.historyPage(checkNotNull(loadedHistoryQuery).copy(file = previousTree.location, search = ""), 0).files.singleOrNull()
            } else null
            files = loadedFiles
            if (treeSelection == null && loadedFiles.none { it.id == selectedId }) selectedId = (if (selectLastOnLoad) loadedFiles.lastOrNull() else loadedFiles.firstOrNull())?.id
            selectLastOnLoad = false
            loadedRequest = request
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WorkspaceChanges", "读取变更目录失败", failure)
            files = emptyList()
            selectedId = null
            error = failure.message ?: failure.javaClass.simpleName
        } finally { loading = false }
    }
    var projectTree by rememberSaveable { mutableStateOf(false) }
    var showFiles by rememberSaveable(directory, environment, conversation) { mutableStateOf(true) }
    var sidebarWidth by rememberSaveable { mutableFloatStateOf(0f) }
    var collapsed by remember(directory, environment, conversation) { mutableStateOf(emptySet<String>()) }
    fun selectFile(file: ReviewFile) {
        treeSelection = null
        selectedId = file.id
        val parts = file.displayPath.trimStart('/').split('/')
        val parents = (1 until parts.size).map { "directory:" + file.location.backend + ":" + parts.take(it).joinToString("/") }.toSet()
        collapsed = collapsed - parents
    }
    val fileListState = rememberLazyListState()
    val fileStates = key(chatId, directory, environment, conversation) { rememberSaveableStateHolder() }
    // 淘汰顺序随状态一同恢复，界面重建后仍能清理此前保存的文件详情。
    val savedFileKeys = rememberSaveable(chatId, directory, environment, conversation) { arrayListOf<String>() }
    LaunchedEffect(selectedId, source, base) {
        selectedId?.let { id ->
            val key = source.name + ":" + base.orEmpty() + ":" + id
            savedFileKeys.remove(key)
            savedFileKeys.add(key)
            while (savedFileKeys.size > 12) {
                val oldest = savedFileKeys.first()
                savedFileKeys.remove(oldest)
                fileStates.removeState(oldest)
            }
        }
    }
    val options = rememberSaveable(saver = WorkspaceDiffOptions.Saver) { WorkspaceDiffOptions() }
    val filtered = remember(files, query, historical) { if (historical) files else files.filter { it.displayPath.contains(query.trim(), ignoreCase = true) } }
    val selected = treeSelection ?: filtered.firstOrNull { it.id == selectedId } ?: filtered.firstOrNull()
    val currentIndex = filtered.indexOfFirst { it.id == selected?.id }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val resizeLabel = stringResource(R.string.workspace_changes_resize_files)
    BackHandler(enabled = isVisible && !showFiles) { showFiles = true }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
        // 捕获约束供拖动回调使用，不能在回调中隐式访问布局接收者。
        val availableWidth = maxWidth.value
        val maximumPanelWidth = minOf(500f, (availableWidth - 160f).coerceAtLeast(0f))
        val minimumPanelWidth = minOf(110f, maximumPanelWidth)
        val desiredWidth = if (sidebarWidth > 0f) sidebarWidth else (availableWidth * 0.36f).coerceAtMost(260f)
        val panelWidth = desiredWidth.coerceIn(minimumPanelWidth, maximumPanelWidth).dp
        Row(Modifier.fillMaxSize()) {
            if (showFiles) {
                Column(Modifier.width(panelWidth).fillMaxHeight().background(colors.surfaceContainerLow.copy(alpha = 0.35f))) {
                    Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        ChangeIconButton(Icons.Default.AccountTree,
                            stringResource(if (projectTree) R.string.workspace_changes_files else R.string.workspace_changes_project_tree),
                            { projectTree = !projectTree }, selected = projectTree, enabled = directory != null)
                        var sourcesOpen by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { sourcesOpen = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
                                Text(sourceLabel(source), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp))
                            }
                            DropdownMenu(expanded = sourcesOpen, onDismissRequest = { sourcesOpen = false }) {
                                git?.currentBranch?.let { current ->
                                    DropdownMenuItem(text = { Text(current.removePrefix("refs/heads/"), fontSize = 10.sp) }, enabled = false, onClick = {})
                                    HorizontalDivider()
                                }
                                listOf(ChangeReviewSource.WORKING_TREE, ChangeReviewSource.BRANCH, ChangeReviewSource.LAST_TURN, ChangeReviewSource.OPERATIONS).forEach { option ->
                                    DropdownMenuItem(text = { Text(sourceLabel(option), fontSize = 12.sp, fontWeight = FontWeight.Normal) },
                                        enabled = directory != null || option == ChangeReviewSource.OPERATIONS || option == ChangeReviewSource.LAST_TURN,
                                        trailingIcon = { if (source == option) Icon(Icons.Default.Check, null, Modifier.size(14.dp)) },
                                        onClick = { source = option; selectedId = null; files = emptyList(); git = null; sourcesOpen = false })
                                }
                            }
                        }
                        }
                        Spacer(Modifier.width(4.dp))
                        ChangeDiffStats(files.sumOf { it.additions }, files.sumOf { it.deletions }, known = (!historical || historyTotal <= files.size) && files.all { it.statisticsAvailable })
                    }
                    if (source == ChangeReviewSource.BRANCH) {
                        var basesOpen by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { basesOpen = true }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Text(base?.removePrefix("refs/heads/")?.removePrefix("refs/remotes/") ?: stringResource(R.string.workspace_changes_choose_base), maxLines = 1, fontSize = 10.sp)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp))
                            }
                            DropdownMenu(expanded = basesOpen, onDismissRequest = { basesOpen = false }) {
                                git?.references?.forEach { ref ->
                                    DropdownMenuItem(text = { Text(ref.name.removePrefix("refs/heads/").removePrefix("refs/remotes/"), fontSize = 12.sp) },
                                        onClick = { base = ref.name; basesOpen = false; selectedId = null })
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
                    ChangeFileFilter(query, { query = it })
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
                    if (historical && historyTotal > WorkspaceHistoryIndex.PAGE_SIZE) HistoryPageNavigation(
                        historyOffset, files.size, historyTotal, loading,
                        { selectLastOnLoad = true; historyOffset = (historyOffset - WorkspaceHistoryIndex.PAGE_SIZE).coerceAtLeast(0) },
                        { historyOffset += WorkspaceHistoryIndex.PAGE_SIZE })
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    if (projectTree && directory != null) ChangeProjectTree(directory, environment, query, selected?.id, refresh) { location ->
                        val target = if (historical) location.historyLocation() else location
                        val changed = files.firstOrNull { (if (historical) it.location.historyLocation() else it.location).key == target.key }
                        if (changed != null) { selectFile(changed); query = "" }
                        else {
                            previewJob?.cancel()
                            previewError = null
                            previewJob = scope.launch {
                                try {
                                    val historyFile = if (historical) loadedHistoryQuery?.let { history ->
                                        withContext(Dispatchers.IO) { store.historyPage(history.copy(file = location, search = ""), 0).files.singleOrNull() }
                                    } else null
                                    if (historyFile != null) { treeSelection = historyFile; selectedId = historyFile.id } else onPreviewFile(location)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    AppLogger.e("WorkspaceChanges", "打开项目树文件失败", failure)
                                    previewError = workspaceFileOpenMessage(context, location.path, failure)
                                }
                            }
                        }
                    }
                    else if (error != null && !historical) Text(checkNotNull(error), Modifier.padding(10.dp), color = colors.error, fontSize = 11.sp)
                    else if (!loading && filtered.isEmpty() && (!historical || query.isNotBlank())) Text(stringResource(if (query.isNotBlank()) R.string.workspace_changes_no_matches
                        else if (source == ChangeReviewSource.OPERATIONS || source == ChangeReviewSource.LAST_TURN) R.string.workspace_changes_empty_records else R.string.workspace_changes_empty_git),
                        Modifier.padding(10.dp), fontSize = 11.sp, color = colors.onSurfaceVariant)
                    else ChangeFileList(filtered, selected?.id, if (query.isBlank()) collapsed else emptySet(), { collapsed = it }, fileListState, onSelect = ::selectFile)
                }
                Box(Modifier.width(5.dp).fillMaxHeight().semantics { contentDescription = resizeLabel }
                    .draggable(rememberDraggableState { delta ->
                        sidebarWidth = (panelWidth.value + with(density) { delta.toDp().value }).coerceIn(minimumPanelWidth, maximumPanelWidth)
                    }, Orientation.Horizontal), contentAlignment = Alignment.Center) {
                    VerticalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
                }
            }
            val file = selected?.takeIf { loadedRequest == request }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!historical) chatId?.let(observationErrors::get)?.let { message ->
                    Text(stringResource(R.string.workspace_changes_observation_error, message),
                        Modifier.fillMaxWidth().padding(8.dp), color = colors.error, fontSize = 11.sp)
                }
                previewError?.let { ChangeActionError(it) { previewError = null } }
                if (file != null) fileStates.SaveableStateProvider(source.name + ":" + base.orEmpty() + ":" + file.id) {
                    ChangeFileDetail(file, repository, isVisible, cache, options, if (conversation) chatId else null, scope,
                        summaryLabel = if (source == ChangeReviewSource.LAST_TURN) R.string.workspace_changes_turn_summary else R.string.workspace_changes_record_summary,
                        onRecordError = { previewError = it },
                        onToggleFiles = { showFiles = !showFiles },
                        onPrevious = {
                            if (currentIndex > 0) selectFile(filtered[currentIndex - 1])
                            else if (historical && currentIndex == 0 && historyOffset > 0) { selectLastOnLoad = true; historyOffset -= WorkspaceHistoryIndex.PAGE_SIZE }
                        },
                        onNext = {
                            if (currentIndex >= 0 && currentIndex < filtered.lastIndex) selectFile(filtered[currentIndex + 1])
                            else if (historical && currentIndex >= 0 && historyOffset + files.size < historyTotal) historyOffset += WorkspaceHistoryIndex.PAGE_SIZE
                        },
                        hasPrevious = currentIndex > 0 || historical && currentIndex == 0 && historyOffset > 0,
                        hasNext = currentIndex >= 0 && (currentIndex < filtered.lastIndex || historical && historyOffset + files.size < historyTotal),
                        onOpenFile = onOpenFile, onRefresh = { refresh++ }, modifier = Modifier.fillMaxSize())
                } else {
                    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChangeIconButton(Icons.Default.ViewSidebar, stringResource(R.string.workspace_changes_toggle_files), { showFiles = !showFiles })
                        ChangeIconButton(Icons.Default.Refresh, stringResource(R.string.workspace_changes_refresh), { refresh++ }, enabled = !loading)
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
                    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                        val emptyMessage = if (historical && (error != null || (!loading && files.isEmpty()))) {
                            stringResource(R.string.workspace_changes_no_readable_records)
                        } else error ?: stringResource(R.string.workspace_changes_choose_file)
                        Text(emptyMessage, fontSize = 12.sp, color = if (historical || error == null) colors.onSurfaceVariant else colors.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun sourceLabel(source: ChangeReviewSource): String = stringResource(when (source) {
    ChangeReviewSource.OPERATIONS -> R.string.workspace_changes_operations
    ChangeReviewSource.WORKING_TREE -> R.string.workspace_changes_working
    ChangeReviewSource.BRANCH -> R.string.workspace_changes_branch
    ChangeReviewSource.LAST_TURN -> R.string.workspace_changes_last_turn
})

@Composable
private fun ChangeFileDetail(
    file: ReviewFile, repository: WorkspaceGitRepository?, active: Boolean, cache: WorkspaceReviewCache,
    options: WorkspaceDiffOptions, removalChatId: String?, recordScope: CoroutineScope, summaryLabel: Int, onRecordError: (String) -> Unit, onToggleFiles: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    hasPrevious: Boolean, hasNext: Boolean, onOpenFile: suspend (ChangeLocation, ChangeOpenAction) -> Unit,
    onRefresh: () -> Unit, modifier: Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { WorkspaceChangeStore.get(context) }
    var recordId by rememberSaveable(file.id) { mutableStateOf<String?>(null) }
    val effectiveRecordId = recordId?.takeIf { id -> file.historyQuery != null || file.records.any { it.id == id } }
    LaunchedEffect(effectiveRecordId) { if (recordId != effectiveRecordId) recordId = effectiveRecordId }
    // 只有显式选择单次操作时才显示操作来源，汇总和记录条数互不影响。
    var selectedHistoryRecord by remember(file, recordId) { mutableStateOf<FileChangeRecord?>(null) }
    val record = if (effectiveRecordId != null) file.records.firstOrNull { it.id == effectiveRecordId } ?: selectedHistoryRecord
        else if (file.aggregateRecords) null else file.records.firstOrNull()
    val scope = rememberCoroutineScope()
    var actionJob by remember(file.id) { mutableStateOf<Job?>(null) }
    var actionError by remember(file.id) { mutableStateOf<String?>(null) }
    var opening by remember(file.id) { mutableStateOf(false) }
    var historyOpen by remember(file.id) { mutableStateOf(false) }
    var recordOffset by remember(file.id) { mutableIntStateOf(0) }
    var recordPage by remember(file) { mutableStateOf(WorkspaceRecordPage(emptyList(), file.historyCount, 0)) }
    var historyLoading by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file, recordOffset, historyOpen, active) {
        val history = file.historyQuery ?: return@LaunchedEffect
        if (!historyOpen || !active) return@LaunchedEffect
        historyLoading = true
        try {
            recordPage = withContext(Dispatchers.IO) { store.recordPage(history, recordOffset) }
            recordOffset = recordPage.offset
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { AppLogger.e("WorkspaceChanges", "读取单文件历史失败", failure); actionError = failure.message ?: failure.javaClass.simpleName }
        finally { historyLoading = false }
    }
    LaunchedEffect(file, effectiveRecordId, active) {
        val id = effectiveRecordId ?: return@LaunchedEffect
        if (!active || file.historyQuery == null) return@LaunchedEffect
        try {
            selectedHistoryRecord = withContext(Dispatchers.IO) { store.historyRecord(id, file.historyQuery) }
            if (selectedHistoryRecord == null) recordId = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { AppLogger.e("WorkspaceChanges", "读取选中历史失败", failure); actionError = failure.message ?: failure.javaClass.simpleName }
    }
    DisposableEffect(file.id, active) { onDispose { actionJob?.cancel() } }
    val cacheKey = remember(file, effectiveRecordId) { ReviewCacheKey(file, effectiveRecordId) }
    var loaded by remember(cache, cacheKey) { mutableStateOf(cache[cacheKey]) }
    var loading by remember(cache, cacheKey) { mutableStateOf(loaded == null) }
    var error by remember(cache, cacheKey) { mutableStateOf<String?>(null) }
    val reviewState = rememberWorkspaceReviewState()
    val loadRequested = reviewState.isLoadRequested(loaded?.snapshotKey)
    LaunchedEffect(cache, cacheKey, active, loadRequested) {
        if (!active || loaded?.diff != null || (loaded != null && !loadRequested)) return@LaunchedEffect
        loading = true
        error = null
        try {
            val content = loaded?.content ?: withContext(Dispatchers.IO) {
                when {
                    effectiveRecordId != null && file.historyQuery != null -> store.content(checkNotNull(store.historyRecord(effectiveRecordId, file.historyQuery)))
                    record != null -> store.content(record)
                    file.aggregateRecords -> store.content(file.records.asReversed())
                    file.initialContent != null -> file.initialContent
                    else -> checkNotNull(repository).content(file)
                }
            }
            val policy = withContext(Dispatchers.Default) { WorkspaceDiffPolicy.classify(content) }
            val manualKey = if (policy == ReviewDiffLoad.MANUAL) loaded?.snapshotKey ?: withContext(Dispatchers.Default) {
                reviewSnapshotKey(checkNotNull(content.before), checkNotNull(content.after))
            } else null
            val prepared = if (policy == ReviewDiffLoad.AUTOMATIC || reviewState.isLoadRequested(manualKey)) {
                withContext(Dispatchers.Default) { prepareReviewLines(content) }
            } else null
            val snapshotKey = prepared?.document?.snapshotKey ?: manualKey
            // 需要用户确认的文本只缓存内容，点击后才计算差异。
            val result = LoadedFileReview(content, prepared, policy, snapshotKey)
            cache.put(cacheKey, result)
            loaded = result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WorkspaceChanges", "读取文件差异失败", failure)
            error = failure.message ?: failure.javaClass.simpleName
        } finally { loading = false }
    }
    val visibleLines = loaded?.diff?.document
    LaunchedEffect(cache, cacheKey, active, visibleLines?.snapshotKey) {
        val entry = loaded ?: return@LaunchedEffect
        val lines = entry.diff ?: return@LaunchedEffect
        if (!active || entry.load != ReviewDiffLoad.AUTOMATIC || entry.highlightComplete) return@LaunchedEffect
        try {
            val highlighted = withContext(Dispatchers.Default) { highlightReviewLines(context, file.location.path, entry.content, lines) }
            val result = entry.copy(diff = highlighted, highlightComplete = true)
            cache.put(cacheKey, result)
            loaded = result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WorkspaceChanges", "读取差异语法颜色失败", failure)
            actionError = failure.message ?: failure.javaClass.simpleName
        }
    }
    val navigation: @Composable () -> Unit = {
        ChangeFileNavigation(file, onToggleFiles, onPrevious, onNext, hasPrevious, hasNext, { location, action ->
            actionJob?.cancel()
            actionError = null
            actionJob = scope.launch {
                opening = true
                try {
                    onOpenFile(location, action)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    AppLogger.e("WorkspaceChanges", "打开当前文件失败", failure)
                    actionError = workspaceFileOpenMessage(context, location.path, failure)
                } finally { opening = false }
            }
        }, onRefresh)
    }
    val ribbon: @Composable () -> Unit = {
        Column {
            actionError?.let { ChangeActionError(it) { actionError = null } }
            if (opening) Text(stringResource(R.string.workspace_open_checking), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().height(32.dp).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.35f)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChangeStatus(file.status)
                Spacer(Modifier.width(6.dp))
                Image(painterResource(workspaceFileTypeIcon(file.location.path)), null, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                val parentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(file.displayPath.substringAfterLast('/')) }
                    val parent = file.displayPath.substringBeforeLast('/', "")
                    if (parent.isNotEmpty()) withStyle(SpanStyle(color = parentColor, fontWeight = FontWeight.Normal)) { append("  "); append(parent) }
                }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                val document = loaded?.diff?.document
                ChangeDiffStats(document?.additions ?: file.additions, document?.deletions ?: file.deletions,
                    known = document != null || (effectiveRecordId == null && file.statisticsAvailable))
            }
            if (file.records.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { historyOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(26.dp)) {
                            Text(record?.let { recordTitle(it) } ?: stringResource(summaryLabel,
                                changeTime(file.records.first().timestamp)), fontSize = 9.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (file.aggregateRecords || file.records.size > 1) Icon(Icons.Default.ArrowDropDown, null, Modifier.size(12.dp))
                        }
                        DropdownMenu(expanded = historyOpen, onDismissRequest = { historyOpen = false }) {
                            if (file.aggregateRecords) DropdownMenuItem(text = { Text(stringResource(summaryLabel, changeTime(file.records.first().timestamp)), fontSize = 11.sp) }, onClick = { recordId = null; historyOpen = false })
                            if (historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            val records = if (file.historyQuery == null) file.records else recordPage.records
                            records.forEach { item -> DropdownMenuItem(text = { Text(recordTitle(item), fontSize = 11.sp) }, enabled = !historyLoading,
                                onClick = { selectedHistoryRecord = item; recordId = item.id; historyOpen = false }) }
                            if (file.historyQuery != null && recordPage.total > WorkspaceHistoryIndex.PAGE_SIZE) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.workspace_history_previous_page), fontSize = 11.sp) },
                                    enabled = !historyLoading && recordOffset > 0, onClick = { recordOffset = (recordOffset - WorkspaceHistoryIndex.PAGE_SIZE).coerceAtLeast(0) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.workspace_history_next_page), fontSize = 11.sp) },
                                    enabled = !historyLoading && recordOffset + recordPage.records.size < recordPage.total, onClick = { recordOffset += WorkspaceHistoryIndex.PAGE_SIZE })
                            }
                        }
                    }
                    ChangeRecordActions(file, record, removalChatId, recordScope, onChanged = onRefresh, onError = onRecordError)
                }
            }
            if (file.records.isNotEmpty()) Text(stringResource(R.string.workspace_changes_record_scope),
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            file.originalPath?.let { Text(stringResource(R.string.workspace_changes_renamed_from, it), Modifier.padding(horizontal = 8.dp), fontSize = 10.sp) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
    val result = loaded
    if (result?.diff != null) WorkspaceDiffView(result.diff, modifier, options, toolbarPrefix = navigation, fileHeader = ribbon, reviewState = reviewState)
    else Column(modifier) {
        Row(Modifier.fillMaxWidth().height(32.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            navigation()
            WorkspaceDiffControls(options, enabled = false)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ribbon()
        when {
            error != null -> Text(checkNotNull(error), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
            result != null && result.content.isText -> {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (result.load == ReviewDiffLoad.BLOCKED) R.string.workspace_changes_hard_limit else R.string.workspace_changes_load_on_demand), fontSize = 12.sp)
                    if (result.load == ReviewDiffLoad.MANUAL) TextButton(onClick = { reviewState.requestLoad(checkNotNull(result.snapshotKey)) }) {
                        Text(stringResource(R.string.workspace_changes_load_diff))
                    }
                }
            }
            result != null && !result.content.isText -> {
                val data = result.content
                Text(stringResource(when {
                    data.snapshotsCleared -> R.string.workspace_changes_no_readable_records
                    data.beforeKind == SnapshotKind.TOO_LARGE || data.afterKind == SnapshotKind.TOO_LARGE -> R.string.workspace_changes_too_large
                    data.beforeKind == SnapshotKind.UNAVAILABLE || data.afterKind == SnapshotKind.UNAVAILABLE -> R.string.workspace_changes_unavailable
                    data.beforeKind == SnapshotKind.DIRECTORY || data.afterKind == SnapshotKind.DIRECTORY -> R.string.workspace_changes_directory
                    else -> R.string.workspace_changes_binary
                }), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                if (!data.snapshotsCleared) data.detail?.let { Text(it, Modifier.padding(horizontal = 12.dp), fontSize = 10.sp) }
            }
        }
    }
}

private fun changeTime(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestamp))

@Composable
private fun recordTitle(record: FileChangeRecord): String {
    val title = stringResource(R.string.workspace_changes_operation_title, changeTime(record.timestamp), record.toolName)
    return if (record.additions != null && record.deletions != null) "$title  +${record.additions}/−${record.deletions}" else title
}

@Composable
private fun ChangeFileFilter(query: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.workspace_changes_filter)
    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth().heightIn(min = 28.dp)
        .background(colors.surfaceContainerHighest.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, contentDescription = null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        BasicTextField(query, onChange, Modifier.weight(1f).padding(vertical = 5.dp).semantics { contentDescription = label },
            singleLine = true, textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Normal), cursorBrush = SolidColor(colors.primary),
            decorationBox = { field -> Box { if (query.isEmpty()) Text(label, color = colors.onSurfaceVariant, fontSize = 11.sp); field() } })
        if (query.isNotEmpty()) IconButton(onClick = { onChange("") }, Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.workspace_changes_clear_filter), Modifier.size(14.dp))
        }
    }
}

private data class ChangeTreeEntry(val key: String, val title: String, val depth: Int, val file: ReviewFile?)

private fun changeTree(files: List<ReviewFile>, collapsed: Set<String>): List<ChangeTreeEntry> {
    val entries = linkedMapOf<String, ChangeTreeEntry>()
    for (file in files.sortedBy { it.displayPath }) {
        val parts = file.displayPath.trimStart('/').split('/')
        var hidden = false
        for (index in 0 until parts.lastIndex) {
            val key = "directory:" + file.location.backend + ":" + parts.take(index + 1).joinToString("/")
            if (!hidden) entries.putIfAbsent(key, ChangeTreeEntry(key, parts[index], index, null))
            if (key in collapsed) hidden = true
        }
        if (!hidden) entries[file.id] = ChangeTreeEntry(file.id, parts.last(), parts.lastIndex, file)
    }
    return entries.values.toList()
}

@Composable
private fun ChangeFileList(
    files: List<ReviewFile>, selectedId: String?, collapsed: Set<String>, onCollapsed: (Set<String>) -> Unit,
    state: LazyListState, onSelect: (ReviewFile) -> Unit,
) {
    val rows = remember(files, collapsed) { changeTree(files, collapsed) }
    LaunchedEffect(selectedId, rows) {
        val index = rows.indexOfFirst { it.file?.id == selectedId }
        if (index >= 0) state.scrollToItem(index)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowWidth = maxOf(maxWidth, (rows.maxOfOrNull { 105 + it.depth * 10 + maxOf(145, it.title.length * 7) } ?: 220).dp)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(Modifier.width(rowWidth).fillMaxHeight(), state = state, contentPadding = PaddingValues(vertical = 2.dp)) {
                items(rows, key = { it.key }) { row ->
                    val file = row.file
                    val selected = file != null && file.id == selectedId
                    Surface(onClick = {
                        if (file != null) onSelect(file)
                        else onCollapsed(if (row.key in collapsed) collapsed - row.key else collapsed + row.key)
                    }, color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent) {
                        Row(Modifier.fillMaxWidth().heightIn(min = if (file == null) 28.dp else 42.dp)
                            .semantics { contentDescription = file?.displayPath ?: row.title }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(28.dp).background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                            Spacer(Modifier.width((5 + row.depth.coerceAtMost(10) * 10).dp))
                            if (file == null) Icon(if (row.key in collapsed) Icons.Default.ChevronRight else Icons.Default.KeyboardArrowDown, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Spacer(Modifier.width(14.dp))
                            if (file == null) Icon(if (row.key in collapsed) Icons.Default.Folder else Icons.Default.FolderOpen,
                                contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                            else Image(painterResource(workspaceFileTypeIcon(file.location.path)), null, Modifier.size(14.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 5.dp, vertical = 4.dp)) {
                                Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                                file?.records?.firstOrNull()?.let { latest ->
                                    Text(changeTime(latest.timestamp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontSize = 9.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (file != null) {
                                ChangeDiffStats(file.additions, file.deletions, known = file.statisticsAvailable)
                                Spacer(Modifier.width(6.dp))
                                ChangeStatus(file.status)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeActionError(message: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(start = 8.dp), verticalAlignment = Alignment.Top) {
        Text(message, Modifier.weight(1f).heightIn(max = 140.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
        ChangeIconButton(Icons.Default.Close, stringResource(R.string.close), onDismiss)
    }
}

/** 翻页替换当前页，文件树、搜索和详情均不累积已浏览的历史页。 */
@Composable
private fun HistoryPageNavigation(offset: Int, count: Int, total: Int, loading: Boolean, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = previous, enabled = !loading && offset > 0, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(stringResource(R.string.workspace_history_previous_page), fontSize = 10.sp)
        }
        Text(stringResource(R.string.workspace_history_page_range, if (total == 0) 0 else offset + 1, minOf(offset + count, total), total),
            Modifier.weight(1f), fontSize = 9.sp, maxLines = 1)
        TextButton(onClick = next, enabled = !loading && offset + WorkspaceHistoryIndex.PAGE_SIZE < total, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(stringResource(R.string.workspace_history_next_page), fontSize = 10.sp)
        }
    }
}
