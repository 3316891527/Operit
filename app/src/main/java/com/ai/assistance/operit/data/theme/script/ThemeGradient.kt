package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.Serializable

/** Coordinates are normalized to the painted bounds; radial radius uses its shortest side. */
@Serializable
data class ThemeGradient(
    val type: String = "linear",
    val stops: List<ThemeGradientStop>,
    val start: ThemeGradientPoint = ThemeGradientPoint(0f, 0f),
    val end: ThemeGradientPoint = ThemeGradientPoint(1f, 1f),
    val center: ThemeGradientPoint = ThemeGradientPoint(0.5f, 0.5f),
    val radius: Float = 0.5f,
)

@Serializable
data class ThemeGradientStop(val offset: Float, val color: String)

@Serializable
data class ThemeGradientPoint(val x: Float, val y: Float)
