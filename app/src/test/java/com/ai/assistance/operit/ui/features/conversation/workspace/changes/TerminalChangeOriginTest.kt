package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import org.junit.Assert.*
import org.junit.Test

class TerminalChangeOriginTest {
    @Test fun oneCallerRetainsFullNameAndExecution() {
        val origin = TerminalChangeOrigin("chat", "arbitrary_plugin:run", "execution")
        assertEquals(origin, terminalChangeOrigin(listOf(origin, origin)))
    }

    @Test fun overlappingExecutionsUseTerminalCategory() {
        val result = terminalChangeOrigin(listOf(
            TerminalChangeOrigin("chat", "one:run", "first"),
            TerminalChangeOrigin("chat", "two:run", "second"),
        ))!!
        assertEquals("terminal", result.toolName)
        assertEquals("chat", result.chatId)
        assertNull(result.executionId)
    }

    @Test fun concurrentChatsAreNotAssignedToOneCaller() {
        val result = terminalChangeOrigin(listOf(
            TerminalChangeOrigin("first", "one:run", "one"),
            TerminalChangeOrigin("second", "two:run", "two"),
        ))!!
        assertEquals("", result.chatId)
        assertEquals("terminal", result.toolName)
    }

    @Test fun oneSubagentExecutionSurvivesMergingItsCommands() {
        val result = terminalChangeOrigin(listOf(
            TerminalChangeOrigin("chat", "one:run", "first", "subagent-run"),
            TerminalChangeOrigin("chat", "two:run", "second", "subagent-run"),
        ))!!
        assertEquals("chat", result.chatId)
        assertEquals("subagent-run", result.subagentExecutionId)
        assertNull(result.executionId)
    }

    @Test fun mixedSubagentExecutionsHaveNoSingleOwner() {
        val first = TerminalChangeOrigin("chat", "run", "first", "subagent-one")
        val second = TerminalChangeOrigin("chat", "run", "second", "subagent-two")
        assertNull(first.merge(second).subagentExecutionId)
        assertNull(terminalChangeOrigin(listOf(first, second, first))!!.subagentExecutionId)
    }

    @Test fun unownedOrDifferentChatOriginClearsSubagentOwnership() {
        val owned = TerminalChangeOrigin("chat", "run", "command", "subagent-run")
        assertEquals("subagent-run", owned.merge(owned).subagentExecutionId)
        assertNull(owned.merge(TerminalChangeOrigin("chat", "run", null)).subagentExecutionId)
        assertNull(TerminalChangeOrigin("chat", "run", null).merge(owned).subagentExecutionId)
        assertNull(owned.merge(owned.copy(chatId = "other")).subagentExecutionId)
    }

    @Test fun idleFilesystemHasNoInventedTerminalOrigin() {
        assertNull(terminalChangeOrigin(emptyList()))
    }
}
