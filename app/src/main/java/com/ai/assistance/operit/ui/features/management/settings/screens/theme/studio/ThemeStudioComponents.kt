package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.LocalThemeScriptState
import com.ai.assistance.operit.ui.theme.script.ThemeScriptState
import java.util.Locale

internal val StudioAccent = Color(0xFFD1B5FF)
internal val StudioBackground = Color(0xFF111318)
internal val StudioPanel = Color(0xFF191D27)
internal val StudioLine = Color(0xFF303746)

@Composable
internal fun StudioTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(primary = StudioAccent, onPrimary = Color(0xFF2D1C42), background = StudioBackground, surface = StudioPanel, onSurface = Color(0xFFEDF0F7), onSurfaceVariant = Color(0xFF99A4B7), outline = Color(0xFF4B435A), surfaceContainer = Color(0xFF232836), surfaceContainerLow = StudioPanel)
    CompositionLocalProvider(LocalThemeScriptState provides ThemeScriptState.Disabled) { MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes(), content = content) }
}

@Composable
internal fun StudioSection(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
    HorizontalDivider(color = StudioLine)
}

@Composable
internal fun StudioHint(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
internal fun <T> StudioChoices(value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { (key, label) ->
            val selected = key == value
            Box(Modifier.heightIn(min = 40.dp).border(1.dp, if (selected) StudioAccent else StudioLine, RoundedCornerShape(8.dp)).background(if (selected) Color(0xFF3C2E4E) else Color(0xFF232936), RoundedCornerShape(8.dp)).clickable { onChange(key) }.padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color(0xFFECD9FF) else Color(0xFFAAB8CC))
            }
        }
    }
}

@Composable
internal fun StudioSlider(state: ThemeStudioState, label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String = "", onChange: (Float) -> Unit) {
    fun formatted(number: Float) = String.format(Locale.ROOT, if (range.endInclusive <= 2f) "%.2f" else "%.0f", number)
    var input by remember(label) { mutableStateOf(formatted(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) input = formatted(value) }
    val parsed = input.toFloatOrNull()
    val invalid = parsed == null || !parsed.isFinite() || parsed !in range
    DisposableEffect(state) { onDispose { state.endGesture() } }
    Column {
        Text(label + if (unit.isEmpty()) "" else " · $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = { state.beginGesture(); onChange(it) }, valueRange = range, onValueChangeFinished = state::endGesture, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = input,
                onValueChange = { text ->
                    input = text
                    text.toFloatOrNull()?.takeIf { it.isFinite() && it in range }?.let { state.beginGesture(); onChange(it) }
                },
                singleLine = true,
                isError = focused && invalid,
                textStyle = MaterialTheme.typography.labelSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(76.dp).onFocusChanged { if (!it.isFocused && focused) state.endGesture(); focused = it.isFocused },
            )
        }
    }
}
@Composable
internal fun StudioFullSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        StudioTheme {
            Column(Modifier.fillMaxSize().background(StudioBackground).imePadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.studio_close)) }
                }
                HorizontalDivider(color = StudioLine)
                content()
            }
        }
    }
}
