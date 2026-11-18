package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.components.slider.WorkbenchSlider
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import kotlin.math.roundToInt

/** 设置独占页面，返回后继续使用同一个编辑会话。 */
@Composable
internal fun EditorSettingsScreen(settings: EditorSettings, change: ((EditorSettings) -> EditorSettings) -> Unit, onBack: () -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val unregister = OKToast.registerWindow(view)
            onDispose { unregister() }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Text(stringResource(R.string.workspace_editor_settings), style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    EditorSettingSection(R.string.workspace_editor_section_typography)
                    EditorSettingSlider(R.string.workspace_editor_font_size, settings.fontSize, 8f..40f, 31,
                        format = { it.roundToInt().toString() }) { value -> change { it.copy(fontSize = value.roundToInt().toFloat()) } }
                    EditorSettingSlider(R.string.workspace_editor_line_spacing, settings.lineSpacing, 1f..2.5f, 29,
                        format = { String.format(java.util.Locale.getDefault(), "%.2f", it) }) { value -> change { it.copy(lineSpacing = value) } }
                    EditorSettingToggle(R.string.workspace_browser_word_wrap, settings.wordWrap) { value -> change { it.copy(wordWrap = value) } }
                    EditorSettingToggle(R.string.workspace_editor_wrap_words, settings.wrapWords, settings.wordWrap) { value -> change { it.copy(wrapWords = value) } }
                    EditorSettingDivider()
                    EditorSettingSection(R.string.workspace_editor_section_display)
                    EditorSettingToggle(R.string.workspace_editor_line_numbers, settings.lineNumbers) { value -> change { it.copy(lineNumbers = value) } }
                    EditorSettingToggle(R.string.workspace_editor_fixed_numbers, settings.fixedLineNumbers, settings.lineNumbers && !settings.wordWrap) { value -> change { it.copy(fixedLineNumbers = value) } }
                    EditorSettingToggle(R.string.workspace_editor_wrap_markers, settings.wrapMarkers, settings.wordWrap && settings.lineNumbers) { value -> change { it.copy(wrapMarkers = value) } }
                    EditorSettingToggle(R.string.workspace_editor_whitespace, settings.whitespace) { value -> change { it.copy(whitespace = value) } }
                    EditorSettingToggle(R.string.workspace_editor_hide_single_space, settings.hideSingleSpaces, settings.whitespace) { value -> change { it.copy(hideSingleSpaces = value) } }
                    EditorSettingDivider()
                    EditorSettingSection(R.string.workspace_editor_section_indent)
                    EditorSettingToggle(R.string.workspace_editor_auto_indent, settings.autoIndent) { value -> change { it.copy(autoIndent = value) } }
                    EditorSettingToggle(R.string.workspace_editor_use_tabs, settings.useTabs) { value -> change { it.copy(useTabs = value) } }
                    EditorSettingSlider(R.string.workspace_editor_indent_width, settings.indentWidth.toFloat(), 1f..8f, 6,
                        format = { it.roundToInt().toString() }) { value -> change { it.copy(indentWidth = value.roundToInt()) } }
                    EditorSettingToggle(R.string.workspace_editor_indent_guides, settings.indentGuides) { value -> change { it.copy(indentGuides = value) } }
                    EditorSettingDivider()
                    EditorSettingSection(R.string.workspace_editor_section_tools)
                    EditorSettingToggle(R.string.workspace_editor_syntax_highlight, settings.syntaxHighlight) { value -> change { it.copy(syntaxHighlight = value) } }
                    EditorSettingToggle(R.string.workspace_editor_symbols, settings.showSymbols) { value -> change { it.copy(showSymbols = value) } }
                    var symbols by remember(settings.symbols) { mutableStateOf(settings.symbols) }
                    val validSymbols = symbols.length <= 512 && '\n' !in symbols && '\r' !in symbols
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        OutlinedTextField(symbols, { symbols = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = settings.showSymbols,
                            label = { Text(stringResource(R.string.workspace_editor_custom_symbols)) }, isError = !validSymbols)
                        TextButton(onClick = { val value = symbols; change { it.copy(symbols = value) } },
                            enabled = settings.showSymbols && validSymbols && symbols != settings.symbols) { Text(stringResource(R.string.save)) }
                    }
                    EditorSettingToggle(R.string.workspace_editor_pinch_zoom, settings.pinchZoom) { value -> change { it.copy(pinchZoom = value) } }
                    EditorSettingDivider()
                    EditorSettingSection(R.string.workspace_editor_section_files)
                    EditorSettingToggle(R.string.workspace_editor_external_detection, settings.detectExternalChanges) { value -> change { it.copy(detectExternalChanges = value) } }
                }
            }
        }
    }
}

@Composable
private fun EditorSettingSection(label: Int) {
    Text(stringResource(label), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
}

@Composable
private fun EditorSettingDivider() {
    HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun EditorSettingToggle(label: Int, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun EditorSettingSlider(label: Int, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    format: (Float) -> String, onChange: (Float) -> Unit) {
    var value by remember(current) { mutableFloatStateOf(current) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(format(value), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        WorkbenchSlider(value, { value = it }, valueRange = range, steps = steps, onValueChangeFinished = { onChange(value) })
    }
}
