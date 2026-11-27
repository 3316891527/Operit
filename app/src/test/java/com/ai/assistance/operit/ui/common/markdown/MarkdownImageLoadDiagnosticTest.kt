package com.ai.assistance.operit.ui.common.markdown

import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownImageLoadDiagnosticTest {
    @Test
    fun preservesMissingFileReasonAndEmptyAltMarkdown() {
        val resource = "file:///data/user/0/example/files/custom_emoji/test/missing.png"
        val markdown = "![]($resource)"
        val cause = FileNotFoundException("/data/user/0/example/files/custom_emoji/test/missing.png (No such file or directory)")
        val diagnostic = formatMarkdownImageLoadDiagnostic(resource, markdown, IOException("open failed", cause))

        assertEquals(resource, diagnostic.resource)
        assertEquals(markdown, diagnostic.markdown)
        assertEquals("java.io.IOException: open failed\njava.io.FileNotFoundException: ${cause.message}", diagnostic.reason)
        assertTrue(diagnostic.logMessage().contains(markdown))
        assertTrue(diagnostic.logMessage().contains("java.io.FileNotFoundException: ${cause.message}"))
    }

    @Test
    fun distinguishesResolvedFileFromOriginalMarkdownAndKeepsRealTypeWithoutMessage() {
        val diagnostic = formatMarkdownImageLoadDiagnostic(
            "file:///workspace/docs/images/sample.png",
            "![示例](images/sample.png \"图片标题\")",
            IOException(),
        )

        assertEquals("file:///workspace/docs/images/sample.png", diagnostic.resource)
        assertEquals("![示例](images/sample.png \"图片标题\")", diagnostic.markdown)
        assertEquals("java.io.IOException", diagnostic.reason)
    }

    @Test
    fun masksHttpQueryInResourceMarkdownCauseAndLog() {
        val resource = "HTTPS://example.test/images/pic.png?token=secret-token&signature=secret-signature"
        val markdown = "![示例]($resource \"图片标题\")"
        val diagnostic = formatMarkdownImageLoadDiagnostic(
            resource,
            markdown,
            IOException("HTTP 403 for '$resource'", IOException("Redirected to //cdn.example.test/image?key=secret-key")),
        )

        assertEquals("HTTPS://example.test/images/pic.png?…", diagnostic.resource)
        assertEquals("![示例](HTTPS://example.test/images/pic.png?… \"图片标题\")", diagnostic.markdown)
        assertEquals(
            "java.io.IOException: HTTP 403 for 'HTTPS://example.test/images/pic.png?…'\n" +
                "java.io.IOException: Redirected to //cdn.example.test/image?…",
            diagnostic.reason,
        )
        assertFalse(diagnostic.logMessage().contains("secret-"))
    }

    @Test
    fun keepsMarkdownDelimitersWhileMaskingEscapedHttpTarget() {
        val resource = "https://example.test/images/pic.png?token=secret-token"
        val markdown = "![](https:\\/\\/example.test/images/pic.png\\?token=secret-token)"
        val diagnostic = formatMarkdownImageLoadDiagnostic(resource, markdown, IOException("HTTP 403"))

        assertEquals("![](https:\\/\\/example.test/images/pic.png\\?…)", diagnostic.markdown)
        assertFalse(diagnostic.logMessage().contains("secret-token"))
    }

    @Test
    fun keepsNonHttpQueriesUntouched() {
        val resource = "content://example.test/images?document=42"
        val markdown = "![]($resource)"
        val diagnostic = formatMarkdownImageLoadDiagnostic(resource, markdown, IOException("Cannot open $resource"))

        assertEquals(resource, diagnostic.resource)
        assertEquals(markdown, diagnostic.markdown)
        assertEquals("java.io.IOException: Cannot open $resource", diagnostic.reason)
    }
}