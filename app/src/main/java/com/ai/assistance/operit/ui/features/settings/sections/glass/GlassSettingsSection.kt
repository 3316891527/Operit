package com.ai.assistance.operit.ui.features.settings.sections.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.glass.GlassThemePreferences
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsSectionTitle
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.liquidGlass
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 参数、区域开关和按钮配色共同进入主题编辑会话，保存前不会写入持久配置。 */
@Composable
internal fun GlassSettingsSection(editorSession: ThemeEditorSession, cardColors: CardColors) {
    val values by editorSession.values.collectAsState()
    val supported = isLiquidGlassSupported()
    val scope = rememberCoroutineScope()
    var colorTarget by remember { mutableStateOf<String?>(null) }
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    val snapshot = LocalThemePreferenceSnapshot.current
    val colors = MaterialTheme.colorScheme
    val colorRows = listOf(
        Triple("custom_navigation_drawer_background_color", R.string.glass_sidebar_background, colors.surface.toArgb()),
        Triple("custom_navigation_drawer_accent_color", R.string.glass_sidebar_accent, colors.surfaceContainerHigh.toArgb()),
        Triple("package_market_button_color", R.string.glass_market_button_color, colors.secondaryContainer.toArgb()),
        Triple("package_add_button_color", R.string.glass_add_button_color, colors.primaryContainer.toArgb()),
    )
    val legacyColorFlags = mapOf(
        "custom_navigation_drawer_background_color" to "use_custom_navigation_drawer_background_color",
        "custom_navigation_drawer_accent_color" to "use_custom_navigation_drawer_accent_color",
    )

    ThemeSettingsSectionTitle(stringResource(R.string.glass_appearance_title), Icons.Default.BlurOn)
    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.glass_appearance_description), style = MaterialTheme.typography.bodySmall)
            if (!supported) {
                Text(stringResource(R.string.theme_glass_requires_android13), style = MaterialTheme.typography.bodySmall)
            }
            // 预览读取尚未保存的草稿，调节滑块时即可看到外观变化。
            CompositionLocalProvider(LocalThemePreferenceSnapshot provides snapshot.copy(values = values)) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp)
                        .liquidGlass(supported, RoundedCornerShape(12.dp), colors.surfaceContainer)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.glass_preview), color = colors.onSurface)
                }
            }
            GlassSlider(R.string.glass_blur, values.requiredFloat("glass_blur_radius"), 0f..40f, supported) {
                editorSession.setFloat("glass_blur_radius", it)
            }
            GlassSlider(R.string.glass_edge, values.requiredFloat("glass_edge_width"), 0f..2f, supported) {
                editorSession.setFloat("glass_edge_width", it)
            }
            GlassSlider(R.string.glass_highlight, values.requiredFloat("glass_highlight_alpha"), 0f..0.8f, supported, true) {
                editorSession.setFloat("glass_highlight_alpha", it)
            }
            GlassSlider(R.string.glass_shadow, values.requiredFloat("glass_shadow_radius"), 0f..20f, supported) {
                editorSession.setFloat("glass_shadow_radius", it)
            }
            GlassSlider(R.string.glass_opacity, values.requiredFloat("glass_tint_opacity"), 0f..1f, supported, true) {
                editorSession.setFloat("glass_tint_opacity", it)
            }
            Text(stringResource(R.string.glass_opacity_description), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            TextButton(onClick = {
                editorSession.update { current -> current.copy(floats = current.floats + GlassThemePreferences.floatDefaults) }
            }) { Text(stringResource(R.string.glass_reset_parameters)) }
            HorizontalDivider()
            listOf(
                "glass_conversation_list" to R.string.glass_conversation_list,
                "glass_settings_cards" to R.string.glass_settings_cards,
                "glass_package_cards" to R.string.glass_package_cards,
            ).forEach { (key, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = supported && values.requiredBoolean(key), enabled = supported,
                        onCheckedChange = { editorSession.setBoolean(key, it) })
                }
            }
        }
    }

    ThemeSettingsSectionTitle(stringResource(R.string.glass_button_colors), Icons.Default.BlurOn)
    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(Modifier.padding(16.dp)) {
            colorRows.forEachIndexed { index, (key, label, defaultColor) ->
                if (index != 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        editorSession.setInt(key, null)
                        legacyColorFlags[key]?.let { editorSession.setBoolean(it, false) }
                    }, enabled = values.int(key) != null) { Text(stringResource(R.string.glass_default_color)) }
                    TextButton(onClick = { colorTarget = key }) {
                        Box(Modifier.size(22.dp).background(Color(values.int(key) ?: defaultColor), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.glass_choose_color))
                    }
                }
            }
        }
    }
    colorTarget?.let { key ->
        val row = colorRows.first { it.first == key }
        val selected = values.int(key) ?: row.third
        ColorPickerDialog(
            showColorPicker = true,
            currentColorPickerMode = "primary",
            primaryColorInput = selected,
            secondaryColorInput = selected,
            cursorUserBubbleColorInput = selected,
            bubbleUserBubbleColorInput = selected,
            bubbleAiBubbleColorInput = selected,
            bubbleUserTextColorInput = selected,
            bubbleAiTextColorInput = selected,
            recentColors = recentColors,
            onColorSelected = { primary, _, _, _, _, _, _ ->
                primary?.let { color ->
                    editorSession.setInt(key, color)
                    scope.launch { editorSession.addRecentColor(color) }
                    legacyColorFlags[key]?.let { editorSession.setBoolean(it, true) }
                }
                colorTarget = null
            },
            onDismiss = { colorTarget = null },
            titleOverride = stringResource(row.second),
        )
    }
}

@Composable
private fun GlassSlider(
    label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    percent: Boolean = false,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(if (percent) stringResource(R.string.glass_value_percent, (value * 100).roundToInt())
                else stringResource(R.string.glass_value_dp, value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}
