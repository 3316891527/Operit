package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import com.ai.assistance.operit.ui.common.oktoast.ErrorToast
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.ChangeOpenAction
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceChangeAccess
import com.ai.assistance.operit.ui.features.conversation.workspace.git.quoteGitArgument
import com.ai.assistance.operit.terminal.provider.type.TerminalType
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.CompositionLocalProvider
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceChangeStore
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.links.LocalWorkspaceCheckedFileLinkOpener
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileOpenException
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileOpenReason
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.ui.features.management.resources.OfflineResourcesActivity
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.automation.terminal.ComputerScreen
import com.ai.assistance.operit.ui.features.conversation.workspace.WorkspaceScreen
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceChangesScreen
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileLink
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileLinkHost
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.ai.assistance.operit.terminal.data.TerminalSessionData
import com.ai.assistance.operit.ui.features.automation.terminal.EmbeddedTerminalPage
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WorkspaceHubScreen(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    chatHistories: List<ChatHistory>,
    isVisible: Boolean,
    reserveChatControlsSpace: Boolean = false,
) {
    key(currentChat?.id) {
        WorkspaceFileLinkHost(actualViewModel, currentChat, Modifier.fillMaxSize()) {
        WorkspaceHubContent(
            actualViewModel = actualViewModel,
            currentChat = currentChat,
            chatHistories = chatHistories,
            isVisible = isVisible,
            reserveChatControlsSpace = reserveChatControlsSpace,
        )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun WorkspaceHubContent(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    chatHistories: List<ChatHistory>,
    isVisible: Boolean,
    reserveChatControlsSpace: Boolean = false,
) {
    val requestedPage by actualViewModel.workspaceHubPage.collectAsState()
    val messages by actualViewModel.chatHistory.collectAsState()
    val userMessages = messages.filter { it.sender == "user" }.map { it.timestamp }
    var lastOpenPage by remember { mutableStateOf(WorkspaceHubPage.HOME) }
    // 关闭时继续绘制离开前的页面，使退出动画保留完整内容。
    val page = if (requestedPage == WorkspaceHubPage.CHAT) lastOpenPage else requestedPage
    SideEffect { if (requestedPage != WorkspaceHubPage.CHAT) lastOpenPage = requestedPage }
    val entryRequest by actualViewModel.workspaceEntryRequest.collectAsState()
    val context = LocalContext.current
    val terminalManager = remember(context) { TerminalManager.getInstance(context) }
    val terminalState by terminalManager.terminalState.collectAsState()
    val sessions = terminalState.sessions.filterNot { it.automation }
    val browser = remember(context) { WorkspaceBrowserController(context) }
    val browserState by browser.state.collectAsState()
    val changeStore = remember(context) { WorkspaceChangeStore.get(context) }
    val changeRevision by changeStore.revision.collectAsState()
    var outputPath by remember(currentChat?.id) { mutableStateOf<String?>(null) }
    var outputsLoaded by remember(currentChat?.id) { mutableStateOf(false) }
    var outputsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentChat?.id, changeRevision, isVisible) {
        if (!isVisible) return@LaunchedEffect
        try {
            val chatId = currentChat?.id
            outputPath = if (chatId == null) null else withContext(Dispatchers.IO) {
                changeStore.records(chatId = chatId).lastOrNull()?.location?.path
            }
            outputsLoaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WorkspaceHub", "读取对话产出入口失败", failure)
            outputsError = failure.message ?: context.getString(R.string.workspace_changes_load_failed)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    fun enterBrowser(newTab: Boolean = false, sessionId: String? = null) {
        keyboard?.hide()
        browser.open(sessionId, newTab)
        actualViewModel.openBrowserFromHub()
    }
    val scope = rememberCoroutineScope()
    val terminalPage by actualViewModel.embeddedTerminalPage.collectAsState()
    var creatingSession by remember { mutableStateOf(false) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var sessionToClose by remember { mutableStateOf<TerminalSessionData?>(null) }
    fun enterTerminal(createNewSession: Boolean = false, sessionId: String? = null, reviewDirectory: Pair<String, String>? = null) {
        if (creatingSession) return
        creatingSession = true
        keyboard?.hide()
        scope.launch {
            try {
                // 先检查实际安装环境；归档可用时由终端完成安装，缺失时直接引导下载。
                val installed = withContext(Dispatchers.IO) { OfflineResources.ubuntuInstalled(context) }
                val resourcesReady = installed || OfflineResources.isReady(context, OfflineResources.UBUNTU)
                // 资源校验期间已返回聊天时，不再打开资源页或终端。
                if (actualViewModel.workspaceHubPage.value == WorkspaceHubPage.CHAT) return@launch
                if (!resourcesReady) {
                    context.startActivity(
                        Intent(context, OfflineResourcesActivity::class.java)
                            .putExtra(OfflineResourcesActivity.EXTRA_GROUP, OfflineResources.UBUNTU)
                    )
                    return@launch
                }
                if (sessionId != null) terminalManager.switchToSession(sessionId)
                actualViewModel.openTerminalFromHub()
                if (createNewSession || terminalManager.terminalState.value.sessions.none { !it.automation }) {
                    val session = terminalManager.createNewSession(context.getString(
                        R.string.workspace_terminal_session_title, terminalManager.terminalState.value.sessions.count { !it.automation } + 1,
                    ))
                    if (reviewDirectory != null) {
                        val (path, backend) = reviewDirectory
                        // 新会话必须属于记录的实际后端，不能把本机路径发到 SSH 主机。
                        val sessionMatches = ((session.terminalType == TerminalType.SSH) == backend.startsWith("linux:ssh:")) &&
                            WorkspaceChangeAccess.backend(context, if (backend.startsWith("linux:")) "linux" else "android") == backend
                        if (!sessionMatches) {
                            terminalManager.closeSession(session.id)
                            error(context.getString(R.string.workspace_changes_terminal_backend))
                        }
                        terminalManager.sendCommandToSession(session.id, "cd -- " + quoteGitArgument(path))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("WorkspaceTerminal", "Opening terminal session failed", error)
                sessionError = error.message ?: context.getString(R.string.workspace_terminal_failed)
            } finally { creatingSession = false }
        }
    }
    // 切换对话重置访问记录，同一对话的界面重建则保留已打开页签。
    var hasOpenedWorkspace by rememberSaveable { mutableStateOf(false) }
    var hasOpenedChanges by rememberSaveable { mutableStateOf(false) }
    var hasOpenedOutputs by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(outputsLoaded, outputPath, requestedPage, browserState.tabs.isEmpty()) {
        if (outputsLoaded && outputPath == null) {
            hasOpenedOutputs = false
            if (requestedPage == WorkspaceHubPage.OUTPUTS) actualViewModel.showWorkspaceHubHome()
        }
        if (requestedPage == WorkspaceHubPage.BROWSER && browserState.tabs.isEmpty()) actualViewModel.showWorkspaceHubHome()
    }
    val checkedOpenFile = LocalWorkspaceCheckedFileLinkOpener.current
    LaunchedEffect(currentChat?.id, page) {
        if (page == WorkspaceHubPage.WORKSPACE) hasOpenedWorkspace = true
        if (page == WorkspaceHubPage.CHANGES) hasOpenedChanges = true
        if (page == WorkspaceHubPage.OUTPUTS) hasOpenedOutputs = true
    }

    BackHandler(enabled = isVisible && requestedPage != WorkspaceHubPage.CHAT) {
        if (page == WorkspaceHubPage.TERMINAL && terminalPage != EmbeddedTerminalPage.CONSOLE) {
            actualViewModel.showEmbeddedTerminalPage(EmbeddedTerminalPage.CONSOLE)
        } else actualViewModel.onWorkspaceHubBack()
    }

    val browserMenu = remember { WorkspaceBrowserMenuHost() }
    val header = remember { WorkspaceHeaderHost() }
    CompositionLocalProvider(LocalWorkspaceBrowserMenu provides browserMenu, LocalWorkspaceHeader provides header) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 1f))
                .padding(top = if (reserveChatControlsSpace) 52.dp else 0.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            WorkspaceSessionTabs(
                page = page,
                hasWorkspace = hasOpenedWorkspace,
                hasTerminal = sessions.isNotEmpty(),
                hasChanges = hasOpenedChanges || page == WorkspaceHubPage.CHANGES,
                hasOutputs = outputPath != null && (hasOpenedOutputs || page == WorkspaceHubPage.OUTPUTS),
                browserTabs = browserState.tabs,
                onBrowser = { enterBrowser(sessionId = it) },
                onCloseBrowser = browser::close,
                onChanges = { keyboard?.hide(); actualViewModel.openChangesFromHub() },
                onOutputs = { keyboard?.hide(); actualViewModel.openChangesFromHub(conversation = true) },
                onCloseChanges = { hasOpenedChanges = false; if (page == WorkspaceHubPage.CHANGES) actualViewModel.showWorkspaceHubHome() },
                onCloseOutputs = { hasOpenedOutputs = false; if (page == WorkspaceHubPage.OUTPUTS) actualViewModel.showWorkspaceHubHome() },
                sessions = sessions,
                currentSessionId = terminalState.currentSessionId,
                creatingSession = creatingSession,
                onHome = { keyboard?.hide(); actualViewModel.showWorkspaceHubHome() },
                onWorkspace = { keyboard?.hide(); actualViewModel.openWorkspaceFromHub() },
                onTerminal = { id -> enterTerminal(sessionId = id) },
                onCloseTerminal = { id -> sessionToClose = terminalManager.terminalState.value.sessions.firstOrNull { it.id == id } },
                onNewTerminal = { enterTerminal(createNewSession = true) },
                onTerminalPage = { keyboard?.hide(); actualViewModel.showEmbeddedTerminalPage(it) },
                browserMenu = browserMenu,
                header = header,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (page == WorkspaceHubPage.HOME) {
                    WorkspaceHubHome(
                        currentChat = currentChat, sessions = sessions, browserTabs = browserState.tabs, outputPath = outputPath,
                        onOpenWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.FILES) },
                        onCreateWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.CREATE) },
                        onSelectWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.SELECT) },
                        onOpenChanges = { keyboard?.hide(); actualViewModel.openChangesFromHub() },
                        onOpenOutputs = { keyboard?.hide(); actualViewModel.openChangesFromHub(conversation = true) },
                        onNewTerminal = { enterTerminal(createNewSession = true) },
                        onTerminal = { enterTerminal(sessionId = it) },
                        onCloseTerminal = { id -> sessionToClose = sessions.firstOrNull { it.id == id } },
                        onNewBrowser = { enterBrowser(newTab = true) }, onBrowser = { enterBrowser(sessionId = it) }, onCloseBrowser = browser::close,
                    )
                }
                val workspaceVisible = isVisible && page == WorkspaceHubPage.WORKSPACE
                // 临时浏览目录和文件也属于工作区会话，切到终端后继续保留编辑状态。
                Layout(
                    modifier = if (workspaceVisible) Modifier.fillMaxSize() else {
                        Modifier.size(0.dp).clearAndSetSemantics {}
                    },
                    content = {
                        if (hasOpenedWorkspace || workspaceVisible) {
                            CompositionLocalProvider(LocalWorkspaceBrowserActive provides workspaceVisible) {
                                WorkspaceScreen(
                                    actualViewModel = actualViewModel,
                                    currentChat = currentChat,
                                    isVisible = workspaceVisible,
                                    entryRequest = entryRequest,
                                )
                            }
                        }
                    },
                ) { measurables, constraints ->
                    if (!workspaceVisible || measurables.isEmpty()) {
                        layout(0, 0) {}
                    } else {
                        val placeable = measurables.first().measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                    }
                }
                // 审阅页隐藏时保留筛选和选择状态，关闭页签才释放页面。
                listOf(WorkspaceHubPage.CHANGES, WorkspaceHubPage.OUTPUTS).forEach { reviewPage ->
                    val opened = if (reviewPage == WorkspaceHubPage.CHANGES) hasOpenedChanges else hasOpenedOutputs
                    val active = isVisible && page == reviewPage
                    if ((reviewPage != WorkspaceHubPage.OUTPUTS || outputPath != null) && (opened || page == reviewPage)) key(reviewPage, if (reviewPage == WorkspaceHubPage.CHANGES) currentChat?.workspace else null, if (reviewPage == WorkspaceHubPage.CHANGES) currentChat?.workspaceEnv else null) {
                        Layout(
                            modifier = if (active) Modifier.fillMaxSize() else Modifier.size(0.dp).clearAndSetSemantics {},
                            content = {
                                WorkspaceChangesScreen(
                                    chatId = currentChat?.id,
                                    directory = currentChat?.workspace,
                                    environment = currentChat?.workspaceEnv,
                                    userMessages = userMessages,
                                    onPreviewFile = { location -> checkedOpenFile(WorkspaceFileLink(location.path, location.environment), location.backend) },
                                    conversation = reviewPage == WorkspaceHubPage.OUTPUTS, isVisible = active,
                                    onOpenFile = { location, action ->
                                        val parent = location.path.substringBeforeLast('/', "").ifEmpty { "/" }
                                        when (action) {
                                            ChangeOpenAction.COPY_PATH -> (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                                .setPrimaryClip(ClipData.newPlainText("path", location.path))
                                            ChangeOpenAction.FILES -> checkedOpenFile(WorkspaceFileLink(parent, location.environment), location.backend)
                                            ChangeOpenAction.EDITOR -> checkedOpenFile(WorkspaceFileLink(location.path, location.environment, editAsText = true), location.backend)
                                            ChangeOpenAction.TERMINAL -> {
                                                if (WorkspaceChangeAccess.backend(context, location.environment) == location.backend)
                                                    enterTerminal(createNewSession = true, reviewDirectory = parent to location.backend)
                                                else throw WorkspaceFileOpenException(
                                                    WorkspaceFileOpenReason.BACKEND)
                                            }
                                        }
                                    },
                                )
                            },
                        ) { measurables, constraints ->
                            if (!active || measurables.isEmpty()) layout(0, 0) {}
                            else {
                                val placeable = measurables.first().measure(constraints)
                                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                            }
                        }
                    }
                }
                if (isVisible && page == WorkspaceHubPage.BROWSER) {
                    browser.Content(onReturn = actualViewModel::showWorkspaceHubHome)
                }
                // 离开终端即释放显示层，会话仍由终端管理器持有。
                if (isVisible && page == WorkspaceHubPage.TERMINAL) {
                    ComputerScreen(page = terminalPage, onPageChange = actualViewModel::showEmbeddedTerminalPage)
                }
            }
        }
    }
    sessionToClose?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToClose = null },
            title = { Text(stringResource(R.string.workspace_terminal_close_title)) },
            text = { Text(stringResource(R.string.workspace_terminal_close_message, session.title)) },
            confirmButton = { TextButton(onClick = {
                terminalManager.closeSession(session.id)
                sessionToClose = null
                if (page == WorkspaceHubPage.TERMINAL && terminalManager.terminalState.value.sessions.none { !it.automation }) { keyboard?.hide(); actualViewModel.showWorkspaceHubHome() }
            }) { Text(stringResource(R.string.close)) } },
            dismissButton = { TextButton(onClick = { sessionToClose = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    outputsError?.let { error -> ErrorToast(errorMessage = error, onShown = { outputsError = null }) }
    sessionError?.let { error ->
        ErrorToast(errorMessage = error, onShown = { sessionError = null })
    }
}
