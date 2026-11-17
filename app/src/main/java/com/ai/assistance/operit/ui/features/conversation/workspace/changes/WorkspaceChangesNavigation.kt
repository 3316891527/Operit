// SPDX-License-Identifier: AGPL-3.0-only
package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

internal enum class ChangeOpenAction { FILES, EDITOR, TERMINAL, COPY_PATH }

/** 打开方式使用 Android 既有入口，选择动作与 Diff 显示模式分别保存。 */
@Composable
internal fun ChangeFileNavigation(
    file: ReviewFile, onToggleFiles: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, hasPrevious: Boolean, hasNext: Boolean,
    onOpenFile: (ChangeLocation, ChangeOpenAction) -> Unit, onRefresh: () -> Unit,
) {
    var openerMenuOpen by remember { mutableStateOf(false) }
    var opener by rememberSaveable { mutableStateOf(ChangeOpenAction.EDITOR) }
    ChangeIconButton(Icons.Default.ViewSidebar, stringResource(R.string.workspace_changes_toggle_files), onToggleFiles)
    VerticalDivider(Modifier.height(14.dp).padding(horizontal = 3.dp))
    ChangeIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.workspace_changes_previous_file), onPrevious, enabled = hasPrevious)
    ChangeIconButton(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.workspace_changes_next_file), onNext, enabled = hasNext)
    VerticalDivider(Modifier.height(14.dp).padding(horizontal = 3.dp))
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
        ChangeIconButton(when (opener) {
            ChangeOpenAction.FILES -> Icons.Default.FolderOpen
            ChangeOpenAction.TERMINAL -> Icons.Default.Terminal
            ChangeOpenAction.EDITOR -> Icons.Default.Code
            ChangeOpenAction.COPY_PATH -> Icons.Default.ContentCopy
        }, stringResource(R.string.workspace_changes_open_with), { openerMenuOpen = true })
        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(12.dp))
        }
        DropdownMenu(expanded = openerMenuOpen, onDismissRequest = { openerMenuOpen = false }) {
            Text(stringResource(R.string.workspace_changes_open_with), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChangeOpenAction.entries.forEach { action ->
                if (action == ChangeOpenAction.COPY_PATH) HorizontalDivider()
                val enabled = when (action) {
                    ChangeOpenAction.COPY_PATH -> true
                    ChangeOpenAction.FILES -> true
                    ChangeOpenAction.EDITOR -> true
                    ChangeOpenAction.TERMINAL -> file.location.environment in setOf("android", "linux")
                }
                DropdownMenuItem(text = { Text(stringResource(when (action) {
                    ChangeOpenAction.FILES -> R.string.workspace_changes_open_files
                    ChangeOpenAction.EDITOR -> R.string.workspace_changes_open_editor
                    ChangeOpenAction.TERMINAL -> R.string.workspace_changes_open_terminal
                    ChangeOpenAction.COPY_PATH -> R.string.workspace_changes_copy_path
                }), fontSize = 12.sp) }, enabled = enabled,
                    leadingIcon = { Icon(when (action) {
                        ChangeOpenAction.FILES -> Icons.Default.FolderOpen
                        ChangeOpenAction.EDITOR -> Icons.Default.Code
                        ChangeOpenAction.TERMINAL -> Icons.Default.Terminal
                        ChangeOpenAction.COPY_PATH -> Icons.Default.ContentCopy
                    }, null, Modifier.size(16.dp)) },
                    trailingIcon = { if (action == opener && action != ChangeOpenAction.COPY_PATH) Icon(Icons.Default.Check, null, Modifier.size(14.dp)) },
                    onClick = {
                        if (action != ChangeOpenAction.COPY_PATH) opener = action
                        openerMenuOpen = false
                        onOpenFile(file.location, action)
                    })
            }
        }
    }
    ChangeIconButton(Icons.Default.Refresh, stringResource(R.string.workspace_changes_refresh), onRefresh)
    Spacer(Modifier.width(8.dp))
}
