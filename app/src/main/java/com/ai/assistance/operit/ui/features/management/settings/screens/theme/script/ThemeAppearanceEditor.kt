package com.ai.assistance.operit.ui.features.management.settings.screens.theme.script

import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.theme.*
import com.ai.assistance.operit.ui.theme.script.themeScriptDecoration
import java.util.Locale

/** 可视化字段修改资源束的基础外观，脚本源码保留，由 render 显式消费该外观。 */
@Composable
internal fun ThemeAppearanceEditor(
    appearance: ThemeScriptAppearance,
    assets: Set<String>,
    onChange: (ThemeScriptAppearance) -> Unit,
    previewMode: String,
    onPreviewModeChange: (String) -> Unit,
) {
    var tab by remember { mutableStateOf("surfaces") }
    var area by remember { mutableStateOf("composer") }
    var iconId by remember { mutableStateOf("composer.send") }
    val mode = previewMode
    ThemeAppearanceChoice(
        stringResource(R.string.theme_appearance_category), tab,
        listOf("surfaces" to stringResource(R.string.theme_appearance_surfaces), "icons" to stringResource(R.string.theme_appearance_icons), "palette" to stringResource(R.string.theme_appearance_palette)),
    ) { tab = it }
    when (tab) {
        "surfaces" -> {
            ThemeAppearanceChoice(
                stringResource(R.string.theme_appearance_area), area,
                listOf("global" to stringResource(R.string.theme_appearance_global_text)) + ThemeScriptContract.surfaceIds.map { it to themeAppearanceAreaLabel(it) },
            ) { area = it }
            if (area == "global") {
                ThemeAppearanceTextEditor(appearance.typography) { onChange(appearance.copy(typography = it)) }
            } else {
                val surface = appearance.surfaces[area] ?: ThemeScriptSurface()
                fun update(value: ThemeScriptSurface) { onChange(appearance.copy(surfaces = appearance.surfaces + (area to value))) }
                ThemeAppearanceChoice(
                    stringResource(R.string.theme_appearance_shape), surface.shape?.type.orEmpty(),
                    listOf("" to stringResource(R.string.theme_appearance_inherit), "rounded" to stringResource(R.string.theme_appearance_rounded), "cut" to stringResource(R.string.theme_appearance_cut), "circle" to stringResource(R.string.theme_appearance_circle)),
                ) { update(surface.copy(shape = if (it.isEmpty()) null else ThemeAppearanceShape(it))) }
                surface.shape?.let { shape ->
                    if (shape.type != "circle") ThemeAppearanceSlider(stringResource(R.string.theme_appearance_radius), shape.radius, 0f..128f) { update(surface.copy(shape = shape.copy(radius = it))) }
                }
                ThemeAppearanceColorField(stringResource(R.string.theme_appearance_fill), surface.fillColor) { update(surface.copy(fillColor = it)) }
                ThemeAppearanceColorField(stringResource(R.string.theme_appearance_content), surface.contentColor) { update(surface.copy(contentColor = it)) }
                ThemeAppearanceColorField(stringResource(R.string.theme_appearance_stroke), surface.strokeColor) { update(surface.copy(strokeColor = it)) }
                ThemeAppearanceSlider(stringResource(R.string.theme_appearance_stroke_width), surface.strokeWidth, 0f..16f) { update(surface.copy(strokeWidth = it)) }
                ThemeAppearanceSlider(stringResource(R.string.theme_appearance_min_height), surface.minHeight, 0f..256f) { update(surface.copy(minHeight = it)) }
                ThemeAppearanceSlider(stringResource(R.string.theme_appearance_elevation), surface.elevation ?: 0f, 0f..24f) { update(surface.copy(elevation = it)) }
                if (area == "userAvatar" || area == "assistantAvatar") {
                    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_avatar_size), surface.size ?: 32f, 16f..96f) { update(surface.copy(size = it)) }
                }
                if (area == "userMessage" || area == "assistantMessage") {
                    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_message_width), surface.messageWidth, 0.3f..1f) { update(surface.copy(messageWidth = it)) }
                }
                if (area != "background") ThemeAppearanceInsetsEditor(surface.padding, 48f) { update(surface.copy(padding = it)) }
                ThemeAppearanceTextEditor(surface.textStyle) { update(surface.copy(textStyle = it)) }
                val texture = surface.layers.firstOrNull { it.id == "editorTexture" }
                ThemeAppearanceChoice(
                    stringResource(R.string.theme_appearance_texture), texture?.asset.orEmpty(),
                    assetChoices(assets, texture?.asset),
                ) { name ->
                    val layers = surface.layers.filterNot { it.id == "editorTexture" }
                    val next = if (name.isEmpty()) layers else listOf(ThemeScriptLayer("image", id = "editorTexture", asset = name, fit = "crop")) + layers
                    update(surface.copy(layers = next, fillColor = if (name.isEmpty()) surface.fillColor else "#00000000"))
                }
                texture?.let { layer ->
                    ThemeAppearanceChoice(
                        stringResource(R.string.theme_appearance_image_fit), if (layer.type == "nineSlice") "nineSlice" else layer.fit,
                        listOf("fill" to stringResource(R.string.theme_appearance_fill_fit), "fit" to stringResource(R.string.theme_appearance_fit), "crop" to stringResource(R.string.theme_appearance_crop), "nineSlice" to stringResource(R.string.theme_appearance_nine_slice)),
                    ) { fit ->
                        val next = if (fit == "nineSlice") layer.copy(type = "nineSlice", slices = ThemeScriptInsets(8f, 8f, 8f, 8f)) else layer.copy(type = "image", fit = fit)
                        update(surface.copy(layers = surface.layers.map { if (it.id == "editorTexture") next else it }))
                    }
                    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_opacity), layer.opacity, 0f..1f) { opacity ->
                        update(surface.copy(layers = surface.layers.map { if (it.id == "editorTexture") it.copy(opacity = opacity) else it }))
                    }
                    if (layer.type == "nineSlice") {
                        Text(stringResource(R.string.theme_appearance_slice_hint), style = MaterialTheme.typography.bodySmall)
                        ThemeAppearanceInsetsEditor(layer.slices, 128f) { slices ->
                            update(surface.copy(layers = surface.layers.map { if (it.id == "editorTexture") it.copy(slices = slices) else it }))
                        }
                    }
                }
                TextButton(onClick = { onChange(appearance.copy(surfaces = appearance.surfaces - area)) }) { Text(stringResource(R.string.theme_appearance_reset_area)) }
            }
        }
        "icons" -> {
            ThemeAppearanceChoice(stringResource(R.string.theme_appearance_icon_target), iconId, ThemeScriptContract.iconIds.map { it to themeAppearanceIconLabel(it) }) { iconId = it }
            val icon = appearance.icons[iconId]
            ThemeAppearanceChoice(stringResource(R.string.theme_appearance_icon_asset), icon?.asset.orEmpty(), assetChoices(assets, icon?.asset)) { name ->
                onChange(appearance.copy(icons = if (name.isEmpty()) appearance.icons - iconId else appearance.icons + (iconId to ThemeAppearanceIcon(name, icon?.size ?: 24f))))
            }
            icon?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.theme_appearance_icon_tint))
                    Switch(checked = it.useContentTint, onCheckedChange = { checked -> onChange(appearance.copy(icons = appearance.icons + (iconId to it.copy(useContentTint = checked)))) })
                }
                ThemeAppearanceSlider(stringResource(R.string.theme_appearance_icon_size), it.size, 8f..64f) { size -> onChange(appearance.copy(icons = appearance.icons + (iconId to it.copy(size = size)))) }
                ThemeAppearanceSlider(stringResource(R.string.theme_appearance_opacity), it.opacity, 0f..1f) { opacity -> onChange(appearance.copy(icons = appearance.icons + (iconId to it.copy(opacity = opacity)))) }
            }
            Text(stringResource(R.string.theme_appearance_icon_hint), style = MaterialTheme.typography.bodySmall)
        }
        "palette" -> {
            ThemeAppearanceChoice(stringResource(R.string.theme_appearance_palette_mode), mode, listOf("light" to stringResource(R.string.theme_appearance_light), "dark" to stringResource(R.string.theme_appearance_dark))) {
                onPreviewModeChange(it)
            }
            val values = if (mode == "dark") appearance.palette.dark else appearance.palette.light
            ThemeScriptContract.colorTokens.forEach { token ->
                ThemeAppearanceColorField(token, values[token]) { color ->
                    val next = if (color == null) values - token else values + (token to color)
                    onChange(appearance.copy(palette = if (mode == "dark") appearance.palette.copy(dark = next) else appearance.palette.copy(light = next)))
                }
            }
        }
    }
}

