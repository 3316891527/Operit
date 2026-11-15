package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.DirectoryEntry

internal fun sortDirectoryEntries(entries: List<DirectoryEntry>, mode: Int, descending: Boolean,
    ignoreCase: Boolean, foldersFirst: Boolean): List<DirectoryEntry> {
    val names = Comparator<DirectoryEntry> { a, b -> a.name.compareTo(b.name, ignoreCase) }
    val modifiedTimes = if (mode == 3) entries.associateWith { directoryModifiedTime(it.lastModified) } else emptyMap()
    val values = when (mode) {
        1 -> Comparator<DirectoryEntry> { a, b ->
            a.name.substringAfterLast('.', "").compareTo(b.name.substringAfterLast('.', ""), ignoreCase)
        }
        2 -> compareBy<DirectoryEntry> { it.size }
        3 -> compareBy<DirectoryEntry> { modifiedTimes[it] }
        else -> names
    }.then(names)
    val ordered = if (descending) values.reversed() else values
    // 文件夹置顶与倒序分别控制，倒序时仍可将文件夹排在前面。
    return entries.sortedWith(if (foldersFirst) compareBy<DirectoryEntry> { !it.isDirectory }.then(ordered) else ordered)
}

@Composable
internal fun FileBrowserContextMenu(name: String, isDirectory: Boolean, canPaste: Boolean,
    onDismiss: () -> Unit, onCopy: (Boolean) -> Unit, onPaste: () -> Unit,
    onRename: () -> Unit, onProperties: () -> Unit, onOpenWith: () -> Unit, onShare: () -> Unit,
    onBookmark: () -> Unit, onHide: () -> Unit, hidden: Boolean, onDelete: () -> Unit,
    onSearch: () -> Unit, extract: Boolean, onArchiveAction: () -> Unit) {
    WorkspaceFileActionMenu(name, onDismiss, WorkspaceFileActions(
        search = onSearch, archive = onArchiveAction, copy = { onCopy(false) }, cut = { onCopy(true) },
        paste = if (canPaste) onPaste else null, rename = onRename, properties = onProperties,
        openWith = if (isDirectory) null else onOpenWith, bookmark = if (isDirectory) onBookmark else null,
        hide = onHide, share = if (isDirectory) null else onShare, delete = onDelete, extract = extract, hidden = hidden,
    ))
}

/** 单选、多选和归档使用同一完整排列；批量不支持的动作保留为禁用项。 */
@Composable
internal fun FileBrowserSelectionMenu(
    count: Int,
    canShare: Boolean,
    onDismiss: () -> Unit,
    onCopy: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onShare: () -> Unit,
    onSearch: () -> Unit,
    extract: Boolean,
    onArchiveAction: () -> Unit,
) {
    WorkspaceFileActionMenu(stringResource(R.string.workspace_selection_count, count), onDismiss, WorkspaceFileActions(
        search = onSearch, archive = onArchiveAction, copy = { onCopy(false) }, cut = { onCopy(true) },
        properties = onProperties, share = if (canShare) onShare else null, delete = onDelete, extract = extract,
    ))
}

@Composable
internal fun FileBrowserActionPanel(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                content()
            }
        }
    }
}

@Composable
internal fun WorkspaceFileAction(modifier: Modifier, icon: ImageVector, label: Int, enabled: Boolean = true,
    destructive: Boolean = false, onClick: () -> Unit) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(modifier.heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(stringResource(label), color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun FileBrowserSortDialog(mode: Int, descending: Boolean, ignoreCase: Boolean, foldersFirst: Boolean,
    onDismiss: () -> Unit, onConfirm: (Int, Boolean, Boolean, Boolean) -> Unit) {
    var selected by remember { mutableIntStateOf(mode) }
    var reverse by remember { mutableStateOf(descending) }
    var insensitive by remember { mutableStateOf(ignoreCase) }
    var folders by remember { mutableStateOf(foldersFirst) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.file_manager_sort_by)) },
        text = { Column {
            listOf(R.string.file_manager_sort_name, R.string.file_manager_sort_type,
                R.string.file_manager_sort_size, R.string.file_manager_sort_by_modified).forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().clickable { selected = index }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == index, onClick = { selected = index })
                    Text(stringResource(label))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SortOption(R.string.workspace_sort_descending, reverse) { reverse = it }
            SortOption(R.string.workspace_sort_ignore_case, insensitive) { insensitive = it }
            SortOption(R.string.workspace_sort_folders_first, folders) { folders = it }
        } },
        confirmButton = { TextButton(onClick = { onConfirm(selected, reverse, insensitive, folders) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun SortOption(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onChange)
        Text(stringResource(label))
    }
}