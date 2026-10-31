package com.ai.assistance.operit.ui.theme.script


import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.theme.ThemeScriptLayer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 放在原生材质的内容区域，保证装饰位于材质之上、正文之下。 */
@Composable
fun ThemeScriptSurface(
    surfaceId: String,
    backgroundOpacity: Float = 1f,
    selected: Boolean = false,
    enabled: Boolean = true,
    error: Boolean = false,
    drawFill: Boolean = true,
    content: @Composable () -> Unit,
) {
    val style = themeSurface(surfaceId)
    val typography = applyThemeTypography(MaterialTheme.typography, style?.textStyle ?: com.ai.assistance.operit.data.model.theme.ThemeAppearanceText())
    MaterialTheme(typography = typography) {
        CompositionLocalProvider(
            LocalContentColor provides themeScriptContentColor(surfaceId, LocalContentColor.current),
            LocalTextStyle provides themeScriptTextStyle(surfaceId, LocalTextStyle.current),
        ) {
            Box(Modifier.themeScriptDecoration(surfaceId, backgroundOpacity, selected, enabled, error, drawFill), propagateMinConstraints = true) { content() }
        }
    }
}

/** 装饰单独绘制；现有背景透明度只影响装饰层，不影响正文与原生交互。 */
@Composable
fun Modifier.themeScriptDecoration(
    surfaceId: String,
    backgroundOpacity: Float = 1f,
    selected: Boolean = false,
    enabled: Boolean = true,
    error: Boolean = false,
    drawFill: Boolean = true,
): Modifier {
    val state = LocalThemeScriptState.current
    if (state !is ThemeScriptState.Ready) return this
    val surface = state.appearance.surfaces[surfaceId] ?: return this
    var pressed by remember(surfaceId) { mutableStateOf(false) }
    var focused by remember(surfaceId) { mutableStateOf(false) }
    val pointer =
        if (surface.layers.any { it.state == "pressed" }) {
            Modifier.pointerInput(surfaceId) {
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            pressed = event.changes.any { it.pressed }
                        }
                    }
                } finally { pressed = false }
            }
        } else Modifier
    val shape = if (surface.shape == null) RectangleShape else appearanceShape(surface.shape)
    return this
        .heightIn(min = surface.minHeight.dp)
        .then(if (surface.elevation == null) Modifier else Modifier.shadow(surface.elevation.dp, shape, clip = false))
        .then(if (surface.shape == null) Modifier else Modifier.clip(shape))
        .then(pointer)
        .onFocusChanged { focused = it.hasFocus }
        .drawWithContent {
            fun drawPlacement(placement: String) {
                surface.layers.forEach { layer ->
                    val visible = when (layer.state) {
                        "always" -> true
                        "pressed" -> pressed && enabled
                        "focused" -> focused
                        "selected" -> selected
                        "disabled" -> !enabled
                        "error" -> error
                        else -> false
                    }
                    if (visible && layer.placement == placement) {
                        clipRect { drawThemeLayer(layer, state.images, backgroundOpacity.coerceIn(0f, 1f)) }
                    }
                }
            }
            if (drawFill) surface.fillColor?.let { drawRect(themeColor(it), alpha = backgroundOpacity.coerceIn(0f, 1f)) }
            drawPlacement("background")
            drawContent()
            drawPlacement("foreground")
            if (surface.strokeColor != null && surface.strokeWidth > 0f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = when (outline) {
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                    is Outline.Generic -> outline.path
                }
                drawPath(path, themeColor(surface.strokeColor), alpha = backgroundOpacity.coerceIn(0f, 1f), style = Stroke(surface.strokeWidth.dp.toPx()))
            }
        }
        .padding(surface.padding.start.dp, surface.padding.top.dp, surface.padding.end.dp, surface.padding.bottom.dp)
}

