package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeTextStroke

/** 自定义主题的所有字体入口共用文字描边编辑，普通主题设置不增加控件。 */
@Composable
internal fun StudioTextStrokeProperties(state: ThemeStudioState) {
    val global = state.target.kind == StudioKind.Typography && state.textTarget == "global"
    val text = state.text()
    val stroke = text.stroke
    var chooseColor by remember { mutableStateOf(false) }
    val title = state.target.label() + " · " + stringResource(R.string.studio_text_stroke_color) + " · " +
        stringResource(if (state.mode == "light") R.string.studio_light else R.string.studio_dark)
    StudioSection(stringResource(R.string.studio_text_stroke)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (global) R.string.studio_text_stroke_enable else R.string.studio_text_stroke_override), Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = stroke != null, onCheckedChange = { enabled ->
                state.updateText { before -> before.copy(stroke = if (enabled)
                    before.stroke ?: state.appearance.typography.stroke ?: ThemeTextStroke(width = 0.1f)
                    else null) }
            })
        }
        if (stroke != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.studio_text_stroke_color), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { chooseColor = true }) {
                    androidx.compose.foundation.layout.Box(Modifier.size(24.dp).background(Color(android.graphics.Color.parseColor(stroke.color)), RoundedCornerShape(6.dp)))
                }
            }
            StudioSlider(state, stringResource(R.string.studio_stroke_width), stroke.width, 0f..8f, "dp", precision = 2, commitOnFinish = true) { width ->
                state.updateText { before -> before.copy(stroke = requireNotNull(before.stroke).copy(width = width)) }
            }
        }
    }
    if (chooseColor) {
        StudioColorPicker(state, title, stroke?.color, Color.Black,
            onChange = { color -> state.updateText { before -> before.copy(stroke = color?.let { (before.stroke ?: ThemeTextStroke(width = 0.1f)).copy(color = it) }) } },
            onClose = { chooseColor = false },
        )
    }
}
