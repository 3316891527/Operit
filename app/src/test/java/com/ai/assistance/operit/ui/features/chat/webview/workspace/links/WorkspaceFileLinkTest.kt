package com.ai.assistance.operit.ui.features.chat.webview.workspace.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceFileLinkTest {
    @Test
    fun devicePathsKeepChineseSpacesAndEncodedCharacters() {
        assertEquals(WorkspaceFileLink("/sdcard/Download/中文 文档.md", "android"),
            parseWorkspaceFileLink("/sdcard/Download/中文 文档.md"))
        assertEquals(WorkspaceFileLink("/storage/emulated/0/a+b c.txt", "android"),
            parseWorkspaceFileLink("file:///storage/emulated/0/a+b%20c.txt"))
    }

    @Test
    fun linuxPathsKeepTheirEnvironment() {
        assertEquals(WorkspaceFileLink("/tmp/report.md", "linux"), parseWorkspaceFileLink("/tmp/report.md"))
        assertEquals(WorkspaceFileLink("/home/demo/a.md", "linux"), parseWorkspaceFileLink("linux:///home/demo/a.md"))
        assertEquals(WorkspaceFileLink("/tmp/a.md", "linux"), parseWorkspaceFileLink("linux://tmp/a.md"))
        assertEquals(WorkspaceFileLink("/tmp/a.md", "linux"), parseWorkspaceFileLink("file://linux/tmp/a.md"))
    }

    @Test
    fun explicitEnvironmentOverridesThePathNamespace() {
        assertEquals(WorkspaceFileLink("/etc/example.txt", "android"),
            parseWorkspaceFileLink("android:///etc/example.txt"))
        assertEquals(WorkspaceFileLink("/sdcard/example.txt", "linux"),
            parseWorkspaceFileLink("file:///sdcard/example.txt?environment=linux"))
    }

    @Test
    fun repositoryPathsPreserveTheRepositoryName() {
        assertEquals(WorkspaceFileLink("/folder/a.md", "repo:文档仓库"),
            parseWorkspaceFileLink("repo://文档仓库/folder/a.md"))
        assertEquals(WorkspaceFileLink("/folder/a.md", "repo:文档仓库"),
            parseWorkspaceFileLink("repo:文档仓库:/folder/a.md"))
    }

    @Test
    fun lineLocationsAreSeparateFromTheFilePath() {
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12), parseWorkspaceFileLink("/tmp/a.kt#L12"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12), parseWorkspaceFileLink("/tmp/a.kt:12"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12), parseWorkspaceFileLink("linux:///tmp/a.kt?line=12"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12), parseWorkspaceFileLink("/tmp/a.kt#L12-L15"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux"), parseWorkspaceFileLink("/tmp/a.kt#L0"))
    }

    @Test
    fun directoryAndArchiveLinksUseTheSameFileEntry() {
        assertEquals(WorkspaceFileLink("/sdcard/Download/", "android"), parseWorkspaceFileLink("/sdcard/Download/"))
        assertEquals(WorkspaceFileLink("/tmp/test.zip", "linux"), parseWorkspaceFileLink("/tmp/test.zip"))
    }

    @Test
    fun webAndApplicationLinksRemainExternal() {
        for (url in listOf("https://example.com/tmp/a.md", "http://example.com/a", "mailto:a@example.com", "content://files/1", "tel:123")) {
            assertNull(parseWorkspaceFileLink(url))
        }
    }

    @Test
    fun malformedAndRelativeLinksDoNotBecomeFileRequests() {
        for (url in listOf("", "a.md", "repo:///a.md", "file://example.com/a.md", "linux:///tmp/a.md?environment=invalid", "/tmp/%invalid")) {
            assertNull(parseWorkspaceFileLink(url))
        }
    }
}