package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalBaselineMigrationAndroidTest {
    private val key = "a".repeat(64)

    @Test fun migratedBaselineKeepsSharedSnapshotWhenHistoryIsRemoved() = withStore { store, context ->
        val record = record(store, "shared")
        store.save(listOf(record))
        val legacy = legacy(context, checkNotNull(record.after.blob))
        store.importTerminalBaseline(key) {}
        assertFalse(legacy.exists())
        assertEquals(1L, store.terminalIndex(key).count())
        assertEquals(1, runBlocking { store.removeRecords(setOf(record.id)) })
        assertEquals("shared", store.content(record).after)
        store.releaseTerminalIndex(key)
        val next = store.terminalIndex(key)
        assertEquals(record.after, next.snapshot("tracked"))
    }

    @Test fun unimportedLegacyBaselineIsCheckedWithoutMaterializingItsMap() = withStore { store, context ->
        val record = record(store, "legacy-shared")
        store.save(listOf(record))
        legacy(context, checkNotNull(record.after.blob))
        assertEquals(1, runBlocking { store.removeRecords(setOf(record.id)) })
        assertTrue(blob(context, checkNotNull(record.after.blob)).exists())
        assertEquals("legacy-shared", store.content(record).after)
    }

    @Test fun inFlightScanProtectsSnapshotUntilLeaseIsClosed() = withStore { store, context ->
        val record = record(store, "in-flight")
        store.save(listOf(record))
        store.terminalIndex(key).beginScan().use { scan ->
            store.putBlob("in-flight".toByteArray(), scan)
            assertEquals(1, runBlocking { store.removeRecords(setOf(record.id)) })
            assertTrue(blob(context, checkNotNull(record.after.blob)).exists())
        }
        val second = record(store, "in-flight")
        store.save(listOf(second))
        assertEquals(1, runBlocking { store.removeRecords(setOf(second.id)) })
        assertFalse(blob(context, checkNotNull(second.after.blob)).exists())
    }

    @Test fun invalidLegacyFileRemainsAvailableForRepairAndDoesNotMarkImportComplete() = withStore { store, context ->
        val legacy = legacy(context, "a".repeat(64)).apply { writeText("{broken") }
        try { store.importTerminalBaseline(key) {}; fail("Migration should fail") }
        catch (expected: java.io.IOException) { assertTrue(legacy.exists()) }
        assertFalse(store.terminalIndex(key).legacyImported())
        assertEquals(0L, store.terminalIndex(key).count())
    }

    private fun legacy(context: Context, hash: String): File = File(context.filesDir, "workspace_changes/v1/terminal-observations/$key.json").apply {
        parentFile!!.mkdirs()
        writeText("""{"files":{"tracked":{"stamp":"s","snapshot":{"kind":"TEXT","size":6,"blob":"$hash","detail":null}}},"unreadable":[]}""", Charsets.UTF_8)
    }

    private fun record(store: WorkspaceChangeStore, text: String) = FileChangeRecord(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "chat", 100, "terminal",
        ChangeLocation("/tracked", "linux", "linux:local"), ChangeSnapshot(SnapshotKind.ABSENT),
        ChangeSnapshot(SnapshotKind.TEXT, text.length.toLong(), store.putBlob(text.toByteArray(Charsets.UTF_8))),
    )

    private fun blob(context: Context, hash: String) = File(context.filesDir, "workspace_changes/v1/objects/$hash")

    private fun withStore(block: (WorkspaceChangeStore, Context) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "terminal-index-${UUID.randomUUID()}")
        val context = object : ContextWrapper(target) { override fun getFilesDir(): File = directory }
        val store = WorkspaceChangeStore(context)
        try { block(store, context) } finally { store.releaseTerminalIndex(key); directory.deleteRecursively() }
    }
}