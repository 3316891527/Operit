package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyPreviewSheetTest {
    @Test
    fun consumesDownwardLeftoverOnlyAtTop() {
        assertEquals(
            12f,
            copyPreviewConsumedOverscrollY(canScrollBackward = false, availableY = 12f),
            0.001f,
        )
        assertEquals(
            0f,
            copyPreviewConsumedOverscrollY(canScrollBackward = true, availableY = 12f),
            0.001f,
        )
        assertEquals(
            0f,
            copyPreviewConsumedOverscrollY(canScrollBackward = false, availableY = -18f),
            0.001f,
        )
        assertEquals(
            0f,
            copyPreviewConsumedOverscrollY(canScrollBackward = true, availableY = -18f),
            0.001f,
        )
    }

    @Test
    fun selectionAutoScrollSpeedsUpTowardEdges() {
        val viewport = 200f
        val edge = 40f
        val min = 32f
        val max = 110f

        assertEquals(
            0f,
            copyPreviewSelectionAutoScrollPxPerSec(
                pointerY = 100f,
                viewportHeightPx = viewport,
                edgePx = edge,
                minPxPerSec = min,
                maxPxPerSec = max,
                canScrollBackward = true,
                canScrollForward = true,
            ),
            0.001f,
        )

        val nearBottom =
            copyPreviewSelectionAutoScrollPxPerSec(
                pointerY = 165f,
                viewportHeightPx = viewport,
                edgePx = edge,
                minPxPerSec = min,
                maxPxPerSec = max,
                canScrollBackward = true,
                canScrollForward = true,
            )
        val atBottom =
            copyPreviewSelectionAutoScrollPxPerSec(
                pointerY = 200f,
                viewportHeightPx = viewport,
                edgePx = edge,
                minPxPerSec = min,
                maxPxPerSec = max,
                canScrollBackward = true,
                canScrollForward = true,
            )
        assertTrue(nearBottom > 0f)
        assertTrue(atBottom > nearBottom)
        assertEquals(
            0f,
            copyPreviewSelectionAutoScrollPxPerSec(
                pointerY = 200f,
                viewportHeightPx = viewport,
                edgePx = edge,
                minPxPerSec = min,
                maxPxPerSec = max,
                canScrollBackward = true,
                canScrollForward = false,
            ),
            0.001f,
        )

        val nearTop =
            copyPreviewSelectionAutoScrollPxPerSec(
                pointerY = 35f,
                viewportHeightPx = viewport,
                edgePx = edge,
                minPxPerSec = min,
                maxPxPerSec = max,
                canScrollBackward = true,
                canScrollForward = true,
            )
        val atTop =
            copyPreviewSelectionAutoScrollPxPerSec(
                pointerY = 0f,
                viewportHeightPx = viewport,
                edgePx = edge,
                minPxPerSec = min,
                maxPxPerSec = max,
                canScrollBackward = true,
                canScrollForward = true,
            )
        assertTrue(nearTop < 0f)
        assertTrue(atTop < nearTop)
    }
}
