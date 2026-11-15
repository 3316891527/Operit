package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 结果和小开关置顶，输入使用细线，替换按需展开，底部只保留动作。 */
@Composable
internal fun EditorFindPanel(query: String, onQuery: (String) -> Unit, replacement: String, onReplacement: (String) -> Unit,
    replacementVisible: Boolean, onReplacementVisible: (Boolean) -> Unit, focusRequest: Int,
    options: EditorSearchOptions, onOptions: (EditorSearchOptions) -> Unit, count: Int, active: Int, error: String?, writable: Boolean,
    inSelection: Boolean, onClearScope: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onReplace: () -> Unit, onReplaceAll: () -> Unit, onClose: () -> Unit, searching: Boolean = false) {
    val focus = remember { FocusRequester() }
    val replacementFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequest) { focus.requestFocus() }
    LaunchedEffect(replacementVisible) { if (replacementVisible && writable) replacementFocus.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.workspace_editor_search_results, if (count == 0) 0 else active + 1, count),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = if (query.isNotEmpty() && count == 0 && error == null && !searching) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (inSelection) SearchOption("[]", R.string.workspace_editor_find_in_selection, true, announceToggle = false, onClick = onClearScope)
                SearchOption("Cc", R.string.workspace_editor_case_sensitive, options.caseSensitive) { onOptions(options.copy(caseSensitive = !options.caseSensitive)) }
                SearchOption("W", R.string.workspace_editor_whole_word, options.wholeWord) { onOptions(options.copy(wholeWord = !options.wholeWord)) }
                SearchOption(".*", R.string.workspace_editor_regex, options.regex) { onOptions(options.copy(regex = !options.regex)) }
            }
            SearchInput(query, onQuery, R.string.workspace_editor_find, Modifier.padding(horizontal = 12.dp).focusRequester(focus), error != null,
                onSubmit = onNext, trailing = {
                    IconButton(onClick = { onReplacementVisible(!replacementVisible) }, modifier = Modifier.size(32.dp)) {
                        Icon(if (replacementVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            stringResource(if (replacementVisible) R.string.workspace_editor_hide_replace else R.string.workspace_editor_show_replace), Modifier.size(18.dp))
                    }
                })
            if (replacementVisible) SearchInput(replacement, onReplacement, R.string.workspace_editor_replace, Modifier.padding(horizontal = 12.dp).focusRequester(replacementFocus),
                enabled = writable, onSubmit = onReplace)
            error?.let { Text(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchAction(R.string.workspace_editor_previous, count > 0 && !searching, Modifier.weight(1f), onPrevious)
                SearchAction(R.string.workspace_editor_next, count > 0 && !searching, Modifier.weight(1f), onNext)
                SearchAction(R.string.workspace_editor_replace, if (replacementVisible) writable && count > 0 && error == null else writable,
                    Modifier.weight(1f)) { if (replacementVisible) onReplace() else onReplacementVisible(true) }
                if (replacementVisible) SearchAction(R.string.workspace_editor_replace_all, writable && count > 0 && error == null, Modifier.weight(1f), onReplaceAll)
                IconButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); onClose() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.close), Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchOption(text: String, description: Int, checked: Boolean, announceToggle: Boolean = true, onClick: () -> Unit) {
    val context = LocalContext.current
    val label = stringResource(description)
    val state = stringResource(if (checked) R.string.workspace_search_option_on else R.string.workspace_search_option_off)
    Box(Modifier.size(width = 32.dp, height = 28.dp).background(
        if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
        .toggleable(value = checked, role = Role.Checkbox) { enabled ->
            onClick()
            if (announceToggle) OKToast.info(context, context.getString(R.string.workspace_search_option_state, label,
                context.getString(if (enabled) R.string.workspace_search_option_on else R.string.workspace_search_option_off)), OKToast.LENGTH_SHORT).show()
        }.semantics { contentDescription = label; stateDescription = state }, contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchAction(label: Int, enabled: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Box(modifier.heightIn(min = 36.dp).clickable(enabled = enabled, onClick = click), contentAlignment = Alignment.Center) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), maxLines = 1)
    }
}

@Composable
private fun SearchInput(value: String, change: (String) -> Unit, label: Int, modifier: Modifier = Modifier, error: Boolean = false,
    enabled: Boolean = true, onSubmit: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val description = stringResource(label)
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, change, Modifier.weight(1f).padding(vertical = 8.dp).semantics { contentDescription = description },
                enabled = enabled, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = color),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }))
            trailing?.invoke()
        }
        HorizontalDivider(color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)
    }
}
