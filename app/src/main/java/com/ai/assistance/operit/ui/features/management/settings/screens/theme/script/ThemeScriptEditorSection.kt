package com.ai.assistance.operit.ui.features.management.settings.screens.theme.script

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard

/** 此处只编辑所选角色的草稿，持久化仍由主题页统一保存。 */
@Composable
internal fun ThemeScriptEditorSection(session: ThemeEditorSession) {
    val values by session.values.collectAsState()
    var editing by remember { mutableStateOf(false) }
    ThemeWorkbenchCard(
        title = stringResource(R.string.theme_script_title),
        subtitle = stringResource(R.string.theme_script_description),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editing = true }) { Text(stringResource(R.string.theme_script_edit)) }
            if (values.string(ThemePreferenceValues.SCRIPT_URI_KEY) != null) {
                TextButton(onClick = { session.setOptionalString(ThemePreferenceValues.SCRIPT_URI_KEY, null) }) {
                    Text(stringResource(R.string.theme_script_remove))
                }
            }
        }
    }
    if (editing) ThemeScriptEditorDialog(session) { editing = false }
}
