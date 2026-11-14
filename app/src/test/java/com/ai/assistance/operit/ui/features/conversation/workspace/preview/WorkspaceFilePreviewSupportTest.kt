package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Assert.assertTrue
import com.ai.assistance.operit.util.files.FileUtils

class WorkspaceFilePreviewSupportTest {
    @Test
    fun typescriptTextUsesEditor() {
        val sample = "export const greeting = \"你好\";\n".toByteArray(Charsets.UTF_8)
        val mimeType = workspaceMimeTypeForPath("/workspace/src/api.ts",
            isTypeScriptText = FileUtils.isTextLike(sample))
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
        val mimeType = workspaceMimeTypeForPath("/workspace/clip.ts",
            isTypeScriptText = FileUtils.isTextLike(sample))
        assertEquals("video/mp2t", mimeType)
        assertTrue(workspaceShouldOpenAsDirectPreview("/workspace/clip.ts", mimeType))
    }

    @Test
    fun emptyUppercaseTsUsesEditor() {
        val mimeType = workspaceMimeTypeForPath("/workspace/empty.TS",
            isTypeScriptText = FileUtils.isTextLike(byteArrayOf()))
        assertEquals("text/plain", mimeType)
        assertFalse(workspaceShouldOpenAsDirectPreview("/workspace/empty.TS", mimeType))
    }

    @Test
    fun tsxUsesEditor() {
        assertEquals("text/plain", workspaceMimeTypeForPath("/workspace/src/App.tsx"))
        assertFalse(workspaceShouldOpenAsDirectPreview("/workspace/src/App.tsx"))
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
