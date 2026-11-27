package com.ai.assistance.operit.core.tools.commands.waifu

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.commands.PlatformCommandExecutor
import com.ai.assistance.operit.core.tools.commands.PlatformCommandRegistry
import com.ai.assistance.operit.core.tools.commands.PlatformCommandRequest
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WaifuCommandContractTest {
    @Test fun allFourPathsMapToTheDeclaredHostTools() {
        val aliases = mapOf("targets" to "list_waifu_targets", "fields" to "describe_waifu_fields", "show" to "get_waifu_settings")
        for ((path, target) in aliases) {
            val parsed = PlatformCommandRegistry.parse(listOf("waifu", path)) as PlatformCommandRequest.Invoke
            assertEquals(target, parsed.toolName)
            assertTrue(parsed.parameters.isEmpty())
        }
        val body = JSONObject().put("expected_revision", "a".repeat(64)).put("changes", JSONObject().put("enable_waifu_mode", true))
        val parsed = PlatformCommandRegistry.parse(listOf("waifu", "set", body.toString())) as PlatformCommandRequest.Invoke
        assertEquals("set_waifu_settings", parsed.toolName)
        assertEquals(WaifuCommandToolDefinitions.tools(true).map { it.name }.toSet(), aliases.values.toSet() + parsed.toolName)
    }
    @Test fun patchParsingPreservesTargetAndNestedPromptWithoutShellConversion() {
        val prompt = "  原样\n\"引号\"\\路径😀  "
        val body = JSONObject().put("target_type", "card").put("target_id", "exact id")
            .put("expected_revision", "b".repeat(64)).put("changes", JSONObject().put("waifu_custom_prompt", prompt))
        val parsed = PlatformCommandRegistry.parse(listOf("waifu", "set", body.toString())) as PlatformCommandRequest.Invoke
        assertEquals("exact id", parsed.parameters["target_id"])
        assertEquals(prompt, JSONObject(parsed.parameters.getValue("changes")).getString("waifu_custom_prompt"))
    }
    @Test fun parserRejectsMissingBodyContextForgeryAndWrongOuterTypes() {
        assertThrows(IllegalArgumentException::class.java) { PlatformCommandRegistry.parse(listOf("waifu", "set")) }
        for (body in listOf(
            "{\"__operit_package_chat_id\":\"forged\"}",
            "{\"target_id\":123}", "{\"changes\":\"{}\"}", "{\"changes\":[]}",
        )) {
            assertThrows(IllegalArgumentException::class.java) { PlatformCommandRegistry.parse(listOf("waifu", "set", body)) }
        }
    }
    @Test fun groupAndDetailedHelpResolveThroughTheLiveCommandRegistry() {
        assertEquals("waifu", (PlatformCommandRegistry.parse(listOf("waifu")) as PlatformCommandRequest.Help).prefix)
        assertEquals("waifu set", (PlatformCommandRegistry.parse(listOf("help", "waifu", "set")) as PlatformCommandRequest.Help).prefix)
        val schemas = WaifuCommandToolDefinitions.tools(true).associate { it.name to it.parametersStructured }
        val executor = PlatformCommandExecutor(
            invoke = { name, _ -> error("Help must not invoke $name") },
            catalog = { emptyList() }, schema = { schemas[it] },
            isVisible = { it in schemas }, skillDirectory = "/test/skills",
        )
        val result = executor.execute(AITool("execute_cli_command", listOf(
            ToolParameter("args", JSONArray(listOf("help", "waifu", "set")).toString()),
        )))
        assertTrue(result.error, result.success)
        val help = (result.result as StringResultData).value
        assertTrue(help.contains("waifu set"))
        for (field in listOf("target_type", "target_id", "expected_revision", "changes")) assertTrue(help.contains(field))
    }
}
