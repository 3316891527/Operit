package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceReadOnlyToolAndroidTest {
    @Test fun directoryAndFileReadsFinishWhileObservationOwnsItsOperationLock() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handler = AIToolHandler.getInstance(context)
        handler.registerDefaultTools()
        val directory = File(context.cacheDir, "readonly-monitor-${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "sample.txt").apply { writeText("first\nsecond\n", Charsets.UTF_8) }
        val executor = Executors.newFixedThreadPool(2)
        // 模拟后台监控仍在扫描；可视化浏览使用的同一个工具入口必须先返回，不能等待释放锁。
        WorkspaceChangeStore.operationMutex.lock()
        try {
            val listing = executor.submit<Boolean> {
                handler.executeTool(AITool("list_files", listOf(
                    ToolParameter("path", directory.path), ToolParameter("environment", "android"),
                ))).success
            }
            val reading = executor.submit<Boolean> {
                handler.executeTool(AITool("read_file_part", listOf(
                    ToolParameter("path", file.path), ToolParameter("environment", "android"),
                    ToolParameter("start_line", "1"), ToolParameter("end_line", "2"),
                ))).success
            }
            assertTrue("目录读取被观察锁阻塞", listing.get(5, TimeUnit.SECONDS))
            assertTrue("文件读取被观察锁阻塞", reading.get(5, TimeUnit.SECONDS))
        } finally {
            WorkspaceChangeStore.operationMutex.unlock()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
            directory.deleteRecursively()
        }
    }
}