internal fun DrawScope.drawThemeLayer(
    layer: ThemeScriptLayer,
    images: Map<String, ThemeScriptImage>,
    backgroundOpacity: Float,
    tintColor: Color? = null,
) {
    val bounds = layer.bounds
    val topLeft = Offset(size.width * bounds.x, size.height * bounds.y)
    val layerSize = Size(size.width * bounds.width, size.height * bounds.height)
    if (layerSize.width <= 0f || layerSize.height <= 0f) return
    val opacity = layer.opacity * backgroundOpacity
    val width = layer.strokeWidth.dp.toPx()
    val fill = layer.color?.let { Color(android.graphics.Color.parseColor(it)) }
    val stroke = layer.strokeColor?.let { Color(android.graphics.Color.parseColor(it)) }
    when (layer.type) {
        "rect" -> {
            val corner = CornerRadius(layer.radius.dp.toPx())
            if (fill != null) drawRoundRect(fill, topLeft, layerSize, corner, alpha = opacity)
            if (stroke != null && width > 0f) drawRoundRect(stroke, topLeft, layerSize, corner, alpha = opacity, style = Stroke(width))
        }
        "ellipse" -> {
            if (fill != null) drawOval(fill, topLeft, layerSize, alpha = opacity)
            if (stroke != null && width > 0f) drawOval(stroke, topLeft, layerSize, alpha = opacity, style = Stroke(width))
        }
        "line" -> {
            val color = requireNotNull(stroke ?: fill)
            drawLine(color, topLeft, topLeft + Offset(layerSize.width, layerSize.height), strokeWidth = width.coerceAtLeast(1f), alpha = opacity)
        }
        "nineSlice" -> {
            val bitmap = (images.getValue(requireNotNull(layer.asset)) as ThemeScriptImage.Bitmap).image
            val slices = layer.slices
            val sx = listOf(0, slices.start.roundToInt(), bitmap.width - slices.end.roundToInt(), bitmap.width)
            val sy = listOf(0, slices.top.roundToInt(), bitmap.height - slices.bottom.roundToInt(), bitmap.height)
            val horizontal = slices.start + slices.end
            val vertical = slices.top + slices.bottom
            val scaleX = if (horizontal > 0f) min(1f, layerSize.width / horizontal.dp.toPx()) else 1f
            val scaleY = if (vertical > 0f) min(1f, layerSize.height / vertical.dp.toPx()) else 1f
            val dx = listOf(topLeft.x, topLeft.x + slices.start.dp.toPx() * scaleX, topLeft.x + layerSize.width - slices.end.dp.toPx() * scaleX, topLeft.x + layerSize.width)
            val dy = listOf(topLeft.y, topLeft.y + slices.top.dp.toPx() * scaleY, topLeft.y + layerSize.height - slices.bottom.dp.toPx() * scaleY, topLeft.y + layerSize.height)
            for (row in 0..2) for (column in 0..2) {
                val sw = sx[column + 1] - sx[column]
                val sh = sy[row + 1] - sy[row]
                val dw = (dx[column + 1] - dx[column]).roundToInt()
                val dh = (dy[row + 1] - dy[row]).roundToInt()
                if (sw > 0 && sh > 0 && dw > 0 && dh > 0) drawImage(
                    bitmap, srcOffset = IntOffset(sx[column], sy[row]), srcSize = IntSize(sw, sh),
                    dstOffset = IntOffset(dx[column].roundToInt(), dy[row].roundToInt()), dstSize = IntSize(dw, dh), alpha = opacity,
                )
            }
        }
        "image" -> {
            val image = images.getValue(requireNotNull(layer.asset))
            val sourceWidth = when (image) {
                is ThemeScriptImage.Bitmap -> image.image.width.toFloat()
                is ThemeScriptImage.Vector -> image.picture.width.toFloat()
            }
            val sourceHeight = when (image) {
                is ThemeScriptImage.Bitmap -> image.image.height.toFloat()
                is ThemeScriptImage.Vector -> image.picture.height.toFloat()
            }
            val scaleX = layerSize.width / sourceWidth
            val scaleY = layerSize.height / sourceHeight
            val scale = if (layer.fit == "crop") max(scaleX, scaleY) else min(scaleX, scaleY)
            val widthPx = if (layer.fit == "fill") layerSize.width else sourceWidth * scale
            val heightPx = if (layer.fit == "fill") layerSize.height else sourceHeight * scale
            val left = topLeft.x + (layerSize.width - widthPx) / 2f
            val top = topLeft.y + (layerSize.height - heightPx) / 2f
            clipRect(topLeft.x, topLeft.y, topLeft.x + layerSize.width, topLeft.y + layerSize.height) {
                when (image) {
                    is ThemeScriptImage.Bitmap -> drawImage(
                        image.image,
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(widthPx.roundToInt().coerceAtLeast(1), heightPx.roundToInt().coerceAtLeast(1)),
                        alpha = opacity,
                        colorFilter = tintColor?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) },
                    )
                    is ThemeScriptImage.Vector -> {
                        val canvas = drawContext.canvas.nativeCanvas
                        val paint = android.graphics.Paint().apply {
                            alpha = (opacity * 255).roundToInt()
                            tintColor?.let { colorFilter = android.graphics.PorterDuffColorFilter(it.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN) }
                        }
                        val destination = android.graphics.RectF(left, top, left + widthPx, top + heightPx)
                        val save = canvas.saveLayer(destination, paint)
                        try { canvas.drawPicture(image.picture, destination) } finally { canvas.restoreToCount(save) }
                    }
                }
            }
        }
    }
}