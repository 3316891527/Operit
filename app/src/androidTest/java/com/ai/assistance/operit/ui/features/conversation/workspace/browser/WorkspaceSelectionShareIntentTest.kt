package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceSelectionShareIntentTest {
    @Test fun everyAttachmentHasATemporaryReadGrantInOneMultipleShareIntent() {
        val uris = arrayListOf(Uri.parse("content://files/one"), Uri.parse("content://files/two"))
        val intent = workspaceShareIntent(uris, listOf("image/png", "image/jpeg"))
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("image/*", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uris, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(2, intent.clipData!!.itemCount)
        assertEquals(uris[1], intent.clipData!!.getItemAt(1).uri)
    }
    @Test fun singleFileUsesTheStandardSingleShareContract() {
        val uri = Uri.parse("content://files/one")
        val intent = workspaceShareIntent(arrayListOf(uri), listOf("text/plain"))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(1, intent.clipData!!.itemCount)
    }
}
