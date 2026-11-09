package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.ThemePageBackground
import com.ai.assistance.operit.ui.theme.script.themePreviewFocus
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

@Composable
internal fun StudioPagePreview(state: ThemeStudioState) {
    ThemePageBackground(state.target.id) {
        Column(Modifier.fillMaxSize().themePreviewFocus(state.target.id).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(studioPageLabel(state.target.id), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.studio_page_preview_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
