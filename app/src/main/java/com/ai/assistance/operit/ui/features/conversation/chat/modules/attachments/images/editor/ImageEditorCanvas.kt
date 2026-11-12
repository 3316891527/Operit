package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.ImagePreviewTransform
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.transformImagePreview
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private data class EditorPlacement(val scale: Float, val origin: Offset) {
    fun point(position: Offset, image: Bitmap, crop: ImageEditCrop): ImageEditPoint = ImageEditPoint(
        ((position.x - origin.x) / scale / image.width).coerceIn(crop.left, crop.right),
        ((position.y - origin.y) / scale / image.height).coerceIn(crop.top, crop.bottom),
    )
}

private fun editorPlacement(viewport: Size, image: Bitmap, crop: ImageEditCrop, transform: ImagePreviewTransform): EditorPlacement {
    val fit = min(viewport.width / (image.width * crop.width), viewport.height / (image.height * crop.height))
    val scale = fit * transform.scale
    val center = Offset(viewport.width / 2f, viewport.height / 2f) + transform.offset
    return EditorPlacement(scale, center - Offset((crop.left + crop.right) * image.width / 2f, (crop.top + crop.bottom) * image.height / 2f) * scale)
}

private fun textAt(document: ImageEditDocument, point: ImageEditPoint, image: Bitmap): Int? {
    val shortSide = min(image.width, image.height).toFloat()
    return document.marks.indices.reversed().firstOrNull { index ->
        val text = document.marks[index] as? ImageEditMark.Text ?: return@firstOrNull false
        val paint = Paint().apply {
            textSize = text.size * shortSide
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val halfWidth = max(text.text.lines().maxOf { paint.measureText(it) } / 2f, shortSide * 0.025f) / image.width
        val halfHeight = max(text.text.lines().size * paint.fontSpacing / 2f, shortSide * 0.025f) / image.height
        abs(point.x - text.position.x) <= halfWidth && abs(point.y - text.position.y) <= halfHeight
    }
}

@Composable
internal fun ImageEditorCanvas(
    image: Bitmap,
    document: ImageEditDocument,
    tool: ImageEditTool,
    color: Int,
    width: Float,
    enabled: Boolean,
    onCommit: (ImageEditDocument) -> Unit,
    onText: (Int?, ImageEditPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = remember(image) { ImageEditRenderer(image) }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var transform by remember(image, viewport) { mutableStateOf(ImagePreviewTransform()) }
    var pending by remember { mutableStateOf<ImageEditMark?>(null) }
    var movedDocument by remember { mutableStateOf<ImageEditDocument?>(null) }
    var cropSelection by remember { mutableStateOf<ImageEditCrop?>(null) }
    val latestDocument by rememberUpdatedState(document)
    val commit by rememberUpdatedState(onCommit)
    val editText by rememberUpdatedState(onText)
    LaunchedEffect(document.crop) { transform = ImagePreviewTransform() }

    Canvas(
        modifier.clipToBounds()
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(image, viewport, tool, color, width, enabled, document.crop) {
                if (!enabled || viewport.width <= 0f || viewport.height <= 0f) return@pointerInput
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val original = latestDocument
                    val crop = original.crop
                    val placement = editorPlacement(viewport, image, crop, transform)
                    val start = placement.point(first.position, image, crop)
                    val imageRect = RectF(
                        placement.origin.x + crop.left * image.width * placement.scale,
                        placement.origin.y + crop.top * image.height * placement.scale,
                        placement.origin.x + crop.right * image.width * placement.scale,
                        placement.origin.y + crop.bottom * image.height * placement.scale,
                    )
                    val startsOnImage = imageRect.contains(first.position.x, first.position.y)
                    var transformed = false
                    var moved = false
                    var points = listOf(start)
                    val textIndex = if (startsOnImage && tool in listOf(ImageEditTool.TEXT, ImageEditTool.MOVE)) textAt(original, start, image) else null
                    val strokeTool = tool in listOf(ImageEditTool.PEN, ImageEditTool.MOSAIC, ImageEditTool.ERASER)
                    if (startsOnImage && strokeTool) pending = ImageEditMark.Stroke(tool, points, color, width)
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val fingerCount = event.changes.count { it.pressed }
                            if (fingerCount >= 2 || transformed || (tool == ImageEditTool.MOVE && textIndex == null)) {
                                transformed = true
                                pending = null
                                movedDocument = null
                                cropSelection = null
                                val focus = event.calculateCentroid(useCurrent = false)
                                if (focus.x.isFinite() && focus.y.isFinite()) {
                                    transform = transformImagePreview(
                                        transform, transform.scale * event.calculateZoom(), focus,
                                        event.calculatePan(), viewport,
                                        Size(image.width * crop.width, image.height * crop.height),
                                    )
                                }
                            } else if (startsOnImage) {
                                val position = event.changes.firstOrNull { it.id == first.id }?.position
                                if (position != null) {
                                    if ((position - first.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                    val point = placement.point(position, image, crop)
                                    when {
                                        textIndex != null && moved -> {
                                            val text = original.marks[textIndex] as ImageEditMark.Text
                                            val next = text.copy(position = ImageEditPoint(
                                                (text.position.x + point.x - start.x).coerceIn(crop.left, crop.right),
                                                (text.position.y + point.y - start.y).coerceIn(crop.top, crop.bottom),
                                            ))
                                            movedDocument = original.copy(marks = original.marks.mapIndexed { index, mark -> if (index == textIndex) next else mark })
                                        }
                                        strokeTool -> {
                                            if (points.last() != point) points = points + point
                                            pending = ImageEditMark.Stroke(tool, points, color, width)
                                        }
                                        tool in listOf(ImageEditTool.RECTANGLE, ImageEditTool.OVAL, ImageEditTool.ARROW) ->
                                            pending = ImageEditMark.Shape(tool, start, point, color, width)
                                        tool == ImageEditTool.CROP -> cropSelection = ImageEditCrop(
                                            min(start.x, point.x), min(start.y, point.y), max(start.x, point.x), max(start.y, point.y),
                                        )
                                    }
                                }
                            }
                            if (startsOnImage || transformed) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        if (!transformed && startsOnImage) {
                            val movement = movedDocument
                            val mark = pending
                            val selectedCrop = cropSelection
                            when {
                                movement != null -> commit(movement)
                                tool == ImageEditTool.TEXT && !moved -> editText(textIndex, start)
                                selectedCrop != null && selectedCrop.width * image.width >= 4f && selectedCrop.height * image.height >= 4f ->
                                    commit(original.copy(crop = selectedCrop))
                                mark is ImageEditMark.Stroke -> commit(original.copy(marks = original.marks + mark))
                                mark is ImageEditMark.Shape && moved -> commit(original.copy(marks = original.marks + mark))
                            }
                        }
                    } finally {
                        pending = null
                        movedDocument = null
                        cropSelection = null
                    }
                }
            },
    ) {
        if (viewport.width <= 0f || viewport.height <= 0f) return@Canvas
        val crop = document.crop
        val placement = editorPlacement(viewport, image, crop, transform)
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val checkpoint = canvas.save()
            try {
                canvas.translate(placement.origin.x, placement.origin.y)
                canvas.scale(placement.scale, placement.scale)
                canvas.clipRect(crop.left * image.width, crop.top * image.height, crop.right * image.width, crop.bottom * image.height)
                renderer.draw(canvas, movedDocument ?: document, pending)
                cropSelection?.let { selection ->
                    val shade = Paint().apply { this.color = 0x99000000.toInt() }
                    val l = selection.left * image.width
                    val t = selection.top * image.height
                    val r = selection.right * image.width
                    val b = selection.bottom * image.height
                    canvas.drawRect(crop.left * image.width, crop.top * image.height, crop.right * image.width, t, shade)
                    canvas.drawRect(crop.left * image.width, b, crop.right * image.width, crop.bottom * image.height, shade)
                    canvas.drawRect(crop.left * image.width, t, l, b, shade)
                    canvas.drawRect(r, t, crop.right * image.width, b, shade)
                    val border = Paint().apply { this.color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f / placement.scale }
                    canvas.drawRect(l, t, r, b, border)
                    for (i in 1..2) {
                        canvas.drawLine(l + (r - l) * i / 3f, t, l + (r - l) * i / 3f, b, border)
                        canvas.drawLine(l, t + (b - t) * i / 3f, r, t + (b - t) * i / 3f, border)
                    }
                }
            } finally { canvas.restoreToCount(checkpoint) }
        }
    }
}
