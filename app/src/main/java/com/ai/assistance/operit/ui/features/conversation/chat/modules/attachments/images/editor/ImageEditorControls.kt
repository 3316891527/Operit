package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R

/** Material handles gestures and accessibility; only the oversized default visuals are replaced. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbColor: Color = MaterialTheme.colorScheme.onSurface,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val alpha = if (enabled) 1f else 0.38f
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = label },
        thumb = {
            Box(Modifier.size(14.dp).background(thumbColor.copy(alpha = alpha), CircleShape))
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val start = Offset(if (rtl) size.width else 0f, size.height / 2f)
                val end = Offset(if (rtl) size.width * (1f - fraction) else size.width * fraction, size.height / 2f)
                drawLine(thumbColor.copy(alpha = 0.24f * alpha), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 3.dp.toPx(), StrokeCap.Round)
                if (fraction > 0f) drawLine(activeColor.copy(alpha = alpha), start, end, 3.dp.toPx(), StrokeCap.Round)
            }
        },
    )
}

/** The four choices float above their anchor; only the selector and palette occupy the row. */
@Composable
internal fun EditorBrushControls(
    selected: ImageEditTool,
    color: Int,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToolSelected: (ImageEditTool) -> Unit,
    onColor: (Int) -> Unit,
) {
    val description = stringResource(editorToolLabel(selected))
    val popupOffset = with(LocalDensity.current) { IntOffset(0, -48.dp.roundToPx()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp)) {
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                enabled = enabled,
                modifier = Modifier.fillMaxSize().background(Color(0xFF292929), RoundedCornerShape(8.dp))
                    .semantics { contentDescription = description },
            ) {
                EditorToolIcon(selected)
            }
            Canvas(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(5.dp)) {
                val indicator = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(indicator, Color.White.copy(alpha = 0.7f))
            }
            if (expanded && enabled) {
                Popup(
                    alignment = Alignment.BottomStart,
                    offset = popupOffset,
                    onDismissRequest = { onExpandedChange(false) },
                    properties = PopupProperties(focusable = true),
                ) {
                    Surface(color = Color(0xFF292929), shape = RoundedCornerShape(10.dp), shadowElevation = 6.dp) {
                        EditorToolRow(
                            tools = listOf(ImageEditTool.PEN, ImageEditTool.RECTANGLE, ImageEditTool.OVAL, ImageEditTool.ARROW),
                            selected = selected,
                            enabled = enabled,
                            modifier = Modifier.padding(4.dp),
                            onToolSelected = {
                                onToolSelected(it)
                                onExpandedChange(false)
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        EditorColors(color, enabled, modifier = Modifier.weight(1f), onColor = onColor)
    }
}

@Composable
internal fun EditorToolRow(
    tools: List<ImageEditTool>,
    selected: ImageEditTool,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToolSelected: (ImageEditTool) -> Unit,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tools.forEach { entry ->
            val description = stringResource(editorToolLabel(entry))
            val chosen = selected == entry
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (chosen) Color(0xFF343434) else Color.Transparent)
                    .clickable(enabled = enabled) { onToolSelected(entry) }
                    .semantics { contentDescription = description; this.selected = chosen },
                contentAlignment = Alignment.Center,
            ) {
                EditorToolIcon(entry)
            }
        }
    }
}

@Composable
internal fun EditorColors(selected: Int, enabled: Boolean, modifier: Modifier = Modifier, onColor: (Int) -> Unit) {
    val colors = listOf(0xFFFF3B30, 0xFFFFCC00, 0xFF34C759, 0xFF00BFFF, 0xFFAF52DE, 0xFFFFFFFF, 0xFF000000)
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { value ->
            val argb = value.toInt()
            Box(Modifier.size(30.dp).padding(2.dp).clip(CircleShape)
                .background(Color(argb))
                .border(if (selected == argb) 3.dp else 1.dp, if (selected == argb) Color(0xFF8BC7FF) else Color.Gray, CircleShape)
                .clickable(enabled = enabled) { onColor(argb) },
            )
        }
    }
}

