package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.ai.assistance.operit.data.theme.script.ThemeAppearanceText
import com.ai.assistance.operit.ui.theme.script.*

@Composable
internal fun themeTextTarget(style: TextStyle, target: String?): List<ThemeAppearanceText> {
    val appearance = (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.appearance ?: return emptyList()
    if (appearance.textStyles.isEmpty()) return emptyList()
    val t = MaterialTheme.typography
    val role = when (style) {
        t.displayLarge -> "displayLarge"
        t.displayMedium -> "displayMedium"
        t.displaySmall -> "displaySmall"
        t.headlineLarge -> "headlineLarge"
        t.headlineMedium -> "headlineMedium"
        t.headlineSmall -> "headlineSmall"
        t.titleLarge -> "titleLarge"
        t.titleMedium -> "titleMedium"
        t.titleSmall -> "titleSmall"
        t.bodyLarge -> "bodyLarge"
        t.bodyMedium -> "bodyMedium"
        t.bodySmall -> "bodySmall"
        t.labelLarge -> "labelLarge"
        t.labelMedium -> "labelMedium"
        t.labelSmall -> "labelSmall"
        else -> null
    }
    return listOfNotNull(role?.let(appearance.textStyles::get), target?.let(appearance.textStyles::get))
}

@Composable
internal fun applyTextTargets(style: TextStyle, targets: List<ThemeAppearanceText>): TextStyle {
    val images = (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.images.orEmpty()
    return targets.fold(style) { current, text -> applyThemeText(current, text, images) }
}

internal fun targetTextStroke(base: ResolvedThemeTextStroke?, targets: List<ThemeAppearanceText>): ResolvedThemeTextStroke? =
    targets.fold(base) { current, text ->
        val declared = text.stroke
        if (declared == null) current else if (declared.width == 0f) null else declared.resolved()
    }
