package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.git.GitBlameRequest
import com.ai.assistance.operit.ui.features.conversation.workspace.git.WorkspaceGitBlameDialog
import com.ai.assistance.operit.ui.features.conversation.workspace.git.WorkspaceGitBlameRepository
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

@Composable
internal fun EditorToolbar(session: EditorSession, editor: NativeCodeEditor?, settings: EditorSettings,
    change: ((EditorSettings) -> EditorSettings) -> Unit, canSave: Boolean, saving: Boolean, onSave: () -> Unit,
    onFind: () -> Unit, onSaveAs: () -> Unit, onReload: () -> Unit, onClose: () -> Unit, onSettings: () -> Unit, path: String, environment: String) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val backend = checkNotNull(session.sourceBackend) { "Editor source backend missing" }
    val blame = remember(session, path, environment, backend) { WorkspaceGitBlameRepository(context, path, environment, backend) }
    var blameRequest by remember(session, path, environment) { mutableStateOf<GitBlameRequest?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }
    var lineOpen by remember { mutableStateOf(false) }
    var statistics by remember { mutableStateOf(false) }
    val ready = editor != null
    val writable = ready && !session.readOnly
    LaunchedEffect(menu, lineOpen, statistics, blameRequest) {
        if (menu != null || lineOpen || statistics || blameRequest != null) editor?.dismissSelectionMenu()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        EditorToolButton(Icons.Default.Undo, R.string.workspace_editor_undo, writable && session.status.canUndo) { editor?.undo() }
        EditorToolButton(Icons.Default.Redo, R.string.workspace_editor_redo, writable && session.status.canRedo) { editor?.redo() }
        EditorToolButton(Icons.Default.Save, R.string.save, canSave && !saving && !session.readOnly) { editor?.dismissSelectionMenu(); onSave() }
        Box {
            EditorToolButton(Icons.Default.Edit, R.string.workspace_editor_edit, ready) { menu = "edit" }
            WorkbenchDropdownMenu(menu == "edit", { menu = null }, modifier = Modifier.widthIn(min = 190.dp, max = 260.dp)) {
                val actions = listOf(
                    EditorAction.COPY_LINE to R.string.workspace_editor_copy_line,
                    EditorAction.CUT_LINE to R.string.workspace_editor_cut_line,
                    EditorAction.DELETE_LINE to R.string.workspace_editor_delete_line,
                    EditorAction.CLEAR_LINE to R.string.workspace_editor_clear_line,
                    EditorAction.DUPLICATE_LINE to R.string.workspace_editor_duplicate_line,
                    EditorAction.UPPERCASE to R.string.workspace_editor_uppercase,
                    EditorAction.LOWERCASE to R.string.workspace_editor_lowercase,
                    EditorAction.INDENT to R.string.workspace_editor_indent,
                    EditorAction.UNINDENT to R.string.workspace_editor_unindent,
                )
                actions.forEach { (action, label) ->
                    val changes = action != EditorAction.COPY_LINE
                    val needsSelection = action in setOf(EditorAction.UPPERCASE, EditorAction.LOWERCASE)
                    DropdownMenuItem(text = { Text(stringResource(label)) },
                        leadingIcon = { Icon(when (action) {
                            EditorAction.COPY_LINE -> Icons.Default.ContentCopy
                            EditorAction.CUT_LINE -> Icons.Default.ContentCut
                            EditorAction.DELETE_LINE, EditorAction.CLEAR_LINE -> Icons.Default.Delete
                            EditorAction.DUPLICATE_LINE -> Icons.Default.Add
                            EditorAction.INDENT -> Icons.Default.FormatIndentIncrease
                            EditorAction.UNINDENT -> Icons.Default.FormatIndentDecrease
                            else -> Icons.Default.TextFields
                        }, null, Modifier.size(18.dp)) },
                        enabled = ready && (!changes || writable) && (!needsSelection || session.status.hasSelection),
                        onClick = { menu = null; editor?.performEditorAction(action) })
                }
            }
        }
        Box {
            EditorToolButton(Icons.Default.MoreVert, R.string.more, ready) { menu = "more" }
            WorkbenchDropdownMenu(menu == "more" || menu == "file", { menu = null }, modifier = Modifier.widthIn(min = 210.dp, max = 280.dp)) {
                if (menu == "file") {
                    EditorMenuItem(R.string.workspace_editor_file, Icons.Default.ArrowBack) { menu = "more" }
                    EditorMenuItem(R.string.workspace_editor_save_as, Icons.Default.Save, enabled = !saving) { menu = null; onSaveAs() }
                    EditorMenuItem(R.string.workspace_editor_reload, Icons.Default.Refresh, enabled = !saving) { menu = null; onReload() }
                    EditorMenuItem(R.string.workspace_editor_statistics, Icons.Default.Info) { menu = null; statistics = true }
                    EditorMenuItem(R.string.close, Icons.Default.Close) { menu = null; onClose() }
                } else {
                    EditorMenuItem(R.string.workspace_editor_file, Icons.Default.FolderOpen) { menu = "file" }
                    EditorMenuItem(R.string.workspace_editor_find, Icons.Default.Search) { menu = null; onFind() }
                    EditorMenuItem(R.string.workspace_editor_go_line, Icons.Default.FormatListNumbered) { menu = null; lineOpen = true }
                    EditorMenuItem(R.string.workspace_blame_title, Icons.Default.History) {
                        menu = null
                        editor?.dismissSelectionMenu()
                        keyboard?.hide()
                        val snapshot = session.document.snapshot()
                        val line = snapshot.lines.lineForOffset(session.document.selectionEnd.coerceIn(0, snapshot.length)) + 1
                        blameRequest = GitBlameRequest(snapshot, line)
                    }
                    EditorMenuItem(R.string.workspace_browser_word_wrap, Icons.Default.WrapText, checked = settings.wordWrap) { menu = null; change { it.copy(wordWrap = !it.wordWrap) } }
                    EditorMenuItem(R.string.workspace_editor_read_only, Icons.Default.Lock, checked = session.readOnly) { menu = null; session.readOnly = !session.readOnly }
                    EditorMenuItem(R.string.settings, Icons.Default.Settings) { menu = null; editor?.dismissSelectionMenu(); onSettings() }
                }
            }
        }
    }
    blameRequest?.let { request ->
        WorkspaceGitBlameDialog(path, request, { blame.load(it.snapshot, it.line) }, blame::change) { blameRequest = null }
    }
    if (lineOpen) {
        var line by remember { mutableStateOf("") }
        val number = line.toIntOrNull()
        val valid = number != null && number in 1..session.status.lines
        AlertDialog(onDismissRequest = { lineOpen = false }, title = { Text(stringResource(R.string.workspace_editor_go_line)) },
            text = { OutlinedTextField(line, { line = it }, singleLine = true, isError = line.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.workspace_editor_line_range, session.status.lines)) }) },
            confirmButton = { TextButton(onClick = { number?.let { editor?.goToLine(it) }; lineOpen = false }, enabled = valid) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { lineOpen = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (statistics) {
        val text = editor?.getText().orEmpty()
        val start = minOf(session.status.selectionStart, session.status.selectionEnd).coerceIn(0, text.length)
        val end = maxOf(session.status.selectionStart, session.status.selectionEnd).coerceIn(start, text.length)
        AlertDialog(onDismissRequest = { statistics = false }, title = { Text(stringResource(R.string.workspace_editor_statistics)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.workspace_editor_stats_text, session.status.lines, text.codePointCount(0, text.length), text.codePointCount(start, end)))
            } }, confirmButton = { TextButton(onClick = { statistics = false }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun EditorToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, enabled: Boolean, click: () -> Unit) {
    IconButton(onClick = click, enabled = enabled, modifier = Modifier.size(44.dp)) { Icon(icon, stringResource(label), Modifier.size(24.dp)) }
}

@Composable
private fun EditorMenuItem(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, checked: Boolean? = null, click: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        trailingIcon = if (checked == null) null else { { if (checked) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp)) } },
        enabled = enabled, onClick = click)
}
