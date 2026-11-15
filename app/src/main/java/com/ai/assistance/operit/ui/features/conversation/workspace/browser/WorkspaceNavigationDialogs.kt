package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

@Composable
internal fun WorkspacePathDialog(current: WorkspaceLocation, onDismiss: () -> Unit, onJump: (WorkspaceLocation) -> Unit) {
    var value by remember { mutableStateOf(current.path) }
    var error by remember { mutableStateOf(false) }
    fun confirm() {
        try { onJump(resolveWorkspacePath(value, current)) }
        catch (_: IllegalArgumentException) { error = true }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_browser_edit_path)) },
        text = {
            Column {
                Text(current.environment, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value, { value = it; error = false }, Modifier.fillMaxWidth(), singleLine = true,
                    isError = error, supportingText = { if (error) Text(stringResource(R.string.workspace_browser_invalid_path)) }, label = { Text(stringResource(R.string.workspace_browser_path)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { confirm() }))
            }
        },
        confirmButton = { TextButton(onClick = ::confirm) { Text(stringResource(R.string.workspace_browser_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun WorkspaceBookmarkDialog(location: WorkspaceLocation, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(location.path.trimEnd('/').substringAfterLast('/').ifEmpty { location.environment }) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_browser_add_bookmark)) },
        text = {
            Column {
                Text("${location.environment}: ${location.path}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.workspace_browser_bookmark_name)) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun WorkspaceBookmarksDialog(
    bookmarks: List<WorkspaceBookmark>, onDismiss: () -> Unit,
    onJump: (WorkspaceLocation) -> Unit, onRemove: (WorkspaceLocation) -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_browser_bookmarks)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (bookmarks.isEmpty()) Text(stringResource(R.string.workspace_browser_no_bookmarks))
                bookmarks.forEach { bookmark ->
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f).clickable { onJump(bookmark.location) }.padding(vertical = 12.dp)) {
                            Text(bookmark.name)
                            Text("${bookmark.location.environment}: ${bookmark.location.path}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onRemove(bookmark.location) }) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}
