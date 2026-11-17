package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionBrowserTab
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.terminal.data.TerminalSessionData
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 上方管理工作区，中间按实际产出显示，终端和浏览器并列展示真实会话。 */
@Composable
internal fun WorkspaceHubHome(
    currentChat: ChatHistory?, sessions: List<TerminalSessionData>, browserTabs: List<WebSessionBrowserTab>, outputPath: String?,
    onOpenWorkspace: () -> Unit, onCreateWorkspace: () -> Unit, onSelectWorkspace: () -> Unit,
    onOpenChanges: () -> Unit, onOpenOutputs: () -> Unit,
    onNewTerminal: () -> Unit, onTerminal: (String) -> Unit, onCloseTerminal: (String) -> Unit,
    onNewBrowser: () -> Unit, onBrowser: (String) -> Unit, onCloseBrowser: (String) -> Unit,
) {
    val workspace = currentChat?.workspace?.takeIf { it.isNotBlank() }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (workspace == null) R.string.workspace_hub_optional else R.string.workspace),
                fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(workspace ?: stringResource(R.string.workspace_hub_optional_description),
                fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (workspace == null) {
                    Button(onClick = onCreateWorkspace, shape = RoundedCornerShape(22.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.workspace_hub_create), fontSize = 13.sp)
                    }
                    TextButton(onClick = onSelectWorkspace) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.workspace_hub_select), fontSize = 13.sp)
                    }
                } else {
                    Button(onClick = onOpenWorkspace, shape = RoundedCornerShape(22.dp)) { Text(stringResource(R.string.workspace_hub_open), fontSize = 13.sp) }
                    TextButton(onClick = onOpenChanges) { Text(stringResource(R.string.workspace_changes_title), fontSize = 13.sp) }
                }
            }
        }
        if (outputPath != null) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenOutputs).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.workspace_outputs_title), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(outputPath, fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkbenchGroupHeader(Icons.Default.Terminal, stringResource(R.string.workspace_hub_terminal_work), stringResource(R.string.workspace_terminal_new), onNewTerminal)
                if (sessions.isEmpty()) WorkbenchEmpty(stringResource(R.string.workspace_hub_no_terminals))
                sessions.forEachIndexed { index, session ->
                    WorkbenchSessionRow(if (session.title == "Local") stringResource(R.string.workspace_terminal_session_title, index + 1) else session.title,
                        session.currentDirectory.removeSuffix(" $").trim(), { onTerminal(session.id) }, { onCloseTerminal(session.id) })
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkbenchGroupHeader(Icons.Default.Language, stringResource(R.string.workspace_hub_browser_work), stringResource(R.string.web_session_new_tab), onNewBrowser)
                if (browserTabs.isEmpty()) WorkbenchEmpty(stringResource(R.string.workspace_hub_no_browsers))
                browserTabs.forEach { tab ->
                    WorkbenchSessionRow(tab.title.ifBlank { stringResource(R.string.web_session_new_tab) }, tab.url,
                        { onBrowser(tab.sessionId) }, { onCloseBrowser(tab.sessionId) })
                }
            }
        }
    }
}

@Composable
private fun WorkbenchGroupHeader(icon: ImageVector, title: String, newLabel: String, onNew: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.weight(1f).padding(start = 6.dp), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onNew, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, newLabel, Modifier.size(18.dp)) }
    }
}

@Composable
private fun WorkbenchEmpty(label: String) {
    Text(label, Modifier.padding(vertical = 12.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WorkbenchSessionRow(title: String, subtitle: String, onOpen: () -> Unit, onClose: () -> Unit) {
    Surface(onClick = onOpen, color = Color.Transparent, shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.close) + " " + title, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
