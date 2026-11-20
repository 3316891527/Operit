package com.ai.assistance.operit.core.tools.javascript

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperitEditorRuntimeTest {
    @Test fun commandEntryAndProductionSdkBridge() {
        val repository = File(requireNotNull(System.getProperty("operit.repositoryRoot")))
        val suite = File(repository, "tools/operit-editor/tests/command-entry.test.cjs")
        val output = File.createTempFile("operit-editor-runtime-", ".log")
        try {
            val process = ProcessBuilder("node", "--test", suite.absolutePath)
                .directory(repository).redirectErrorStream(true).redirectOutput(output).start()
            try {
                assertTrue("平台编辑器运行时回归超时", process.waitFor(45, TimeUnit.SECONDS))
                assertEquals(output.readText(), 0, process.exitValue())
            } finally { if (process.isAlive) process.destroyForcibly() }
        } finally { output.delete() }
    }
}