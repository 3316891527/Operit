package com.ai.assistance.operit.ui.theme.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported

data class GlassAppearance(
    val blurRadius: Float,
    val edgeWidth: Float,
    val highlightAlpha: Float,
    val shadowRadius: Float,
    val tintOpacity: Float,
    val conversationList: Boolean,
    val settingsCards: Boolean,
    val packageCards: Boolean,
) {
    companion object {
        fun from(values: ThemePreferenceValues): GlassAppearance {
            val supported = isLiquidGlassSupported()
            return GlassAppearance(
                blurRadius = values.requiredFloat("glass_blur_radius").coerceIn(0f, 40f),
                edgeWidth = values.requiredFloat("glass_edge_width").coerceIn(0f, 2f),
                highlightAlpha = values.requiredFloat("glass_highlight_alpha").coerceIn(0f, 0.8f),
                shadowRadius = values.requiredFloat("glass_shadow_radius").coerceIn(0f, 20f),
                tintOpacity = values.requiredFloat("glass_tint_opacity").coerceIn(0f, 1f),
                conversationList = supported && values.requiredBoolean("glass_conversation_list"),
                settingsCards = supported && values.requiredBoolean("glass_settings_cards"),
                packageCards = supported && values.requiredBoolean("glass_package_cards"),
            )
        }
    }
}

@Composable
fun rememberGlassAppearance(): GlassAppearance {
    val values = LocalThemePreferenceSnapshot.current.values
    return remember(values) { GlassAppearance.from(values) }
}
