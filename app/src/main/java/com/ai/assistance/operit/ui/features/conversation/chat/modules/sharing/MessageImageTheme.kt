package com.ai.assistance.operit.ui.features.conversation.chat.modules.sharing

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot

/** The active chat appearance passed to the separate image-rendering composition. */
data class MessageImageTheme(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val preferences: ThemePreferenceSnapshot,
    val darkTheme: Boolean,
)

@Composable
fun rememberMessageImageTheme(): MessageImageTheme {
    val preferences = LocalThemePreferenceSnapshot.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = if (preferences.useSystemTheme) {
        systemDarkTheme
    } else {
        preferences.themeMode == UserPreferencesManager.THEME_MODE_DARK
    }
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    return remember(colorScheme, typography, shapes, preferences, darkTheme) {
        MessageImageTheme(colorScheme, typography, shapes, preferences, darkTheme)
    }
}
