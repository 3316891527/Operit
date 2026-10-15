package com.ai.assistance.operit.ui.theme

import android.os.Build
import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid

val LocalWaterGlassState = compositionLocalOf<LiquidState?> { null }

private const val WaterGlassMinApi = Build.VERSION_CODES.TIRAMISU

fun isWaterGlassSupported(): Boolean = Build.VERSION.SDK_INT >= WaterGlassMinApi

@Composable
fun Modifier.waterGlass(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(0.dp),
    containerColor: Color,
    subtleRefraction: Boolean = false,
): Modifier {
    if (!enabled) {
        return this
    }

    val appearance = rememberGlassAppearance()
    val liquidState = if (isWaterGlassSupported()) LocalWaterGlassState.current else null
    val isLightGlass = containerColor.luminance() >= 0.5f
    if (liquidState == null) {
        val borderColor = Color.White.copy(alpha = appearance.highlightAlpha)

        return this
            .shadow(
                elevation = appearance.shadowRadius.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isLightGlass) 0.10f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (isLightGlass) 0.10f else 0.18f),
            )
            .then(
                if (appearance.edgeWidth > 0f && appearance.highlightAlpha > 0f) {
                    Modifier.border(appearance.edgeWidth.dp, borderColor, shape)
                } else Modifier
            )
            .background(color = containerColor.copy(alpha = appearance.tintOpacity), shape = shape)
    }
    val surfaceTint = containerColor.copy(alpha = appearance.tintOpacity)
    val borderColor = Color.White.copy(alpha = appearance.highlightAlpha)
    val shadowColor =
        if (isLightGlass) {
            Color.Black.copy(alpha = 0.10f)
        } else {
            Color.Black.copy(alpha = 0.18f)
        }

    return this
        .shadow(
            elevation = appearance.shadowRadius.dp,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        )
        .then(
            if (appearance.edgeWidth > 0f && appearance.highlightAlpha > 0f) {
                Modifier.border(appearance.edgeWidth.dp, borderColor, shape)
            } else Modifier
        )
        .liquid(liquidState) {
            this.shape = shape
            // 磨砂仍使用主题参数，恢复原有曲面与折射强度，保留水玻璃的视觉区分。
            this.frost = appearance.blurRadius.dp
            this.curve = if (subtleRefraction) 0.08f else if (isLightGlass) 0.40f else 0.30f
            this.refraction = if (subtleRefraction) 0.02f else if (isLightGlass) 0.12f else 0.09f
            this.dispersion = 0.02f
            this.saturation = 1f
            this.contrast = 1f
            this.tint = surfaceTint
        }
}
