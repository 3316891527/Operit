package com.ai.assistance.operit.core.chat.tools

import com.ai.assistance.operit.core.tools.HiddenTerminalCommandResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.TerminalCommandResultData
import com.ai.assistance.operit.core.tools.TerminalTaskResultData
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.util.toolmarkup.ToolResultMarkup
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerminalOutputPresentationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun lines(count: Int = 100): String =
        (1..count).joinToString("") { "> Task :module$it:compileDebugKotlin UP-TO-DATE\n" }
    private fun presenter(directory: File = temporary.root) = TerminalOutputPresentation { directory }
    private fun raw(output: String = lines()): ToolResult = ToolResult(
        "terminal_exec", true, TerminalCommandResultData("gradle build", output, 0, "s"),
    )
    private fun archive(): File = temporary.root.listFiles()!!.single { it.extension == "log" }

    @Test fun nativeProjectionArchivesExactUtf8OutputAndPreservesTheRawResult() {
        val original = lines() + "BUILD SUCCESSFUL\n中文正文 🌏\r\n"
        val raw = raw(original)
        val data = presenter().present(raw) as TerminalCommandResultData
        assertEquals(original, archive().readText(Charsets.UTF_8))
        assertEquals(original, (raw.result as TerminalCommandResultData).output)
        assertEquals(raw.result.copy(output = data.output), data)
        assertTrue(data.output.contains(archive().absolutePath))
        assertTrue(data.output.contains("environment=android"))
        assertTrue(data.output.contains("temporary"))
        assertTrue(data.toString().length * 5 <= raw.result.toString().length * 4)
    }

    @Test fun failedBuildKeepsDiagnosticsAndEveryExecutionField() {
        val diagnostic = "e: Main.kt:8: 编译错误\njava.lang.IllegalStateException: broken\n\tat demo.Main.run(Main.kt:8)\n"
        val data = TerminalCommandResultData("gradle build", lines() + diagnostic, 2, "session", true, false)
        val raw = ToolResult("terminal_exec", false, data, "command failed")
        val displayed = presenter().present(raw) as TerminalCommandResultData
        assertEquals(data.copy(output = displayed.output), displayed)
        assertTrue(displayed.output.contains(diagnostic))
        assertEquals(data.output, archive().readText(Charsets.UTF_8))
        assertFalse(raw.success)
        assertEquals("command failed", raw.error)
    }

    @Test fun hiddenExecutionAndTaskTailsKeepTheirOwnMetadata() {
        val hidden = HiddenTerminalCommandResultData("gradle test", lines(), 0, "key", true)
        val projectedHidden = presenter().present(ToolResult("terminal_hidden_exec", true, hidden)) as HiddenTerminalCommandResultData
        assertEquals(hidden.copy(output = projectedHidden.output), projectedHidden)
        val task = TerminalTaskResultData("run", "session", "gradle test", "running", lines(), true,
            null, null, null, "screen remains", false, outputMode = "tail_snapshot")
        val projectedTask = presenter().present(ToolResult("terminal_poll", true, task)) as TerminalTaskResultData
        assertEquals(task.copy(output = projectedTask.output), projectedTask)
        assertTrue(projectedTask.output.contains("tail (already truncated by the tool)"))
        assertEquals("screen remains", projectedTask.screen)
    }

    @Test fun packageProjectionChangesOnlyItsDisplayedOutputString() {
        val original = JSONObject().put("command", "gradle build").put("output", lines())
            .put("exitCode", 2).put("sessionId", "s").put("timedOut", false)
            .put("context_preserved", true).put("extra", JSONObject().put("keep", "value"))
        val payload = original.toString()
        val raw = ToolResult("super_admin:terminal", false, StringResultData(payload), "exitCode=2")
        val projected = JSONObject(requireNotNull(presenter().present(raw)).toString())
        assertEquals(payload, raw.result.toString())
        assertEquals(original.getString("output"), archive().readText(Charsets.UTF_8))
        original.remove("output"); projected.remove("output")
        original.keys().forEach { key -> assertEquals(original.get(key).toString(), projected.get(key).toString()) }
        assertEquals(original.length(), projected.length())
        assertEquals("exitCode=2", raw.error)
    }

    @Test fun nestedPackageFailureKeepsItsEnvelope() {
        val body = JSONObject().put("command", "cargo test").put("output",
            (1..100).joinToString("") { "test module::case_$it ... ok\n" } + "test broken ... FAILED\nassertion failed\n")
        val root = JSONObject().put("success", false).put("message", "failed").put("data", body)
        val projected = JSONObject(requireNotNull(presenter().present(ToolResult("super_admin:terminal", false, StringResultData(root.toString()), "failed"))).toString())
        assertFalse(projected.getBoolean("success"))
        assertEquals("failed", projected.getString("message"))
        assertTrue(projected.getJSONObject("data").getString("output").contains("assertion failed"))
    }

    @Test fun existingFilePointersAreNeitherExpandedNorRead() {
        val payload = JSONObject().put("command", "gradle build").put("output", "(saved_to_file)")
            .put("output_saved_to", "/nonexistent/terminal_output.log").put("output_chars", 99999999).toString()
        val raw = ToolResult("super_admin:terminal", true, StringResultData(payload))
        assertNull(presenter().present(raw))
        assertEquals(payload, raw.result.toString())
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }

    @Test fun unknownToolsMalformedEnvelopesAndMachineDataRemainUntouched() {
        val payload = JSONObject().put("command", "gradle build").put("output", lines()).toString()
        val cases = listOf(
            ToolResult("custom:terminal", true, StringResultData(payload)),
            ToolResult("super_admin:terminal", true, StringResultData("not json")),
            ToolResult("super_admin:terminal", true, StringResultData(JSONObject().put("output", lines()).toString())),
            raw("{\n\"result\":\"json\"\n}\n" + lines()),
            raw("error: keep complete\n".repeat(200)),
        )
        cases.forEach { assertNull(presenter().present(it)) }
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }

    @Test fun fullEnvelopeMustSaveSpaceAndIncludeAReadableOriginalReference() {
        val hugeCommand = "gradle build " + "-Dpadding=" + "x".repeat(12000)
        val payload = JSONObject().put("command", hugeCommand).put("output", lines(50)).toString()
        assertNull(presenter().present(ToolResult("super_admin:terminal", true, StringResultData(payload))))
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }

    @Test fun sourcesAboveTheBoundAreNotCopiedOrArchived() {
        assertNull(presenter().present(raw(lines(8000))))
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }

    @Test fun aLargePreservedDiagnosticSectionCannotTruncateTheArchiveReference() {
        val data = lines(2000) + "error: " + "detail".repeat(6000)
        assertNull(presenter().present(raw(data)))
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }

    @Test fun archiveErrorsAreExplicitAndCannotProduceAnUnrecoverableSummary() {
        val blocker = temporary.newFile("not-a-directory")
        assertThrows(IOException::class.java) { presenter(blocker).present(raw()) }
        assertEquals(0L, blocker.length())
        assertEquals(1, temporary.root.listFiles()!!.size)
    }

    @Test fun eachProjectionGetsASeparateOriginalFile() {
        val first = presenter().present(raw()) as TerminalCommandResultData
        val second = presenter().present(raw()) as TerminalCommandResultData
        val files = temporary.root.listFiles()!!.filter { it.extension == "log" }
        assertEquals(2, files.size)
        assertNotEquals(first.output, second.output)
        assertTrue(files.all { it.readText(Charsets.UTF_8) == lines() })
    }

    @Test fun modelAndSavedHistoryShareOneProjectionWhileBatchRetainsRawData() = runBlocking {
        val raw = raw()
        val projected = requireNotNull(presenter().present(raw))
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(2) { emitted += it }
        ordered.complete(1, ToolResult("read_file", true, StringResultData("other result")))
        assertTrue(emitted.isEmpty())
        ordered.complete(0, raw, presentation = projected)
        val batch = ordered.finish()
        assertSame(raw, batch.results[0])
        assertEquals(lines(), (batch.results[0].result as TerminalCommandResultData).output)
        assertEquals(emitted.joinToString("\n"), batch.message)
        assertTrue(ToolResultMarkup.contentFromBlock(emitted[0]).contains(archive().absolutePath))
        assertEquals("other result", ToolResultMarkup.contentFromBlock(emitted[1]))
    }

    @Test fun failureMarkupUsesTheSameProjectionAndKeepsTheOriginalStatus() = runBlocking {
        val source = raw().copy(success = false, error = "build failed")
        val projected = requireNotNull(presenter().present(source))
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(1) { emitted += it }
        ordered.complete(0, source, presentation = projected)
        val batch = ordered.finish()
        assertFalse(batch.results.single().success)
        assertSame(source, batch.results.single())
        assertTrue(emitted.single().contains("status=\"error\""))
        assertTrue(ToolResultMarkup.contentFromBlock(batch.message).startsWith("<error>build failed\n\n"))
        assertEquals(emitted.single(), batch.message)
    }
}