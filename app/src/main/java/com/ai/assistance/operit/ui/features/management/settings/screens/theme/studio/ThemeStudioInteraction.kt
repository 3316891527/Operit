package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeRipple
import com.ai.assistance.operit.data.theme.script.ThemeStateTransition
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import kotlin.math.roundToInt

@Composable
internal fun StudioInteractionProperties(state: ThemeStudioState) {
    val surface = state.surface()
    var chooseColor by remember { mutableStateOf(false) }
    StudioSection(stringResource(R.string.studio_state_transition)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.studio_state_transition_enable), Modifier.weight(1f))
            Switch(checked = surface.transition != null, onCheckedChange = { enabled ->
                state.updateSurface { it.copy(transition = if (enabled) ThemeStateTransition() else null) }
            })
        }
        surface.transition?.let { transition ->
            StudioSlider(state, stringResource(R.string.studio_state_transition_duration), transition.durationMillis.toFloat(), 0f..1000f, "ms", commitOnFinish = true) { duration ->
                state.updateSurface { it.copy(transition = transition.copy(durationMillis = duration.roundToInt())) }
            }
        }
        StudioHint(stringResource(R.string.studio_state_transition_hint))
    }
    StudioSection(stringResource(R.string.studio_ripple)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.studio_ripple_override), Modifier.weight(1f))
            Switch(checked = surface.ripple != null, onCheckedChange = { custom ->
                state.updateSurface { it.copy(ripple = if (custom) ThemeRipple() else null) }
            })
        }
        surface.ripple?.let { ripple ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.studio_ripple_enable), Modifier.weight(1f))
                Switch(checked = ripple.enabled, onCheckedChange = { enabled -> state.updateSurface { it.copy(ripple = ripple.copy(enabled = enabled)) } })
            }
            if (ripple.enabled) TextButton(onClick = { chooseColor = true }) { Text(stringResource(R.string.studio_ripple_color)) }
        }
    }
    if (chooseColor) StudioColorPicker(state, stringResource(R.string.studio_ripple_color), surface.ripple?.color, StudioAccent,
        onChange = { color -> state.updateSurface { it.copy(ripple = (it.ripple ?: ThemeRipple()).copy(color = color)) } },
        onClose = { chooseColor = false })
}
