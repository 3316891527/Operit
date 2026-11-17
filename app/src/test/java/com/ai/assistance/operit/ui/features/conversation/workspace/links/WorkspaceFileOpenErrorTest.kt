package com.ai.assistance.operit.ui.features.conversation.workspace.links

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import org.junit.Assert.*
import org.junit.Test

class WorkspaceFileOpenErrorTest {
    @Test fun `permission diagnostics in file not found exceptions are not labeled missing`() {
        assertEquals(WorkspaceFileOpenReason.PERMISSION, workspaceFileOpenReason(FileNotFoundException("file.txt: open failed: EACCES (Permission denied)")))
        assertEquals(WorkspaceFileOpenReason.PERMISSION, workspaceFileOpenReason(SecurityException("access")))
        assertEquals(WorkspaceFileOpenReason.PERMISSION, workspaceFileOpenReason(AccessDeniedException("/file")))
    }

    @Test fun `only explicit missing diagnostics are classified as missing`() {
        assertEquals(WorkspaceFileOpenReason.MISSING, workspaceFileOpenReason(NoSuchFileException("/file")))
        assertEquals(WorkspaceFileOpenReason.MISSING, workspaceFileOpenReason(IOException("File does not exist: /file")))
        assertEquals(WorkspaceFileOpenReason.READ_FAILED, workspaceFileOpenReason(FileNotFoundException("provider refused /file")))
        assertEquals(WorkspaceFileOpenReason.READ_FAILED, workspaceFileOpenReason(IOException("SSH channel closed")))
    }

    @Test fun `structured backend size and type failures retain their reason`() {
        for (reason in WorkspaceFileOpenReason.entries) assertEquals(reason, workspaceFileOpenReason(WorkspaceFileOpenException(reason)))
        assertEquals(WorkspaceFileOpenReason.NOT_TEXT, workspaceFileOpenReason(IOException("Skipped non-text file: /data")))
    }

    @Test fun `wrapped causes are inspected without changing the original diagnostic`() {
        val cause = WorkspaceFileOpenException(WorkspaceFileOpenReason.BACKEND, "connection changed")
        val error = IOException("reading failed", cause)
        assertEquals(WorkspaceFileOpenReason.BACKEND, workspaceFileOpenReason(error))
        assertEquals("reading failed", error.message)
        assertEquals("connection changed", error.cause!!.message)
    }
}