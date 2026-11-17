package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import org.junit.Assert.*
import org.junit.Test

class WorkspaceHiddenPathsTextTest {
    @Test fun serializationKeepsOnlyTheSelectedEnvironment() {
        val hidden = setOf(WorkspaceLocation("/same", "android"), WorkspaceLocation("/same", "linux"), WorkspaceLocation("/other", "linux"))
        assertEquals("/same\n", workspaceHiddenPathsText(hidden, "android"))
        assertEquals(setOf(WorkspaceLocation("/same", "android")), workspaceHiddenPathsFromText(workspaceHiddenPathsText(hidden, "android"), "android"))
    }

    @Test fun blankLinesAndDuplicatePathsDoNotCreateExtraRules() {
        val parsed = workspaceHiddenPathsFromText("\r\n/a\r\n/a\r\n \r\n/b\n", "android")
        assertEquals(setOf(WorkspaceLocation("/a"), WorkspaceLocation("/b")), parsed)
    }

    @Test fun fullPathsPreserveUnicodeAndSpacesIncludingTheLastFilenameCharacter() {
        val hidden = setOf(WorkspaceLocation("/storage/文档/文件 夹/末尾 ", "android"))
        assertEquals(hidden, workspaceHiddenPathsFromText(workspaceHiddenPathsText(hidden, "android"), "android"))
        assertEquals("", workspaceHiddenPathsText(emptySet(), "android"))
    }

    @Test fun parsedRulesStillRespectDirectoryAndEnvironmentBoundaries() {
        val rules = workspaceHiddenPathsFromText("/a/b\n", "linux")
        assertTrue(isWorkspacePathManuallyHidden(WorkspaceLocation("/a/b/child", "linux"), rules))
        assertFalse(isWorkspacePathManuallyHidden(WorkspaceLocation("/a/bc", "linux"), rules))
        assertFalse(isWorkspacePathManuallyHidden(WorkspaceLocation("/a/b", "android"), rules))
    }
}
