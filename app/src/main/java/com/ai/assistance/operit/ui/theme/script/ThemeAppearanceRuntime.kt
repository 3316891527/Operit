package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.model.theme.*

internal fun themeColor(value: String): Color = Color(android.graphics.Color.parseColor(value))

/** 显式声明的 token 覆盖原配色，其余角色沿用当前角色主题。 */
internal fun applyThemePalette(base: ColorScheme, palette: ThemeAppearancePalette, dark: Boolean): ColorScheme {
    val values = if (dark) palette.dark else palette.light
    fun value(id: String, current: Color): Color = if (id in values) themeColor(values.getValue(id)) else current
    return base.copy(
        primary = value("primary", base.primary),
        onPrimary = value("onPrimary", base.onPrimary),
        primaryContainer = value("primaryContainer", base.primaryContainer),
        onPrimaryContainer = value("onPrimaryContainer", base.onPrimaryContainer),
        inversePrimary = value("inversePrimary", base.inversePrimary),
        secondary = value("secondary", base.secondary),
        onSecondary = value("onSecondary", base.onSecondary),
        secondaryContainer = value("secondaryContainer", base.secondaryContainer),
        onSecondaryContainer = value("onSecondaryContainer", base.onSecondaryContainer),
        tertiary = value("tertiary", base.tertiary),
        onTertiary = value("onTertiary", base.onTertiary),
        tertiaryContainer = value("tertiaryContainer", base.tertiaryContainer),
        onTertiaryContainer = value("onTertiaryContainer", base.onTertiaryContainer),
        background = value("background", base.background),
        onBackground = value("onBackground", base.onBackground),
        surface = value("surface", base.surface),
        onSurface = value("onSurface", base.onSurface),
        surfaceVariant = value("surfaceVariant", base.surfaceVariant),
        onSurfaceVariant = value("onSurfaceVariant", base.onSurfaceVariant),
        surfaceTint = value("surfaceTint", base.surfaceTint),
        inverseSurface = value("inverseSurface", base.inverseSurface),
        inverseOnSurface = value("inverseOnSurface", base.inverseOnSurface),
        error = value("error", base.error),
        onError = value("onError", base.onError),
        errorContainer = value("errorContainer", base.errorContainer),
        onErrorContainer = value("onErrorContainer", base.onErrorContainer),
        outline = value("outline", base.outline),
        outlineVariant = value("outlineVariant", base.outlineVariant),
        scrim = value("scrim", base.scrim),
        surfaceBright = value("surfaceBright", base.surfaceBright),
        surfaceDim = value("surfaceDim", base.surfaceDim),
        surfaceContainerLowest = value("surfaceContainerLowest", base.surfaceContainerLowest),
        surfaceContainerLow = value("surfaceContainerLow", base.surfaceContainerLow),
        surfaceContainer = value("surfaceContainer", base.surfaceContainer),
        surfaceContainerHigh = value("surfaceContainerHigh", base.surfaceContainerHigh),
        surfaceContainerHighest = value("surfaceContainerHighest", base.surfaceContainerHighest),
    )
}

internal fun themeColorTokens(colors: ColorScheme): Map<String, String> {
    fun hex(color: Color): String = java.lang.String.format(java.util.Locale.ROOT, "#%08X", color.toArgb())
    return mapOf(
        "primary" to hex(colors.primary),
        "onPrimary" to hex(colors.onPrimary),
        "primaryContainer" to hex(colors.primaryContainer),
        "onPrimaryContainer" to hex(colors.onPrimaryContainer),
        "inversePrimary" to hex(colors.inversePrimary),
        "secondary" to hex(colors.secondary),
        "onSecondary" to hex(colors.onSecondary),
        "secondaryContainer" to hex(colors.secondaryContainer),
        "onSecondaryContainer" to hex(colors.onSecondaryContainer),
        "tertiary" to hex(colors.tertiary),
        "onTertiary" to hex(colors.onTertiary),
        "tertiaryContainer" to hex(colors.tertiaryContainer),
        "onTertiaryContainer" to hex(colors.onTertiaryContainer),
        "background" to hex(colors.background),
        "onBackground" to hex(colors.onBackground),
        "surface" to hex(colors.surface),
        "onSurface" to hex(colors.onSurface),
        "surfaceVariant" to hex(colors.surfaceVariant),
        "onSurfaceVariant" to hex(colors.onSurfaceVariant),
        "surfaceTint" to hex(colors.surfaceTint),
        "inverseSurface" to hex(colors.inverseSurface),
        "inverseOnSurface" to hex(colors.inverseOnSurface),
        "error" to hex(colors.error),
        "onError" to hex(colors.onError),
        "errorContainer" to hex(colors.errorContainer),
        "onErrorContainer" to hex(colors.onErrorContainer),
        "outline" to hex(colors.outline),
        "outlineVariant" to hex(colors.outlineVariant),
        "scrim" to hex(colors.scrim),
        "surfaceBright" to hex(colors.surfaceBright),
        "surfaceDim" to hex(colors.surfaceDim),
        "surfaceContainerLowest" to hex(colors.surfaceContainerLowest),
        "surfaceContainerLow" to hex(colors.surfaceContainerLow),
        "surfaceContainer" to hex(colors.surfaceContainer),
        "surfaceContainerHigh" to hex(colors.surfaceContainerHigh),
        "surfaceContainerHighest" to hex(colors.surfaceContainerHighest),
    )
}

