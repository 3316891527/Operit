package com.ai.assistance.operit.core.tools.commands.theme.editor

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThemeCommandFieldsTest {
    private fun values() = ThemePreferenceValues.defaultVisual()
    private fun patch(current: ThemePreferenceValues = values(), json: String): ThemePreferenceValues =
        ThemeCommandFields.apply(current, ThemeCommandFields.validate(JSONObject(json)))
    private fun entries(section: String? = null): List<JSONObject> {
        val array = ThemeCommandFields.describe(section)
        return (0 until array.length()).map(array::getJSONObject)
    }
    private fun rejected(json: String) {
        assertThrows(IllegalArgumentException::class.java) { ThemeCommandFields.validate(JSONObject(json)) }
    }

    @Test fun `catalog exposes only the six authorized sections and twelve information switches`() {
        assertEquals(setOf("colors", "fonts", "avatars", "background", "messages", "info"), ThemeCommandFields.sections)
        val catalog = entries()
        assertEquals(catalog.size, catalog.map { it.getString("name") }.toSet().size)
        assertEquals(ThemeCommandFields.sections, catalog.map { it.getString("section") }.toSet())
        assertEquals(setOf("show_thinking_process", "show_status_tags", "show_model_provider", "show_model_name",
            "show_role_name", "show_user_name", "show_message_token_stats", "show_message_timing_stats",
            "show_message_token_speed", "show_message_timestamp", "show_input_processing_status", "show_chat_floating_dots_animation"),
            entries("info").map { it.getString("name") }.toSet())
        for (field in catalog) {
            assertTrue(field.has("type"))
            assertTrue(field.has("nullable"))
            if (field.getString("type") == "number") {
                assertTrue(field.has("range"))
                assertTrue(field.has("unit"))
            }
        }
    }

    @Test fun `out of scope fields are absent and rejected even beside a valid change`() {
        val names = entries().map { it.getString("name") }.toSet()
        for (name in listOf("language", "input_style", "chat_input_opacity", "chat_header_transparent",
            "navigation_drawer_water_glass", "glass_blur_radius", "custom_theme_script_uri",
            "chat_area_horizontal_padding", "show_floating_ball", "chat_style")) {
            assertFalse(name, name in names)
            assertThrows(IllegalArgumentException::class.java) {
                ThemeCommandFields.validate(JSONObject().put("show_model_name", true).put(name, true))
            }
        }
    }

    @Test fun `invalid sections are rejected and section reads contain only matching fields`() {
        assertThrows(IllegalArgumentException::class.java) { ThemeCommandFields.describe("input") }
        assertThrows(IllegalArgumentException::class.java) { ThemeCommandFields.read(values(), "") }
        assertEquals(entries("info").map { it.getString("name") }.toSet(),
            ThemeCommandFields.read(values(), "info").keys().asSequence().toSet())
    }

    @Test fun `profile catalog uses user scope and theme reads never fabricate profile values`() {
        assertEquals(setOf("global_user_avatar_uri", "global_user_name"), ThemeCommandFields.profileNames)
        val profiles = entries().filter { it.getString("scope") == "user" }
        assertEquals(ThemeCommandFields.profileNames, profiles.map { it.getString("name") }.toSet())
        val read = ThemeCommandFields.read(values().withString("global_user_name", "ignored"), null)
        ThemeCommandFields.profileNames.forEach { assertFalse(read.has(it)) }
        assertEquals("", ThemeCommandFields.validate(JSONObject("""{"global_user_name":""}"""))["global_user_name"])
        rejected("""{"global_user_name":null}""")
        assertThrows(IllegalArgumentException::class.java) {
            ThemeCommandFields.apply(values(), mapOf("global_user_avatar_uri" to null))
        }
    }

    @Test fun `resource catalog uses real preference keys and strict kinds`() {
        assertEquals(mapOf("background_image_uri" to "image", "custom_user_avatar_uri" to "image",
            "custom_ai_avatar_uri" to "image", "global_user_avatar_uri" to "image",
            "bubble_user_image_uri" to "image", "bubble_ai_image_uri" to "image",
            "custom_font_path" to "font", "bubble_user_custom_font_path" to "font", "bubble_ai_custom_font_path" to "font"),
            ThemeCommandFields.resourceKinds)
        ThemeCommandFields.resourceKinds.keys.forEach {
            assertNull(ThemeCommandFields.validate(JSONObject().put(it, JSONObject.NULL))[it])
            assertThrows(IllegalArgumentException::class.java) { ThemeCommandFields.validate(JSONObject().put(it, "   ")) }
        }
    }

    @Test fun `JSON types are strict and arrays objects and coercible strings are rejected`() {
        for (value in listOf<Any>("true", 1, JSONArray(), JSONObject(), JSONObject.NULL)) {
            assertThrows(IllegalArgumentException::class.java) {
                ThemeCommandFields.validate(JSONObject().put("show_model_name", value))
            }
        }
        for (value in listOf<Any>("1.2", true, JSONArray(), JSONObject(), JSONObject.NULL)) {
            assertThrows(IllegalArgumentException::class.java) {
                ThemeCommandFields.validate(JSONObject().put("font_scale", value))
            }
        }
        rejected("""{"theme_mode":1}""")
        rejected("""{"theme_mode":null}""")
        rejected("""{"theme_mode":"DARK"}""")
        rejected("""{"avatar_shape":"rounded"}""")
        rejected("""{"bubble_image_render_mode":"stretch"}""")
    }

    @Test fun `colors normalize RGB and ARGB and read back uppercase ARGB`() {
        val normalized = ThemeCommandFields.validate(JSONObject("""{"custom_primary_color":"#a1b2c3","bubble_ai_text_color":"#8012abef"}"""))
        assertEquals(0xFFA1B2C3.toInt(), normalized["custom_primary_color"])
        assertEquals(0x8012ABEF.toInt(), normalized["bubble_ai_text_color"])
        val read = ThemeCommandFields.read(ThemeCommandFields.apply(values(), normalized), null)
        assertEquals("#FFA1B2C3", read.getString("custom_primary_color"))
        assertEquals("#8012ABEF", read.getString("bubble_ai_text_color"))
        for (color in listOf("red", "#fff", "#1234567", "#GG112233", " #112233", "112233")) {
            assertThrows(IllegalArgumentException::class.java) {
                ThemeCommandFields.validate(JSONObject().put("custom_primary_color", color))
            }
        }
        rejected("""{"custom_primary_color":-1}""")
        rejected("""{"thinking_particle_color":null}""")
    }

    @Test fun `numeric catalog boundaries are accepted without decimal float boundary loss`() {
        for (field in entries().filter { it.getString("type") == "number" }) {
            val name = field.getString("name")
            val range = field.getJSONObject("range")
            for (edge in listOf("min", "max")) {
                val input = range.get(edge).toString().toDouble()
                val normalized = ThemeCommandFields.validate(JSONObject().put(name, input))
                assertTrue(name, normalized[name] is Float)
            }
            val low = range.get("min").toString().toDouble() - 0.000001
            val high = range.get("max").toString().toDouble() + 0.000001
            assertThrows(IllegalArgumentException::class.java) { ThemeCommandFields.validate(JSONObject().put(name, low)) }
            assertThrows(IllegalArgumentException::class.java) { ThemeCommandFields.validate(JSONObject().put(name, high)) }
        }
        assertEquals(0.8f, ThemeCommandFields.validate(JSONObject("""{"font_scale":0.8}"""))["font_scale"])
        assertEquals(0.45f, ThemeCommandFields.validate(JSONObject("""{"bubble_ai_image_crop_left":0.45}"""))["bubble_ai_image_crop_left"])
    }

    @Test fun `huge and nonfinite numbers cannot enter a patch`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemeCommandFields.validate(JSONObject().put("font_scale", BigDecimal("1e1000")))
        }
        for (number in listOf(BigDecimal("1.50000000000000000001"), BigDecimal("0.79999999999999999999"))) {
            assertThrows(IllegalArgumentException::class.java) {
                ThemeCommandFields.validate(JSONObject().put("font_scale", number))
            }
        }
        for (number in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { ThemeCommandFields.apply(values(), mapOf("font_scale" to number)) }
        }
        assertThrows(IllegalArgumentException::class.java) { ThemeCommandFields.apply(values(), mapOf("font_scale" to 1.0)) }
    }

    @Test fun `empty and simple information patches preserve scripts unrelated fields and damaged unrelated settings`() {
        val original = values().withString(ThemePreferenceValues.SCRIPT_URI_KEY, "file:///theme/script.json")
            .withString("custom_chat_title", "原有标题").withBoolean("bubble_ai_use_image", true)
            .withString("font_type", "file").withBoolean("use_custom_font", true)
            .withFloat("bubble_ai_image_repeat_start", 0.9f)
        assertEquals(original, ThemeCommandFields.apply(original, emptyMap()))
        assertEquals(original.withBoolean("show_model_name", true), patch(original, """{"show_model_name":true}"""))
        val colored = patch(original, """{"bubble_ai_bubble_color":"#112233"}""")
        assertEquals(true, colored.boolean("bubble_ai_use_image"))
        assertEquals("file", colored.string("font_type"))
    }

    @Test fun `custom colors activate their switch and explicit disabling conflicts`() {
        val result = patch(json = """{"custom_primary_color":"#102030"}""")
        assertEquals(true, result.boolean("use_custom_colors"))
        assertEquals(values().strings, result.strings)
        rejected("""{"use_custom_colors":false,"custom_primary_color":"#102030"}""")
        val cursor = patch(json = """{"cursor_user_bubble_color":"#40102030"}""")
        assertEquals(false, cursor.boolean("cursor_user_bubble_follow_theme"))
        rejected("""{"cursor_user_bubble_follow_theme":true,"cursor_user_bubble_color":"#102030"}""")
    }

    @Test fun `clearing a color or optional opacity removes only that stored entry`() {
        val original = values().withInt("bubble_ai_text_color", -1).withFloat("bubble_ai_bubble_opacity", 0.4f)
        val result = patch(original, """{"bubble_ai_text_color":null,"bubble_ai_bubble_opacity":null}""")
        assertEquals(original.copy(ints = original.ints - "bubble_ai_text_color", floats = original.floats - "bubble_ai_bubble_opacity"), result)
        assertTrue(ThemeCommandFields.read(result, "messages").isNull("bubble_ai_bubble_opacity"))
    }

    @Test fun `each avatar reference can be cleared and empty references are rejected`() {
        for (name in listOf("custom_user_avatar_uri", "custom_ai_avatar_uri")) {
            val original = values().withString(name, "file:///avatar.png")
            val result = ThemeCommandFields.apply(original, ThemeCommandFields.validate(JSONObject().put(name, JSONObject.NULL)))
            assertEquals(values(), result)
        }
    }

    @Test fun `font paths select and enable file fonts for all three scopes`() {
        for (prefix in listOf("", "bubble_user_", "bubble_ai_")) {
            val path = prefix + "custom_font_path"
            val result = ThemeCommandFields.apply(values(), ThemeCommandFields.validate(JSONObject().put(path, "/private/font.ttf")))
            assertEquals("file", result.string(prefix + "font_type"))
            assertEquals(true, result.boolean(prefix + "use_custom_font"))
            assertEquals("/private/font.ttf", result.string(path))
            val cleared = ThemeCommandFields.apply(result, mapOf(path to null))
            assertNull(cleared.string(path))
            assertEquals(false, cleared.boolean(prefix + "use_custom_font"))
            assertEquals("file", cleared.string(prefix + "font_type"))
        }
    }

    @Test fun `system font choices enable custom system font without deleting saved file path`() {
        val original = values().withString("custom_font_path", "/private/font.ttf").withString("font_type", "file")
        val result = patch(original, """{"system_font_name":"serif"}""")
        assertEquals("system", result.string("font_type"))
        assertEquals(true, result.boolean("use_custom_font"))
        assertEquals("/private/font.ttf", result.string("custom_font_path"))
        assertThrows(IllegalArgumentException::class.java) { patch(json = """{"font_type":"file"}""") }
        rejected("""{"custom_font_path":"/f.ttf","font_type":"system"}""")
        rejected("""{"custom_font_path":"/f.ttf","use_custom_font":false}""")
        rejected("""{"system_font_name":"serif","font_type":"file"}""")
        rejected("""{"custom_font_path":null,"font_type":"file"}""")
    }

    @Test fun `switching glass modes closes only the corresponding incompatible modes`() {
        for (side in listOf("user", "ai")) {
            val prefix = "bubble_${side}_"
            val original = values().withBoolean(prefix + "bubble_water_glass", true).withBoolean(prefix + "use_image", true)
                .withString(prefix + "image_uri", "file:///bubble.png")
            val result = ThemeCommandFields.apply(original, mapOf(prefix + "bubble_liquid_glass" to true))
            assertEquals(true, result.boolean(prefix + "bubble_liquid_glass"))
            assertEquals(false, result.boolean(prefix + "bubble_water_glass"))
            assertEquals(false, result.boolean(prefix + "use_image"))
            assertEquals("file:///bubble.png", result.string(prefix + "image_uri"))
        }
        val cursor = patch(values().withBoolean("cursor_user_bubble_water_glass", true), """{"cursor_user_bubble_liquid_glass":true}""")
        assertEquals(false, cursor.boolean("cursor_user_bubble_water_glass"))
        assertEquals(false, cursor.boolean("bubble_user_bubble_liquid_glass"))
    }

    @Test fun `explicit glass contradictions fail regardless of JSON order`() {
        rejected("""{"bubble_ai_bubble_liquid_glass":true,"bubble_ai_bubble_water_glass":true}""")
        rejected("""{"bubble_ai_bubble_water_glass":true,"bubble_ai_bubble_liquid_glass":true}""")
        rejected("""{"bubble_user_use_image":true,"bubble_user_bubble_water_glass":true}""")
        rejected("""{"cursor_user_bubble_water_glass":true,"cursor_user_bubble_liquid_glass":true}""")
    }

    @Test fun `image selection follows glass state and explicit image enable requires compatible state and URI`() {
        val selected = patch(json = """{"bubble_user_image_uri":"file:///bubble.png"}""")
        assertEquals(true, selected.boolean("bubble_user_use_image"))
        val glass = values().withBoolean("bubble_user_bubble_water_glass", true)
        val stored = patch(glass, """{"bubble_user_image_uri":"file:///bubble.png"}""")
        assertEquals(false, stored.boolean("bubble_user_use_image"))
        assertThrows(IllegalArgumentException::class.java) { patch(stored, """{"bubble_user_use_image":true}""") }
        val enabled = patch(stored, """{"bubble_user_use_image":true,"bubble_user_bubble_water_glass":false}""")
        assertEquals(true, enabled.boolean("bubble_user_use_image"))
        assertThrows(IllegalArgumentException::class.java) { patch(json = """{"bubble_ai_use_image":true}""") }
        val cleared = patch(selected, """{"bubble_user_image_uri":null}""")
        assertEquals(false, cleared.boolean("bubble_user_use_image"))
        assertNull(cleared.string("bubble_user_image_uri"))
    }

    @Test fun `stretch region validates changed pairs against both new and existing endpoints`() {
        rejected("""{"bubble_user_image_repeat_start":0.7,"bubble_user_image_repeat_end":0.7}""")
        assertThrows(IllegalArgumentException::class.java) { patch(json = """{"bubble_user_image_repeat_start":0.8}""") }
        val result = patch(json = """{"bubble_user_image_repeat_start":0.8,"bubble_user_image_repeat_end":0.9}""")
        assertEquals(0.8f, result.float("bubble_user_image_repeat_start"))
        assertEquals(0.9f, result.float("bubble_user_image_repeat_end"))
        patch(json = """{"bubble_ai_image_repeat_y_start":0,"bubble_ai_image_repeat_y_end":1}""")
    }

    @Test fun `background references preserve enable switch while explicit enable requires a reference`() {
        val result = patch(json = """{"background_image_uri":"file:///video.mp4","background_media_type":"video"}""")
        assertEquals(false, result.boolean("use_background_image"))
        assertEquals("video", result.string("background_media_type"))
        val enabled = patch(result, """{"use_background_image":true}""")
        assertEquals(true, enabled.boolean("use_background_image"))
        val cleared = patch(enabled, """{"background_image_uri":null}""")
        assertEquals(false, cleared.boolean("use_background_image"))
        assertNull(cleared.string("background_image_uri"))
        assertThrows(IllegalArgumentException::class.java) { patch(json = """{"use_background_image":true}""") }
        rejected("""{"background_image_uri":null,"use_background_image":true}""")
    }

    @Test fun `message background color follows UI alpha unless an explicit opacity overrides it`() {
        val original = values().withFloat("bubble_user_bubble_opacity", 0.9f)
        val result = patch(original, """{"bubble_user_bubble_color":"#80112233"}""")
        assertEquals(128f / 255f, result.requiredFloat("bubble_user_bubble_opacity"), 0f)
        val explicit = patch(original, """{"bubble_user_bubble_color":"#80112233","bubble_user_bubble_opacity":0.2}""")
        assertEquals(0.2f, explicit.requiredFloat("bubble_user_bubble_opacity"), 0f)
        val cleared = patch(result, """{"bubble_user_bubble_color":null}""")
        assertEquals(result.float("bubble_user_bubble_opacity"), cleared.float("bubble_user_bubble_opacity"))
    }

    @Test fun `normalized application rejects JSON null sentinels and unnormalized colors`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemeCommandFields.apply(values(), mapOf("custom_user_avatar_uri" to JSONObject.NULL))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThemeCommandFields.apply(values(), mapOf("custom_primary_color" to "#112233"))
        }
    }

    @Test fun `read reports stored defaults nullable values and never exposes script or unrelated preferences`() {
        val stored = values().withString(ThemePreferenceValues.SCRIPT_URI_KEY, "file:///theme.json")
            .withString("input_style", "classic").withInt("custom_primary_color", 0x40112233)
        val read = ThemeCommandFields.read(stored, null)
        assertEquals("#40112233", read.getString("custom_primary_color"))
        assertEquals("#FFFFFFFF", read.getString("thinking_particle_color"))
        assertTrue(read.has("custom_secondary_color"))
        assertTrue(read.isNull("custom_secondary_color"))
        assertFalse(read.has(ThemePreferenceValues.SCRIPT_URI_KEY))
        assertFalse(read.has("input_style"))
        assertEquals(1.0, read.getDouble("font_scale"), 0.0)
    }
}