@Composable
internal fun ThemeAppearanceChoice(label: String, value: String, choices: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val text = choices.first { it.first == value }.second
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$label · $text") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onChange(id) }) }
        }
    }
}

@Composable
private fun assetChoices(assets: Set<String>, selected: String?): List<Pair<String, String>> =
    listOf("" to stringResource(R.string.theme_appearance_inherit)) +
        (assets + listOfNotNull(selected)).sorted().map { name -> name to if (name in assets) name else "$name · ${stringResource(R.string.theme_appearance_missing_asset)}" }

@Composable
private fun ThemeAppearanceColorField(label: String, value: String?, onChange: (String?) -> Unit) {
    val valid = value == null || Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?").matches(value)
    OutlinedTextField(
        value = value.orEmpty(), onValueChange = { onChange(it.takeIf(String::isNotEmpty)) },
        label = { Text(label) }, placeholder = { Text(stringResource(R.string.theme_appearance_color_hint)) },
        singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth().themeScriptDecoration("input", error = !valid),
    )
}

@Composable
private fun ThemeAppearanceSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label · ${String.format(Locale.ROOT, "%.2f", value)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ThemeAppearanceInsetsEditor(value: ThemeScriptInsets, maximum: Float, onChange: (ThemeScriptInsets) -> Unit) {
    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_inset_start), value.start, 0f..maximum) { onChange(value.copy(start = it)) }
    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_inset_top), value.top, 0f..maximum) { onChange(value.copy(top = it)) }
    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_inset_end), value.end, 0f..maximum) { onChange(value.copy(end = it)) }
    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_inset_bottom), value.bottom, 0f..maximum) { onChange(value.copy(bottom = it)) }
}

