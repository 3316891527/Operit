package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.editor

/** Coordinates follow the current image orientation, never the viewport. */
internal data class ImageEditPoint(val x: Float, val y: Float)

internal data class ImageEditSize(val width: Int, val height: Int)

private fun ImageEditPoint.rotateClockwise() = ImageEditPoint(1f - y, x)

internal data class ImageEditCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(point: ImageEditPoint): Boolean =
        point.x in left..right && point.y in top..bottom
}

internal enum class ImageEditTool {
    MOVE, PEN, RECTANGLE, OVAL, ARROW, MOSAIC, TEXT, CROP, ERASER,
}

internal sealed interface ImageEditMark {
    data class Stroke(
        val tool: ImageEditTool,
        val points: List<ImageEditPoint>,
        val color: Int,
        val width: Float,
    ) : ImageEditMark

    data class Shape(
        val tool: ImageEditTool,
        val start: ImageEditPoint,
        val end: ImageEditPoint,
        val color: Int,
        val width: Float,
    ) : ImageEditMark

    data class Text(
        val text: String,
        val position: ImageEditPoint,
        val color: Int,
        val size: Float,
        val quarterTurns: Int = 0,
    ) : ImageEditMark
}

internal data class ImageEditDocument(
    val marks: List<ImageEditMark> = emptyList(),
    val crop: ImageEditCrop = ImageEditCrop(),
    val quarterTurns: Int = 0,
) {
    val geometryChanged: Boolean get() = quarterTurns != 0 || crop != ImageEditCrop()

    fun imageSize(sourceWidth: Int, sourceHeight: Int): ImageEditSize =
        if (quarterTurns % 2 == 0) ImageEditSize(sourceWidth, sourceHeight)
        else ImageEditSize(sourceHeight, sourceWidth)

    // Transform annotations with the image; newly inserted text still starts upright.
    fun rotateClockwise(): ImageEditDocument = copy(
        quarterTurns = (quarterTurns + 1) % 4,
        crop = ImageEditCrop(1f - crop.bottom, crop.left, 1f - crop.top, crop.right),
        marks = marks.map { mark ->
            when (mark) {
                is ImageEditMark.Stroke -> mark.copy(points = mark.points.map { it.rotateClockwise() })
                is ImageEditMark.Shape -> mark.copy(start = mark.start.rotateClockwise(), end = mark.end.rotateClockwise())
                is ImageEditMark.Text -> mark.copy(position = mark.position.rotateClockwise(), quarterTurns = (mark.quarterTurns + 1) % 4)
            }
        },
    )

    /** Reset crop and orientation without deleting pen, text or mosaic edits. */
    fun restoreGeometry(): ImageEditDocument {
        var restored = this
        repeat((4 - quarterTurns) % 4) { restored = restored.rotateClockwise() }
        return restored.copy(crop = ImageEditCrop())
    }
}

/** Whole-document undo includes crop, rotation, text changes and eraser strokes. */
internal data class ImageEditHistory(
    val current: ImageEditDocument = ImageEditDocument(),
    val previous: List<ImageEditDocument> = emptyList(),
    val following: List<ImageEditDocument> = emptyList(),
) {
    val changed: Boolean get() = current != ImageEditDocument()

    fun commit(document: ImageEditDocument): ImageEditHistory =
        if (document == current) this
        else ImageEditHistory(document, previous + current)

    fun undo(): ImageEditHistory =
        if (previous.isEmpty()) this
        else ImageEditHistory(previous.last(), previous.dropLast(1), listOf(current) + following)

    fun redo(): ImageEditHistory =
        if (following.isEmpty()) this
        else ImageEditHistory(following.first(), previous + current, following.drop(1))
}
