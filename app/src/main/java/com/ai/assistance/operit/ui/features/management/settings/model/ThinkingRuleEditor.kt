package com.ai.assistance.operit.ui.features.management.settings.model

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class ThinkingRuleEditor(
    val originalJson: String? = null,
    val id: String = "",
    val enabled: Boolean = true,
    val providerIds: String = "",
    val matchField: String = "modelContains",
    val matchValues: String = "",
    val control: String = "levels",
    val parameterLabel: String = "",
    val enabledActions: List<ThinkingActionEditor> = emptyList(),
    val disabledActions: List<ThinkingActionEditor> = emptyList(),
    val options: List<ThinkingOptionEditor> = listOf(
        ThinkingOptionEditor(id = "low", label = "low", value = "low"),
        ThinkingOptionEditor(id = "high", label = "high", value = "high"),
        ThinkingOptionEditor(id = "max", label = "max", value = "max")
    )
)

internal data class ThinkingActionEditor(
    val originalJson: String? = null,
    val path: String = "",
    val value: String = ""
)

internal data class ThinkingOptionEditor(
    val originalJson: String? = null,
    val id: String = "",
    val label: String = "",
    val path: String = "",
    val value: String = "",
    val editorKey: String = UUID.randomUUID().toString()
)

internal val thinkingControlChoices =
    listOf(
        "unsupported" to "不支持思考",
        "levels" to "多档滑块",
        "toggle_only" to "仅开关",
    )

internal val thinkingMatchFieldChoices =
    listOf(
        "modelContains" to "模型包含",
        "modelPrefix" to "模型前缀",
        "modelSuffix" to "模型后缀",
        "modelRegex" to "模型正则",
        "firstSegment" to "斜杠前段",
        "lastSegmentPrefix" to "后段前缀",
        "lastSegmentContains" to "后段包含",
        "lastSegmentRegex" to "后段正则",
        "endpointSuffix" to "端点后缀"
    )

internal fun parseThinkingRuleEditors(raw: String): List<ThinkingRuleEditor> {
    val array = thinkingRulesArray(raw)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(item.toThinkingRuleEditor(index))
        }
    }
}

internal fun serializeThinkingRuleEditors(
    rules: List<ThinkingRuleEditor>,
    originalConfigurations: String = "[]",
): String {
    val array = JSONArray()
    rules.forEachIndexed { index, rule -> array.put(rule.toJson(index)) }
    val original = originalConfigurations.trim()
    if (original.startsWith("{")) {
        val container = JSONObject(original)
        if (container.optJSONArray("rules") != null) return container.put("rules", array).toString()
    }
    return array.toString()
}

internal fun ThinkingRuleEditor.toJson(index: Int = 0): JSONObject {
    // Keep the complete source object. A compact editor must not delete fields it cannot edit.
    val json = originalJson?.let(::JSONObject) ?: JSONObject()
    val original = originalJson?.let { JSONObject(it).toThinkingRuleEditor(index) }
    if (original == null || id != original.id) json.put("id", id.trim().ifEmpty { "custom-thinking-${index + 1}" })
    if (original == null || enabled != original.enabled) json.put("enabled", enabled)
    if (original == null || providerIds != original.providerIds) {
        json.remove("providerTypeIds")
        json.remove("providers")
        val providers = splitThinkingCsv(providerIds)
        if (providers.isNotEmpty()) json.put("providers", thinkingStringArray(providers))
    }
    if (original == null || matchField != original.matchField || matchValues != original.matchValues) {
        val match = json.optJSONObject("match") ?: JSONObject()
        if (original != null && matchField != original.matchField) {
            require(match.stringArrayValues(matchField).isEmpty() && json.stringArrayValues(matchField).isEmpty()) {
                "Another match condition already uses $matchField; keep its existing condition"
            }
        }
        if (original != null) {
            match.remove(original.matchField)
            json.remove(original.matchField)
        }
        json.remove(matchField)
        val values = splitThinkingCsv(matchValues)
        if (values.isNotEmpty()) match.put(matchField, thinkingStringArray(values))
        json.put("match", match)
    }
    if (original == null || control != original.control) json.put("control", control)
    if (original == null || parameterLabel != original.parameterLabel) {
        json.remove("label")
        json.put("parameterLabel", parameterLabel.trim())
    }
    if (original == null || enabledActions != original.enabledActions) {
        listOf("enabledActions", "on").forEach(json::remove)
        json.put("enable", thinkingActionsArray(enabledActions))
    }
    if (original == null || disabledActions != original.disabledActions) {
        listOf("disabledActions", "off").forEach(json::remove)
        json.put("disable", thinkingActionsArray(disabledActions))
    }
    val content = options.map { listOf(it.id, it.label, it.path, it.value, it.originalJson) }
    val originalContent = original?.options?.map { listOf(it.id, it.label, it.path, it.value, it.originalJson) }
    if (control == "levels" && content != originalContent) {
        val array = JSONArray()
        options.forEachIndexed { optionIndex, option ->
            val valueText = option.value.trim()
            val path = option.path.trim().ifEmpty { parameterLabel.trim() }
            if (option.id.isBlank() && option.label.isBlank() && path.isBlank() && valueText.isBlank()) return@forEachIndexed
            val item = option.originalJson?.let(::JSONObject) ?: JSONObject()
            val old = option.originalJson?.let(::JSONObject)
            if (old == null || option.id != old.optString("id", "")) {
                item.put("id", option.id.trim().ifEmpty { valueText.ifEmpty { "option-${optionIndex + 1}" } })
            }
            if (old == null || option.label != old.optString("label", old.optString("id", ""))) item.put("label", option.label.trim())
            if (old == null || option.path != old.optString("path", "")) item.put("path", path)
            if (old == null || option.value != thinkingEditorValueText(old.opt("value"))) item.put("value", parseThinkingEditorValue(valueText))
            array.put(item)
        }
        json.put("options", array)
    }
    return json
}

