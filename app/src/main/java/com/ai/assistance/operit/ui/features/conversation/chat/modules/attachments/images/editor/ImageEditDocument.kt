package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.editor

/** Coordinates and widths are relative to the oriented source image, never the viewport. */
internal data class ImageEditPoint(val x: Float, val y: Float)

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
    ) : ImageEditMark
}

internal data class ImageEditDocument(
    val marks: List<ImageEditMark> = emptyList(),
    val crop: ImageEditCrop = ImageEditCrop(),
)

/** Whole-document undo includes crop, text changes and eraser strokes. */
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
