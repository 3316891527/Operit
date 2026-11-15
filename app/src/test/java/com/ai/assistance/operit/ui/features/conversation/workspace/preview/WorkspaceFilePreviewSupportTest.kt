package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightConfiguration
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightConfigParser
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.WorkspaceFileRoute
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceFileRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Assert.assertTrue
import com.ai.assistance.operit.util.files.FileUtils

class WorkspaceFilePreviewSupportTest {
    private fun customConfiguration(): EditorHighlightConfiguration {
        val language = EditorHighlightConfigParser().parse("demolang", """{
            "version":1,"mode":"replace","extensions":[".operitdemo",".png",".zip"],
            "fileNames":["demo.special"],"rules":[{"words":["hello"],"style":"keyword"}]
        }""")
        return EditorHighlightConfiguration(languages = mapOf("demolang" to language)).also { it.validate() }
    }

    @Test fun registeredUnknownNamesAndExtensionsOpenAsText() {
        val config = customConfiguration()
        for (path in listOf("/workspace/sample.OPERITDEMO", "/workspace/demo.special")) {
            val mime = workspaceMimeTypeForContent(path, "application/octet-stream", true, config)
            assertEquals("text/plain", mime)
            assertEquals(WorkspaceFileRoute.TEXT, workspaceFileRoute(path, mime))
        }
        val binaryMime = workspaceMimeTypeForContent("sample.operitdemo", "application/octet-stream", false, config)
        assertEquals(WorkspaceFileRoute.SYSTEM, workspaceFileRoute("sample.operitdemo", binaryMime))
        assertEquals("application/octet-stream", workspaceMimeTypeForContent("sample.unknown", "application/octet-stream", true, config))
    }

    @Test fun customAssociationsPreserveMediaAndArchiveRoutes() {
        val config = customConfiguration()
        for ((path, original, expected) in listOf(
            Triple("image.png", "image/png", WorkspaceFileRoute.PREVIEW),
            Triple("bundle.zip", "application/zip", WorkspaceFileRoute.ARCHIVE),
        )) {
            val mime = workspaceMimeTypeForContent(path, original, true, config)
            assertEquals(original, mime)
            assertEquals(expected, workspaceFileRoute(path, mime))
        }
    }

    @Test
    fun typescriptTextUsesEditor() {
        val sample = "export const greeting = \"你好\";\n".toByteArray(Charsets.UTF_8)
        val path = "/workspace/src/api.ts"
        val mimeType = workspaceMimeTypeForContent(path, workspaceMimeTypeForPath(path), FileUtils.isTextLike(sample))
        assertEquals("text/plain", mimeType)
        assertFalse(workspaceShouldOpenAsDirectPreview("/workspace/src/api.ts", mimeType))
    }

    @Test
    fun mpegTransportStreamUsesVideoPreview() {
        val sample = ByteArray(188 * 3) { 0xff.toByte() }
        for (offset in sample.indices step 188) {
            sample[offset] = 0x47
            sample[offset + 1] = 0x1f
            sample[offset + 3] = 0x10
        }
        val path = "/workspace/clip.ts"
        val mimeType = workspaceMimeTypeForContent(path, workspaceMimeTypeForPath(path), FileUtils.isTextLike(sample))
        assertEquals("video/mp2t", mimeType)
        assertTrue(workspaceShouldOpenAsDirectPreview("/workspace/clip.ts", mimeType))
    }

    @Test
    fun emptyUppercaseTsUsesEditor() {
        val path = "/workspace/empty.TS"
        val mimeType = workspaceMimeTypeForContent(path, workspaceMimeTypeForPath(path), FileUtils.isTextLike(byteArrayOf()))
        assertEquals("text/plain", mimeType)
        assertFalse(workspaceShouldOpenAsDirectPreview("/workspace/empty.TS", mimeType))
    }

    @Test
    fun tsxUsesEditor() {
        assertEquals("text/plain", workspaceMimeTypeForPath("/workspace/src/App.tsx"))
        assertFalse(workspaceShouldOpenAsDirectPreview("/workspace/src/App.tsx"))
    }

    @Test
    fun textSourcesWithMediaMimeTypesUseTheEditor() {
        val sources = mapOf("module.mod" to ("audio/mod" to "<?xml version=\"1.0\"?><module/>"),
            "analysis.r" to ("audio/x-pn-realaudio" to "value <- 42"),
            "Main.as" to ("video/x-ms-asf" to "package demo { public class Main {} }"))
        for ((path, sample) in sources) {
            val mimeType = workspaceMimeTypeForContent(path, sample.first, FileUtils.isTextLike(sample.second.toByteArray()))
            assertEquals(path, "text/plain", mimeType)
            assertFalse(path, workspaceShouldOpenAsDirectPreview(path, mimeType))
        }
    }
    @Test
    fun binaryContentAndTextBasedImagesKeepTheirPreviewMimeTypes() {
        val binary = byteArrayOf(0, 1, 2, 3, 0, 0, 0, 0)
        assertFalse(FileUtils.isTextLike(binary))
        for ((path, mimeType) in mapOf("music.mod" to "audio/mod", "sound.r" to "audio/x-pn-realaudio")) {
            assertEquals(path, mimeType, workspaceMimeTypeForContent(path, mimeType, FileUtils.isTextLike(binary)))
        }
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()
        assertTrue(FileUtils.isTextLike(svg))
        assertEquals("image/svg+xml", workspaceMimeTypeForContent("drawing.svg", "image/svg+xml", FileUtils.isTextLike(svg)))
        assertEquals("application/pdf", workspaceMimeTypeForContent("document.pdf", "application/pdf", true))
    }
    @Test
    fun textDetectionReadsAtMost512Bytes() {
        val input = java.io.ByteArrayInputStream(ByteArray(2048) { 'a'.code.toByte() })
        assertTrue(FileUtils.isTextLike(input))
        assertEquals(1536, input.available())
    }

    @Test(expected = java.io.IOException::class)
    fun textDetectionPropagatesReadFailure() {
        val input = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("read failed")
        }
        FileUtils.isTextLike(input)
    }
}
