package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported

/** 侧栏使用主题默认配色，仅提供背景和导航按钮的玻璃开关。 */
@Composable
internal fun ThemeSettingsSidebarSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    waterGlass: Boolean,
    buttonGlass: Boolean,
) {
    val waterSupported = isWaterGlassSupported()
    val buttonSupported = isLiquidGlassSupported()
    ThemeSettingsSectionTitle(
        title = stringResource(R.string.theme_navigation_drawer_title),
        icon = Icons.Default.BlurOn,
    )
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(Modifier.padding(16.dp)) {
            SidebarGlassSetting(
                title = stringResource(R.string.theme_navigation_drawer_water_glass),
                description = stringResource(if (waterSupported) R.string.theme_navigation_drawer_water_glass_desc
                    else R.string.theme_glass_requires_android13),
                checked = waterGlass && waterSupported,
                enabled = waterSupported,
                onCheckedChange = { editorSession.setBoolean("navigation_drawer_water_glass", it) },
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SidebarGlassSetting(
                title = stringResource(R.string.theme_navigation_drawer_button_liquid_glass),
                description = stringResource(if (buttonSupported) R.string.theme_navigation_drawer_button_liquid_glass_desc
                    else R.string.theme_glass_requires_android13),
                checked = buttonGlass && buttonSupported,
                enabled = buttonSupported,
                onCheckedChange = { editorSession.setBoolean("navigation_drawer_button_liquid_glass", it) },
            )
        }
    }
}

@Composable
private fun SidebarGlassSetting(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}