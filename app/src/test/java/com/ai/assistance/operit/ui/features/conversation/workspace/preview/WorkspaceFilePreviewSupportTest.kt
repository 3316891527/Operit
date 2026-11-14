package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceFilePreviewSupportTest {
    @Test
    fun typescriptFilesUseTextEditorInsteadOfMediaPreview() {
        listOf("/workspace/src/api.ts", "/workspace/src/App.tsx").forEach { path ->
            assertEquals("text/plain", workspaceMimeTypeForPath(path))
            assertFalse(workspaceShouldOpenAsDirectPreview(path))
        }
    }
}
