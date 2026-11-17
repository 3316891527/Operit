package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 同一浏览器的菜单随显示层注册和注销，编辑器或隐藏页面不占用页签菜单。 */
internal class WorkspaceBrowserMenuHost {
    private val owners = mutableStateListOf<Pair<Any, @Composable ColumnScope.() -> Unit>>()
    val content: (@Composable ColumnScope.() -> Unit)? get() = owners.lastOrNull()?.second
    fun register(owner: Any, content: @Composable ColumnScope.() -> Unit) {
        owners.removeAll { it.first === owner }
        owners.add(owner to content)
    }
    fun unregister(owner: Any) { owners.removeAll { it.first === owner } }
}

internal val LocalWorkspaceBrowserActive = staticCompositionLocalOf { true }

internal val LocalWorkspaceBrowserMenu = staticCompositionLocalOf<WorkspaceBrowserMenuHost?> { null }

@Composable
internal fun WorkspaceBrowserMenuButton(host: WorkspaceBrowserMenuHost) {
    val content = host.content ?: return
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.workspace_browser_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WorkbenchDropdownMenu(expanded, { expanded = false },
            modifier = Modifier.widthIn(min = 184.dp, max = 256.dp).width(IntrinsicSize.Max),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            WorkspaceMenuDismissProvider({ expanded = false }) { content() }
        }
    }
}

private val LocalWorkspaceMenuDismiss = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
private fun WorkspaceMenuDismissProvider(dismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalWorkspaceMenuDismiss provides dismiss, content = content)
}

@Composable
internal fun WorkspaceBrowserMenuItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    checked: Boolean? = null,
    action: () -> Unit,
) {
    val dismiss = LocalWorkspaceMenuDismiss.current
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        trailingIcon = if (checked != null) {
            { if (checked) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp)) }
        } else null,
        contentPadding = PaddingValues(horizontal = 12.dp),
        enabled = enabled,
        onClick = { dismiss(); action() },
    )
}
