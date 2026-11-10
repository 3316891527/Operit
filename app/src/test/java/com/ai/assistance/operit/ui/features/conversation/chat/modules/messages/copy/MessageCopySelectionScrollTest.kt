package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.copy

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCopySelectionScrollTest {
    @Test
    fun edgeScrollFactorTracksDirectionAndProximity() {
        assertEquals(-1f, messageCopyEdgeScrollFactor(pointerY = 0f, viewportHeight = 200f, edgeSize = 40f), 0f)
        assertEquals(-0.5f, messageCopyEdgeScrollFactor(pointerY = 20f, viewportHeight = 200f, edgeSize = 40f), 0f)
        assertEquals(0f, messageCopyEdgeScrollFactor(pointerY = 100f, viewportHeight = 200f, edgeSize = 40f), 0f)
        assertEquals(0.5f, messageCopyEdgeScrollFactor(pointerY = 180f, viewportHeight = 200f, edgeSize = 40f), 0f)
        assertEquals(1f, messageCopyEdgeScrollFactor(pointerY = 220f, viewportHeight = 200f, edgeSize = 40f), 0f)
    }

    @Test
    fun edgeZoneDoesNotExceedHalfOfShortViewport() {
        assertEquals(-1f, messageCopyEdgeScrollFactor(pointerY = 0f, viewportHeight = 60f, edgeSize = 48f), 0f)
        assertEquals(1f, messageCopyEdgeScrollFactor(pointerY = 60f, viewportHeight = 60f, edgeSize = 48f), 0f)
        assertEquals(0f, messageCopyEdgeScrollFactor(pointerY = 30f, viewportHeight = 60f, edgeSize = 48f), 0f)
    }

    @Test
    fun invalidViewportDisablesEdgeScroll() {
        assertEquals(0f, messageCopyEdgeScrollFactor(pointerY = 0f, viewportHeight = 0f, edgeSize = 48f), 0f)
        assertEquals(0f, messageCopyEdgeScrollFactor(pointerY = 10f, viewportHeight = 100f, edgeSize = 0f), 0f)
    }
}