@Composable
private fun ThemeAppearanceTextEditor(value: ThemeAppearanceText, onChange: (ThemeAppearanceText) -> Unit) {
    ThemeAppearanceChoice(
        stringResource(R.string.theme_appearance_font), value.family.orEmpty(),
        listOf("" to stringResource(R.string.theme_appearance_inherit), "default" to stringResource(R.string.theme_appearance_system_font), "sans-serif" to "Sans Serif", "serif" to "Serif", "monospace" to "Monospace", "cursive" to "Cursive"),
    ) { onChange(value.copy(family = it.takeIf(String::isNotEmpty))) }
    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_font_scale), value.scale, 0.5f..2f) { onChange(value.copy(scale = it)) }
    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_line_height), value.lineHeight, 0.8f..2f) { onChange(value.copy(lineHeight = it)) }
    ThemeAppearanceSlider(stringResource(R.string.theme_appearance_letter_spacing), value.letterSpacing, -2f..8f) { onChange(value.copy(letterSpacing = it)) }
}

@Composable
internal fun themeAppearanceAreaLabel(id: String): String = stringResource(when (id) {
    "background" -> R.string.theme_area_background
    "userMessage" -> R.string.theme_area_user_message
    "assistantMessage" -> R.string.theme_area_assistant_message
    "composer" -> R.string.theme_area_composer
    "userAvatar" -> R.string.theme_area_user_avatar
    "assistantAvatar" -> R.string.theme_area_assistant_avatar
    "appBar" -> R.string.theme_area_app_bar
    "navigation" -> R.string.theme_area_navigation
    "navigationItem" -> R.string.theme_area_navigation_item
    "button" -> R.string.theme_area_button
    "iconButton" -> R.string.theme_area_icon_button
    "input" -> R.string.theme_area_input
    "settingsCard" -> R.string.theme_area_settings_card
    "dialog" -> R.string.theme_area_dialog
    "sheet" -> R.string.theme_area_sheet
    "menu" -> R.string.theme_area_menu
    "listItem" -> R.string.theme_area_list_item
    "status" -> R.string.theme_area_status
    "toolCard" -> R.string.theme_area_tool_card
    else -> error("Unknown appearance area")
})

@Composable
internal fun themeAppearanceIconLabel(id: String): String = stringResource(when (id) {
    "composer.settings" -> R.string.theme_icon_settings
    "composer.attachment" -> R.string.theme_icon_attachment
    "composer.microphone" -> R.string.theme_icon_microphone
    "composer.send" -> R.string.theme_icon_send
    "composer.stop" -> R.string.theme_icon_stop
    "composer.fullscreen" -> R.string.theme_icon_fullscreen
    "composer.queue" -> R.string.theme_icon_queue
    "composer.expand" -> R.string.theme_icon_expand
    "appBar.back" -> R.string.theme_icon_back
    "appBar.menu" -> R.string.theme_icon_menu
    "appBar.history" -> R.string.theme_icon_history
    "appBar.floating" -> R.string.theme_icon_floating
    "navigation.item" -> R.string.theme_icon_navigation
    else -> error("Unknown appearance icon")
})