package com.ai.assistance.operit.ui.features.conversation.workspace

import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.conversation.workspace.svg.isWorkspaceSvg

import android.annotation.SuppressLint
import android.net.Uri
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceChangeAccess
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceNavigationTab
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceEntryAction
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceEntryRequest
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.preview.AudioAttachmentPlayer
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.preview.VideoAttachmentPlayer
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.WorkspaceHtmlPreview
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.*
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceBrowserPreferences
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceBrowserSettings
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.LocalWorkspaceHeader
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorKeyCommand
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.LanguageDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.ai.assistance.operit.ui.features.conversation.workspace.search.*
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileLink
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileLinkDialog
import com.ai.assistance.operit.ui.features.conversation.workspace.links.findWorkspaceMarkdownAnchorLine
import com.ai.assistance.operit.ui.features.conversation.workspace.markdown.WorkspaceMarkdownPreview
import com.ai.assistance.operit.ui.features.conversation.workspace.search.activeSearchMatchAfterUpdate
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.WorkspaceImagePreview
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.WorkspaceReadOnlyDocumentPreview
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.isAudio
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.isHtml
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.isImage
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.isMarkdown
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.isReadOnlyDocumentPreviewable
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.isReadOnlyPreview
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.isVideo
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.rememberWorkspacePreviewFileState
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.workspacePreviewUriFromFile

