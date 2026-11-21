package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.core.config.SystemToolPromptsInternal
import com.ai.assistance.operit.core.tools.FunctionModelConfigResultData
import com.ai.assistance.operit.core.tools.ModelConfigResultItem
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.climode.HiddenToolCatalogEntry
import com.ai.assistance.operit.core.tools.climode.HiddenToolSourceKind
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class PlatformCommandExecutorTest {
    private val schemas = (SystemToolPromptsInternal.internalToolCategoriesEn + PlatformCommandToolDefinitions.category(true))
        .flatMap { it.tools }.associate { it.name to it.parametersStructured }
    private fun executor(
        invoke: (String, Map<String, String>) -> ToolResult = { name, _ -> error("unexpected invocation: " + name) },
        entries: List<HiddenToolCatalogEntry> = emptyList(),
        visible: (String) -> Boolean = { true },
    ) = PlatformCommandExecutor(invoke, { entries }, { schemas[it] }, visible, "/actual/skills")
    private fun command(vararg args: String) = AITool("execute_cli_command", listOf(ToolParameter("args", JSONArray(args.toList()).toString())))

    @Test fun allDirectAliasesHaveDocumentedHostParameters() {
        PlatformCommandRegistry.commands.filterNot { it.target.startsWith("@") || it.target == "use_package" }.forEach { spec ->
            assertTrue("Missing host declaration for " + spec.path, schemas.containsKey(spec.target))
            val names = schemas.getValue(spec.target).orEmpty().map { it.name }.toSet()
            assertTrue("Missing positional for " + spec.path, names.containsAll(spec.arguments + spec.optionalArguments + spec.fixedParameters.keys))
        }
    }

    @Test fun unknownAndMissingParametersFailBeforeInvocation() {
        val executor = executor()
        assertFalse(executor.execute(command("model", "update", "cfg", "{\"typo_setting\":true}")).success)
        assertFalse(executor.execute(command("tool", "exec", "get_function_model_config")).success)
        assertFalse(executor.execute(AITool("execute_cli_command", listOf(ToolParameter("args", "[]"), ToolParameter("query", "old")))).success)
    }

    @Test fun businessResultAndFailureDiagnosticsArePreserved() {
        for (success in listOf(true, false)) {
            val expected = ToolResult("write_environment_variable", success, StringResultData("native diagnostics"), if (success) null else "native failure")
            var calls = 0
            val executor = executor(invoke = { name, params ->
                calls++
                assertEquals("write_environment_variable", name)
                assertEquals(mapOf("key" to "KEY", "value" to ""), params)
                expected
            })
            assertSame(expected, executor.execute(command("env", "set", "KEY", "")))
            assertEquals(1, calls)
        }
    }

    @Test fun contextUpdateUsesTheResolvedBindingAndOnlyRequestedFields() {
        val binding = FunctionModelConfigResultData("default", "CHAT", "bound-config", "Bound", 0, 0, "model", fixture())
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val executor = executor(invoke = { name, params ->
            calls += name to params
            if (name == "get_function_model_config") ToolResult(name, true, binding)
            else ToolResult(name, true, StringResultData("updated"))
        })
        assertTrue(executor.execute(command("context", "set", "CHAT", "{\"enable_summary\":false}")).success)
        assertEquals(listOf("get_function_model_config", "update_model_config"), calls.map { it.first })
        assertEquals(mapOf("enable_summary" to "false", "config_id" to "bound-config"), calls.last().second)
    }

    @Test fun failedContextReadPreventsAnyWrite() {
        var calls = 0
        val failure = ToolResult("get_function_model_config", false, StringResultData("detail"), "read denied")
        val executor = executor(invoke = { _, _ -> calls++; failure })
        assertSame(failure, executor.execute(command("context", "set", "CHAT", "{\"enable_summary\":false}")))
        assertEquals(1, calls)
    }

    @Test fun helpAndCatalogUseCurrentVisibilityAndLiveEntries() {
        val live = HiddenToolCatalogEntry("dynamic:run", "dynamic:run", "Live package", listOf("text [string, required]: input"), HiddenToolSourceKind.PACKAGE)
        val executor = executor(entries = listOf(live), visible = { it != "delete_model_config" })
        val help = executor.execute(command()).result.toString()
        assertTrue(help.contains("model update"))
        assertFalse(help.contains("model delete"))
        assertTrue(executor.execute(command("help", "model", "update")).result.toString().contains("enable_summary"))
        assertTrue(executor.execute(command("tool", "show", "dynamic:run")).result.toString().contains("text [string, required]"))
        assertFalse(executor.execute(command("tool", "show", "missing:run")).success)
        assertTrue(executor.execute(command("skill", "guide")).result.toString().contains("/actual/skills"))
    }

    @Test fun jsonModeSerializesSuccessfulTextAndKeepsFailuresUnchanged() {
        val expected = ToolResult("read_environment_variable", true, StringResultData("line1\nline2"))
        val json = executor(invoke = { _, _ -> expected }).execute(command("--json", "env", "get", "KEY"))
        assertEquals("\"line1\\nline2\"", json.result.toString())
        val failure = expected.copy(success = false, error = "denied")
        assertSame(failure, executor(invoke = { _, _ -> failure }).execute(command("--json", "env", "get", "KEY")))
    }

    private fun fixture() = ModelConfigResultItem(
        id = "bound-config",
        name = "Bound model",
        apiProviderType = "",
        apiProviderTypeId = "",
        apiEndpoint = "",
        modelName = "",
        modelList = emptyList(),
        apiKeySet = false,
        apiKeyPreview = "",
        maxTokensEnabled = false,
        maxTokens = 0,
        temperatureEnabled = false,
        temperature = 0f,
        topPEnabled = false,
        topP = 0f,
        topKEnabled = false,
        topK = 0,
        presencePenaltyEnabled = false,
        presencePenalty = 0f,
        frequencyPenaltyEnabled = false,
        frequencyPenalty = 0f,
        repetitionPenaltyEnabled = false,
        repetitionPenalty = 0f,
        hasCustomParameters = false,
        customParameters = "",
        hasCustomHeaders = false,
        customHeaders = "",
        contextLength = 0f,
        maxContextLength = 0f,
        enableMaxContextMode = false,
        summaryTokenThreshold = 0f,
        enableSummary = false,
        enableSummaryByMessageCount = false,
        summaryMessageCountThreshold = 0,
        mnnForwardType = 0,
        mnnThreadCount = 0,
        llamaThreadCount = 0,
        llamaContextSize = 0,
        llamaBatchSize = 0,
        llamaUBatchSize = 0,
        llamaGpuLayers = 0,
        llamaUseMmap = false,
        llamaFlashAttention = false,
        llamaKvUnified = false,
        llamaOffloadKqv = false,
        enableDirectImageProcessing = false,
        enableDirectAudioProcessing = false,
        enableDirectVideoProcessing = false,
        enableGoogleSearch = false,
        enableClaude1hPromptCache = false,
        enableToolCall = false,
        requestLimitPerMinute = 0,
        maxConcurrentRequests = 0,
        useMultipleApiKeys = false,
        apiKeyPoolCount = 0,
    )
}