internal fun applyThemeText(style: TextStyle, text: ThemeAppearanceText): TextStyle {
    if (text == ThemeAppearanceText()) return style
    val family = when (text.family) {
        null -> style.fontFamily
        "default" -> FontFamily.Default
        "sans-serif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> error("Unsupported theme font family")
    }
    return style.copy(
        fontFamily = family,
        fontSize = if (style.fontSize.isSpecified) style.fontSize * text.scale else style.fontSize,
        lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * (text.scale * text.lineHeight) else style.lineHeight,
        letterSpacing = if (text.letterSpacing == 0f) style.letterSpacing else text.letterSpacing.sp,
    )
}

internal fun applyThemeTypography(base: Typography, text: ThemeAppearanceText): Typography = base.copy(
    displayLarge = applyThemeText(base.displayLarge, text),
    displayMedium = applyThemeText(base.displayMedium, text),
    displaySmall = applyThemeText(base.displaySmall, text),
    headlineLarge = applyThemeText(base.headlineLarge, text),
    headlineMedium = applyThemeText(base.headlineMedium, text),
    headlineSmall = applyThemeText(base.headlineSmall, text),
    titleLarge = applyThemeText(base.titleLarge, text),
    titleMedium = applyThemeText(base.titleMedium, text),
    titleSmall = applyThemeText(base.titleSmall, text),
    bodyLarge = applyThemeText(base.bodyLarge, text),
    bodyMedium = applyThemeText(base.bodyMedium, text),
    bodySmall = applyThemeText(base.bodySmall, text),
    labelLarge = applyThemeText(base.labelLarge, text),
    labelMedium = applyThemeText(base.labelMedium, text),
    labelSmall = applyThemeText(base.labelSmall, text),
)

@Composable
internal fun themeSurface(id: String): ThemeScriptSurface? =
    (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.appearance?.surfaces?.get(id)?.takeUnless { it == ThemeScriptSurface() }

@Composable
fun themeScriptShape(id: String, original: Shape): Shape {
    val shape = themeSurface(id)?.shape ?: return original
    return appearanceShape(shape)
}

/** 玻璃效果读取角点信息，解析结果需要保留原有角点形状的静态类型。 */
@Composable
fun themeScriptShape(id: String, original: CornerBasedShape): CornerBasedShape {
    val shape = themeSurface(id)?.shape ?: return original
    return appearanceShape(shape)
}

internal fun appearanceShape(shape: ThemeAppearanceShape): CornerBasedShape = when (shape.type) {
    "rounded" -> RoundedCornerShape(shape.radius.dp)
    "cut" -> CutCornerShape(shape.radius.dp)
    "circle" -> CircleShape
    else -> error("Unsupported theme shape")
}

@Composable
fun themeScriptFill(id: String, original: Color, backgroundOpacity: Float? = null): Color {
    val color = themeSurface(id)?.fillColor
    if (color == null) return if (backgroundOpacity == null) original else original.copy(alpha = backgroundOpacity)
    val configured = themeColor(color)
    return configured.copy(alpha = configured.alpha * (backgroundOpacity ?: 1f))
}

@Composable
fun themeScriptContentColor(id: String, original: Color): Color {
    val color = themeSurface(id)?.contentColor
    return if (color == null) original else themeColor(color)
}

@Composable
fun themeScriptAvatarSize(id: String, original: Dp): Dp {
    val size = themeSurface(id)?.size
    return if (size == null) original else size.dp
}

@Composable
fun themeScriptMessageFraction(id: String): Float = themeSurface(id)?.messageWidth ?: 1f

@Composable
fun themeScriptTypography(id: String, original: Typography): Typography =
    applyThemeTypography(original, themeSurface(id)?.textStyle ?: ThemeAppearanceText())

@Composable
fun themeScriptMessageWidth(id: String, original: Dp): Dp = original * (themeSurface(id)?.messageWidth ?: 1f)

@Composable
internal fun themeScriptBorder(id: String, original: BorderStroke?): BorderStroke? {
    val style = themeSurface(id)
    return if (style?.strokeColor == null) original
        else if (style.strokeWidth == 0f) null
        else BorderStroke(style.strokeWidth.dp, themeColor(style.strokeColor))
}

@Composable
fun themeScriptTextStyle(id: String, original: TextStyle): TextStyle =
    applyThemeText(original, themeSurface(id)?.textStyle ?: ThemeAppearanceText())

/** 素材只替换图像，外层原生按钮继续持有点击、长按和无障碍语义。 */
@Composable
fun ThemeScriptIcon(
    id: String,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val state = LocalThemeScriptState.current
    val icon = (state as? ThemeScriptState.Ready)?.appearance?.icons?.get(id)
    if (icon == null) {
        Icon(imageVector, contentDescription, modifier, tint)
    } else {
        val images = (state as ThemeScriptState.Ready).images
        Canvas(modifier.requiredSize(icon.size.dp).then(
            if (contentDescription == null) Modifier else Modifier.semantics { this.contentDescription = contentDescription }
        )) {
            drawThemeLayer(ThemeScriptLayer("image", asset = icon.asset, fit = "fit", opacity = icon.opacity), images, 1f, tintColor = if (icon.useContentTint) tint else null)
        }
    }
}
