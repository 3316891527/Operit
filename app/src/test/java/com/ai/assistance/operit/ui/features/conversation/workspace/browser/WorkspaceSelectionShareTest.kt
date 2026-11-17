package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import org.junit.Assert.*
import org.junit.Test

class WorkspaceSelectionShareTest {
    @Test fun identicalMimeTypesRemainSpecific() {
        assertEquals("image/png", workspaceShareMimeType(listOf("image/png", "image/png")))
    }
    @Test fun mixedSubtypesUseOnlyTheirCommonFamily() {
        assertEquals("image/*", workspaceShareMimeType(listOf("image/png", "image/jpeg")))
        assertEquals("*/*", workspaceShareMimeType(listOf("image/png", "text/plain")))
    }
    @Test fun emptySelectionCannotBuildAShareType() {
        assertThrows(IllegalArgumentException::class.java) { workspaceShareMimeType(emptyList()) }
    }
}
