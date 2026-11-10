package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.CHAT_PAGE_BACKGROUND_ID
import com.ai.assistance.operit.ui.components.ActionSheetItem
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField

@Composable
internal fun StudioBackgroundTargetPicker(state: ThemeStudioState) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    StudioSection(stringResource(R.string.studio_background_target)) {
        Box {
            TextButton(onClick = { expanded = true }) { Text(state.target.label()) }
            WorkbenchDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                OutlinedTextField(query, { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.studio_search_targets)) })
                ThemeStudioTargets.filter { it.isBackground }.forEach { target ->
                    val label = target.label()
                    if (label.contains(query, true) || target.id.contains(query, true)) ActionSheetItem(
                        label = label, icon = target.glyph,
                        onClick = { state.select(target); expanded = false },
                    )
                }
            }
        }
        StudioHint(stringResource(R.string.studio_background_target_hint))
        if (state.target.id != "background") TextButton(onClick = { state.resetPageBackground() }) {
            Text(stringResource(if (state.target.id == CHAT_PAGE_BACKGROUND_ID) R.string.studio_page_inherit else R.string.studio_page_follow_chat))
        }
        TextButton(onClick = { state.followChatBackgrounds() }) { Text(stringResource(R.string.studio_pages_follow_chat)) }
    }
}
