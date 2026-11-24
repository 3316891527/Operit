package com.ai.assistance.operit.ui.features.conversation.workspace

import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeTextField as TextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.workspaceMimeTypeForFile
import com.ai.assistance.operit.ui.features.conversation.workspace.text.WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES
import com.ai.assistance.operit.ui.features.conversation.workspace.text.readWorkspaceTextWithinLimit
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.*
import com.ai.assistance.operit.ui.features.conversation.workspace.svg.*
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.*
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceCopyProgressDialog
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceDeleteProgressDialog
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboard
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserContextMenu
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserOperations
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.WorkspacePropertiesTarget
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.WorkspaceBatchPropertiesDialog
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.WorkspacePropertiesDialog
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserSortDialog
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.isValidWorkspaceEntryName
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.sortDirectoryEntries

// 目录条目数据类
data class DirectoryEntry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: String,
        val permissions: String
)

// 快速路径条目
data class QuickPathEntry(
        val name: String,
        val path: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val environment: String = "android"
)

/** 文件浏览器组件 - VSCode风格 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowser(
        initialPath: String,
        environment: String? = null,
        onBindWorkspace: ((String, String?) -> Unit)? = null,
        onCancel: () -> Unit,
        showHeader: Boolean = true,
        workspaceRoot: WorkspaceLocation? = null,
        entrySequence: Long = 0,
        onUnbindWorkspace: (() -> Unit)? = null,
        onRenameWorkspace: (() -> Unit)? = null,
        onFileOpen: (OpenFileInfo) -> Unit
) {
    val context = LocalContext.current
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val browserPreferences = remember(context) { WorkspaceBrowserPreferences(context) }
    val browserSettings by browserPreferences.settings.collectAsState(initial = WorkspaceBrowserSettings())
    val browserActive = LocalWorkspaceBrowserActive.current
    val menuHost = LocalWorkspaceBrowserMenu.current.takeUnless { showHeader }
    val localMenuHost = remember { WorkspaceBrowserMenuHost() }
    val activeMenuHost = menuHost ?: localMenuHost
    var showPathDialog by remember { mutableStateOf(false) }
    var showFullSearch by remember { mutableStateOf(false) }
    var searchScope by remember { mutableStateOf(emptyList<WorkspaceSearchEntry>()) }
    var archivePath by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveEnvironment by rememberSaveable { mutableStateOf(workspaceEnvironment(environment)) }
    var extractionTargets by remember { mutableStateOf<List<WorkspaceLocation>?>(null) }
    var compressionTargets by remember { mutableStateOf<List<FileBrowserClipboardItem>?>(null) }
    var bookmarkLocation by remember { mutableStateOf<WorkspaceLocation?>(null) }
    var showBookmarks by remember { mutableStateOf(false) }
    var applyHome by rememberSaveable(initialPath, environment) { mutableStateOf(true) }
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val safBookmarks by apiPreferences.safBookmarksFlow.collectAsState(initial = emptyList())
    var currentPath by rememberSaveable(initialPath, environment) { mutableStateOf(initialPath) }
    var currentEnvironment by rememberSaveable(initialPath, environment) { mutableStateOf(environment) }
    var fileList by remember { mutableStateOf<List<DirectoryEntry>>(emptyList()) }
    var isOperationLoading by remember { mutableStateOf(false) }
    var fileOperationJob by remember { mutableStateOf<Job?>(null) }
    var cancellingFileOperation by remember { mutableStateOf(false) }
    var isDirectoryLoading by remember { mutableStateOf(false) }
    var directoryLoadJob by remember { mutableStateOf<Job?>(null) }
    var directoryLoadVersion by remember { mutableIntStateOf(0) }
    val isLoading = isOperationLoading || isDirectoryLoading
    val coroutineScope = rememberCoroutineScope()
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileNameError by remember { mutableStateOf(false) }
    var pendingRepoBookmarkUri by remember { mutableStateOf<Uri?>(null) }
    var repoBookmarkNameInput by remember { mutableStateOf("") }
    var showRepoBookmarkNameDialog by remember { mutableStateOf(false) }
    var repoBookmarkNameError by remember { mutableStateOf<String?>(null) }
    // 用于控制长按上下文菜单的状态
    var contextMenuExpandedFor by remember { mutableStateOf<DirectoryEntry?>(null) }
    var svgConversion by remember(currentPath, currentEnvironment) { mutableStateOf<WorkspaceSvgConversionRequest?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(0) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var foldersFirst by rememberSaveable { mutableStateOf(true) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<DirectoryEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DirectoryEntry?>(null) }
    var propertiesTarget by remember { mutableStateOf<WorkspacePropertiesTarget?>(null) }
    var batchPropertiesTargets by remember { mutableStateOf<List<WorkspacePropertiesTarget>?>(null) }
    val listState = rememberLazyListState()
    val operations = remember(context) { FileBrowserOperations(context) }
    val clipboard by FileBrowserClipboard.content.collectAsState()
    val copyProgress by operations.copyProgress.collectAsState()
    val deleteProgress by operations.deleteProgress.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var selectionMode by rememberSaveable(currentPath, currentEnvironment) { mutableStateOf(false) }
    var selectedNames by rememberSaveable(currentPath, currentEnvironment) { mutableStateOf(emptySet<String>()) }
    var selectionAnchor by rememberSaveable(currentPath, currentEnvironment) { mutableStateOf<String?>(null) }
    var showSelectionMenu by remember(currentPath, currentEnvironment) { mutableStateOf(false) }
    var swipeInProgress by remember(currentPath, currentEnvironment) { mutableStateOf(false) }
    var pendingBatchDelete by remember { mutableStateOf<List<FileBrowserClipboardItem>?>(null) }
    val currentLocation = WorkspaceLocation(currentPath, workspaceEnvironment(currentEnvironment))
    val visibleFiles = remember(fileList, browserSettings, currentLocation, searchQuery, sortMode, descending, ignoreCase, foldersFirst) {
        sortDirectoryEntries(fileList.filter {
            !isWorkspaceEntryHidden(workspaceChild(currentLocation, it.name), it.name, browserSettings) &&
                it.name.contains(searchQuery, ignoreCase = true)
        }, sortMode, descending, ignoreCase, foldersFirst)
    }
    fun updateSettings(transform: (WorkspaceBrowserSettings) -> WorkspaceBrowserSettings) {
        coroutineScope.launch {
            try { browserPreferences.update(transform) } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (failure: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "保存浏览偏好失败", failure)
                errorMessage = failure.localizedMessage
            }
        }
    }
    fun finishSelection() { selectionMode = false; selectedNames = emptySet(); selectionAnchor = null; showSelectionMenu = false }
    fun toggleSelection(name: String) {
        selectedNames = if (name in selectedNames) selectedNames - name else selectedNames + name
    }



    fun swipeSelect(name: String) {
        if (isLoading || !browserActive) return
        val range = selectWorkspaceRange(visibleFiles.map { it.name }, selectedNames,
            selectionAnchor.takeIf { selectionMode }, name)
        selectionMode = true
        selectedNames = range.selected
        selectionAnchor = range.anchor
    }
    fun queryRepoBookmarkName(uri: Uri): String {
        fun normalizeName(raw: String): String {
            return raw.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), "_")
                .ifBlank { "repo" }
        }

        val providerLabel =
            runCatching {
                val authority = uri.authority ?: return@runCatching null
                val provider = context.packageManager.resolveContentProvider(authority, 0)
                provider?.applicationInfo?.loadLabel(context.packageManager)?.toString()?.trim()
            }.getOrNull()

        val raw = providerLabel?.takeIf { it.isNotBlank() } ?: uri.authority ?: "repo"
        return normalizeName(raw)
    }

    val isSafEnv = remember(currentEnvironment) { currentEnvironment?.startsWith("repo:", ignoreCase = true) == true }

    fun joinPath(parent: String, child: String): String {
        val p = if (parent.isBlank()) "/" else parent
        return if (p.endsWith("/")) "$p$child" else "$p/$child"
    }

    fun parentPath(path: String): String? {
        val normalized = path.trimEnd('/').ifBlank { "/" }
        if (normalized == "/") return null
        val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isBlank()) "/" else parent
    }

    val linuxInstalled = rememberWorkspaceLinuxInstalled()
    // 入口只依据安装完成标记，浏览文件不创建终端会话。
    val quickPaths = remember(linuxInstalled, workspaceRoot) {
        buildList {
            if (linuxInstalled) add(QuickPathEntry(
                name = "Linux",
                path = "/",
                icon = Icons.Default.Terminal, environment = "linux"
            ))
            add(QuickPathEntry(
                name = "SDCard",
                path = Environment.getExternalStorageDirectory().absolutePath,
                icon = Icons.Default.SdCard
            ))
            add(QuickPathEntry(
                name = "Workspace",
                path = workspaceRoot?.path ?: File(context.filesDir, "workspace").absolutePath,
                icon = Icons.Default.Folder, environment = workspaceRoot?.environment ?: "android"
            ))
        }
    }

    // 本机 Android 路径直接使用 File API，避免权限级文件工具的 shell 列表解析漏掉普通文件。
    suspend fun listLocalDirectory(path: String): List<DirectoryEntry>? = withContext(Dispatchers.IO) {
        val directory = File(path)
        if (!directory.isDirectory) return@withContext null
        val entries = directory.listFiles() ?: return@withContext null
        entries
            .filter { it.name != "." && it.name != ".." }
            .map { file ->
                DirectoryEntry(
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified().toString(),
                    permissions = ""
                )
            }
    }

    fun publishDirectory(entries: List<DirectoryEntry>, path: String, targetEnvironment: String?) {
        // 只用成功读取的目录修正选择；初始空列表不能清掉恢复的多选状态。
        if (path == currentPath && targetEnvironment == currentEnvironment) {
            selectedNames = selectedNames.intersect(entries.map { it.name }.toSet())
        } else {
            finishSelection()
        }
        fileList = entries
        currentPath = path
        currentEnvironment = targetEnvironment
        errorMessage = null
    }

    fun loadDirectory(path: String, targetEnvironment: String? = currentEnvironment) {
        if (isOperationLoading) return
        // 新导航替换旧读取；旧任务的 finally 不得清除新任务的加载状态。
        val requestVersion = ++directoryLoadVersion
        directoryLoadJob?.cancel()
        isDirectoryLoading = true
        directoryLoadJob = coroutineScope.launch {
            try {
                val localEntries =
                    if (targetEnvironment.isNullOrBlank() || targetEnvironment.equals("android", ignoreCase = true)) {
                        listLocalDirectory(path)
                    } else {
                        null
                    }
                if (localEntries != null) {
                    publishDirectory(localEntries, path, targetEnvironment)
                    AppLogger.d(
                        "WorkspaceFileBrowser",
                        "listed local path=$path directories=${localEntries.count { it.isDirectory }} files=${localEntries.count { !it.isDirectory }}"
                    )
                    return@launch
                }

                val parameters = buildList {
                    add(ToolParameter("path", path))
                    targetEnvironment?.let { add(ToolParameter("environment", it)) }
                }
                val tool = AITool("list_files", parameters)
                AppLogger.d("WorkspaceFileBrowser", "execute list_files path=$path env=$currentEnvironment")
                val result = withContext(Dispatchers.IO) { toolHandler.executeTool(tool) }
                AppLogger.d("WorkspaceFileBrowser", "result list_files success=${result.success} error=${result.error}")
                if (result.success && result.result is DirectoryListingData) {
                    val entries = (result.result as DirectoryListingData).entries
                    val listedEntries = entries.map {
                        DirectoryEntry(
                            name = it.name,
                            isDirectory = it.isDirectory,
                            size = it.size,
                            lastModified = it.lastModified,
                            permissions = it.permissions
                        )
                    }
                    publishDirectory(listedEntries, path, targetEnvironment)
                } else {
                    errorMessage = result.error
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "读取目录失败", e)
                errorMessage = e.message
            } finally {
                if (requestVersion == directoryLoadVersion) isDirectoryLoading = false
            }
        }
    }

    val addSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            pendingRepoBookmarkUri = uri
            repoBookmarkNameInput = queryRepoBookmarkName(uri)
            showRepoBookmarkNameDialog = true
        }
    }

    if (showRepoBookmarkNameDialog && browserActive) {
        AlertDialog(
            onDismissRequest = {
                showRepoBookmarkNameDialog = false
                pendingRepoBookmarkUri = null
                repoBookmarkNameError = null
            },
            title = { Text(stringResource(R.string.repo_bookmark_name)) },
            text = {
                TextField(
                    value = repoBookmarkNameInput,
                    onValueChange = {
                        repoBookmarkNameInput = it
                        repoBookmarkNameError = null
                    },
                    label = { Text(stringResource(R.string.repo_bookmark_name_label)) },
                    singleLine = true,
                    isError = repoBookmarkNameError != null,
                    supportingText = {
                        repoBookmarkNameError?.let { Text(it) }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRepoBookmarkUri
                        val name = repoBookmarkNameInput.trim()
                        if (uri == null) {
                            showRepoBookmarkNameDialog = false
                            pendingRepoBookmarkUri = null
                            repoBookmarkNameError = null
                            return@TextButton
                        }

                        if (name.isEmpty()) {
                            repoBookmarkNameError = context.getString(R.string.repo_bookmark_name_empty)
                            return@TextButton
                        }

                        val nameExists = safBookmarks.any {
                            it.uri != uri.toString() && it.name.equals(name, ignoreCase = true)
                        }
                        if (nameExists) {
                            repoBookmarkNameError = context.getString(R.string.repo_bookmark_name_exists)
                            return@TextButton
                        }

                        coroutineScope.launch {
                            apiPreferences.addSafBookmark(uri.toString(), name)
                            loadDirectory("/", "repo:$name")
                        }

                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    fun selectedEntries(cut: Boolean): List<FileBrowserClipboardItem> = fileList
        .filter { it.name in selectedNames }
        .map { FileBrowserClipboardItem(joinPath(currentPath, it.name), currentEnvironment, cut, it.isDirectory) }

    fun createNewFile(fileName: String, isDirectory: Boolean) {
        if (isLoading) return
        if (!isValidWorkspaceEntryName(fileName)) {
            newFileNameError = true
            return
        }
        val createPath = currentPath
        val createEnvironment = currentEnvironment
        val createInRepository = isSafEnv
        isOperationLoading = true
        coroutineScope.launch {
            var succeeded = false
            try {
                val filePath =
                    if (createInRepository) {
                        joinPath(createPath, fileName)
                    } else {
                        File(createPath, fileName).path
                    }
                val tool =
                    if (isDirectory) {
                        AITool("make_directory", listOf(ToolParameter("path", filePath)) + listOfNotNull(createEnvironment?.let { ToolParameter("environment", it) }))
                    } else {
                        AITool(
                            "write_file",
                            listOf(
                                ToolParameter("path", filePath),
                                ToolParameter("content", "")
                            ) + listOfNotNull(createEnvironment?.let { ToolParameter("environment", it) })
                        )
                    }
                AppLogger.d("WorkspaceFileBrowser", "execute ${tool.name} path=$filePath env=$createEnvironment")
                val result = withContext(Dispatchers.IO) { toolHandler.executeTool(tool) }
                check(result.success) { result.error.orEmpty() }
                errorMessage = null
                succeeded = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "创建文件失败", e)
                errorMessage = e.message
            } finally {
                isOperationLoading = false
            }
            if (succeeded) loadDirectory(currentPath)
        }
    }

    fun runOperation(block: suspend () -> Unit) {
        if (isLoading) return
        isOperationLoading = true
        cancellingFileOperation = false
        fileOperationJob = coroutineScope.launch {
            try {
                block()
                errorMessage = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "文件操作失败", e)
                errorMessage = e.message
            } finally {
                isOperationLoading = false
                fileOperationJob = null
                loadDirectory(currentPath)
            }
        }
    }

    pendingBatchDelete?.takeIf { browserActive }?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = null },
            title = { Text(stringResource(R.string.workspace_delete_selected_title)) },
            text = { Text(stringResource(R.string.workspace_delete_selected_message, targets.size)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingBatchDelete = null
                    runOperation {
                        operations.delete(targets) { target ->
                            withContext(Dispatchers.Main) {
                                selectedNames = selectedNames - target.path.substringAfterLast('/')
                                if (selectedNames.isEmpty()) selectionMode = false
                            }
                        }
                    }
                }, enabled = !isLoading) { Text(stringResource(R.string.file_manager_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingBatchDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    fun openFile(filePath: String, asText: Boolean = false, fileEnvironment: String = workspaceEnvironment(currentEnvironment), initialLine: Int? = null) {
        if (isLoading) return
        if (!asText && workspaceArchiveKind(filePath) != null) {
            archivePath = filePath
            archiveEnvironment = fileEnvironment
            return
        }
        isOperationLoading = true
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val mimeType = if (asText) "text/plain" else
                        workspaceMimeTypeForFile(context, filePath, fileEnvironment)
                    val route = if (asText) WorkspaceFileRoute.TEXT else workspaceFileRoute(filePath, mimeType)
                    if (route == WorkspaceFileRoute.SYSTEM) {
                        operations.externalOpen(filePath, fileEnvironment, false)
                        null
                    } else if (route == WorkspaceFileRoute.PREVIEW) {
                        OpenFileInfo(filePath, "", System.currentTimeMillis(), mimeType = mimeType, environment = fileEnvironment)
                    } else {
                        val parameters = listOf(ToolParameter("path", filePath),
                            ToolParameter("environment", fileEnvironment))
                        val exists = toolHandler.executeTool(AITool("file_exists", parameters))
                        check(exists.success) { exists.error.orEmpty() }
                        val info = exists.result
                        check(info is FileExistsData && info.exists && !info.isDirectory) {
                            context.getString(R.string.cannot_open_file, filePath)
                        }
                        checkNotNull(readWorkspaceTextWithinLimit(info.size) {
                            val read = toolHandler.executeTool(AITool("read_file_full",
                                parameters + ToolParameter("text_only", "true")))
                            check(read.success && read.result is FileContentData) { read.error.orEmpty() }
                            OpenFileInfo(filePath, (read.result as FileContentData).content,
                                System.currentTimeMillis(), mimeType = "text/plain", environment = fileEnvironment)
                        }) {
                            context.getString(R.string.workspace_text_preview_too_large,
                                (WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES / (1024 * 1024)).toInt())
                        }
                    }
                }
                result?.let { onFileOpen(it.copy(initialLine = initialLine)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "打开文件失败", e)
                errorMessage = e.message
            } finally {
                isOperationLoading = false
            }
        }
    }

    var handledEntry by rememberSaveable(initialPath, environment) { mutableLongStateOf(-1L) }
    LaunchedEffect(initialPath, environment, linuxInstalled, workspaceRoot, entrySequence) {
        // 项目入口回到真实根目录；自由浏览才应用首页，编辑器返回不重置导航。
        val projectEntry = workspaceRoot != null && handledEntry != entrySequence
        handledEntry = entrySequence
        val savedSettings = browserPreferences.settings.first()
        val home = if (projectEntry) workspaceRoot else savedSettings.home.takeIf { applyHome && workspaceRoot == null }
        applyHome = false
        val pathToLoad = home?.path ?: currentPath
        val environmentToLoad = home?.environment ?: currentEnvironment
        if (environmentToLoad.equals("linux", ignoreCase = true) && !linuxInstalled) {
            errorMessage = context.getString(R.string.hub_unprepared)
        } else {
            loadDirectory(pathToLoad, environmentToLoad)
        }
    }

    BackHandler(enabled = browserActive && !isOperationLoading &&
        (swipeInProgress || selectionMode || searchVisible || parentPath(currentPath) != null)) {
        when {
            swipeInProgress -> Unit
            selectionMode -> finishSelection()
            searchVisible -> { searchQuery = ""; searchVisible = false }
            else -> parentPath(currentPath)?.let { loadDirectory(it) }
        }
    }
    LaunchedEffect(currentPath, currentEnvironment) { listState.scrollToItem(0) }
    LaunchedEffect(browserActive) {
        if (!browserActive) {
            showFullSearch = false
            archivePath = null
            extractionTargets = null
            compressionTargets = null
            showPathDialog = false
            showBookmarks = false
            bookmarkLocation = null
            contextMenuExpandedFor = null
            svgConversion = null
            showSelectionMenu = false
            swipeInProgress = false
            batchPropertiesTargets = null
        }
    }
    val menuContent: @Composable ColumnScope.() -> Unit = {
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_search), Icons.Default.Search, !isLoading) { searchScope = emptyList(); showFullSearch = true }
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_filter), Icons.Default.FilterAlt, checked = searchVisible) {
            searchVisible = !searchVisible
            if (!searchVisible) searchQuery = ""
        }
        WorkspaceBrowserMenuItem(stringResource(R.string.file_manager_sort), Icons.Default.Sort) { showSortMenu = true }
        WorkspaceBrowserMenuItem(stringResource(R.string.file_manager_refresh), Icons.Default.Refresh, !isLoading) { loadDirectory(currentPath) }
        WorkspaceBrowserMenuItem(stringResource(R.string.file_manager_new), Icons.Default.Add, !isLoading) { showCreateFileDialog = true }
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_multi_select), Icons.Default.CheckBox, !isLoading, selectionMode) {
            if (selectionMode) finishSelection() else selectionMode = true
        }
        if (onUnbindWorkspace != null) WorkspaceBrowserMenuItem(stringResource(R.string.unbind), Icons.Default.LinkOff, !isLoading) {
            onUnbindWorkspace()
        }
        if (onRenameWorkspace != null) WorkspaceBrowserMenuItem(stringResource(R.string.workspace_rename_action), Icons.Default.DriveFileRenameOutline, !isLoading) {
            onRenameWorkspace()
        }
        if (selectionMode) WorkspaceBrowserMenuItem(stringResource(R.string.select_all_current_list), Icons.Default.SelectAll, !isLoading) {
            selectedNames = visibleFiles.map { it.name }.toSet()
        }
        if (clipboard.isNotEmpty()) WorkspaceBrowserMenuItem(stringResource(R.string.file_manager_paste), Icons.Default.ContentPaste, !isLoading) {
            runOperation { operations.paste(currentPath, currentEnvironment) }
        }
        HorizontalDivider()
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_add_bookmark), Icons.Default.Bookmark) { bookmarkLocation = currentLocation }
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_bookmarks), Icons.Default.Book) { showBookmarks = true }
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_go_home), Icons.Default.Home, !isLoading) {
            val home = browserSettings.home ?: WorkspaceLocation(initialPath, workspaceEnvironment(environment))
            loadDirectory(home.path, home.environment)
        }
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_set_home), Icons.Default.Save) { updateSettings { it.copy(home = currentLocation) } }
        if (browserSettings.home != null) WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_reset_home), Icons.Default.RestartAlt) {
            updateSettings { it.copy(home = null) }
        }
        HorizontalDivider()
        WorkspaceBrowserMenuItem(stringResource(R.string.file_manager_show_dot_files), Icons.Default.Visibility, checked = browserSettings.showDotFiles) {
            updateSettings { it.copy(showDotFiles = !it.showDotFiles) }
        }
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_show_hidden), Icons.Default.VisibilityOff, checked = browserSettings.showManuallyHidden) {
            updateSettings { it.copy(showManuallyHidden = !it.showManuallyHidden) }
        }
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_browser_edit_hidden_file), Icons.Default.Edit, !isLoading) {
            isOperationLoading = true
            val targetEnvironment = currentEnvironment
            coroutineScope.launch {
                try {
                    val fileInfo = withContext(Dispatchers.IO) {
                        val file = browserPreferences.hiddenPathsFile(targetEnvironment)
                        checkNotNull(readWorkspaceTextWithinLimit(file.length()) {
                            OpenFileInfo(file.path, file.readText(Charsets.UTF_8), file.lastModified(), mimeType = "text/plain", environment = "android")
                        }) { context.getString(R.string.workspace_text_preview_too_large, (WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES / (1024 * 1024)).toInt()) }
                    }
                    onFileOpen(fileInfo)
                    OKToast.info(context, R.string.workspace_browser_hidden_file_hint, OKToast.LENGTH_LONG).show()
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    AppLogger.e("WorkspaceFileBrowser", "打开隐藏路径文件失败", failure)
                    errorMessage = failure.localizedMessage ?: failure.javaClass.simpleName
                } finally { isOperationLoading = false }
            }
        }
        WorkspaceBrowserMenuItem(stringResource(R.string.workspace_properties_title), Icons.Default.Info, !isLoading) {
            propertiesTarget = WorkspacePropertiesTarget(currentPath, currentEnvironment)
        }
    }
    val latestMenu by rememberUpdatedState(menuContent)
    val menuOwner = remember { Any() }
    DisposableEffect(activeMenuHost, browserActive) {
        if (browserActive) activeMenuHost.register(menuOwner) { latestMenu() }
        onDispose { activeMenuHost.unregister(menuOwner) }
    }
    if (showPathDialog && browserActive) WorkspacePathDialog(currentLocation, { showPathDialog = false }) {
        showPathDialog = false
        loadDirectory(it.path, it.environment)
    }
    bookmarkLocation?.takeIf { browserActive }?.let { location -> WorkspaceBookmarkDialog(location, { bookmarkLocation = null }) { name ->
        bookmarkLocation = null
        updateSettings { it.copy(bookmarks = it.bookmarks.filterNot { entry -> entry.location == location } + WorkspaceBookmark(name, location)) }
    } }
    if (showBookmarks && browserActive) WorkspaceBookmarksDialog(browserSettings.bookmarks, { showBookmarks = false }, {
        showBookmarks = false
        loadDirectory(it.path, it.environment)
    }, { location -> updateSettings { it.copy(bookmarks = it.bookmarks.filterNot { entry -> entry.location == location }) } })
    if (showFullSearch && browserActive) WorkspaceSearchDialog(currentLocation, browserSettings, { showFullSearch = false }, selected = searchScope) { hit ->
        showFullSearch = false
        if (hit.entry.directory) loadDirectory(hit.entry.location.path, hit.entry.location.environment)
        else openFile(hit.entry.location.path, asText = hit.content != null,
            fileEnvironment = hit.entry.location.environment, initialLine = hit.content?.line)
    }

    if (showCreateFileDialog && browserActive) {
        AlertDialog(
                onDismissRequest = { showCreateFileDialog = false },
                title = { Text(stringResource(R.string.file_manager_create_new_file)) },
                text = {
                    Column {
                        TextField(
                                value = newFileName,
                                onValueChange = { newFileName = it; newFileNameError = false },
                                isError = newFileNameError,
                                supportingText = {
                                    if (newFileNameError) Text(stringResource(R.string.workspace_rename_name_invalid))
                                },
                                label = { Text(stringResource(R.string.file_manager_file_name)) },
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor =
                                                        MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor =
                                                        MaterialTheme.colorScheme.surface
                                        )
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                                onClick = {
                                    val name = newFileName.trim()
                                    if (!isValidWorkspaceEntryName(name)) {
                                        newFileNameError = true
                                        return@TextButton
                                    }
                                    createNewFile(name, false)
                                    showCreateFileDialog = false
                                    newFileName = ""
                                    newFileNameError = false
                                }
                        ) { Text(stringResource(R.string.file_manager_create_file)) }
                        TextButton(
                                onClick = {
                                    val name = newFileName.trim()
                                    if (!isValidWorkspaceEntryName(name)) {
                                        newFileNameError = true
                                        return@TextButton
                                    }
                                    createNewFile(name, true)
                                    showCreateFileDialog = false
                                    newFileName = ""
                                    newFileNameError = false
                                }
                        ) { Text(stringResource(R.string.file_manager_create_folder)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFileDialog = false }) { Text(stringResource(R.string.file_manager_cancel)) }
                }
        )
    }

    if (showSortMenu && browserActive) {
        FileBrowserSortDialog(sortMode, descending, ignoreCase, foldersFirst,
            onDismiss = { showSortMenu = false },
            onConfirm = { mode, reverse, insensitive, folders ->
                sortMode = mode
                descending = reverse
                ignoreCase = insensitive
                foldersFirst = folders
                showSortMenu = false
            })
    }
    renameTarget?.takeIf { browserActive }?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.file_menu_rename)) },
            text = { OutlinedTextField(renameInput, { renameInput = it }, singleLine = true,
                label = { Text(stringResource(R.string.file_dialog_new_name)) }) },
            confirmButton = { TextButton(onClick = {
                val name = renameInput.trim()
                if (renameInput == target.name || name == target.name) {
                    renameTarget = null
                    return@TextButton
                }
                if (isValidWorkspaceEntryName(name)) {
                    renameTarget = null
                    runOperation { operations.rename(joinPath(currentPath, target.name), joinPath(currentPath, name), currentEnvironment) }
                }
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    deleteTarget?.takeIf { browserActive }?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.file_op_confirm_delete)) },
            text = { Text(stringResource(R.string.file_dialog_delete_single, target.name)) },
            confirmButton = { TextButton(onClick = {
                deleteTarget = null
                runOperation { operations.delete(joinPath(currentPath, target.name), currentEnvironment, target.isDirectory) }
            }) { Text(stringResource(R.string.file_manager_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } })
    }
    copyProgress?.takeIf { browserActive }?.let { progress ->
        key(progress.sourceEnvironment, progress.source, progress.destinationEnvironment, progress.destination) {
            WorkspaceCopyProgressDialog(progress, cancelling = cancellingFileOperation, onCancel = {
                cancellingFileOperation = true
                fileOperationJob?.cancel()
            })
        }
    }
    deleteProgress?.takeIf { browserActive }?.let { progress ->
        WorkspaceDeleteProgressDialog(progress, cancelling = cancellingFileOperation, onCancel = {
            cancellingFileOperation = true
            fileOperationJob?.cancel()
        })
    }
    archivePath?.takeIf { browserActive }?.let { path ->
        key(path, archiveEnvironment) {
            WorkspaceArchiveBrowser(WorkspaceLocation(path, archiveEnvironment),
                onDismiss = { archivePath = null; loadDirectory(currentPath) },
                onOpen = { opened -> openFile(opened.path, fileEnvironment = opened.environment) })
        }
    }
    extractionTargets?.takeIf { browserActive }?.let { targets ->
        WorkspaceArchiveExtractDialog(targets, currentLocation, onDismiss = { extractionTargets = null },
            onComplete = { extractionTargets = null; finishSelection(); loadDirectory(currentPath) })
    }
    compressionTargets?.takeIf { browserActive }?.let { targets ->
        WorkspaceCompressionDialog(targets, currentLocation, onDismiss = { compressionTargets = null },
            onComplete = { compressionTargets = null; finishSelection(); loadDirectory(currentPath) })
    }
    batchPropertiesTargets?.takeIf { browserActive }?.let { targets ->
        WorkspaceBatchPropertiesDialog(targets, onDismiss = { batchPropertiesTargets = null })
    }
    propertiesTarget?.takeIf { browserActive }?.let { target ->
        WorkspacePropertiesDialog(target,
            onDismiss = { propertiesTarget = null },
            onModified = { loadDirectory(currentPath, currentEnvironment) })
    }
    if (showSelectionMenu && selectionMode && selectedNames.isNotEmpty() && browserActive) {
        FileBrowserSelectionMenu(
            count = selectedNames.size,
            canShare = fileList.none { it.name in selectedNames && it.isDirectory },
            onDismiss = { showSelectionMenu = false },
            onCopy = { cut -> FileBrowserClipboard.set(selectedEntries(cut)); finishSelection() },
            onDelete = { showSelectionMenu = false; pendingBatchDelete = selectedEntries(false) },
            onProperties = {
                showSelectionMenu = false
                val targets = selectedEntries(false).map { WorkspacePropertiesTarget(it.path, it.environment) }
                if (targets.size == 1) propertiesTarget = targets.single() else batchPropertiesTargets = targets
            },
            onShare = {
                showSelectionMenu = false
                val targets = selectedEntries(false)
                runOperation { operations.share(targets) }
            },
            onSearch = {
                showSelectionMenu = false
                searchScope = fileList.filter { it.name in selectedNames }.map { entry ->
                    WorkspaceSearchEntry(workspaceChild(currentLocation, entry.name), entry.name, entry.isDirectory,
                        entry.size.takeIf { it >= 0 }, directoryModifiedTime(entry.lastModified))
                }
                showFullSearch = true
            },
            extract = selectedEntries(false).let { targets -> targets.isNotEmpty() && targets.all { !it.isDirectory && workspaceArchiveKind(it.path) != null } },
            onArchiveAction = {
                val targets = selectedEntries(false)
                if (targets.isNotEmpty()) {
                    if (targets.all { !it.isDirectory && workspaceArchiveKind(it.path) != null })
                        extractionTargets = targets.map { WorkspaceLocation(it.path, workspaceEnvironment(it.environment)) }
                    else compressionTargets = targets
                }
                showSelectionMenu = false
            },
        )
    }
    contextMenuExpandedFor?.takeIf { browserActive }?.let { target ->
        if (!target.isDirectory && isWorkspaceSvg(target.name)) {
            val location = workspaceChild(currentLocation, target.name)
            WorkspaceSvgActionMenu(target.name, onDismiss = { contextMenuExpandedFor = null },
                onEdit = {
                    contextMenuExpandedFor = null
                    openFile(location.path, asText = true, fileEnvironment = location.environment)
                }, onConvert = { format ->
                    contextMenuExpandedFor = null
                    svgConversion = WorkspaceSvgConversionRequest(target.name, format, currentLocation) {
                        WorkspaceThumbnailLoader.prepareFile(context, location)
                    }
                })
        } else FileBrowserContextMenu(target.name, target.isDirectory, clipboard.isNotEmpty(),
            onDismiss = { contextMenuExpandedFor = null },
            onCopy = { cut ->
                FileBrowserClipboard.set(FileBrowserClipboardItem(joinPath(currentPath, target.name), currentEnvironment, cut, target.isDirectory))
                contextMenuExpandedFor = null
            },
            onPaste = {
                contextMenuExpandedFor = null
                val destination = if (target.isDirectory) joinPath(currentPath, target.name) else currentPath
                runOperation { operations.paste(destination, currentEnvironment) }
            },
            onRename = { contextMenuExpandedFor = null; renameTarget = target; renameInput = target.name },
            onProperties = {
                propertiesTarget = WorkspacePropertiesTarget(joinPath(currentPath, target.name), currentEnvironment)
                contextMenuExpandedFor = null
            },
            onOpenWith = { contextMenuExpandedFor = null; runOperation {
                operations.externalOpen(joinPath(currentPath, target.name), currentEnvironment, false)
            } },
            onShare = { contextMenuExpandedFor = null; runOperation {
                operations.externalOpen(joinPath(currentPath, target.name), currentEnvironment, true)
            } },
            onBookmark = {
                contextMenuExpandedFor = null
                bookmarkLocation = workspaceChild(currentLocation, target.name)
            },
            onHide = {
                contextMenuExpandedFor = null
                val location = workspaceChild(currentLocation, target.name)
                updateSettings { it.copy(hidden = if (location in it.hidden) it.hidden - location else it.hidden + location) }
            },
            hidden = workspaceChild(currentLocation, target.name) in browserSettings.hidden,
            onSearch = {
                contextMenuExpandedFor = null
                searchScope = listOf(WorkspaceSearchEntry(workspaceChild(currentLocation, target.name), target.name, target.isDirectory,
                    target.size.takeIf { it >= 0 }, directoryModifiedTime(target.lastModified)))
                showFullSearch = true
            },
            extract = !target.isDirectory && workspaceArchiveKind(target.name) != null,
            onArchiveAction = {
                contextMenuExpandedFor = null
                val location = workspaceChild(currentLocation, target.name)
                if (!target.isDirectory && workspaceArchiveKind(target.name) != null) extractionTargets = listOf(location)
                else compressionTargets = listOf(FileBrowserClipboardItem(location.path, location.environment, false, target.isDirectory))
            },
            onDelete = { contextMenuExpandedFor = null; deleteTarget = target })
    }

    svgConversion?.takeIf { browserActive }?.let { request ->
        WorkspaceSvgConversionDialog(request, onDismiss = { svgConversion = null }) { output ->
            svgConversion = null
            OKToast.success(context, context.getString(R.string.workspace_svg_exported, output.path)).show()
            loadDirectory(currentPath)
        }
    }
    // 浏览工具栏使用文件页面的主题前景，不继承聊天背景上的颜色。
    Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier =
                    Modifier.fillMaxSize()
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, // 移除点击时的涟漪效果
                                    enabled = true,
                                    onClick = {}
                            ) // 拦截点击事件，防止穿透
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeader) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, stringResource(R.string.file_manager_return)) }
                    Text(stringResource(if (onBindWorkspace == null) R.string.files else R.string.select_existing_workspace),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (menuHost == null) WorkspaceBrowserMenuButton(localMenuHost)
                }
            }
            if (searchVisible) {
                OutlinedTextField(searchQuery, { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true,
                    label = { Text(stringResource(R.string.workspace_browser_filter_hint)) },
                    trailingIcon = { IconButton(onClick = { searchQuery = ""; searchVisible = false }) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    } })
            }
            errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${currentLocation.environment}: $currentPath", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).clickable(enabled = !isLoading) { showPathDialog = true })
                IconButton(onClick = { showPathDialog = true }, enabled = !isLoading) {
                    Icon(Icons.Default.Edit, stringResource(R.string.workspace_browser_edit_path), Modifier.size(18.dp))
                }
                if (!showHeader && menuHost == null) WorkspaceBrowserMenuButton(localMenuHost)
            }

            // 快速路径栏
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPaths) { quickPath ->
                    QuickPathChip(
                        entry = quickPath,
                        isActive = currentLocation.environment == quickPath.environment &&
                            (currentPath == quickPath.path || currentPath.startsWith(quickPath.path.trimEnd('/') + "/")),
                        onClick = { loadDirectory(quickPath.path, quickPath.environment) }
                    )
                }

                items(safBookmarks) { bookmark ->
                    var menuExpanded by remember(bookmark.uri) { mutableStateOf(false) }
                    val repoEnv = remember(bookmark.name) { "repo:${bookmark.name}" }
                    Box {
                        QuickPathChipWithLongPress(
                            entry = QuickPathEntry(name = bookmark.name, path = "/", icon = Icons.Default.Folder),
                            isActive = currentEnvironment == repoEnv,
                            onClick = {
                                loadDirectory("/", repoEnv)
                            },
                            onLongPress = { menuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.repo_bookmark_delete)) },
                                onClick = {
                                    menuExpanded = false
                                    val uri = runCatching { Uri.parse(bookmark.uri) }.getOrNull()
                                    if (uri != null) {
                                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
                                    }
                                    coroutineScope.launch {
                                        apiPreferences.removeSafBookmark(bookmark.uri)
                                        if (currentEnvironment == repoEnv) {
                                            loadDirectory(initialPath, environment)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                items(browserSettings.bookmarks, key = { "bookmark:" + it.location.key }) { bookmark ->
                    QuickPathChip(
                        QuickPathEntry(bookmark.name, bookmark.location.path, Icons.Default.Bookmark),
                        isActive = currentLocation == bookmark.location,
                        onClick = { loadDirectory(bookmark.location.path, bookmark.location.environment) },
                    )
                }
                item {
                    QuickPathChip(
                        entry = QuickPathEntry(name = stringResource(R.string.workspace_browser_add_location), path = "", icon = Icons.Default.Add),
                        isActive = false,
                        onClick = { addSafLauncher.launch(null) }
                    )
                }
            }

            // 文件列表
            if (isLoading && copyProgress == null && deleteProgress == null) {
                Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                        state = listState, modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 8.dp)
                ) {
                    // 使用更健壮的方式来判断是否应该显示返回上一级的选项
                    val canGoUp = if (isSafEnv) parentPath(currentPath) != null else File(currentPath).parent != null
                    if (canGoUp) {
                        item {
                            WorkspaceFileRow(
                                    name = "..",
                                    icon = Icons.Default.FolderOpen,
                                    isDirectory = true,
                                    onClick = {
                                        if (isSafEnv) {
                                            val p = parentPath(currentPath)
                                            if (p != null) loadDirectory(p)
                                        } else {
                                            File(currentPath).parent?.let { parentPath ->
                                                loadDirectory(parentPath)
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    if (visibleFiles.isEmpty()) {
                        item { Text(stringResource(R.string.file_manager_empty_folder),
                            modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(visibleFiles, key = { it.name }) { item ->
                        Box { // 使用Box来定位上下文菜单
                            WorkspaceFileRow(
                                    name = item.name,
                                    icon =
                                            if (item.isDirectory) Icons.Default.Folder
                                            else getFileIcon(item.name),
                                    isDirectory = item.isDirectory,
                                    leading = { WorkspaceFileIcon(workspaceChild(currentLocation, item.name), item.isDirectory,
                                        "${item.size}:${item.lastModified}", symbolicLink = item.permissions.startsWith("l")) },
                                    selection = if (selectionMode) item.name in selectedNames else null,
                                    enabled = !isLoading && browserActive,
                                    onSwipeSelect = { swipeSelect(item.name) },
                                    onSwipeActiveChanged = { swipeInProgress = it },
                                    detail = listOf(formatLastModified(item.lastModified), if (item.isDirectory) "" else formatSize(item.size)).filter { it.isNotBlank() }.joinToString(" · "),
                                    onClick = {
                                        if (selectionMode) {
                                            toggleSelection(item.name)
                                        } else if (item.isDirectory) {
                                            val newPath =
                                                if (isSafEnv) joinPath(currentPath, item.name)
                                                else File(currentPath, item.name).path
                                            loadDirectory(newPath)
                                        } else {
                                            val filePath =
                                                if (isSafEnv) joinPath(currentPath, item.name)
                                                else File(currentPath, item.name).path
                                            openFile(filePath)
                                        }
                                    },
                                    onLongPress = {
                                        if (!isLoading) {
                                            if (selectionMode) {
                                                selectedNames = selectedNames + item.name
                                                showSelectionMenu = true
                                            } else contextMenuExpandedFor = item
                                        }
                                    }
                            )

                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }

            // 底部操作栏
            if (onBindWorkspace != null) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showHeader) {
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.file_manager_cancel)) }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            AppLogger.d(
                                "WorkspaceFileBrowser",
                                "bind workspace path=$currentPath env=$currentEnvironment"
                            )
                            onBindWorkspace(currentPath, currentEnvironment)
                        }
                    ) {
                        Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.file_manager_bind_current_folder))
                    }
                }
            }
        }
    }
}

/** 快速路径芯片组件 */
@Composable
private fun QuickPathChip(
    entry: QuickPathEntry,
    isActive: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                if (entry.name.isNotBlank() && entry.name != "+") {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isActive,
            borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun QuickPathChipWithLongPress(
    entry: QuickPathEntry,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    var suppressClickOnce by remember(entry.name, entry.path) { mutableStateOf(false) }
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    Box(
        modifier = Modifier.pointerInput(entry.name, entry.path) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis.toLong()) {
                    waitForUpOrCancellation()
                    false
                } ?: true

                if (longPressed) {
                    suppressClickOnce = true
                    latestOnLongPress()
                    waitForUpOrCancellation()
                }
            }
        }
    ) {
        QuickPathChip(
            entry = entry,
            isActive = isActive,
            onClick = {
                if (suppressClickOnce) {
                    suppressClickOnce = false
                    return@QuickPathChip
                }
                onClick()
            }
        )
    }
}

private fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        // 按 1024 进位并沿用 KB、MB 等标签，大文件继续换算到 GB 和 TB。
        size < 1024L * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
        size < 1024L * 1024 * 1024 * 1024 -> "%.1f GB".format(size / (1024.0 * 1024.0 * 1024.0))
        else -> "%.1f TB".format(size / (1024.0 * 1024.0 * 1024.0 * 1024.0))
    }
}

@SuppressLint("SimpleDateFormat")
private fun formatLastModified(dateString: String): String {
    val millis = dateString.toLongOrNull()
    if (millis != null) {
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(millis))
    }
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}

/** 根据文件名获取对应的图标 */
@Composable
fun getFileIcon(fileName: String) =
        when {
            fileName.endsWith(".html", true) || fileName.endsWith(".htm", true) ->
                    Icons.Default.Code
            fileName.endsWith(".css", true) -> Icons.Default.Brush
            fileName.endsWith(".js", true) -> Icons.Default.Code // 使用通用的代码图标替代Javascript
            fileName.endsWith(".json", true) -> Icons.Default.DataObject
            fileName.endsWith(".jpg", true) ||
                    fileName.endsWith(".png", true) ||
                    fileName.endsWith(".gif", true) ||
                    fileName.endsWith(".jpeg", true) -> Icons.Default.Image
            fileName.endsWith(".txt", true) -> Icons.AutoMirrored.Filled.TextSnippet
            fileName.endsWith(".md", true) -> Icons.AutoMirrored.Filled.Article
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
