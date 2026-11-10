package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.*
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

@Composable
internal fun StudioSurfaceGradient(state: ThemeStudioState) {
    StudioGradientProperties(state, state.surface().fillGradient) { gradient ->
        state.updateSurface { it.copy(fillGradient = gradient, fillColor = if (gradient == null) it.fillColor else null) }
    }
}

/** Uses exactly the same stops and normalized geometry as the JavaScript protocol. */
@Composable
internal fun StudioGradientProperties(state: ThemeStudioState, gradient: ThemeGradient?, onChange: (ThemeGradient?) -> Unit) {
    var colorIndex by remember { mutableStateOf<Int?>(null) }
    StudioSection(stringResource(R.string.studio_gradient)) {
        StudioChoices(gradient?.type ?: "none", listOf("none" to stringResource(R.string.studio_gradient_none), "linear" to stringResource(R.string.studio_gradient_linear), "radial" to stringResource(R.string.studio_gradient_radial))) { type ->
            onChange(if (type == "none") null else (gradient ?: ThemeGradient(stops = listOf(ThemeGradientStop(0f, "#FF6750A4"), ThemeGradientStop(1f, "#FFB8D8FF")))).copy(type = type))
        }
        if (gradient != null) {
            gradient.stops.forEachIndexed { index, stop ->
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { colorIndex = index }) { Text("${index + 1} · ${stop.color}") }
                    if (index > 0 && index < gradient.stops.lastIndex) TextButton(onClick = { onChange(gradient.copy(stops = gradient.stops.filterIndexed { i, _ -> i != index })) }) { Text(stringResource(R.string.studio_delete)) }
                }
                if (index > 0 && index < gradient.stops.lastIndex) {
                    val lower = gradient.stops[index - 1].offset + .001f
                    val upper = gradient.stops[index + 1].offset - .001f
                    if (lower < upper) StudioSlider(state, stringResource(R.string.studio_gradient_stop), stop.offset, lower..upper, precision = 3) { value -> onChange(gradient.copy(stops = gradient.stops.mapIndexed { i, old -> if (i == index) old.copy(offset = value) else old })) }
                }
            }
            TextButton(enabled = gradient.stops.size < 16, onClick = {
                val pair = gradient.stops.zipWithNext().withIndex().maxBy { it.value.second.offset - it.value.first.offset }
                val stops = gradient.stops.toMutableList()
                stops.add(pair.index + 1, ThemeGradientStop((pair.value.first.offset + pair.value.second.offset) / 2, pair.value.first.color))
                onChange(gradient.copy(stops = stops))
            }) { Text(stringResource(R.string.studio_gradient_add_stop)) }
            if (gradient.type == "linear") {
                val direction = when {
                    gradient.start != ThemeGradientPoint(0f, 0f) -> "custom"
                    gradient.end == ThemeGradientPoint(1f, 0f) -> "horizontal"
                    gradient.end == ThemeGradientPoint(0f, 1f) -> "vertical"
                    gradient.end == ThemeGradientPoint(1f, 1f) -> "diagonal"
                    else -> "custom"
                }
                StudioChoices(direction, listOf("horizontal" to stringResource(R.string.studio_gradient_horizontal), "vertical" to stringResource(R.string.studio_gradient_vertical), "diagonal" to stringResource(R.string.studio_gradient_diagonal))) { selection ->
                    val end = when (selection) { "horizontal" -> ThemeGradientPoint(1f, 0f); "vertical" -> ThemeGradientPoint(0f, 1f); else -> ThemeGradientPoint(1f, 1f) }
                    onChange(gradient.copy(start = ThemeGradientPoint(0f, 0f), end = end))
                }
                StudioSlider(state, stringResource(R.string.studio_gradient_start_x), gradient.start.x, 0f..1f) { onChange(gradient.copy(start = gradient.start.copy(x = it))) }
                StudioSlider(state, stringResource(R.string.studio_gradient_start_y), gradient.start.y, 0f..1f) { onChange(gradient.copy(start = gradient.start.copy(y = it))) }
                StudioSlider(state, stringResource(R.string.studio_gradient_end_x), gradient.end.x, 0f..1f) { onChange(gradient.copy(end = gradient.end.copy(x = it))) }
                StudioSlider(state, stringResource(R.string.studio_gradient_end_y), gradient.end.y, 0f..1f) { onChange(gradient.copy(end = gradient.end.copy(y = it))) }
            } else {
                StudioSlider(state, stringResource(R.string.studio_gradient_center_x), gradient.center.x, 0f..1f) { onChange(gradient.copy(center = gradient.center.copy(x = it))) }
                StudioSlider(state, stringResource(R.string.studio_gradient_center_y), gradient.center.y, 0f..1f) { onChange(gradient.copy(center = gradient.center.copy(y = it))) }
                StudioSlider(state, stringResource(R.string.studio_gradient_radius), gradient.radius, .01f..2f) { onChange(gradient.copy(radius = it)) }
            }
        }
    }
    val selected = colorIndex
    if (selected != null && gradient != null && selected in gradient.stops.indices) StudioColorPicker(state,
        stringResource(R.string.studio_gradient), gradient.stops[selected].color, Color.White,
        onChange = { value -> if (value != null) onChange(gradient.copy(stops = gradient.stops.mapIndexed { i, old -> if (i == selected) old.copy(color = value) else old })) },
        onClose = { colorIndex = null }, allowInherit = false)
}