private fun editorToolLabel(tool: ImageEditTool): Int = when (tool) {
    ImageEditTool.MOVE -> R.string.image_editor_move
    ImageEditTool.PEN -> R.string.image_editor_pen
    ImageEditTool.RECTANGLE -> R.string.image_editor_rectangle
    ImageEditTool.OVAL -> R.string.image_editor_oval
    ImageEditTool.ARROW -> R.string.image_editor_arrow
    ImageEditTool.MOSAIC -> R.string.image_editor_mosaic
    ImageEditTool.TEXT -> R.string.image_editor_text
    ImageEditTool.CROP -> R.string.image_editor_crop
    ImageEditTool.ERASER -> R.string.image_editor_eraser
}

internal fun ImageEditTool.isDrawingTool(): Boolean = this == ImageEditTool.PEN ||
    this == ImageEditTool.RECTANGLE || this == ImageEditTool.OVAL || this == ImageEditTool.ARROW

@Composable
private fun EditorToolIcon(tool: ImageEditTool) {
    val icon = when (tool) {
        ImageEditTool.PEN -> Icons.Default.Brush
        ImageEditTool.TEXT -> Icons.Default.TextFields
        ImageEditTool.CROP -> Icons.Default.Crop
        else -> null
    }
    if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
    else Canvas(Modifier.size(22.dp)) {
        val lineWidth = 2.dp.toPx()
        val start = Offset(size.width * 0.16f, size.height * 0.16f)
        val end = Offset(size.width * 0.84f, size.height * 0.84f)
        when (tool) {
            ImageEditTool.MOVE -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                val arm = size.width * 0.32f
                drawLine(Color.White, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), lineWidth)
                drawLine(Color.White, Offset(center.x - arm, center.y), Offset(center.x - arm + lineWidth * 2f, center.y - lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x - arm, center.y), Offset(center.x - arm + lineWidth * 2f, center.y + lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x + arm, center.y), Offset(center.x + arm - lineWidth * 2f, center.y - lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x + arm, center.y), Offset(center.x + arm - lineWidth * 2f, center.y + lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y - arm), Offset(center.x - lineWidth * 2f, center.y - arm + lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y - arm), Offset(center.x + lineWidth * 2f, center.y - arm + lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y + arm), Offset(center.x - lineWidth * 2f, center.y + arm - lineWidth * 2f), lineWidth)
                drawLine(Color.White, Offset(center.x, center.y + arm), Offset(center.x + lineWidth * 2f, center.y + arm - lineWidth * 2f), lineWidth)
            }
            ImageEditTool.RECTANGLE -> drawRect(Color.White, start, Size(end.x - start.x, end.y - start.y), style = Stroke(lineWidth))
            ImageEditTool.OVAL -> drawOval(Color.White, start, Size(end.x - start.x, end.y - start.y), style = Stroke(lineWidth))
            ImageEditTool.ARROW -> {
                drawLine(Color.White, Offset(start.x, end.y), Offset(end.x, start.y), lineWidth)
                drawLine(Color.White, Offset(end.x, start.y), Offset(end.x, size.height * 0.55f), lineWidth)
                drawLine(Color.White, Offset(end.x, start.y), Offset(size.width * 0.45f, start.y), lineWidth)
            }
            ImageEditTool.MOSAIC -> for (x in 0..2) for (y in 0..2) drawRect(if ((x + y) % 2 == 0) Color.White else Color.Gray, Offset(x * size.width / 3f, y * size.height / 3f), Size(size.width / 3f, size.height / 3f))
            ImageEditTool.ERASER -> {
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(start.x, size.height * 0.6f); lineTo(size.width * 0.6f, start.y); lineTo(end.x, size.height * 0.4f); lineTo(size.width * 0.4f, end.y); close() }
                drawPath(path, Color.White, style = Stroke(lineWidth))
            }
            else -> Unit
        }
    }
}
