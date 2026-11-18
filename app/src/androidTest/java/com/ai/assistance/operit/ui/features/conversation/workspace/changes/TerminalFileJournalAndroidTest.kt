package com.ai.assistance.operit.ui.features.conversation.workspace.changes

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
class TerminalFileJournalAndroidTest {
    @Test fun fastCreationIsSavedBeforeExecutionFinishesWithoutWorkspace() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val directory = File(target.cacheDir, "terminal-observation-test-$id").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getApplicationContext() = this
        }
        val root = File(context.filesDir, "observed").apply { mkdirs() }
        val store = WorkspaceChangeStore(context)
        val journal = TerminalFileJournal(context, {
            listOf(TerminalObservationRoot(root, ChangeLocation(root.path, "android", "android")))
        }, store)
        try {
            val execution = journal.begin(TerminalChangeOrigin(id, "plugin_direct:generate", UUID.randomUUID().toString()))
            File(root, "new/deep").mkdirs()
            File(root, "new/deep/created.txt").writeText("first\nsecond\n")
            journal.finish(execution)
            val records = store.records(chatId = id)
            assertEquals(1, records.size)
            assertEquals("plugin_direct:generate", records.single().toolName)
            assertEquals("terminal", records.single().sourceKind)
            assertEquals(SnapshotKind.ABSENT, records.single().before.kind)
            assertEquals("first\nsecond\n", store.content(records.single()).after)
        } finally {
            journal.close()
            directory.deleteRecursively()
        }
    }
}
