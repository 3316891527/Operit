package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEditDocumentTest {
    private val stroke = ImageEditMark.Stroke(
        ImageEditTool.PEN,
        listOf(ImageEditPoint(0.25f, 0.5f), ImageEditPoint(0.75f, 0.875f)),
        0xFFFF0000.toInt(),
        0.01f,
    )
    private val arrow = ImageEditMark.Shape(
        ImageEditTool.ARROW,
        ImageEditPoint(0.25f, 0.5f),
        ImageEditPoint(0.75f, 0.875f),
        0xFF00FF00.toInt(),
        0.02f,
    )
    private val label = ImageEditMark.Text("label", ImageEditPoint(0.25f, 0.75f), -1, 0.06f)
    private val cropped = ImageEditDocument(
        marks = listOf(stroke, arrow, label),
        crop = ImageEditCrop(0.125f, 0.25f, 0.75f, 0.875f),
    )

    @Test
    fun quarterTurnsSwapDimensionsOfNonSquareImages() {
        var document = ImageEditDocument()
        assertEquals(ImageEditSize(120, 80), document.imageSize(120, 80))
        for (turns in 1..4) {
            document = document.rotateClockwise()
            assertEquals(turns % 4, document.quarterTurns)
            assertEquals(
                if (turns % 2 == 0) ImageEditSize(120, 80) else ImageEditSize(80, 120),
                document.imageSize(120, 80),
            )
        }
        assertEquals(ImageEditDocument(), document)
        assertFalse(ImageEditHistory(document).changed)
    }

    @Test
    fun cropRotatesAroundFullImageRatherThanViewport() {
        val rotated = cropped.rotateClockwise()
        assertEquals(ImageEditCrop(0.125f, 0.125f, 0.75f, 0.75f), rotated.crop)
        assertTrue(rotated.geometryChanged)
    }

    @Test
    fun penMosaicAndEraserKeepTheirWidthAndRotateTheirPoints() {
        for (tool in listOf(ImageEditTool.PEN, ImageEditTool.MOSAIC, ImageEditTool.ERASER)) {
            val original = stroke.copy(tool = tool)
            val rotated = ImageEditDocument(marks = listOf(original)).rotateClockwise()
            assertEquals(
                original.copy(points = listOf(ImageEditPoint(0.5f, 0.25f), ImageEditPoint(0.125f, 0.75f))),
                rotated.marks.single(),
            )
        }
    }

    @Test
    fun shapeRotationPreservesArrowDirection() {
        val rotated = cropped.rotateClockwise()
        assertEquals(
            arrow.copy(start = ImageEditPoint(0.5f, 0.25f), end = ImageEditPoint(0.125f, 0.75f)),
            rotated.marks[1],
        )
    }

    @Test
    fun existingTextRotatesButNewTextStartsUpright() {
        val rotated = cropped.rotateClockwise()
        assertEquals(label.copy(position = ImageEditPoint(0.25f, 0.25f), quarterTurns = 1), rotated.marks[2])
        val inserted = ImageEditMark.Text("new", ImageEditPoint(0.5f, 0.5f), -1, 0.06f)
        val withNewText = rotated.copy(marks = rotated.marks + inserted)
        assertEquals(0, (withNewText.marks.last() as ImageEditMark.Text).quarterTurns)
        assertEquals(3, (withNewText.restoreGeometry().marks.last() as ImageEditMark.Text).quarterTurns)
    }

    @Test
    fun fourRotationsRestoreAllAnnotationKindsAndCrop() {
        val document = cropped.copy(marks = cropped.marks + stroke.copy(tool = ImageEditTool.MOSAIC) + stroke.copy(tool = ImageEditTool.ERASER))
        var rotated = document
        repeat(4) { rotated = rotated.rotateClockwise() }
        assertEquals(document, rotated)
    }

    @Test
    fun geometryResetRestoresCropAndOrientationWithoutRemovingMarks() {
        var rotated = cropped
        repeat(3) { rotated = rotated.rotateClockwise() }
        val restored = rotated.restoreGeometry()
        assertEquals(cropped.copy(crop = ImageEditCrop()), restored)
        assertFalse(restored.geometryChanged)
        assertEquals(cropped.marks.size, restored.marks.size)
        assertTrue(ImageEditHistory(restored).changed)
    }

    @Test
    fun undoAndRedoIncludeRotationAndReset() {
        val rotated = cropped.rotateClockwise()
        val restored = rotated.restoreGeometry()
        val history = ImageEditHistory().commit(cropped).commit(rotated).commit(restored)
        assertEquals(rotated, history.undo().current)
        assertEquals(cropped, history.undo().undo().current)
        assertEquals(restored, history.undo().redo().current)
        assertEquals(ImageEditDocument(), history.undo().undo().undo().current)
    }

    @Test
    fun resetWithNoGeometryChangeDoesNotCreateUndoEntry() {
        val document = ImageEditDocument(marks = listOf(stroke, label))
        val history = ImageEditHistory().commit(document)
        assertSame(history, history.commit(document.restoreGeometry()))
    }
}
