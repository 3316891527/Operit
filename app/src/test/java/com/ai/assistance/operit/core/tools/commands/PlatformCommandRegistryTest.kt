package com.ai.assistance.operit.core.tools.commands

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlatformCommandRegistryTest {
    private fun invocation(vararg args: String) = PlatformCommandRegistry.parse(args.toList()) as PlatformCommandRequest.Invoke

    @Test fun `help and command lookup share the same catalog`() {
        assertEquals(PlatformCommandRequest.Help("", false), PlatformCommandRegistry.parse(emptyList()))
        assertEquals(PlatformCommandRequest.Help("model update", true), PlatformCommandRegistry.parse(listOf("--json", "help", "model", "update")))
        assertEquals(PlatformCommandRegistry.commands.size, PlatformCommandRegistry.commands.map { it.path }.distinct().size)
    }

    @Test fun `positionals preserve spaces empty strings newline and option shaped text`() {
        for (value in listOf("", "  a b  ", "line1\nline2", "--json", "--help", "$(touch /tmp/should-not-exist)")) {
            assertEquals(value, invocation("env", "set", "KEY", value).parameters["value"])
            assertEquals(value, invocation("speech", "test", value).parameters["text"])
        }
    }

    @Test fun `json string arguments preserve the same array values`() {
        val args = listOf("package", "exec", "sample:run", "{\"text\":\" a b \",\"empty\":\"\",\"line\":\"a\\nb\"}")
        assertEquals(args, PlatformCommandRegistry.parseArgsJson(JSONArray(args).toString()))
        val request = PlatformCommandRegistry.parse(args) as PlatformCommandRequest.Invoke
        assertEquals(mapOf("text" to " a b ", "empty" to "", "line" to "a\nb"), request.parameters)
    }

    @Test fun `package payload may itself use tool_name without changing the target`() {
        val request = invocation("package", "exec", "sample:run", "{\"tool_name\":\"nested:target\"}")
        assertEquals("sample:run", request.toolName)
        assertEquals("nested:target", request.parameters["tool_name"])
    }

    @Test fun `nested objects arrays booleans and null remain json values`() {
        val request = invocation("model", "update", "config", "{\"custom_headers\":{\"X-Test\":\" a b \"},\"models\":[\"a\",\"b\"],\"enabled\":false,\"other\":null}")
        assertEquals(" a b ", JSONObject(request.parameters.getValue("custom_headers")).getString("X-Test"))
        assertEquals(2, JSONArray(request.parameters.getValue("models")).length())
        assertEquals("false", request.parameters["enabled"])
        assertEquals("null", request.parameters["other"])
    }

    @Test fun `malformed argument arrays and extra json documents fail`() {
        for (value in listOf("{}", "null", "[1]", "[true]", "[null]", "[[]]", "[] []", "[\"package\"")) {
            assertThrows(Exception::class.java) { PlatformCommandRegistry.parseArgsJson(value) }
        }
        assertThrows(Exception::class.java) { invocation("model", "update", "config", "{} trailing") }
    }

    @Test fun `missing surplus and unknown commands fail`() {
        for (args in listOf(listOf("missing"), listOf("model", "missing"), listOf("env", "set", "KEY"), listOf("env", "get", "KEY", "extra"), listOf("script", "run"))) {
            assertThrows(IllegalArgumentException::class.java) { PlatformCommandRegistry.parse(args) }
        }
    }

    @Test fun `payload cannot override a positional or forge system context`() {
        assertThrows(IllegalArgumentException::class.java) { invocation("model", "update", "real", "{\"config_id\":\"fake\"}") }
        for (key in listOf("__operit_package_caller_card_id", "__operit_package_chat_id", "__operit_future_context")) {
            assertThrows(IllegalArgumentException::class.java) { invocation("tool", "exec", "sample:run", JSONObject().put(key, "fake").toString()) }
        }
    }

    @Test fun `package targets require an exact qualified name`() {
        for (name in listOf("run", ":run", "sample:", "a:b:c", " sample:run")) {
            assertThrows(IllegalArgumentException::class.java) { invocation("package", "exec", name) }
        }
    }

    @Test fun `context updates contain only explicitly requested fields`() {
        val request = PlatformCommandRegistry.parse(listOf("context", "set", "CHAT", "{\"enable_summary\":false}")) as PlatformCommandRequest.ContextUpdate
        assertEquals("CHAT", request.functionType)
        assertEquals(mapOf("enable_summary" to "false"), request.parameters)
        assertThrows(IllegalArgumentException::class.java) { PlatformCommandRegistry.parse(listOf("context", "set", "CHAT", "{}")) }
        assertThrows(IllegalArgumentException::class.java) { PlatformCommandRegistry.parse(listOf("context", "set", "CHAT", "{\"api_key\":\"secret\"}")) }
    }
}