internal fun ThinkingRuleEditor.duplicate(): ThinkingRuleEditor =
    toJson().put("id", "custom-thinking-${UUID.randomUUID()}").toThinkingRuleEditor(0)

internal fun thinkingRulesArray(raw: String): JSONArray {
    val text = raw.trim().ifEmpty { "[]" }
    return when {
        text.startsWith("[") -> JSONArray(text)
        text.startsWith("{") -> {
            val objectValue = JSONObject(text)
            objectValue.optJSONArray("rules") ?: JSONArray().put(objectValue)
        }
        else -> JSONArray(text)
    }
}

internal fun JSONObject.toThinkingRuleEditor(index: Int): ThinkingRuleEditor {
    val matchPair = firstThinkingMatchPair()
    return ThinkingRuleEditor(
        originalJson = toString(),
        id = optString("id", "custom-thinking-${index + 1}"),
        enabled = optBoolean("enabled", true),
        providerIds = (stringArrayValues("providers") + stringArrayValues("providerTypeIds"))
            .distinct()
            .joinToString(", "),
        matchField = matchPair.first,
        matchValues = matchPair.second.joinToString(", "),
        control = when (val value = optString("control", "unsupported")) {
            "toggle", "toggle_only" -> "toggle_only"
            else -> value
        },
        parameterLabel = optString("parameterLabel", optString("label", "")),
        enabledActions = actionEditors("enable", "enabledActions", "on"),
        disabledActions = actionEditors("disable", "disabledActions", "off"),
        options = optionEditors()
    )
}

internal fun JSONObject.firstThinkingMatchPair(): Pair<String, List<String>> {
    val match = optJSONObject("match") ?: JSONObject()
    thinkingMatchFieldChoices.forEach { (field, _) ->
        val values = match.stringArrayValues(field) + stringArrayValues(field)
        if (values.isNotEmpty()) return field to values
    }
    return "modelContains" to emptyList()
}

internal fun JSONObject.actionEditors(vararg keys: String): List<ThinkingActionEditor> =
    keys.flatMap { key -> actionEditorsFromValue(opt(key)) }

internal fun actionEditorsFromValue(value: Any?): List<ThinkingActionEditor> {
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                val item = value.optJSONObject(index) ?: continue
                item.toActionEditor()?.let(::add)
            }
        }
        is JSONObject -> listOfNotNull(value.toActionEditor())
        else -> emptyList()
    }
}

internal fun JSONObject.toActionEditor(): ThinkingActionEditor? {
    val path = optString("path", "").trim()
    if (path.isEmpty()) return null
    return ThinkingActionEditor(originalJson = toString(), path = path, value = thinkingEditorValueText(opt("value")))
}

internal fun JSONObject.optionEditors(): List<ThinkingOptionEditor> {
    val array = optJSONArray("options") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val option = array.optJSONObject(index) ?: continue
            add(
                ThinkingOptionEditor(
                    originalJson = option.toString(),
                    id = option.optString("id", ""),
                    label = option.optString("label", option.optString("id", "")),
                    path = option.optString("path", ""),
                    value = thinkingEditorValueText(option.opt("value"))
                )
            )
        }
    }
}

internal fun JSONObject.stringArrayValues(key: String): List<String> {
    val value = opt(key)
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                value.optString(index, "").trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        is String -> splitThinkingCsv(value)
        else -> emptyList()
    }
}

internal fun thinkingStringArray(values: List<String>): JSONArray =
    JSONArray().apply {
        values.forEach { put(it) }
    }

internal fun thinkingActionsArray(actions: List<ThinkingActionEditor>): JSONArray {
    val array = JSONArray()
    actions.forEach { action ->
        val path = action.path.trim()
        if (path.isNotEmpty()) {
            array.put(
                (action.originalJson?.let(::JSONObject) ?: JSONObject()).apply {
                    val original = action.originalJson?.let(::JSONObject)
                    put("path", path)
                    if (original == null || action.value != thinkingEditorValueText(original.opt("value"))) {
                        put("value", parseThinkingEditorValue(action.value))
                    }
                }
            )
        }
    }
    return array
}

internal fun splitThinkingCsv(value: String): List<String> =
    value.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

internal fun parseThinkingEditorValue(value: String): Any {
    val text = value.trim()
    if (text.equals("true", ignoreCase = true)) return true
    if (text.equals("false", ignoreCase = true)) return false
    if (text.equals("null", ignoreCase = true)) return JSONObject.NULL
    text.toIntOrNull()?.let { return it }
    text.toLongOrNull()?.let { return it }
    if (text.contains('.')) {
        text.toDoubleOrNull()?.let { return it }
    }
    if (text.startsWith("{") && text.endsWith("}")) {
        runCatching { JSONObject(text) }.onSuccess { return it }
    }
    if (text.startsWith("[") && text.endsWith("]")) {
        runCatching { JSONArray(text) }.onSuccess { return it }
    }
    return text
}

internal fun thinkingEditorValueText(value: Any?): String =
    when (value) {
        null -> ""
        JSONObject.NULL -> "null"
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }
