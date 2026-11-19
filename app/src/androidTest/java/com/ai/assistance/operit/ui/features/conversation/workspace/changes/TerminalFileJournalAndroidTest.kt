package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalFileJournalAndroidTest {
    @Test fun mappedEditOfTrackedFileIsSavedOnCommandExit() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val directory = File(target.cacheDir, "mapped-edit-$id").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getApplicationContext() = this
        }
        val root = File(context.filesDir, "linux").apply { mkdirs() }
        val file = File(root, "message.txt").apply { writeText("first") }
        val store = WorkspaceChangeStore(context)
        val location = ChangeLocation("/message.txt", "linux", "linux:local")
        val before = ChangeSnapshot(SnapshotKind.TEXT, file.length(), store.putBlob(file.readBytes()))
        store.save(listOf(FileChangeRecord(id, id, id, 1, "create_file", location, ChangeSnapshot(SnapshotKind.ABSENT), before)))
        val journal = TerminalFileJournal(context, { listOf(TerminalObservationRoot(root, location.copy(path = "/"))) },
            store, {}, commandPreparationWaitMs = 5_000)
        try {
            java.io.RandomAccessFile(file, "rw").use { opened ->
                val mapped = opened.channel.map(java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, file.length())
                val execution = journal.begin(TerminalChangeOrigin(id, "demo:terminal", UUID.randomUUID().toString()))
                // 映射写入时描述符仍然打开，命令收尾不应依赖 CLOSE_WRITE 通知。
                mapped.put("final".toByteArray())
                mapped.force()
                assertTrue(file.setLastModified(file.lastModified() + 2_000L))
                journal.finish(execution).join()
                val history = store.records(id)
                assertEquals(2, history.size)
                val change = history.last()
                assertEquals("demo:terminal", change.toolName)
                assertEquals("terminal", change.sourceKind)
                assertEquals("first", store.content(change).before)
                assertEquals("final", store.content(change).after)
            }
        } finally {
            journal.close()
            directory.deleteRecursively()
        }
    }

    @Test fun coldExecutionCapturesNewSubtreeWhileBaselinePreparationIsBlocked() = runBlocking {
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
        val releaseScan = CountDownLatch(1)
        val scanSlotWasReleased = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val preparationDispatcher = executor.asCoroutineDispatcher()
        // 占住准备任务的执行线程，覆盖基线尚未生成时的命令提交和收尾。
        executor.execute {
            releaseScan.await(15, TimeUnit.SECONDS)
            scanSlotWasReleased.set(true)
        }
        val journal = TerminalFileJournal(context, {
            listOf(TerminalObservationRoot(root, ChangeLocation(root.path, "android", "android")))
        }, store, preparationDispatcher = preparationDispatcher, commandPreparationWaitMs = 5_000)
        try {
            val execution = withTimeout(5_000) {
                journal.begin(TerminalChangeOrigin(id, "plugin_direct:generate", UUID.randomUUID().toString()))
            }
            File(root, "new/deep").mkdirs()
            File(root, "new/deep/created.txt").writeText("first\nsecond\n")
            journal.finish(execution).join()
            assertFalse(scanSlotWasReleased.get())
            val records = store.records(chatId = id)
            assertEquals(1, records.size)
            assertEquals("plugin_direct:generate", records.single().toolName)
            assertEquals("terminal", records.single().sourceKind)
            assertEquals(SnapshotKind.ABSENT, records.single().before.kind)
            assertEquals("first\nsecond\n", store.content(records.single()).after)
        } finally {
            releaseScan.countDown()
            journal.close()
            preparationDispatcher.close()
            directory.deleteRecursively()
        }
    }

    @Test fun commandPathDoesNotWaitForLockedHistoryStore() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val directory = File(target.cacheDir, "terminal-observation-lock-$id").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getApplicationContext() = this
        }
        val root = File(context.filesDir, "observed").apply { mkdirs() }
        val journal = TerminalFileJournal(context, {
            listOf(TerminalObservationRoot(root, ChangeLocation(root.path, "android", "android")))
        }, WorkspaceChangeStore(context))
        try {
            // 文件记录锁在 begin 和 finish 返回之前一直被占用，观察任务必须独立等待它。
            val capture = withTimeout(3_000) {
                WorkspaceChangeStore.operationMutex.withLock {
                    val execution = journal.begin(TerminalChangeOrigin(id, "demo:terminal", id))
                    journal.finish(execution).also { assertFalse(it.isCompleted) }
                }
            }
            withTimeout(10_000) { capture.join() }
        } finally {
            journal.close()
            directory.deleteRecursively()
        }
    }
}
