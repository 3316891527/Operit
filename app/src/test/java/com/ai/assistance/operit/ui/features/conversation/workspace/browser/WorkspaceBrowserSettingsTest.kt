package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import org.junit.Assert.*
import org.junit.Test

class WorkspaceBrowserSettingsTest {
    @Test fun pathsResolveRelativeSegmentsWithoutCrossingAnEnvironmentRoot() {
        val base = WorkspaceLocation("/root/project", "linux")
        assertEquals(WorkspaceLocation("/root/shared", "linux"), resolveWorkspacePath("../shared/./", base))
        assertEquals(WorkspaceLocation("/", "linux"), resolveWorkspacePath("../../../../", base))
        assertEquals(WorkspaceLocation("/root/file", "linux"), resolveWorkspacePath("~/file", base))
        assertNull(workspaceParent(WorkspaceLocation("/", "repo:docs")))
        assertEquals(WorkspaceLocation("/", "repo:docs"), workspaceParent(WorkspaceLocation("/a", "repo:docs")))
        for (input in listOf("", "a\u0000b", "a\nb", "a\rb")) {
            assertThrows(IllegalArgumentException::class.java) { resolveWorkspacePath(input, base) }
        }
    }

    @Test fun settingsRoundTripPreservesNamesEnvironmentsAndExplicitFalseWrap() {
        val original = WorkspaceBrowserSettings(
            home = WorkspaceLocation("/", "linux"),
            bookmarks = listOf(WorkspaceBookmark("中文 \"收藏\"", WorkspaceLocation("/项目", "repo:docs"))),
            hidden = setOf(WorkspaceLocation("/private"), WorkspaceLocation("/private", "linux")),
            showDotFiles = true, showManuallyHidden = true, wordWrap = false,
        )
        assertEquals(original, decodeWorkspaceBrowserSettings(encodeWorkspaceBrowserSettings(original)))
        val defaults = WorkspaceBrowserSettings()
        assertEquals(defaults, decodeWorkspaceBrowserSettings(encodeWorkspaceBrowserSettings(defaults)))
    }

    @Test fun hiddenRulesRespectEnvironmentAndPathBoundaries() {
        val hidden = setOf(WorkspaceLocation("/a"))
        assertTrue(isWorkspacePathManuallyHidden(WorkspaceLocation("/a/child"), hidden))
        assertFalse(isWorkspacePathManuallyHidden(WorkspaceLocation("/ab"), hidden))
        assertFalse(isWorkspacePathManuallyHidden(WorkspaceLocation("/a", "linux"), hidden))
        val settings = WorkspaceBrowserSettings(hidden = hidden, showManuallyHidden = true)
        assertFalse(isWorkspaceEntryHidden(WorkspaceLocation("/a"), "a", settings))
        assertTrue(isWorkspaceEntryHidden(WorkspaceLocation("/.git"), ".git", settings))
    }
}
