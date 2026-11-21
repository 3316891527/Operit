package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem
import org.junit.Assert.*
import org.junit.Test

class WorkspaceCompressionNamesTest {
    private fun target(name: String, directory: Boolean = false) = FileBrowserClipboardItem("/source/$name", "android", false, directory)

    @Test fun defaultNamesGetTheSelectedExtension() {
        for ((source, base) in listOf("prompt.md" to "prompt", "backup.TAR.GZ" to "backup", "report.final.md" to "report.final", ".env" to ".env", "LICENSE" to "LICENSE")) {
            val file = target(source)
            assertEquals(base, workspaceCompressionBaseName(file))
            assertEquals("$base.zip", workspaceCompressionFileName(base, WorkspaceArchiveKind.ZIP, file))
        }
    }

    @Test fun directoryNamesContainingDotsStillGetAnArchiveExtension() {
        val file = target("project.v2", directory = true)
        assertEquals("project.v2.zip", workspaceCompressionFileName(workspaceCompressionBaseName(file), WorkspaceArchiveKind.ZIP, file))
    }

    @Test fun explicitCustomExtensionsAreKeptWithoutDuplication() {
        val file = target("prompt.md")
        for (name in listOf("backup.zip", "backup.ZIP", "backup.tar.gz", "backup.custom"))
            assertEquals(name, workspaceCompressionFileName(name, WorkspaceArchiveKind.ZIP, file))
    }

    @Test fun enteringTheOriginalFileNameStillAddsTheArchiveExtension() {
        assertEquals("prompt.md.zip", workspaceCompressionFileName("prompt.md", WorkspaceArchiveKind.ZIP, target("prompt.md")))
        assertEquals("backup.zip.zip", workspaceCompressionFileName("backup.zip", WorkspaceArchiveKind.ZIP, target("backup.zip")))
    }

    @Test fun changingFormatUpdatesOnlyAutomaticallyAddedExtensions() {
        val file = target("report.md")
        assertEquals("report.7z", workspaceCompressionFileName("report", WorkspaceArchiveKind.SEVEN_Z, file))
        assertEquals("report.tar.gz", workspaceCompressionFileName("report", WorkspaceArchiveKind.TAR_GZIP, file))
        assertEquals("custom.ext", workspaceCompressionFileName("custom.ext", WorkspaceArchiveKind.SEVEN_Z, file))
    }
}