/** 工作区文件与编辑会话，项目入口不启动网页服务。 */
@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceManager(
        actualViewModel: ChatViewModel,
        currentChat: ChatHistory,
        workspacePath: String,
        workspaceEnv: String? = null,
        isVisible: Boolean,
        entryRequest: WorkspaceEntryRequest = WorkspaceEntryRequest(),
        initialFile: OpenFileInfo? = null,
        onReturnToBrowser: (() -> Unit)? = null
) {
    val standalone = onReturnToBrowser != null
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val isSafEnv = remember(workspaceEnv) { workspaceEnv?.startsWith("repo:", ignoreCase = true) == true }

    fun withWorkspaceEnvParams(base: List<ToolParameter>): List<ToolParameter> {
        if (workspaceEnv.isNullOrBlank()) return base
        return base + ToolParameter("environment", workspaceEnv)
    }

    // 文件管理和标签状态 - 使用内存态，避免编辑大文件时频繁持久化整份内容
    var openFiles by remember(workspacePath, workspaceEnv) { mutableStateOf(initialFile?.let { listOf(it) } ?: emptyList()) }
    var currentFileIndex by remember(workspacePath, workspaceEnv) { mutableStateOf(if (initialFile == null) -2 else 0) }
    var handledEntrySequence by remember(workspacePath, workspaceEnv) {
        mutableStateOf(if (initialFile != null) entryRequest.sequence else -1L)
    }
    LaunchedEffect(isVisible, workspacePath, workspaceEnv, entryRequest.sequence) {
        // 入口请求只消费一次，异步打开文件后不能再次重置活动标签。
        if (isVisible && handledEntrySequence != entryRequest.sequence) {
            handledEntrySequence = entryRequest.sequence
            if (entryRequest.action == WorkspaceEntryAction.FILES) currentFileIndex = -2
        }
    }
    var filePreviewStates by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var fileLineNavigationRequests by remember(workspacePath, workspaceEnv) { mutableStateOf(mapOf<String, Int>()) }
    var markdownLinkTarget by remember(workspacePath, workspaceEnv) { mutableStateOf<WorkspaceFileLink?>(null) }
    var markdownNavigationJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(workspacePath, workspaceEnv) { markdownNavigationJob?.cancel() }
    var unsavedFiles by remember(workspacePath, workspaceEnv) { mutableStateOf(emptySet<String>()) }
    val navigation = LocalWorkspaceHeader.current
    val headerOwner = remember { Any() }
    val editorSessions = remember(workspacePath, workspaceEnv) { mutableMapOf<String, EditorSession>() }
    val diskContents = remember(workspacePath, workspaceEnv) { mutableMapOf<String, EditorDiskContent>() }
    var externalChange by remember { mutableStateOf<EditorExternalChange?>(null) }
    var resumeRevision by remember { mutableIntStateOf(0) }
    val editorLifecycle = LocalLifecycleOwner.current
    DisposableEffect(editorLifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeRevision++ }
        editorLifecycle.lifecycle.addObserver(observer)
        onDispose { editorLifecycle.lifecycle.removeObserver(observer) }
    }
    val editorPreferences = remember(context) { WorkspaceBrowserPreferences(context) }
    val persistedEditorSettings by editorPreferences.settings.collectAsState(initial = WorkspaceBrowserSettings())
    val editorSettings = persistedEditorSettings.editor.copy(wordWrap = persistedEditorSettings.wordWrap)
    var editorSettingsOpen by remember(workspacePath, workspaceEnv) { mutableStateOf(false) }
    val currentPreviewFile = openFiles.getOrNull(currentFileIndex)
    var markdownOutlineVisible by remember(currentPreviewFile?.key) { mutableStateOf(false) }
    var htmlFindVisible by remember(currentPreviewFile?.key) { mutableStateOf(false) }
    val editorFile = currentPreviewFile?.takeUnless { it.isReadOnlyPreview }
    if (editorFile != null && !diskContents.containsKey(editorFile.key)) diskContents[editorFile.key] = EditorDiskContent(true, editorFile.content)
    val editorSession = editorFile?.let { editorSessions.getOrPut(it.key) { EditorSession(it.content, WorkspaceChangeAccess.backend(context, it.environment)) } }
    fun changeEditorSettings(transform: (EditorSettings) -> EditorSettings) {
        coroutineScope.launch {
            try { editorPreferences.update { old ->
                val changed = transform(old.editor.copy(wordWrap = old.wordWrap))
                old.copy(wordWrap = changed.wordWrap, editor = changed)
            } } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
                AppLogger.e("WorkspaceEditor", "保存编辑器设置失败", failure)
                OKToast.error(context, failure.localizedMessage ?: failure.javaClass.simpleName).show()
            }
        }
    }
    var saveAsRequested by remember { mutableStateOf(false) }
    var reloadRequested by remember { mutableStateOf(false) }
    val browserStateHolder = rememberSaveableStateHolder()
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var replacementVisible by remember { mutableStateOf(false) }
    var pendingSearchOffset by remember { mutableStateOf<Int?>(null) }
    var editorSearchOptions by remember { mutableStateOf(EditorSearchOptions()) }
    var replacing by remember { mutableStateOf(false) }
    var editorSearchError by remember { mutableStateOf<String?>(null) }
    var editorSearchPending by remember { mutableStateOf(false) }
    var searchMatches by remember { mutableStateOf(emptyList<IntRange>()) }
    var activeMatch by remember { mutableIntStateOf(-1) }
    var searchNavigationRequest by remember { mutableIntStateOf(0) }
    var searchOpenRequest by remember { mutableIntStateOf(0) }
    var searchContext by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    var showRenameWorkspaceDialog by remember { mutableStateOf(false) }
    var renameWorkspaceInput by remember(workspacePath) { mutableStateOf(File(workspacePath).name) }
    var renameWorkspaceError by remember { mutableStateOf<String?>(null) }
    var isRenamingWorkspace by remember { mutableStateOf(false) }
    val canRenameWorkspace by remember(workspacePath, workspaceEnv) {
        mutableStateOf(
            !isSafEnv &&
                runCatching {
                    val workspaceRoot = File(context.filesDir, "workspace").canonicalFile
                    File(workspacePath).canonicalFile.parentFile?.canonicalFile == workspaceRoot
                }.getOrDefault(false)
        )
    }
    
    // 解绑确认对话框状态
    var showUnbindConfirmDialog by remember { mutableStateOf(false) }
    
    // 关闭文件确认对话框状态
    var fileToCloseIndex by remember { mutableStateOf(-1) }
    
    // 当前活动的编辑器引用
    var activeEditor by remember { mutableStateOf<com.ai.assistance.operit.ui.features.conversation.workspace.editor.NativeCodeEditor?>(null) }
    val searchableFile = openFiles.getOrNull(currentFileIndex)?.takeUnless { it.isReadOnlyPreview }
    fun setEditorSearchScope(scope: EditorSearchScope?) {
        editorSession?.let { it.document.searchScope = scope; it.searchScope = scope }
    }
    fun openEditorSearch(query: String? = null, scope: EditorSearchScope? = null) {
        activeEditor?.dismissSelectionMenu()
        setEditorSearchScope(scope)
        if (query != null || scope != null) searchQuery = query.orEmpty()
        if (query != null) editorSearchOptions = editorSearchOptions.copy(regex = false)
        replacementVisible = false
        pendingSearchOffset = null
        searchContext = null
        searchOpenRequest++
        searchVisible = true
    }
    fun closeEditorSearch() {
        searchVisible = false
        replacementVisible = false
        pendingSearchOffset = null
        setEditorSearchScope(null)
    }
    DisposableEffect(editorSession) {
        val session = editorSession
        onDispose { session?.document?.searchScope = null; session?.searchScope = null }
    }
    LaunchedEffect(searchableFile?.key) { replacementVisible = false; pendingSearchOffset = null }
    LaunchedEffect(searchableFile?.key, editorSession, editorSession?.status?.revision, searchQuery, searchVisible,
        editorSearchOptions, replaceText, replacementVisible, editorSession?.readOnly, editorSession?.searchScope, pendingSearchOffset, searchOpenRequest) {
        val scope = editorSession?.searchScope
        val snapshot = editorSession?.document?.snapshot()
        val originalContent = searchableFile?.content.orEmpty()
        val nextContext = searchableFile?.key to (searchQuery + editorSearchOptions.toString() + (scope != null))
        editorSearchPending = true
        val result = withContext(Dispatchers.Default) {
            if (searchVisible) searchEditorText(if (snapshot == null) originalContent else snapshot.toString(), searchQuery, editorSearchOptions,
                if (replacementVisible && editorSession?.readOnly == false) replaceText else null, scope) else EditorSearchResult()
        }
        editorSearchPending = false
        editorSearchError = result.error
        val matches = result.matches.map { it.range }
        val pending = pendingSearchOffset
        val changedQuery = searchContext != nextContext
        // 区域边界独立于光标；文本输入仅更新高亮，查询或显式替换才请求定位。
        activeMatch = if (pending != null && matches.isNotEmpty()) {
            matches.indexOfFirst { it.first >= pending }.let { if (it >= 0) it else 0 }
        } else activeSearchMatchAfterUpdate(if (changedQuery) 0 else activeMatch, matches.size)
        searchMatches = matches
        searchContext = nextContext
        pendingSearchOffset = null
        if (searchVisible && matches.isNotEmpty() && (changedQuery || pending != null)) searchNavigationRequest++
    }

    // 回到前台或切换文件后比较真实磁盘内容，读取失败不伪装成删除。
    LaunchedEffect(isVisible, editorFile?.key, resumeRevision, editorSettings.detectExternalChanges, saving) {
        val file = editorFile
        if (!isVisible || file == null || saving || !editorSettings.detectExternalChanges) return@LaunchedEffect
        try {
            val before = checkNotNull(diskContents[file.key])
            val disk = readEditorDisk(context.applicationContext, file)
            if (disk != before && openFiles.any { it.key == file.key }) externalChange = EditorExternalChange(file, disk)
        } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
            AppLogger.e("WorkspaceEditor", "检查编辑文件外部变化失败", failure)
            saveError = failure.localizedMessage ?: failure.javaClass.simpleName
        }
    }


    // 保存文件函数
    fun saveFile(fileInfo: OpenFileInfo, afterSave: (() -> Unit)? = null) {
        if (fileInfo.isReadOnlyPreview || saving) return
        val session = editorSessions[fileInfo.key]
        val snapshot = session?.document?.snapshot()
        saving = true
        coroutineScope.launch {
            try {
                val content = withContext(Dispatchers.Default) {
                    if (snapshot == null) fileInfo.content else snapshot.toString()
                }
                val result = withContext(Dispatchers.IO) {
                    toolHandler.executeTool(AITool("write_file", listOf(
                        ToolParameter("path", fileInfo.path), ToolParameter("content", content),
                        ToolParameter("environment", fileInfo.environment)
                    )))
                }
                check(result.success) { result.error.orEmpty() }
                diskContents[fileInfo.key] = EditorDiskContent(true, content)
                // 保存绑定点击时的不可变版本，期间输入或重新加载不能清掉新内容的未保存标记。
                if (openFiles.any { it.key == fileInfo.key } && editorSessions[fileInfo.key] === session &&
                    (session == null || session.document.version == checkNotNull(snapshot).version)) {
                    unsavedFiles = unsavedFiles - fileInfo.key
                    afterSave?.invoke()
                }
                saveError = null
                OKToast.success(context, R.string.save_successful).show()
                if (fileInfo.isHtml && filePreviewStates[fileInfo.key] == true) actualViewModel.refreshWebView()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceManager", "保存文件失败", e)
                saveError = e.message
            } finally { saving = false }
        }
    }

    // 实际执行关闭文件操作
    fun confirmCloseFile(index: Int) {
        if (index >= 0 && index < openFiles.size) {
            val fileToClose = openFiles[index]
            val updatedFiles = openFiles.toMutableList()
            updatedFiles.removeAt(index)
            openFiles = updatedFiles

            // 从未保存集合中移除
            unsavedFiles = unsavedFiles - fileToClose.key
            editorSessions.remove(fileToClose.key)
            diskContents.remove(fileToClose.key)
            
            // 更新当前选中的标签
            currentFileIndex = when {
                updatedFiles.isEmpty() -> -2
                index >= updatedFiles.size -> updatedFiles.size - 1
                else -> index
            }
        }
        fileToCloseIndex = -1
        if (openFiles.isEmpty()) onReturnToBrowser?.invoke()
    }

    // 关闭文件标签
    fun closeFile(index: Int) {
        if (index >= 0 && index < openFiles.size) {
            val fileToClose = openFiles[index]
            // 如果文件有未保存的更改，显示确认对话框
            if (unsavedFiles.contains(fileToClose.key)) {
                fileToCloseIndex = index
            } else {
                // 否则直接关闭
                confirmCloseFile(index)
            }
        }
    }

    // 键盘命令统一复用现有文件状态和未保存确认流程。
    fun handleEditorKeyCommand(command: EditorKeyCommand): Boolean {
        return when (command) {
            EditorKeyCommand.SAVE -> {
                val file = openFiles.getOrNull(currentFileIndex)
                if (file == null || file.isReadOnlyPreview || editorSession?.readOnly == true) false else {
                    saveFile(file)
                    true
                }
            }
            EditorKeyCommand.SAVE_AS -> if (searchableFile == null) false else { saveAsRequested = true; true }
            EditorKeyCommand.CLOSE -> {
                if (currentFileIndex !in openFiles.indices) false else {
                    closeFile(currentFileIndex)
                    true
                }
            }
            EditorKeyCommand.FIND -> {
                if (searchableFile == null) false else {
                    openEditorSearch()
                    true
                }
            }
            EditorKeyCommand.ESCAPE -> when {
                fileToCloseIndex != -1 -> {
                    fileToCloseIndex = -1
                    true
                }
                searchVisible -> {
                    closeEditorSearch()
                    true
                }
                currentFileIndex in openFiles.indices -> {
                    closeFile(currentFileIndex)
                    true
                }
                else -> false
            }
        }
    }

    // 切换文件预览状态
    fun togglePreview(path: String) {
        markdownOutlineVisible = false
        htmlFindVisible = false
        closeEditorSearch()
        filePreviewStates =
                filePreviewStates.toMutableMap().apply { this[path] = !(this[path] ?: false) }
    }

    // 打开文件
    fun openFile(fileInfo: OpenFileInfo) {
        handledEntrySequence = entryRequest.sequence
        // 检查文件是否已经打开
        val existingIndex = openFiles.indexOfFirst { it.key == fileInfo.key }

        if (existingIndex != -1) {
            val existing = openFiles[existingIndex]
            // SVG 预览与源码共用标签；只有无未保存修改时才切换模式，避免丢失编辑内容。
            if (isWorkspaceSvg(fileInfo.path) && existing.isReadOnlyPreview != fileInfo.isReadOnlyPreview &&
                fileInfo.key !in unsavedFiles) {
                if (!fileInfo.isReadOnlyPreview) {
                    editorSessions.remove(fileInfo.key)
                    diskContents.remove(fileInfo.key)
                }
                openFiles = openFiles.toMutableList().apply { this[existingIndex] = fileInfo }
                filePreviewStates = filePreviewStates + (fileInfo.key to false)
            }
            // 已打开的文件保留内存内容，仅更新显式定位和活动标签。
            if (fileInfo.initialLine != null) {
                openFiles = openFiles.toMutableList().apply {
                    this[existingIndex] = this[existingIndex].copy(initialLine = fileInfo.initialLine)
                }
                fileLineNavigationRequests = fileLineNavigationRequests +
                    (fileInfo.key to ((fileLineNavigationRequests[fileInfo.key] ?: 0) + 1))
                filePreviewStates = filePreviewStates + (fileInfo.key to false)
            }
            currentFileIndex = existingIndex
        } else {
            // 否则添加到打开的文件列表
            openFiles = openFiles + fileInfo
            currentFileIndex = openFiles.size - 1

            // 初始化预览状态
            filePreviewStates =
                    filePreviewStates.toMutableMap().apply {
                        // 文本文件默认进入现有编辑器，预览由用户主动切换。
                        this[fileInfo.key] = false
                    }
        }
    }

    fun openMarkdownFileLink(target: WorkspaceFileLink) {
        markdownNavigationJob?.cancel()
        val existing = openFiles.firstOrNull { it.environment == target.environment && it.path == target.path }
        if (existing == null) {
            markdownLinkTarget = target
            return
        }
        // 标题扫描在后台读取不可变快照，包括尚未保存的修改。
        val snapshot = editorSessions[existing.key]?.document?.snapshot()
        markdownNavigationJob = coroutineScope.launch {
            try {
                val line = withContext(Dispatchers.Default) {
                    if (target.anchor == null) target.line else {
                        checkNotNull(findWorkspaceMarkdownAnchorLine(if (snapshot == null) existing.content else snapshot.toString(), target.anchor)) {
                            context.getString(R.string.workspace_markdown_anchor_not_found, target.anchor)
                        }
                    }
                }
                if (openFiles.any { it.key == existing.key }) openFile(existing.copy(initialLine = line))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("WorkspaceMarkdown", "定位文档链接失败", error)
                OKToast.error(context, error.message ?: context.getString(R.string.file_error_open_failed), OKToast.LENGTH_SHORT).show()
            }
        }
    }

    val markdownTarget = markdownLinkTarget
    if (markdownTarget != null) {
        key(markdownTarget) {
            WorkspaceFileLinkDialog(
                actualViewModel = actualViewModel,
                currentChat = currentChat,
                target = markdownTarget,
                onDismiss = { markdownLinkTarget = null },
                onFileOpen = ::openFile,
            )
        }
    }

    fun acceptEditorDisk(file: OpenFileInfo, disk: EditorDiskContent) {
        check(disk.exists)
        val index = openFiles.indexOfFirst { it.key == file.key }
        if (index >= 0) {
            editorSessions[file.key] = EditorSession(disk.content, WorkspaceChangeAccess.backend(context, file.environment)).also { it.readOnly = editorSessions[file.key]?.readOnly == true }
            diskContents[file.key] = disk
            openFiles = openFiles.toMutableList().also { it[index] = it[index].copy(content = disk.content, lastModified = System.currentTimeMillis()) }
            unsavedFiles = unsavedFiles - file.key
            saveError = null
        }
    }
    fun reloadEditorFile(file: OpenFileInfo) {
        if (openFiles.none { it.key == file.key }) return
        val expectedSession = checkNotNull(editorSessions[file.key])
        val expectedVersion = expectedSession.document.version
        coroutineScope.launch {
            try {
                val disk = readEditorDisk(context.applicationContext, file)
                val latest = openFiles.firstOrNull { it.key == file.key } ?: return@launch
                if (disk.exists && editorSessions[file.key] === expectedSession && expectedSession.document.version == expectedVersion)
                    acceptEditorDisk(file, disk) else externalChange = EditorExternalChange(latest, disk)
            } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
                AppLogger.e("WorkspaceEditor", "重新加载文件失败", failure)
                saveError = failure.localizedMessage ?: failure.javaClass.simpleName
            }
        }
    }
    if (saveAsRequested && editorFile != null) EditorSaveAsDialog(
        editorFile.copy(content = checkNotNull(editorSessions[editorFile.key]).document.snapshot().toString()), { saveAsRequested = false }) { saved ->
        saveAsRequested = false
        diskContents[saved.key] = EditorDiskContent(true, saved.content)
        openFile(saved)
        OKToast.success(context, context.getString(R.string.workspace_editor_saved_as, saved.path)).show()
    }
    if (reloadRequested && editorFile != null) {
        val file = editorFile
        AlertDialog(onDismissRequest = { reloadRequested = false }, title = { Text(stringResource(R.string.workspace_editor_reload)) },
            text = { Text(stringResource(R.string.workspace_editor_reload_confirm)) },
            confirmButton = { TextButton(onClick = { reloadRequested = false; reloadEditorFile(file) }) { Text(stringResource(R.string.workspace_editor_reload)) } },
            dismissButton = { TextButton(onClick = { reloadRequested = false }) { Text(stringResource(R.string.cancel)) } })
    }
    externalChange?.let { change ->
        fun keepEdits() {
            diskContents[change.file.key] = change.disk
            val session = editorSessions[change.file.key]
            if (openFiles.any { it.key == change.file.key } && (!change.disk.exists ||
                    (if (session == null) change.file.content else session.document.snapshot().toString()) != change.disk.content)) {
                unsavedFiles = unsavedFiles + change.file.key
            }
            externalChange = null
        }
        AlertDialog(onDismissRequest = ::keepEdits, title = { Text(stringResource(R.string.workspace_editor_file)) },
            text = { Text(stringResource(if (change.disk.exists) R.string.workspace_editor_external_changed else R.string.workspace_editor_external_deleted, change.file.name)) },
            confirmButton = { TextButton(onClick = {
                if (change.disk.exists) { externalChange = null; reloadEditorFile(change.file) }
                else { keepEdits(); saveAsRequested = true }
            }) { Text(stringResource(if (change.disk.exists) R.string.workspace_editor_reload else R.string.workspace_editor_save_as)) } },
            dismissButton = { TextButton(onClick = ::keepEdits) { Text(stringResource(R.string.workspace_editor_keep_editing)) } })
    }
    fun performEditorReplacement(all: Boolean) {
        val editor = activeEditor ?: return
        val target = editorFile ?: return
        val session = editorSession ?: return
        if (session.readOnly || replacing) return
        val text = editor.getText()
        val query = searchQuery
        val options = editorSearchOptions
        val replacement = replaceText
        val index = activeMatch
        val searchScope = session.searchScope
        replacing = true
        coroutineScope.launch {
            try {
                val (result, updated) = withContext(Dispatchers.Default) {
                    val found = searchEditorText(text, query, options, replacement, searchScope)
                    found to if (all && found.error == null) replaceEditorMatches(text, found.matches) else null
                }
                editorSearchError = result.error
                if (result.error != null || result.matches.isEmpty()) return@launch
                if (activeEditor !== editor || openFiles.getOrNull(currentFileIndex)?.key != target.key || editor.getText() != text || session.readOnly ||
                    !searchVisible || !replacementVisible || (!all && activeMatch != index) || searchQuery != query || editorSearchOptions != options || replaceText != replacement || session.searchScope != searchScope) {
                    editorSearchError = context.getString(R.string.workspace_editor_changed_retry)
                    return@launch
                }
                if (all) {
                    val value = checkNotNull(updated)
                    val range = searchScope ?: EditorSearchScope(0, text.length)
                    pendingSearchOffset = range.start
                    // 仅替换搜索范围，让范围标记和一次撤销保持精确，范围外文本原样保留。
                    if (value != text) editor.replaceEditorRange(range.start, range.end, value.substring(range.start, range.end + value.length - text.length))
                } else result.matches.getOrNull(index)?.let {
                    pendingSearchOffset = it.start + it.replacement.length + if (it.start == it.end && it.replacement.isEmpty()) 1 else 0
                    if (it.replacement != text.substring(it.start, it.end)) editor.replaceEditorRange(it.start, it.end, it.replacement)
                }
            } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
                AppLogger.e("WorkspaceEditor", "替换文本失败", failure)
                editorSearchError = failure.localizedMessage ?: failure.javaClass.simpleName
            } finally { replacing = false }
        }
    }
    val headerTabs: @Composable RowScope.() -> Unit = {
                        if (!standalone && navigation == null) VSCodeTab(
                            title = stringResource(R.string.files),
                            icon = Icons.Default.Folder,
                            isActive = currentFileIndex == -2,
                            isUnsaved = false,
                            onClose = null,
                            onClick = { currentFileIndex = -2 }
                        )
                        // 打开的文件标签
                        openFiles.forEachIndexed { index, fileInfo ->
                            VSCodeTab(
                                    title = fileInfo.name,
                                    icon = getFileIcon(fileInfo.name), // 使用统一的 getFileIcon
                                    isActive = currentFileIndex == index,
                                    isUnsaved = unsavedFiles.contains(fileInfo.key),
                                    onClose = { closeFile(index) },
                                    onClick = { currentFileIndex = index }
                            )
                        }
    }
    val headerActions: @Composable RowScope.() -> Unit = {
                    // 动态操作区域
                    val currentFile = openFiles.getOrNull(currentFileIndex)
                    val currentTextFile = currentFile?.takeUnless { it.isReadOnlyPreview || filePreviewStates[it.key] == true }
                    if (currentTextFile != null && editorSession != null) {
                        key(currentTextFile.key) {
                            EditorToolbar(editorSession, activeEditor, editorSettings, ::changeEditorSettings,
                                currentTextFile.key in unsavedFiles, saving, { saveFile(currentTextFile) },
                                { openEditorSearch() }, { saveAsRequested = true }, { reloadRequested = true }, { closeFile(currentFileIndex) }, { editorSettingsOpen = true },
                                path = currentTextFile.path, environment = currentTextFile.environment)
                        }
                    }
                    if (currentFile != null && (currentFile.isHtml || currentFile.isMarkdown)) {
                        val isPreview = filePreviewStates[currentFile.key] ?: false
                        if (isPreview && currentFile.isMarkdown) IconButton(onClick = { markdownOutlineVisible = !markdownOutlineVisible }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Toc, stringResource(R.string.workspace_markdown_outline))
                        }
                        if (isPreview && currentFile.isHtml) IconButton(onClick = { htmlFindVisible = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Search, stringResource(R.string.workspace_search_content))
                        }
                        IconButton(
                                onClick = { togglePreview(currentFile.key) },
                                // 限制按钮大小，使其与标签高度(40.dp)保持一致，防止撑开父布局
                                modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                    if (isPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                    contentDescription = "Toggle Preview"
                            )
                        }
                    }
    }
    if (navigation != null) {
        SideEffect { navigation.register(headerOwner, headerTabs, headerActions, currentFileIndex != -2) { currentFileIndex = -2 } }
        DisposableEffect(navigation) { onDispose { navigation.unregister(headerOwner) } }
    }
    if (editorSettingsOpen) EditorSettingsScreen(editorSettings, ::changeEditorSettings) { editorSettingsOpen = false }
    // 不透明底色与主题前景色一起提供，避免编辑按钮沿用外层聊天颜色。
    Surface(
        modifier = Modifier.fillMaxSize().imePadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BackHandler(enabled = currentFileIndex != -2) {
            if (standalone && currentFileIndex in openFiles.indices) closeFile(currentFileIndex)
            else currentFileIndex = -2
        }

        BackHandler(enabled = searchVisible && searchableFile != null) { closeEditorSearch() }
        Column(modifier = Modifier.fillMaxSize()) {
            if (navigation == null && (!standalone || openFiles.isNotEmpty())) Surface(
                color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { headerTabs() }
                    headerActions()
                }
            }
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            // 主内容区域
            Box(
                    modifier =
                            Modifier.weight(1f)
                                    .background(MaterialTheme.colorScheme.surface) // 添加背景色防止闪烁
            ) {
                when {
                    // 项目入口显式使用绑定根目录，普通浏览首页不覆盖项目位置。
                    currentFileIndex == -2 -> browserStateHolder.SaveableStateProvider("files") {
                        FileBrowser(
                            initialPath = workspacePath,
                            environment = workspaceEnv,
                            workspaceRoot = if (standalone) null else com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation(workspacePath,
                                com.ai.assistance.operit.ui.features.conversation.workspace.browser.workspaceEnvironment(workspaceEnv)),
                            entrySequence = entryRequest.sequence,
                            onUnbindWorkspace = if (standalone) null else ({ showUnbindConfirmDialog = true }),
                            onRenameWorkspace = if (standalone || !canRenameWorkspace) null else ({
                                if (unsavedFiles.isNotEmpty()) {
                                    OKToast.info(context, context.getString(R.string.workspace_rename_save_first)).show()
                                } else {
                                    renameWorkspaceInput = File(workspacePath).name
                                    renameWorkspaceError = null
                                    showRenameWorkspaceDialog = true
                                }
                            }),
                            onCancel = {},
                            showHeader = false,
                            onFileOpen = { fileInfo -> openFile(fileInfo) }
                        )
                    }
                    // 显示打开的文件
                    currentFileIndex in openFiles.indices -> {
                        val openedFile = openFiles[currentFileIndex]
                        val isPreviewMode = filePreviewStates[openedFile.key] ?: false
                        val previewSession = editorSessions[openedFile.key]
                        val fileInfo = if (isPreviewMode && previewSession != null)
                            openedFile.copy(content = previewSession.document.snapshot().toString()) else openedFile

                        when {
                            // 图片文件：显示图片预览
                            fileInfo.isImage -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = fileInfo.environment.takeUnless { it == "android" },
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                WorkspaceImagePreview(
                                    fileName = fileInfo.name,
                                    previewUri = previewUri,
                                    isSourceLoading = previewFileState.loading,
                                    errorMessage = previewFileState.errorMessage,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            fileInfo.isAudio -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = fileInfo.environment.takeUnless { it == "android" },
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewFileState.loading) {
                                        CircularProgressIndicator()
                                    } else if (previewUri != null) {
                                        AudioAttachmentPlayer(
                                            uri = previewUri,
                                            modifier = Modifier.fillMaxWidth(),
                                            autoPlay = false
                                        )
                                    } else {
                                        Text(
                                            text = previewFileState.errorMessage
                                                ?: context.getString(R.string.cannot_open_file, fileInfo.name),
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            fileInfo.isVideo -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = fileInfo.environment.takeUnless { it == "android" },
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewFileState.loading) {
                                        CircularProgressIndicator(color = Color.White)
                                    } else if (previewUri != null) {
                                        VideoAttachmentPlayer(
                                            uri = previewUri,
                                            modifier = Modifier.fillMaxSize(),
                                            autoPlay = false
                                        )
                                    } else {
                                        Text(
                                            text = previewFileState.errorMessage
                                                ?: context.getString(R.string.cannot_open_file, fileInfo.name),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            fileInfo.isReadOnlyDocumentPreviewable -> {
                                WorkspaceReadOnlyDocumentPreview(
                                    fileInfo = fileInfo,
                                    workspaceEnv = fileInfo.environment.takeUnless { it == "android" },
                                    toolHandler = toolHandler,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            fileInfo.isMarkdown && isPreviewMode -> {
                                browserStateHolder.SaveableStateProvider("markdown:${fileInfo.key}") {
                                    WorkspaceMarkdownPreview(
                                        fileInfo = fileInfo,
                                        onFileLink = ::openMarkdownFileLink,
                                        outlineVisible = markdownOutlineVisible,
                                        onOutlineDismiss = { markdownOutlineVisible = false },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            // HTML文件的预览模式：使用WebView
                            fileInfo.isHtml && isPreviewMode -> {
                                WorkspaceHtmlPreview(
                                    fileInfo = fileInfo,
                                    searchVisible = htmlFindVisible,
                                    onCloseSearch = { htmlFindVisible = false },
                                    // 文件预览直接使用对话选择器，避免依赖已移除的项目预览处理器。
                                    onFileChooserRequest = actualViewModel::startFileChooserForResult,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            // 其他所有情况：使用CodeEditor
                            else -> {
                                key(fileInfo.key) {
                                    val fileLanguage = LanguageDetector.detectLanguage(fileInfo.name)
                                    CodeEditor(
                                            session = editorSessions.getOrPut(fileInfo.key) { EditorSession(fileInfo.content, WorkspaceChangeAccess.backend(context, fileInfo.environment)) },
                                            readOnly = editorSession?.readOnly == true,
                                            enableCompletion = false,
                                            code = fileInfo.content,
                                            language = fileLanguage,
                                            fileKey = fileInfo.key,
                                            fileName = fileInfo.name,
                                            onDocumentChange = {
                                                if (openFiles.any { it.key == fileInfo.key } && fileInfo.key !in unsavedFiles) {
                                                    unsavedFiles = unsavedFiles + fileInfo.key
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            searchMatches = searchMatches,
                                            activeSearchMatch = activeMatch,
                                            searchNavigationRequest = searchNavigationRequest,
                                            initialLine = fileInfo.initialLine,
                                            initialLineRequest = fileLineNavigationRequests[fileInfo.key] ?: 0,
                                            editorRef = { editor -> if (editor != null || openFiles.getOrNull(currentFileIndex)?.key == fileInfo.key) activeEditor = editor },
                                            onKeyCommand = ::handleEditorKeyCommand,
                                            onFind = ::openEditorSearch
                                    )
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(searchVisible && searchableFile != null, enter = expandVertically(expandFrom = Alignment.Bottom), exit = shrinkVertically(shrinkTowards = Alignment.Bottom)) {
                EditorFindPanel(searchQuery, { if (searchQuery != it) { searchQuery = it; editorSearchPending = true } }, replaceText, { replaceText = it },
                    replacementVisible, { replacementVisible = it }, searchOpenRequest,
                    editorSearchOptions, { editorSearchOptions = it; editorSearchPending = true }, searchMatches.size, activeMatch, editorSearchError,
                    writable = editorSession?.readOnly == false && !replacing,
                    inSelection = editorSession?.searchScope != null, onClearScope = { setEditorSearchScope(null) },
                    onPrevious = { if (!editorSearchPending && searchMatches.isNotEmpty()) { activeMatch = (activeMatch - 1 + searchMatches.size) % searchMatches.size; searchNavigationRequest++ } },
                    onNext = { if (!editorSearchPending && searchMatches.isNotEmpty()) { activeMatch = (activeMatch + 1) % searchMatches.size; searchNavigationRequest++ } },
                    onReplace = { performEditorReplacement(false) },
                    onReplaceAll = { performEditorReplacement(true) }, onClose = ::closeEditorSearch, searching = editorSearchPending)
            }

        }

        // 解绑确认对话框
        if (isVisible && showUnbindConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showUnbindConfirmDialog = false },
                title = { Text(context.getString(R.string.unbind_workspace_title)) },
                text = { Text(context.getString(R.string.unbind_workspace_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actualViewModel.unbindChatFromWorkspace(currentChat.id)
                            showUnbindConfirmDialog = false
                        }
                    ) {
                        Text(context.getString(R.string.confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnbindConfirmDialog = false }) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            )
        }

        if (isVisible && showRenameWorkspaceDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isRenamingWorkspace) {
                        showRenameWorkspaceDialog = false
                        renameWorkspaceError = null
                    }
                },
                title = { Text(context.getString(R.string.workspace_rename_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = renameWorkspaceInput,
                            onValueChange = {
                                renameWorkspaceInput = it
                                renameWorkspaceError = null
                            },
                            label = { Text(context.getString(R.string.file_dialog_new_name)) },
                            singleLine = true,
                            isError = renameWorkspaceError != null,
                            supportingText = {
                                renameWorkspaceError?.let { Text(it) }
                            }
                        )
                        Text(
                            text = context.getString(R.string.workspace_rename_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isRenamingWorkspace,
                        onClick = {
                            if (unsavedFiles.isNotEmpty()) {
                                renameWorkspaceError =
                                    context.getString(R.string.workspace_rename_save_first)
                                return@TextButton
                            }
                            isRenamingWorkspace = true
                            coroutineScope.launch {
                                runCatching {
                                    actualViewModel.renameWorkspace(
                                        chatId = currentChat.id,
                                        newWorkspaceName = renameWorkspaceInput
                                    )
                                }.onSuccess { result ->
                                    OKToast.success(context,
                                        context.getString(
                                            R.string.workspace_rename_success,
                                            result.workspaceName
                                        )
                                    ).show()
                                    showRenameWorkspaceDialog = false
                                    renameWorkspaceError = null
                                }.onFailure { error ->
                                    renameWorkspaceError =
                                        error.message
                                            ?: context.getString(R.string.workspace_rename_failed)
                                }
                                isRenamingWorkspace = false
                            }
                        }
                    ) {
                        Text(context.getString(R.string.confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isRenamingWorkspace,
                        onClick = {
                            showRenameWorkspaceDialog = false
                            renameWorkspaceError = null
                        }
                    ) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            )
        }
        
        // 关闭文件确认对话框
        if (isVisible && fileToCloseIndex != -1) {
            val file = openFiles.getOrNull(fileToCloseIndex)
            if (file != null) {
                AlertDialog(
                    onDismissRequest = { fileToCloseIndex = -1 },
                    title = { Text(context.getString(R.string.save_changes_question)) },
                    text = { Text(context.getString(R.string.file_modified_save_prompt, file.name)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val closingKey = file.key
                                saveFile(file) {
                                    confirmCloseFile(openFiles.indexOfFirst { it.key == closingKey })
                                }
                            }
                        ) {
                            Text(context.getString(R.string.save))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { fileToCloseIndex = -1 }) {
                                Text(context.getString(R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    confirmCloseFile(fileToCloseIndex)
                                }
                            ) {
                                Text(context.getString(R.string.dont_save))
                            }
                        }
                    }
                )
            }
        }

    }
}

/** 紧凑文件标签，激活后在共用导航行内滚动到可见位置。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VSCodeTab(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isActive: Boolean,
        isUnsaved: Boolean,
        onClose: (() -> Unit)? = null,
        onClick: () -> Unit
) {
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(isActive) { if (isActive) { withFrameNanos { }; reveal.bringIntoView() } }
    WorkspaceNavigationTab(title = title, selected = isActive, onClick = onClick,
        modifier = Modifier.bringIntoViewRequester(reveal), icon = icon, unsaved = isUnsaved, onClose = onClose)
}
