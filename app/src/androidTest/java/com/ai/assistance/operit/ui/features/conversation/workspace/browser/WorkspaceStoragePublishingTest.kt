package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.WorkspaceArchiveKind
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.WorkspaceArchiveStore
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceStoragePublishingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun localRenamePublishesTheFileWithoutCallingTheUnsupportedFileStoreApi() = runBlocking<Unit> {
        withDirectory { root ->
            val source = File(root, ".pending.tmp").also { it.writeText("完整内容") }
            val destination = File(root, "published.zip")
            FileBrowserOperations(context).rename(source.path, destination.path, "android")
            assertFalse(source.exists())
            assertEquals("完整内容", destination.readText())
        }
    }

    @Test fun renameRefusesAnExistingDestinationAndKeepsBothFiles() = runBlocking<Unit> {
        withDirectory { root ->
            val source = File(root, "source").also { it.writeText("source") }
            val destination = File(root, "target").also { it.writeText("target") }
            try {
                FileBrowserOperations(context).rename(source.path, destination.path, "android")
                fail("Existing destination must be rejected")
            } catch (_: IllegalStateException) {
                assertEquals("source", source.readText())
                assertEquals("target", destination.readText())
            }
        }
    }

    @Test fun compressionPublishesAVerifiableZipAndRemovesItsPendingFile() = runBlocking<Unit> {
        withDirectory { root ->
            val source = File(root, "prompt.md").also { it.writeText("压缩发布内容") }
            val destination = File(root, "output").also { assertTrue(it.mkdir()) }
            WorkspaceArchiveStore(context).create(listOf(FileBrowserClipboardItem(source.path, "android", false, false)),
                WorkspaceLocation(destination.path), "prompt.zip", WorkspaceArchiveKind.ZIP, "", 1)
            val output = File(destination, "prompt.zip")
            assertTrue(output.isFile)
            ZipFile(output).use { archive ->
                archive.getInputStream(archive.getFileHeader("prompt.md")).bufferedReader().use {
                    assertEquals("压缩发布内容", it.readText())
                }
            }
            assertEquals(listOf("prompt.zip"), checkNotNull(destination.list()).toList())
            assertEquals("压缩发布内容", source.readText())
        }
    }

    @Test fun compressionFailureIncludesTheTargetAndPreservesTheUnderlyingCause() = runBlocking<Unit> {
        withDirectory { root ->
            val source = File(root, "prompt.md").also { it.writeText("source") }
            val destination = File(root, "output").also { assertTrue(it.mkdir()) }
            val existing = File(destination, "prompt.zip").also { it.writeText("existing") }
            try {
                WorkspaceArchiveStore(context).create(listOf(FileBrowserClipboardItem(source.path, "android", false, false)),
                    WorkspaceLocation(destination.path), "prompt.zip", WorkspaceArchiveKind.ZIP, "", 1)
                fail("Existing output must be rejected")
            } catch (failure: IOException) {
                assertTrue(failure.message.orEmpty().contains("prompt.zip"))
                assertTrue(failure.message.orEmpty().contains(destination.path))
                assertTrue(failure.cause is IllegalStateException)
                assertEquals("existing", existing.readText())
            }
        }
    }

    private suspend fun withDirectory(block: suspend (File) -> Unit) {
        val root = File(context.cacheDir, "workspace-publish-test-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        try { block(root) } finally { root.deleteRecursively() }
    }
}
