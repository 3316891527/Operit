package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryDragSelectionTest {
    @Test
    fun `drag across rows keeps original selection and adds each image once`() {
        val drag = GalleryDragSelection(
            visibleUris = (0..8).map(Int::toString),
            existing = listOf("8", "2"),
            anchor = 1,
        )

        assertEquals(listOf("8", "2", "1", "3", "4", "5", "6", "7"), drag.through(7))
        assertEquals(listOf("8", "2", "1", "3"), drag.through(3))
    }

    @Test
    fun `dragging backward retracts only images added by the current gesture`() {
        val drag = GalleryDragSelection(
            visibleUris = (0..5).map(Int::toString),
            existing = listOf("1", "5"),
            anchor = 4,
        )

        assertEquals(listOf("1", "5", "4", "3", "2", "0"), drag.through(0))
        assertEquals(listOf("1", "5", "4", "3"), drag.through(3))
    }

    @Test
    fun `right to left row preserves the order each image was selected`() {
        val drag = GalleryDragSelection((0..7).map(Int::toString), emptyList(), anchor = 3)

        assertEquals(listOf("3"), drag.through(3))
        assertEquals(listOf("3", "2"), drag.through(2))
        assertEquals(listOf("3", "2", "1", "0"), drag.through(0))
        assertEquals(listOf("3", "2", "1", "0"), drag.through(0))
    }

    @Test
    fun `a new gesture starting on a selected image deselects its range`() {
        val images = (0..7).map(Int::toString)
        val selection = GalleryDragSelection(images, listOf("7"), anchor = 3).through(0)
        val deselection = GalleryDragSelection(images, selection, anchor = 3)

        assertEquals(listOf("7", "1", "0"), deselection.through(2))
        assertEquals(listOf("7"), deselection.through(0))
    }

    @Test
    fun `deselection retreat restores original items in their original order`() {
        val drag = GalleryDragSelection(
            (0..7).map(Int::toString),
            existing = listOf("7", "2", "4", "1", "3"),
            anchor = 4,
        )

        assertEquals(listOf("7"), drag.through(0))
        assertEquals(listOf("7", "2", "1"), drag.through(3))
        assertEquals(listOf("7", "2", "1", "3"), drag.through(4))
        assertEquals(listOf("7", "2", "1", "3"), drag.through(4))
    }

    @Test
    fun `deselection leaves unselected gaps unchanged and restores when crossing anchor`() {
        val drag = GalleryDragSelection(
            (0..8).map(Int::toString),
            existing = listOf("8", "1", "4", "6"),
            anchor = 4,
        )

        assertEquals(listOf("8", "6"), drag.through(0))
        assertEquals(listOf("8", "1"), drag.through(7))
        assertEquals(listOf("8", "1", "6"), drag.through(4))
    }

    @Test
    fun `selection retreat keeps original selected items and supports crossing anchor`() {
        val drag = GalleryDragSelection(
            (0..8).map(Int::toString),
            existing = listOf("8", "2"),
            anchor = 4,
        )

        assertEquals(listOf("8", "2", "4", "3", "1", "0"), drag.through(0))
        assertEquals(listOf("8", "2", "4", "3"), drag.through(3))
        assertEquals(listOf("8", "2", "4", "5", "6", "7"), drag.through(7))
        assertEquals(listOf("8", "2", "4"), drag.through(4))
    }

    @Test
    fun `a later selecting gesture appends after surviving selections`() {
        val images = (0..5).map(Int::toString)
        val deselected = GalleryDragSelection(images, listOf("5", "3", "2", "1"), anchor = 3).through(1)
        val reselected = GalleryDragSelection(images, deselected, anchor = 1)

        assertEquals(listOf("5", "1", "2", "3"), reselected.through(3))
    }
}
