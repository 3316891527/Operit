package com.ai.assistance.operit.ui.features.memory.workbench

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.slider.WorkbenchSlider

internal data class MemoryChoice(val id: String, val title: String, val subtitle: String = "")

/** 下拉框和操作菜单共享表面样式，选项改变前不修改调用方草稿。 */
@Composable
internal fun MemoryMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 240.dp, max = 360.dp).heightIn(max = 380.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        properties = PopupProperties(focusable = true),
        content = content,
    )
}

@Composable
internal fun MemoryPicker(label: String, value: String, choices: List<MemoryChoice>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Surface(
                onClick = { query = ""; expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(choices.firstOrNull { it.id == value }?.title.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
            MemoryMenu(expanded, { expanded = false }) {
                if (choices.size > 8) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.memory_workbench_find_option)) },
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                    )
                }
                val filtered = choices.filter { (it.title + " " + it.subtitle).contains(query.trim(), ignoreCase = true) }
                if (filtered.isEmpty()) Text(stringResource(R.string.memory_workbench_no_options), Modifier.padding(16.dp))
                filtered.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(choice.title, style = MaterialTheme.typography.bodyMedium)
                                if (choice.subtitle.isNotBlank()) Text(choice.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailingIcon = { if (value == choice.id) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { expanded = false; onSelect(choice.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryChoiceSheet(title: String, choices: List<MemoryChoice>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit, description: String = "") {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 14.dp))
            choices.forEach { choice ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(choice.id) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = choice.id == selected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(choice.title, style = MaterialTheme.typography.bodyLarge)
                        if (choice.subtitle.isNotBlank()) Text(choice.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (choice.id == selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            }
        }
    }
}

/** 长详情和编辑共用居中模态，正文单独滚动，底部操作始终可达。 */
@Composable
internal fun MemoryModal(title: String, onDismiss: () -> Unit, footer: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                    }
                    HorizontalDivider()
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                    HorizontalDivider()
                    Box(Modifier.fillMaxWidth().padding(12.dp)) { footer() }
                }
            }
        }
    }
}

@Composable
internal fun MemoryValueSlider(label: String, value: Float, onValueChange: (Float) -> Unit, max: Float = 1f, enabled: Boolean = true) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.2f".format(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        WorkbenchSlider(value = value, onValueChange = onValueChange, valueRange = 0f..max, enabled = enabled, steps = if (max == 1f) 99 else 0)
    }
}
