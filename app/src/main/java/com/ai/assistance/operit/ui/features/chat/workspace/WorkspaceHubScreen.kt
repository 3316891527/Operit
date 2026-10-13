package com.ai.assistance.operit.ui.features.chat.workspace

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.computer.ComputerScreen
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceScreen
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WorkspaceHubScreen(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    chatHistories: List<ChatHistory>,
    isVisible: Boolean,
    onOpenHtmlPackager: () -> Unit,
    reserveChatControlsSpace: Boolean = false,
    onExportClick: (File) -> Unit,
) {
    val page by actualViewModel.workspaceHubPage.collectAsState()
    val entryRequest by actualViewModel.workspaceEntryRequest.collectAsState()
    val context = LocalContext.current
    val terminalManager = remember(context) { TerminalManager.getInstance(context) }
    val terminalState by terminalManager.terminalState.collectAsState()
    var hasOpenedWorkspace by remember { mutableStateOf(false) }
    var hasOpenedTerminal by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        if (page == WorkspaceHubPage.WORKSPACE) hasOpenedWorkspace = true
        if (page == WorkspaceHubPage.TERMINAL) hasOpenedTerminal = true
    }

    BackHandler(enabled = isVisible) {
        actualViewModel.onWorkspaceHubBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // 终端保持透明父容器，避免覆盖原生显示层。
                if (page == WorkspaceHubPage.TERMINAL) Color.Transparent else MaterialTheme.colorScheme.surface
            )
            .padding(top = if (reserveChatControlsSpace) 52.dp else 0.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceHubTab(
                title = stringResource(R.string.workspace_hub_home),
                selected = page == WorkspaceHubPage.HOME,
                onClick = actualViewModel::showWorkspaceHubHome,
            )
            if (hasOpenedWorkspace) {
                WorkspaceHubTab(
                    title = stringResource(R.string.workspace),
                    selected = page == WorkspaceHubPage.WORKSPACE,
                    onClick = { actualViewModel.openWorkspaceFromHub() },
                )
            }
            if (hasOpenedTerminal) {
                WorkspaceHubTab(
                    title = stringResource(R.string.terminal),
                    selected = page == WorkspaceHubPage.TERMINAL,
                    onClick = actualViewModel::openTerminalFromHub,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (page == WorkspaceHubPage.HOME) {
                WorkspaceHubHome(
                    currentChat = currentChat,
                    chatHistories = chatHistories,
                    terminalSessionCount = terminalState.sessions.size,
                    onOpenWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.FILES) },
                    onCreateWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.CREATE) },
                    onSelectWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.SELECT) },
                    onOpenTerminal = actualViewModel::openTerminalFromHub,
                    onOpenHtmlPackager = onOpenHtmlPackager,
                )
            }
            val workspaceVisible = isVisible && page == WorkspaceHubPage.WORKSPACE
            // 工作区隐藏时保留编辑状态；未绑定工作区的选择器仅在当前页面组合。
            Layout(
                modifier = if (workspaceVisible) Modifier.fillMaxSize() else {
                    Modifier.size(0.dp).clearAndSetSemantics {}
                },
                content = {
                    if ((hasOpenedWorkspace && currentChat?.workspace != null) || workspaceVisible) {
                        WorkspaceScreen(
                            actualViewModel = actualViewModel,
                            currentChat = currentChat,
                            isVisible = workspaceVisible,
                            entryRequest = entryRequest,
                            onExportClick = onExportClick,
                        )
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
            // 离开终端即释放显示层，会话仍由终端管理器持有。
            if (isVisible && page == WorkspaceHubPage.TERMINAL) {
                ComputerScreen()
            }
        }
    }
}

@Composable
private fun WorkspaceHubTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
