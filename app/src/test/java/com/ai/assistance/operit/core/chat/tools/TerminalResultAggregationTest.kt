package com.ai.assistance.operit.core.chat.tools

import com.ai.assistance.operit.core.tools.HiddenTerminalCommandResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.TerminalCommandResultData
import com.ai.assistance.operit.core.tools.TerminalStreamEventData
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import org.junit.Assert.*
import org.junit.Test

class TerminalResultAggregationTest {
    private fun event(type: String, command: String = "gradle build", session: String = "s", success: Boolean = true) =
        ToolResult("terminal_exec", success, TerminalStreamEventData(type, command, session, chunk = "once\n"))

    private fun completed(success: Boolean = true): ToolResult = ToolResult(
        "terminal_exec", success,
        TerminalCommandResultData("gradle build", "once\n", if (success) 0 else 2, "s"),
        error = if (success) null else "build failed",
    )

    @Test fun terminalChunksAreNotRepeatedAlongsideAuthoritativeOutput() {
        val raw = completed()
        val aggregated = aggregateToolResultValues("terminal_exec", listOf(event("start"), event("chunk"), raw))
        assertSame(raw.result, aggregated.result)
        assertEquals(1, Regex("once").findAll(aggregated.result.toString()).count())
    }

    @Test fun failedTerminalStillKeepsItsExitCodeAndOriginalError() {
        val raw = completed(false)
        val aggregated = aggregateToolResultValues("terminal_exec", listOf(event("start"), event("chunk"), raw))
        assertFalse(aggregated.success)
        assertSame(raw.result, aggregated.result)
        assertEquals(raw.error, aggregated.error)
        assertEquals(2, (aggregated.result as TerminalCommandResultData).exitCode)
    }

    @Test fun unrelatedEventsAndIntermediateFailuresAreNotDiscarded() {
        val cases = listOf(
            event("chunk", session = "other"), event("chunk", command = "another"),
            event("other"), event("chunk", success = false),
            ToolResult("terminal_exec", true, StringResultData("independent data")),
        )
        cases.forEach { extra ->
            val aggregated = aggregateToolResultValues("terminal_exec", listOf(extra, completed()))
            assertTrue(aggregated.result is StringResultData)
            assertTrue(aggregated.result.toString().contains(extra.result.toString().trim()))
        }
    }

    @Test fun anIncompleteStreamDoesNotBecomeACompletedCommand() {
        val aggregated = aggregateToolResultValues("terminal_exec", listOf(event("start"), event("chunk")))
        assertTrue(aggregated.result is StringResultData)
        assertTrue(aggregated.result.toString().contains("once"))
    }

    @Test fun standaloneTypedResultsKeepTheirOriginalFields() {
        val data = HiddenTerminalCommandResultData("cargo test", "output", 0, "executor")
        val raw = ToolResult("internal", true, data)
        val aggregated = aggregateToolResultValues("terminal_hidden_exec", listOf(raw))
        assertEquals("terminal_hidden_exec", aggregated.toolName)
        assertSame(data, aggregated.result)
    }

    @Test fun otherToolsRetainFailureAndMultipartAggregation() {
        val failure = ToolResult("file", false, StringResultData("stderr"), "first")
        val success = ToolResult("file", true, StringResultData("done"))
        val aggregated = aggregateToolResultValues("file", listOf(failure, success))
        assertTrue(aggregated.success)
        assertEquals("Step error: first\n\nstderr\ndone", aggregated.result.toString())
        val empty = aggregateToolResultValues("file", emptyList())
        assertFalse(empty.success)
        assertEquals("The tool execution returned no results.", empty.error)
    }
}