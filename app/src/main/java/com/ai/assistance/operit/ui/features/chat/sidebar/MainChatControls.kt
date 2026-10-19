package com.ai.assistance.operit.ui.features.chat.sidebar

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CodeOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.components.useFloatingWindowLauncher
import com.ai.assistance.operit.ui.features.chat.workspace.WorkspaceHubPage
import com.ai.assistance.operit.ui.theme.getTextColorForBackground

/** 主聊天页保留一行透明操作区，标题与入口共用菜单按钮的高度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatControls(modifier: Modifier = Modifier) {
    val viewModel = LocalMainChatViewModel.current
    val openSidebar = LocalOpenMainChatSidebar.current
    val currentChatId by viewModel.currentChatId.collectAsState()
    val histories by viewModel.chatHistories.collectAsState()
    val streamingIds by viewModel.activeStreamingChatIds.collectAsState()
    val isFloatingMode by viewModel.isFloatingMode.collectAsState()
    val isWorkspacePreparing by viewModel.isWorkspacePreparing.collectAsState()
    val workspacePage by viewModel.workspaceHubPage.collectAsState()
    val currentChat = histories.firstOrNull { it.id == currentChatId }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val workspaceVisible = workspacePage != WorkspaceHubPage.CHAT
    val contentColor = getTextColorForBackground(if (workspaceVisible) colorScheme.surface else colorScheme.background)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.launchWindowFloatingModeAfterMicPermissionGranted(
                colorScheme, typography, moveTaskToBackOnReady = true,
            )
        } else {
            viewModel.showToast(context.getString(R.string.microphone_permission_denied))
        }
    }
    val launchFloatingWindow = useFloatingWindowLauncher(viewModel, permissionLauncher)
    fun dismissKeyboard() {
        keyboard?.hide()
        focus.clearFocus()
    }

    Row(
        modifier = modifier.fillMaxWidth().height(44.dp)
            .then(if (workspaceVisible) Modifier.background(colorScheme.surface.copy(alpha = 1f)) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { dismissKeyboard(); openSidebar() }, modifier = Modifier.size(40.dp)) {
            BadgedBox(badge = {
                if (streamingIds.size >= 2) Badge { Text(streamingIds.size.toString()) }
            }) {
                Icon(Icons.Default.Menu, stringResource(R.string.menu), tint = contentColor, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            // 这行在工作区覆盖层之上，必须切换标题，不能继续透出原对话名。
            text = when (workspacePage) {
                WorkspaceHubPage.CHAT -> currentChat?.title ?: stringResource(R.string.new_chat)
                WorkspaceHubPage.TERMINAL -> stringResource(R.string.terminal_title)
                else -> stringResource(R.string.workspace_hub_entry)
            },
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { dismissKeyboard(); launchFloatingWindow() }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.PictureInPicture,
                stringResource(if (isFloatingMode) R.string.close_floating_window else R.string.open_floating_window),
                tint = if (isFloatingMode) colorScheme.primary else contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = { dismissKeyboard(); viewModel.onWorkspaceHubButtonClick() }, modifier = Modifier.size(36.dp)) {
            if (isWorkspacePreparing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = contentColor)
            } else {
                Icon(
                    if (currentChat?.workspace.isNullOrBlank()) Icons.Default.CodeOff else Icons.Default.Code,
                    stringResource(R.string.workspace_hub_entry),
                    tint = if (workspacePage != WorkspaceHubPage.CHAT) colorScheme.primary else contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
