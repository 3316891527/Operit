package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.builtin.ToolGetter
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.core.tools.runtime.ToolExecutor
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceBoundWritesAndroidTest {
    private val names = listOf("apply_file", "create_file", "edit_file", "write_file", "write_file_binary",
        "move_file", "delete_file", "copy_file", "make_directory", "zip_files", "unzip_files", "download_file")

    @Test fun everyWriteEntrySkipsSnapshotsWhenUnboundOrEntirelyOutsideWorkspace() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "recording-gate-" + UUID.randomUUID()).apply { mkdirs() }
        val bound = File(root, "bound").apply { mkdirs() }
        var invoked = 0
        var storeRequests = 0
        var scanRequests = 0
        var opened = 0
        var invalidated = 0
        val delegate = object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult {
                invoked++
                return ToolResult(toolName = tool.name, success = true, result = FileOperationData(
                    operation = tool.name, path = root.path, successful = true, details = "完成"))
            }
        }
        val recorder = WorkspaceChangeRecorder(context, delegate, storeProvider = { opened++; error("目录外操作不能打开历史库") },
            recordingIdentity = { Unit }, invalidate = { invalidated++; error("目录外操作不能触发扫描") })
        try {
            WorkspaceChangeStore.operationMutex.lock()
            try {
                for (workspace in listOf<String?>(null, bound.path)) for (name in names) {
                    val tool = AITool(name, listOf(ToolParameter("path", File(root, "outside").path),
                        ToolParameter("source", File(root, "source").path), ToolParameter("destination", File(root, "destination").path),
                        ToolParameter("environment", "android")))
                    val caller = ToolExecutionManager.ToolRuntimeContext(callerChatId = "chat", workspacePath = workspace, workspaceEnv = "android")
                    ToolExecutionManager.withToolRuntimeContext(caller) {
                        assertTrue(recorder.invoke(tool).success)
                        assertTrue(runBlocking { recorder.invokeAndStream(tool).toList().last().success })
                    }
                }
                assertEquals(names.size * 4, invoked)
                assertEquals(0, storeRequests)
                assertEquals(0, scanRequests)
                assertEquals(0, opened)
                assertEquals(0, invalidated)
            } finally { WorkspaceChangeStore.operationMutex.unlock() }
        } finally { root.deleteRecursively() }
    }

    @Test fun realCreateAndEditFinishWhileBoundDirectoryScanIsPaused() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "bound-write-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getApplicationContext() = this
        }
        val workspace = File(directory, "workspace").apply { mkdirs() }
        val file = File(workspace, "sample.txt")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = WorkspaceChangeStore(context)
        val monitor = WorkspaceFileMonitor(context, { store }, beforeScan = { entered.complete(Unit); release.await() })
        val fileTools = ToolGetter.getFileSystemTools(context)
        val delegate = object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult = runBlocking {
                assertFalse("真实写入不应持有全局观察锁", WorkspaceChangeStore.operationMutex.isLocked)
                when (tool.name) {
                    "create_file" -> fileTools.createFile(tool)
                    "edit_file" -> fileTools.editFile(tool)
                    else -> error("非预期工具")
                }
            }
        }
        val recorder = WorkspaceChangeRecorder(context, delegate, acknowledge = monitor::acknowledge,
            storeProvider = { store }, recordingIdentity = monitor::bindingIdentity, invalidate = monitor::invalidate)
        val executor = Executors.newSingleThreadExecutor()
        try {
            monitor.updateOwner("owner", "chat", workspace.path, "android")
            withTimeout(5_000) { entered.await() }
            val caller = ToolExecutionManager.ToolRuntimeContext(callerChatId = "chat", workspacePath = workspace.path, workspaceEnv = "android")
            val result = executor.submit<Boolean> {
                ToolExecutionManager.withToolRuntimeContext(caller) {
                    val create = AITool("create_file", listOf(ToolParameter("path", file.path), ToolParameter("environment", "android"), ToolParameter("new", "first")))
                    val edit = AITool("edit_file", listOf(ToolParameter("path", file.path), ToolParameter("environment", "android"), ToolParameter("old", "first"), ToolParameter("new", "final")))
                    recorder.invoke(create).success && recorder.invoke(edit).success
                }
            }
            assertTrue("单文件写入仍被扫描阻塞", result.get(5, TimeUnit.SECONDS))
            assertFalse(release.isCompleted)
            assertEquals("final", file.readText())
            val saved = store.records("chat")
            assertEquals(2, saved.size)
            assertEquals("first", store.content(saved.last()).before)
            assertEquals("final", store.content(saved.last()).after)
            release.complete(Unit)
            withTimeout(5_000) { monitor.reconcile(WorkspaceRecordingScope("chat", workspace.path, "android")) }
            assertEquals(2, store.records("chat").size)
        } finally {
            release.complete(Unit)
            executor.shutdownNow()
            monitor.close(); store.close(); directory.deleteRecursively()
        }
    }
}
