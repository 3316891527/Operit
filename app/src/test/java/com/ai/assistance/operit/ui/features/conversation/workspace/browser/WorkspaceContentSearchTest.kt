package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkspaceContentSearchTest {
    @Test fun utf8AndBomUtf16ReportTheOriginalOneBasedLine() = runBlocking<Unit> {
        val text = "first\n中文 NeedLe\nlast"
        val pattern = Regex("needle", RegexOption.IGNORE_CASE)
        for (bytes in listOf(text.toByteArray(), byteArrayOf(-1, -2) + text.toByteArray(Charsets.UTF_16LE),
            byteArrayOf(-2, -1) + text.toByteArray(Charsets.UTF_16BE))) {
            val hit = findWorkspaceContent(ByteArrayInputStream(bytes), pattern)!!
            assertEquals(2, hit.line)
            assertTrue(hit.snippet.contains("中文"))
        }
    }
    @Test fun binaryContentIsExplicitlySkipped() {
        assertThrows(WorkspaceContentSkippedException::class.java) {
            runBlocking { findWorkspaceContent(ByteArrayInputStream(byteArrayOf(0, 1, 2)), Regex("x")) }
        }
    }
    @Test fun missingTextReturnsNoMatchAndLongSnippetsStayBounded() = runBlocking<Unit> {
        assertNull(findWorkspaceContent(ByteArrayInputStream("abc".toByteArray()), Regex("absent")))
        val text = "a".repeat(500) + "needle" + "b".repeat(500)
        val hit = findWorkspaceContent(ByteArrayInputStream(text.toByteArray()), Regex("needle"))!!
        assertTrue(hit.snippet.contains("needle"))
        assertTrue(hit.snippet.length <= 240)
    }
    @Test fun unknownSizeSingleLineCannotReadPastTheByteLimit() {
        var consumed = 0L
        val stream = object : InputStream() {
            override fun read(): Int { consumed++; return 'a'.code }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes.fill('a'.code.toByte(), offset, offset + length)
                consumed += length
                return length
            }
        }
        assertThrows(WorkspaceContentSkippedException::class.java) {
            runBlocking { findWorkspaceContent(stream, Regex("absent")) }
        }
        assertTrue(consumed <= WorkspaceSearchRepository.CONTENT_LIMIT_BYTES + 1)
    }
}
