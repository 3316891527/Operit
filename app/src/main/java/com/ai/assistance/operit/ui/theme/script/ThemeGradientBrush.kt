package com.ai.assistance.operit.ui.theme.script

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import com.ai.assistance.operit.data.theme.script.ThemeGradient

internal fun ThemeGradient.brush(size: Size, origin: Offset = Offset.Zero): Brush {
    val colors = stops.map { it.offset to themeColor(it.color) }.toTypedArray()
    return when (type) {
        "linear" -> Brush.linearGradient(*colors,
            start = origin + Offset(start.x * size.width, start.y * size.height),
            end = origin + Offset(end.x * size.width, end.y * size.height))
        "radial" -> Brush.radialGradient(*colors,
            center = origin + Offset(center.x * size.width, center.y * size.height),
            radius = radius * size.minDimension)
        else -> error("Unknown gradient type")
    }
}
