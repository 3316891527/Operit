package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class PlatformCommandDispatcherTest {
    private fun dispatcher(
        owner: String = "chat",
        allowed: Boolean = true,
        execute: (AITool) -> ToolResult,
    ) = PlatformCommandDispatcher(
        trustedContext = listOf(ToolParameter("__operit_package_chat_id", owner), ToolParameter("__operit_package_caller_card_id", "card-$owner")),
        isReserved = { it in setOf("search", "proxy", "package_proxy") },
        isAllowed = { allowed },
        executeWithPermission = execute,
        accessDeniedMessage = "role denied",
    )

    @Test fun `denied roles never reach permission or execution`() {
        val calls = AtomicInteger()
        val result = dispatcher(allowed = false) { calls.incrementAndGet(); error("must not execute") }.execute("read_environment_variable", emptyMap())
        assertFalse(result.success)
        assertEquals("role denied", result.error)
        assertEquals(0, calls.get())
    }

    @Test fun `system parameters and reserved proxy paths cannot bypass dispatch`() {
        val dispatch = dispatcher { error("must not execute") }
        assertFalse(dispatch.execute("sample:run", mapOf("__operit_package_chat_id" to "forged")).success)
        for (name in listOf("execute_cli_command", "search", "proxy", "package_proxy")) {
            assertFalse(dispatch.execute(name, emptyMap()).success)
        }
    }

    @Test fun `permission failures preserve their original result and execute once`() {
        val calls = AtomicInteger()
        val failure = ToolResult("write_environment_variable", false, StringResultData("diagnostics"), "permission denied")
        val result = dispatcher { calls.incrementAndGet(); failure }.execute("write_environment_variable", mapOf("value" to " a b\n"))
        assertSame(failure, result)
        assertEquals(1, calls.get())
    }

    @Test fun `nested dispatch restores the same caller identity without losing business parameters`() {
        val observations = mutableListOf<AITool>()
        val nested = dispatcher(owner = "original") { tool -> observations += tool; ToolResult(tool.name, true, StringResultData("done")) }
        val outer = dispatcher(owner = "original") { tool -> observations += tool; nested.execute("env-write", mapOf("value" to "")) }
        assertTrue(outer.execute("sample:run", mapOf("message" to "one\ntwo")).success)
        assertEquals(2, observations.size)
        observations.forEach { tool ->
            assertEquals("original", tool.parameters.single { it.name == "__operit_package_chat_id" }.value)
            assertEquals("card-original", tool.parameters.single { it.name == "__operit_package_caller_card_id" }.value)
        }
        assertEquals("", observations.last().parameters.single { it.name == "value" }.value)
    }

    @Test fun `parallel commands do not exchange their caller contexts`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 12).map { index -> pool.submit<Boolean> {
                dispatcher(owner = "chat-$index") { tool ->
                    assertEquals("chat-$index", tool.parameters.single { it.name == "__operit_package_chat_id" }.value)
                    ToolResult(tool.name, true, StringResultData("done"))
                }.execute("sample:run", mapOf("value" to index.toString())).success
            } }
            tasks.forEach { assertTrue(it.get(5, TimeUnit.SECONDS)) }
        } finally { pool.shutdownNow() }
    }
}
