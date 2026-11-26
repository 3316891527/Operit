package com.ai.assistance.operit.core.tools.javascript

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptExecutionReceiverCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var scriptFile: File
    private lateinit var paramsFile: File
    private lateinit var envFile: File
    private lateinit var resultFile: File

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "receiver-cleanup-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        scriptFile = File(directory, "script.js")
        paramsFile = File(directory, "params.json")
        envFile = File(directory, "env.txt")
        resultFile = File(directory, "result.json")
        createTemporaryFiles()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun invalidParamsFileStillCleansTemporaryFilesAndWritesOriginalError() {
        replaceParamsFileWithDirectory()

        execute("return 'unused';")

        assertTemporaryFilesDeleted()
        val payload = Json.parseToJsonElement(resultFile.readText()).jsonObject
        assertFalse(payload.getValue("success").jsonPrimitive.boolean)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("params_file_path"))
    }

    @Test
    fun paramsFailureAndUnwritableResultStillCleanTemporaryFiles() {
        replaceParamsFileWithDirectory()
        assertTrue(resultFile.mkdir())

        execute("return 'unused';")

        assertTemporaryFilesDeleted()
        assertTrue(resultFile.isDirectory)
    }

    @Test
    fun repeatedSuccessfulExecutionsReleaseTimerAndEngineThreads() {
        assertExecutionsReleaseThreads("setInterval(function() {}, 5); return 'receiver-ok';", true)
    }

    @Test
    fun repeatedFailedExecutionsKeepOriginalErrorAndReleaseThreads() {
        assertExecutionsReleaseThreads(
            "setInterval(function() {}, 5); throw new Error('receiver-failure');",
            false
        )
    }

    @Test
    fun resultWriteFailureStillDestroysEngineAndCleansTemporaryFiles() {
        val existingThreads = engineThreads().map { it.id }.toSet()
        assertTrue(resultFile.mkdir())

        execute("setInterval(function() {}, 5); return 'receiver-ok';")

        assertTemporaryFilesDeleted()
        assertTrue(resultFile.isDirectory)
        assertNewEngineThreadsStopped(existingThreads)
    }

    private fun assertExecutionsReleaseThreads(script: String, success: Boolean) {
        val existingThreads = engineThreads().map { it.id }.toSet()
        repeat(3) {
            createTemporaryFiles()

            execute(script)

            assertTemporaryFilesDeleted()
            val payload = Json.parseToJsonElement(resultFile.readText()).jsonObject
            assertEquals(success, payload.getValue("success").jsonPrimitive.boolean)
            if (success) {
                assertTrue(payload.getValue("result").toString().contains("receiver-ok"))
            } else {
                assertTrue(payload.getValue("error").jsonPrimitive.content.contains("receiver-failure"))
            }
        }
        assertNewEngineThreadsStopped(existingThreads)
    }

    private fun createTemporaryFiles() {
        scriptFile.writeText("// 接收器生命周期回归占位文件。")
        paramsFile.writeText("{}")
        envFile.writeText("RECEIVER_CLEANUP_TEST=1")
    }

    private fun replaceParamsFileWithDirectory() {
        assertTrue(paramsFile.delete())
        assertTrue(paramsFile.mkdir())
    }

    private fun execute(script: String) {
        // 同步调用私有入口，直接观察返回时的清理状态，避免广播调度带来的时序干扰。
        val method = ScriptExecutionReceiver::class.java.declaredMethods.single { it.name == "execute" }
        method.isAccessible = true
        method.invoke(
            ScriptExecutionReceiver(),
            context,
            ScriptExecutionReceiver.EXECUTION_MODE_CODE,
            scriptFile.absolutePath,
            script,
            "",
            "{}",
            paramsFile.absolutePath,
            true,
            true,
            envFile.absolutePath,
            true,
            resultFile.absolutePath
        )
    }

    private fun assertTemporaryFilesDeleted() {
        assertFalse("Temporary script survived execution", scriptFile.exists())
        assertFalse("Temporary params survived execution", paramsFile.exists())
        assertFalse("Temporary env survived execution", envFile.exists())
    }

    private fun engineThreads(): Set<Thread> =
        Thread.getAllStackTraces().keys.filterTo(mutableSetOf()) { thread ->
            thread.isAlive && thread.name in setOf(
                "OperitQuickJsEngine",
                "OperitQuickJsRuntime",
                "QuickJsNativeTimer"
            )
        }

    private fun assertNewEngineThreadsStopped(existingThreads: Set<Long>) {
        // 执行器关闭后线程可能还在退栈；只等待本轮新建线程，保留既有线程的独立生命周期。
        val deadline = System.nanoTime() + 3_000_000_000L
        var remaining = engineThreads().filter { it.id !in existingThreads }
        while (remaining.isNotEmpty() && System.nanoTime() < deadline) {
            remaining.forEach { it.join(20) }
            remaining = engineThreads().filter { it.id !in existingThreads }
        }
        assertTrue("Temporary engine threads survived: ${remaining.map { it.name to it.id }}", remaining.isEmpty())
    }
}
