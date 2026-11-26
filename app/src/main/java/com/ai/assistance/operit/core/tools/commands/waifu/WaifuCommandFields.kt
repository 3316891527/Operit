package com.ai.assistance.operit.core.tools.commands.waifu

import com.ai.assistance.operit.data.preferences.WaifuSettings
import org.json.JSONArray
import org.json.JSONObject

/** 字段使用现有偏好键，校验范围与设置页一致，关闭功能时仍保留其配置。 */
internal object WaifuCommandFields {
    private val booleans = setOf(
        "enable_waifu_mode", "waifu_remove_punctuation", "waifu_enable_emoticons",
        "waifu_enable_selfie", "waifu_enable_merge_send",
    )
    private val integers = mapOf(
        "waifu_char_delay" to 200..1000,
        "waifu_merge_send_delay_ms" to 500..10000,
    )
    private val strings = setOf("waifu_custom_prompt", "waifu_selfie_prompt")

    fun describe(): JSONArray = JSONArray().apply {
        booleans.forEach { name -> put(field(name, "boolean")) }
        integers.forEach { (name, range) ->
            put(field(name, "integer").put("unit", "ms")
                .put("range", JSONObject().put("min", range.first).put("max", range.last)))
        }
        strings.forEach { name -> put(field(name, "string").put("allowEmpty", true)) }
    }

    private fun field(name: String, type: String): JSONObject = JSONObject()
        .put("name", name).put("type", type).put("nullable", false).put("scope", "card_or_group")
        .put("description", when (name) {
            "enable_waifu_mode" -> "Enable Waifu mode; disabling it preserves the other settings."
            "waifu_char_delay" -> "Delay per character in milliseconds, used while Waifu mode is enabled."
            "waifu_remove_punctuation" -> "Remove punctuation from Waifu message segments."
            "waifu_enable_emoticons" -> "Enable emoticon use in Waifu mode."
            "waifu_enable_selfie" -> "Enable selfies in Waifu mode."
            "waifu_enable_merge_send" -> "Merge consecutive user sends in Waifu mode."
            "waifu_merge_send_delay_ms" -> "Merge wait in milliseconds, used when merge send is enabled."
            "waifu_custom_prompt" -> "Additional Waifu prompt; an empty string clears it."
            "waifu_selfie_prompt" -> "Selfie appearance prompt, used when selfies are enabled; an empty string clears it."
            else -> error("Unknown Waifu field: $name")
        })

    fun read(settings: WaifuSettings): JSONObject = JSONObject().apply {
        put("enable_waifu_mode", settings.enabled)
        put("waifu_char_delay", settings.charDelay)
        put("waifu_remove_punctuation", settings.removePunctuation)
        put("waifu_enable_emoticons", settings.enableEmoticons)
        put("waifu_enable_selfie", settings.enableSelfie)
        put("waifu_enable_merge_send", settings.enableMergeSend)
        put("waifu_merge_send_delay_ms", settings.mergeSendDelayMs)
        put("waifu_custom_prompt", settings.customPrompt)
        put("waifu_selfie_prompt", settings.selfiePrompt)
    }

    fun validate(changes: JSONObject): Map<String, Any> {
        require(changes.length() > 0) { "changes must contain at least one Waifu field" }
        return changes.keys().asSequence().associateWith { name ->
            val value = changes.get(name)
            when (name) {
                in booleans -> {
                    require(value is Boolean) { "$name must be a JSON boolean" }
                    value
                }
                in integers -> {
                    require(value is Number) { "$name must be a JSON integer" }
                    val decimal = requireNotNull(value.toString().toBigDecimalOrNull()) { "$name must be a finite JSON integer" }
                    val range = integers.getValue(name)
                    require(decimal.stripTrailingZeros().scale() <= 0 &&
                        decimal >= range.first.toBigDecimal() && decimal <= range.last.toBigDecimal()) {
                        "$name must be an integer within ${range.first}..${range.last}"
                    }
                    decimal.toInt()
                }
                in strings -> {
                    require(value is String) { "$name must be a JSON string" }
                    value
                }
                else -> throw IllegalArgumentException("Unknown Waifu field: $name")
            }
        }
    }

    /** 仅显式字段进入补丁，不修改未提交的提示词或开关。 */
    fun apply(current: WaifuSettings, changes: Map<String, Any>): WaifuSettings {
        val valid = validate(JSONObject(changes))
        var result = current
        valid.forEach { (name, value) ->
            result = when (name) {
                "enable_waifu_mode" -> result.copy(enabled = value as Boolean)
                "waifu_char_delay" -> result.copy(charDelay = value as Int)
                "waifu_remove_punctuation" -> result.copy(removePunctuation = value as Boolean)
                "waifu_enable_emoticons" -> result.copy(enableEmoticons = value as Boolean)
                "waifu_enable_selfie" -> result.copy(enableSelfie = value as Boolean)
                "waifu_enable_merge_send" -> result.copy(enableMergeSend = value as Boolean)
                "waifu_merge_send_delay_ms" -> result.copy(mergeSendDelayMs = value as Int)
                "waifu_custom_prompt" -> result.copy(customPrompt = value as String)
                "waifu_selfie_prompt" -> result.copy(selfiePrompt = value as String)
                else -> error("Unknown Waifu field: $name")
            }
        }
        return result
    }
}
