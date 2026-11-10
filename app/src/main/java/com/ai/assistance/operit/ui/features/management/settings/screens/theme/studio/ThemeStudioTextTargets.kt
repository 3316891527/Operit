package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeStyleTargets
import com.ai.assistance.operit.ui.components.ActionSheetItem
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField

@Composable
internal fun StudioTextTargetPicker(state: ThemeStudioState) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    StudioSection(stringResource(R.string.studio_text_target)) {
        Box {
            TextButton(onClick = { expanded = true }) { Text(studioTextTargetLabel(state.textTarget)) }
            WorkbenchDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.studio_search_targets)) })
                (listOf("global") + ThemeStyleTargets.textIds).forEach { id ->
                    val label = studioTextTargetLabel(id)
                    if (label.contains(query, true) || id.contains(query, true)) ActionSheetItem(
                        label = label, icon = state.target.glyph,
                        onClick = { state.textTarget = id; expanded = false },
                    )
                }
            }
        }
        StudioHint(stringResource(R.string.studio_text_target_hint))
    }
}
