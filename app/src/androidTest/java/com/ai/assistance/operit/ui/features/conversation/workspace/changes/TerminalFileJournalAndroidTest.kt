package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalFileJournalAndroidTest {
    @Test fun unboundTerminalDoesNotEvenRequestAMonitor() = runBlocking {
        val journal = TerminalFileJournal { error("未绑定时不应访问监控") }
        try {
            assertNull(journal.begin(TerminalChangeOrigin("chat", "terminal", "execution"), null, null))
            journal.interactiveInput("session", TerminalChangeOrigin("chat", "terminal", null), null, null)
        } finally { journal.close() }
    }

    @Test fun unboundOwnerDoesNotOpenHistoryStorage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val monitor = WorkspaceFileMonitor(context, { error("空绑定不能打开存储") })
        try {
            monitor.updateOwner("owner", "chat", null, null)
            assertFalse(monitor.hasBinding(WorkspaceRecordingScope("chat", "/tmp", "linux")))
            monitor.clearOwner("owner")
        } finally { monitor.close() }
    }

    @Test fun unbindingCancelsPausedScanAndLateCommandCannotRestartIt() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "bound-cancel-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getApplicationContext() = this
        }
        val workspace = File(directory, "workspace").apply { mkdirs() }
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val store = WorkspaceChangeStore(context)
        val monitor = WorkspaceFileMonitor(context, { store }, beforeScan = {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        })
        val journal = TerminalFileJournal { monitor }
        try {
            monitor.updateOwner("owner", "chat", workspace.path, "android")
            withTimeout(5_000) { entered.await() }
            val execution = checkNotNull(journal.begin(TerminalChangeOrigin("chat", "terminal", UUID.randomUUID().toString()), workspace.path, "android"))
            withTimeout(5_000) { monitor.clearOwner("owner"); cancelled.await() }
            assertFalse(monitor.hasBinding(WorkspaceRecordingScope("chat", workspace.path, "android")))
            withTimeout(5_000) { journal.finish(execution).join() }
            assertNull(journal.begin(TerminalChangeOrigin("chat", "terminal", UUID.randomUUID().toString()), workspace.path, "android"))
            assertTrue(store.records("chat").isEmpty())
        } finally {
            journal.close(); monitor.close(); store.close(); directory.deleteRecursively()
        }
    }
    @Test fun terminalExitReconcilesOnlyItsBoundWorkspace() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "bound-shell-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getApplicationContext() = this
        }
        val workspace = File(directory, "workspace").apply { mkdirs() }
        val inside = File(workspace, "inside.txt").apply { writeText("before") }
        val outside = File(directory, "outside.txt").apply { writeText("untouched") }
        val store = WorkspaceChangeStore(context)
        lateinit var journal: TerminalFileJournal
        val monitor = WorkspaceFileMonitor(context, { store }, originProvider = { chat, location -> journal.origin(chat, location) })
        journal = TerminalFileJournal { monitor }
        val recording = WorkspaceRecordingScope("chat", workspace.path, "android")
        try {
            monitor.updateOwner("owner", "chat", workspace.path, "android")
            withTimeout(5_000) { monitor.reconcile(recording) }
            val id = UUID.randomUUID().toString()
            val execution = checkNotNull(journal.begin(TerminalChangeOrigin("chat", "terminal", id), workspace.path, "android"))
            val process = ProcessBuilder("/system/bin/sh", "-c",
                """printf after > "$1"; printf changed > "$2" """,
                "bound-test", inside.path, outside.path).start()
            assertEquals(0, process.waitFor())
            withTimeout(5_000) { journal.finish(execution).join() }
            val record = store.records("chat").single()
            assertEquals(inside.path, record.location.path)
            assertEquals(id, record.executionId)
            assertEquals("terminal", record.toolName)
            assertEquals("before", store.content(record).before)
            assertEquals("after", store.content(record).after)
            assertEquals("changed", outside.readText())
            monitor.clearOwner("owner")
            inside.writeText("after-unbind")
            withTimeout(5_000) { journal.finish(execution).join() }
            assertEquals(1, store.records("chat").size)
        } finally { journal.close(); monitor.close(); store.close(); directory.deleteRecursively() }
    }
    @Test fun rebindingWaitsForCancelledObserverToReleaseTheSameIndex() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "bound-rebind-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getApplicationContext() = this
        }
        val workspace = File(directory, "workspace").apply { mkdirs() }
        val entered = CompletableDeferred<Unit>()
        val closing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reopened = CompletableDeferred<Unit>()
        val store = WorkspaceChangeStore(context)
        var scans = 0
        val monitor = WorkspaceFileMonitor(context, { store }, beforeScan = {
            if (++scans == 1) {
                entered.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { closing.complete(Unit); release.await() }
                }
            } else reopened.complete(Unit)
        })
        try {
            monitor.updateOwner("owner", "chat", workspace.path, "android")
            withTimeout(5_000) { entered.await() }
            val clear = launch { monitor.clearOwner("owner") }
            withTimeout(5_000) { closing.await() }
            val rebind = launch { monitor.updateOwner("owner", "chat", workspace.path, "android") }
            yield()
            assertFalse(reopened.isCompleted)
            release.complete(Unit)
            withTimeout(5_000) { clear.join(); rebind.join(); reopened.await() }
            withTimeout(5_000) { monitor.reconcile(WorkspaceRecordingScope("chat", workspace.path, "android")) }
            assertTrue(monitor.errors.value.isEmpty())
            assertTrue(monitor.hasBinding(WorkspaceRecordingScope("chat", workspace.path, "android")))
        } finally { release.complete(Unit); monitor.close(); store.close(); directory.deleteRecursively() }
    }
}
