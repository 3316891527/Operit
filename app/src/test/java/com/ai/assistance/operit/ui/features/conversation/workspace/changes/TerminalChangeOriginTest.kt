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

    @Test fun idleFilesystemHasNoInventedTerminalOrigin() {
        assertNull(terminalChangeOrigin(emptyList()))
    }
}
