package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceChangeRemovalTest {
    @Test fun removingOneFileRetainsItsBatchSiblingAndSharedSnapshotsAfterReload() = withStore { store, context ->
        val first = record(store, "chat", "/one.txt", "shared")
        val sibling = record(store, "chat", "/two.txt", "shared").copy(operationId = first.operationId)
        store.save(listOf(first, sibling))
        val revision = store.revision.value
        assertEquals(1, runBlocking { store.removeRecords(setOf(first.id)) })
        assertEquals(revision + 1, store.revision.value)
        val reloaded = WorkspaceChangeStore(context)
        assertEquals(listOf(sibling.id), reloaded.records().map { it.id })
        assertEquals("shared", reloaded.content(sibling).after)
        assertTrue(blobFile(context, checkNotNull(sibling.after.blob)).exists())
        assertEquals(1, runBlocking { reloaded.removeRecords(setOf(sibling.id)) })
        assertTrue(WorkspaceChangeStore(context).records().isEmpty())
        assertFalse(blobFile(context, checkNotNull(sibling.after.blob)).exists())
    }

    @Test fun deletingOldHistoryKeepsNewestContentAndDropsOnlyUnreferencedObjects() = withStore { store, context ->
        val first = record(store, "chat", "/file.txt", "old")
        val second = record(store, "chat", "/file.txt", "new").copy(timestamp = 200)
        store.save(listOf(first)); store.save(listOf(second))
        assertEquals(1, runBlocking { store.removeRecords(setOf(first.id)) })
        assertFalse(blobFile(context, checkNotNull(first.after.blob)).exists())
        val file = store.reviewFiles(store.records()).single()
        assertEquals(second.id, file.records.single().id)
        assertEquals("new", store.content(file.records.single()).after)
        assertEquals(1, file.additions)
    }

    @Test fun clearFileScopeIncludesOtherTurnsButPreservesOtherChatsAndBackends() = withStore { store, _ ->
        val old = record(store, "chat", "/file.txt", "shared")
        val next = record(store, "chat", "/file.txt", "later").copy(timestamp = 200)
        val otherChat = record(store, "other-chat", "/file.txt", "shared")
        val otherBackend = record(store, "chat", "/file.txt", "remote").copy(location = old.location.copy(backend = "linux:ssh:other", environment = "linux"))
        listOf(old, next, otherChat, otherBackend).forEach { store.save(listOf(it)) }
        val targets = store.fileRecords(old.location, "chat")
        assertEquals(setOf(old.id, next.id), targets.map { it.id }.toSet())
        assertEquals(2, runBlocking { store.removeRecords(targets.map { it.id }.toSet()) })
        assertEquals(setOf(otherChat.id, otherBackend.id), store.records().map { it.id }.toSet())
        assertEquals("shared", store.content(otherChat).after)
        assertEquals(listOf(otherChat.id), store.fileRecords(old.location, null).map { it.id })
    }

    @Test fun removalWaitsForCaptureAndKeepsBlobPublishedByThatCapture() = withStore { store, context ->
        val existing = record(store, "chat", "/old.txt", "shared")
        store.save(listOf(existing))
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val capture = async(Dispatchers.IO) {
                WorkspaceChangeStore.operationMutex.withLock {
                    val pending = record(store, "chat", "/new.txt", "shared")
                    entered.complete(Unit)
                    release.await()
                    store.save(listOf(pending))
                    pending
                }
            }
            entered.await()
            val deletion = async(Dispatchers.IO) { store.removeRecords(setOf(existing.id)) }
            assertFalse(deletion.isCompleted)
            release.complete(Unit)
            val published = capture.await()
            assertEquals(1, deletion.await())
            assertEquals(listOf(published.id), store.records().map { it.id })
            assertTrue(blobFile(context, checkNotNull(published.after.blob)).exists())
            assertEquals("shared", store.content(published).after)
        }
    }

    @Test fun nonexistentIdsDoNotPublishRevisionOrModifyCurrentFile() = withStore { store, context ->
        val actual = File(context.filesDir, "actual.txt").apply { parentFile!!.mkdirs(); writeText("current content") }
        val history = record(store, "chat", actual.absolutePath, "historical")
        store.save(listOf(history))
        val revision = store.revision.value
        assertEquals(0, runBlocking { store.removeRecords(setOf("missing")) })
        assertEquals(revision, store.revision.value)
        runBlocking { store.removeRecords(setOf(history.id)) }
        assertEquals("current content", actual.readText())
    }

    private fun record(store: WorkspaceChangeStore, chat: String, path: String, content: String) = FileChangeRecord(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), chat, 100, "create_file",
        ChangeLocation(path, "android", "android"), ChangeSnapshot(SnapshotKind.ABSENT),
        ChangeSnapshot(SnapshotKind.TEXT, content.length.toLong(), store.putBlob(content.toByteArray())))

    private fun blobFile(context: android.content.Context, hash: String) = File(context.filesDir, "workspace_changes/v1/objects/$hash")

    private fun withStore(block: (WorkspaceChangeStore, android.content.Context) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(target.cacheDir, "record-removal-${UUID.randomUUID()}")
        val context = object : ContextWrapper(target) { override fun getFilesDir(): File = folder }
        try { block(WorkspaceChangeStore(context), context) } finally { folder.deleteRecursively() }
    }
}