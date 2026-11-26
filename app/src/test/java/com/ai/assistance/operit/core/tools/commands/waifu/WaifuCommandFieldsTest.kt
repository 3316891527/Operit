package com.ai.assistance.operit.core.tools.commands.waifu

import com.ai.assistance.operit.data.preferences.WaifuSettings
import java.math.BigDecimal
import java.math.BigInteger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WaifuCommandFieldsTest {
    private val names = setOf(
        "enable_waifu_mode", "waifu_char_delay", "waifu_remove_punctuation",
        "waifu_enable_emoticons", "waifu_enable_selfie", "waifu_enable_merge_send",
        "waifu_merge_send_delay_ms", "waifu_custom_prompt", "waifu_selfie_prompt",
    )
    private val original = WaifuSettings(
        enabled = true, charDelay = 400, removePunctuation = true, enableEmoticons = true,
        enableSelfie = true, enableMergeSend = true, mergeSendDelayMs = 1500,
        customPrompt = "原提示词\n保留", selfiePrompt = "原自拍词",
    )
    private fun patch(json: JSONObject) = WaifuCommandFields.apply(original, WaifuCommandFields.validate(json))
    private fun reject(json: JSONObject) {
        assertThrows(IllegalArgumentException::class.java) { WaifuCommandFields.validate(json) }
    }
    @Test fun catalogListsEveryReadableFieldExactlyOnceWithItsScopeAndNullability() {
        val fields = WaifuCommandFields.describe()
        assertEquals(9, fields.length())
        assertEquals(names, (0 until fields.length()).map { fields.getJSONObject(it).getString("name") }.toSet())
        assertEquals(names, WaifuCommandFields.read(original).keys().asSequence().toSet())
        for (index in 0 until fields.length()) {
            val field = fields.getJSONObject(index)
            assertEquals("card_or_group", field.getString("scope"))
            assertFalse(field.getBoolean("nullable"))
            assertTrue(field.getString("description").isNotBlank())
        }
    }
    @Test fun catalogDocumentsBothMillisecondRangesAndClearablePrompts() {
        val fields = WaifuCommandFields.describe()
        val byName = (0 until fields.length()).map { fields.getJSONObject(it) }.associateBy { it.getString("name") }
        for ((name, range) in mapOf("waifu_char_delay" to 200..1000, "waifu_merge_send_delay_ms" to 500..10000)) {
            val field = byName.getValue(name)
            assertEquals("integer", field.getString("type"))
            assertEquals("ms", field.getString("unit"))
            assertEquals(range.first, field.getJSONObject("range").getInt("min"))
            assertEquals(range.last, field.getJSONObject("range").getInt("max"))
        }
        for (name in listOf("waifu_custom_prompt", "waifu_selfie_prompt")) {
            assertEquals("string", byName.getValue(name).getString("type"))
            assertTrue(byName.getValue(name).getBoolean("allowEmpty"))
        }
    }
    @Test fun rejectsEmptyUnknownAndNullPatches() {
        reject(JSONObject())
        reject(JSONObject().put("waifu_typo", true))
        names.forEach { reject(JSONObject().put(it, JSONObject.NULL)) }
    }
    @Test fun booleansRejectNumbersAndStringCoercion() {
        for (name in names.filter { it !in setOf("waifu_char_delay", "waifu_merge_send_delay_ms", "waifu_custom_prompt", "waifu_selfie_prompt") }) {
            for (value in listOf<Any>("true", 1, 0, JSONObject())) reject(JSONObject().put(name, value))
        }
    }
    @Test fun promptsRejectStructuredAndScalarNonStrings() {
        for (name in listOf("waifu_custom_prompt", "waifu_selfie_prompt")) {
            for (value in listOf<Any>(false, 200, JSONObject())) reject(JSONObject().put(name, value))
        }
    }
    @Test fun integerFieldsRejectStringsAndBooleans() {
        for (name in listOf("waifu_char_delay", "waifu_merge_send_delay_ms")) {
            for (value in listOf<Any>("500", true, JSONObject())) reject(JSONObject().put(name, value))
        }
    }
    @Test fun inclusiveIntegerEndpointsAreAccepted() {
        assertEquals(200, patch(JSONObject().put("waifu_char_delay", 200)).charDelay)
        assertEquals(1000, patch(JSONObject().put("waifu_char_delay", 1000)).charDelay)
        assertEquals(500, patch(JSONObject().put("waifu_merge_send_delay_ms", 500)).mergeSendDelayMs)
        assertEquals(10000, patch(JSONObject().put("waifu_merge_send_delay_ms", 10000)).mergeSendDelayMs)
    }
    @Test fun rejectsJustOutsideBothRangesWithoutIntegerOverflow() {
        for (value in listOf<Number>(199, 1001, Long.MAX_VALUE, BigInteger("999999999999999999999999"))) {
            reject(JSONObject().put("waifu_char_delay", value))
        }
        for (value in listOf<Number>(499, 10001, Long.MIN_VALUE)) reject(JSONObject().put("waifu_merge_send_delay_ms", value))
    }
    @Test fun rejectsFractionalValuesIncludingPrecisionBeyondDouble() {
        for (value in listOf<Number>(400.5, BigDecimal("400.0000000000000000001"), BigDecimal("999.9999999999999999999"))) {
            reject(JSONObject().put("waifu_char_delay", value))
        }
        reject(JSONObject().put("waifu_merge_send_delay_ms", BigDecimal("500.0000000000000000001")))
    }
    @Test fun mathematicallyIntegralDecimalsAndExponentNotationAreAccepted() {
        assertEquals(400, patch(JSONObject().put("waifu_char_delay", BigDecimal("400.000"))).charDelay)
        assertEquals(1000, patch(JSONObject().put("waifu_char_delay", BigDecimal("1E+3"))).charDelay)
        assertEquals(500, patch(JSONObject().put("waifu_merge_send_delay_ms", 500.0)).mergeSendDelayMs)
    }
    @Test fun disablingWaifuPreservesAllOtherPreferences() {
        assertEquals(original.copy(enabled = false), patch(JSONObject().put("enable_waifu_mode", false)))
    }
    @Test fun oneDelayPatchPreservesPromptsAndEveryUnsubmittedSwitch() {
        assertEquals(original.copy(charDelay = 650), patch(JSONObject().put("waifu_char_delay", 650)))
    }
    @Test fun promptPatchPreservesWhitespaceUnicodeAndEmptyClearSemantics() {
        val prompt = "  中文\n\"引号\"\\路径😀  "
        assertEquals(original.copy(customPrompt = prompt), patch(JSONObject().put("waifu_custom_prompt", prompt)))
        assertEquals(original.copy(selfiePrompt = ""), patch(JSONObject().put("waifu_selfie_prompt", "")))
    }
    @Test fun validFieldCannotMakeAnUnknownFieldAcceptable() {
        reject(JSONObject().put("enable_waifu_mode", false).put("theme_mode", "dark"))
    }
    @Test fun completePatchCanSetEveryFieldWithoutDroppingDisabledFeatureConfiguration() {
        val changes = JSONObject()
            .put("enable_waifu_mode", false).put("waifu_char_delay", 750)
            .put("waifu_remove_punctuation", false).put("waifu_enable_emoticons", false)
            .put("waifu_enable_selfie", false).put("waifu_enable_merge_send", false)
            .put("waifu_merge_send_delay_ms", 2500).put("waifu_custom_prompt", "")
            .put("waifu_selfie_prompt", "关闭时仍保存的自拍词")
        assertEquals(
            WaifuSettings(false, 750, false, false, false, false, 2500, "", "关闭时仍保存的自拍词"),
            patch(changes),
        )
    }
    @Test fun applyRevalidatesUntrustedMapsBeforeChangingSettings() {
        assertThrows(IllegalArgumentException::class.java) {
            WaifuCommandFields.apply(original, mapOf("waifu_char_delay" to "400"))
        }
    }
}
