package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem
import org.junit.Assert.*
import org.junit.Test

class WorkspaceCompressionNamesTest {
    private fun target(name: String, directory: Boolean = false) = FileBrowserClipboardItem("/source/$name", "android", false, directory)

    @Test fun textFileBecomesAnArchiveWithOnlyTheSelectedExtension() {
        val base = workspaceCompressionBaseName(target("prompt.md"))
        assertEquals("prompt", base)
        assertEquals("prompt.zip", workspaceCompressionFileName(base, WorkspaceArchiveKind.ZIP))
    }

    @Test fun directoryNamesContainingDotsAreNotTreatedAsFileExtensions() {
        assertEquals("project.v2", workspaceCompressionBaseName(target("project.v2", directory = true)))
    }

    @Test fun dotFilesAndExtensionlessFilesKeepTheirNames() {
        assertEquals(".env", workspaceCompressionBaseName(target(".env")))
        assertEquals("LICENSE", workspaceCompressionBaseName(target("LICENSE")))
    }

    @Test fun compoundArchiveExtensionsAreRemovedAsAWholeIgnoringCase() {
        assertEquals("backup", workspaceCompressionBaseName(target("backup.TAR.GZ")))
        assertEquals("backup", workspaceCompressionBaseName(target("backup.tar.bz2")))
        assertEquals("report.final", workspaceCompressionBaseName(target("report.final.md")))
    }

    @Test fun changingTheFormatChangesOnlyTheOutputExtension() {
        val base = workspaceCompressionBaseName(target("report.md"))
        assertEquals("report.7z", workspaceCompressionFileName(base, WorkspaceArchiveKind.SEVEN_Z))
        assertEquals("report.tar.gz", workspaceCompressionFileName(base, WorkspaceArchiveKind.TAR_GZIP))
    }
}
