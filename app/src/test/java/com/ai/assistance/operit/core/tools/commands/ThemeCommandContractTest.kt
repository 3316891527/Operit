package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCommandContractTest {
    @Test
    fun `theme reads and field discovery use ordinary command dispatch`() {
        val show = PlatformCommandRegistry.parse(listOf("theme", "show")) as PlatformCommandRequest.Invoke
        assertEquals("get_theme_settings", show.toolName)
        assertTrue(show.parameters.isEmpty())
        val fields = PlatformCommandRegistry.parse(listOf("theme", "fields", "fonts")) as PlatformCommandRequest.Invoke
        assertEquals("describe_theme_fields", fields.toolName)
        assertEquals(mapOf("section" to "fonts"), fields.parameters)
    }

    @Test
    fun `theme patch retains nested values null and a target with spaces`() {
        val body = JSONObject().put("target_type", "card").put("target_id", "exact id")
            .put("expected_revision", "a".repeat(64))
            .put("changes", JSONObject().put("custom_ai_avatar_uri", JSONObject.NULL).put("show_model_name", false))
        val request = PlatformCommandRegistry.parse(listOf("theme", "set", body.toString())) as PlatformCommandRequest.Invoke
        assertEquals("set_theme_settings", request.toolName)
        assertEquals("exact id", request.parameters["target_id"])
        val changes = JSONObject(request.parameters.getValue("changes"))
        assertTrue(changes.isNull("custom_ai_avatar_uri"))
        assertEquals(false, changes.getBoolean("show_model_name"))
    }

    @Test
    fun `theme changes cannot forge caller identity or omit the required body`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformCommandRegistry.parse(listOf("theme", "set"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlatformCommandRegistry.parse(listOf("theme", "show", "{\"__operit_package_chat_id\":\"other\"}"))
        }
    }

    @Test
    fun `theme body requires an object patch and string routing parameters`() {
        for (body in listOf("{\"changes\":\"{}\"}", "{\"changes\":[]}", "{\"target_id\":1}", "{\"target_type\":null}")) {
            assertThrows(IllegalArgumentException::class.java) {
                PlatformCommandRegistry.parse(listOf("theme", "set", body))
            }
        }
    }

    @Test
    fun `revision ignores map insertion order and changes with target identity`() {
        val left = ThemePreferenceValues(strings = linkedMapOf("a" to "x", "b" to "中文"))
        val right = ThemePreferenceValues(strings = linkedMapOf("b" to "中文", "a" to "x"))
        val revision = ThemeCommandRevision.of("card", "one", left)
        assertEquals(revision, ThemeCommandRevision.of("card", "one", right))
        assertNotEquals(revision, ThemeCommandRevision.of("card", "two", left))
        assertNotEquals(revision, ThemeCommandRevision.of("group", "one", left))
        assertTrue(revision.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `revision detects hidden script and each typed map including deletions`() {
        val values = ThemePreferenceValues.defaultVisual()
        val revision = ThemeCommandRevision.of("card", "one", values)
        val changed = listOf(
            values.withString(ThemePreferenceValues.SCRIPT_URI_KEY, "file:///theme.bundle"),
            values.withBoolean("show_model_name", true),
            values.withInt("custom_primary_color", 0xFFAABBCC.toInt()),
            values.withFloat("font_scale", 1.2f),
            values.copy(strings = values.strings - "theme_mode"),
        )
        changed.forEach { assertNotEquals(revision, ThemeCommandRevision.of("card", "one", it)) }
    }

    @Test
    fun `revision encoding distinguishes values with embedded separators`() {
        val left = ThemePreferenceValues(strings = mapOf("a" to "b\u0000c", "d" to "e"))
        val right = ThemePreferenceValues(strings = mapOf("a" to "b", "c\u0000d" to "e"))
        assertNotEquals(ThemeCommandRevision.of("card", "one", left), ThemeCommandRevision.of("card", "one", right))
    }
}