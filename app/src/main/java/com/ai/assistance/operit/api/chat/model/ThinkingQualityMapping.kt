package com.ai.assistance.operit.api.chat.model

import android.content.Context
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.data.config.model.ModelParameter
import com.ai.assistance.operit.data.config.model.ParameterCategory
import com.ai.assistance.operit.data.config.model.ParameterValueType
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class ThinkingQualityControl { LEVELS, TOGGLE_ONLY, UNSUPPORTED }

internal sealed interface ThinkingQualityWireValue {
    data class Text(val value: String) : ThinkingQualityWireValue
    data class Number(val value: Int) : ThinkingQualityWireValue
    data object Omitted : ThinkingQualityWireValue
}

internal data class ThinkingQualityOption(
    val id: String,
    val displayLabel: String,
    val wireValue: ThinkingQualityWireValue,
    val actions: List<ThinkingQualityJsonAction> = emptyList(),
)

internal data class ThinkingQualityJsonAction(
    val path: String,
    val value: Any?,
    val overwrite: Boolean = false,
)

internal data class ResolvedThinkingRequest(
    val enableThinking: Boolean,
    val thinkingOptionId: String,
)

internal data class ThinkingRuleMatch(
    val index: Int,
    val id: String,
    val mapping: ThinkingQualityMapping,
)

internal data class ThinkingQualityMapping(
    val control: ThinkingQualityControl,
    val parameterLabel: String,
    val options: List<ThinkingQualityOption>,
    val reasoningRequired: Boolean = false,
    val disabledValue: String? = null,
    val enabledActions: List<ThinkingQualityJsonAction> = emptyList(),
    val disabledActions: List<ThinkingQualityJsonAction> = emptyList(),
) {
    companion object {
        fun toggleOnly(parameterLabel: String, reasoningRequired: Boolean = false): ThinkingQualityMapping =
            ThinkingQualityMapping(ThinkingQualityControl.TOGGLE_ONLY, parameterLabel, emptyList(), reasoningRequired)

        fun unsupported(): ThinkingQualityMapping = ThinkingQualityMapping(ThinkingQualityControl.UNSUPPORTED, "", emptyList())
    }

    fun optionFor(id: String): ThinkingQualityOption? = options.firstOrNull { it.id == id }
    fun textValueFor(id: String): String? = (optionFor(id)?.wireValue as? ThinkingQualityWireValue.Text)?.value
    fun numberValueFor(id: String): Int? = (optionFor(id)?.wireValue as? ThinkingQualityWireValue.Number)?.value

    fun resolveOptionId(preferred: String, fallback: String = ""): String {
        if (control != ThinkingQualityControl.LEVELS) return ""
        require(options.isNotEmpty()) { "Thinking level configuration has no options" }
        if (preferred.isNotBlank() && optionFor(preferred) != null) return preferred
        if (fallback.isNotBlank() && optionFor(fallback) != null) return fallback
        return options.first().id
    }

    // Tests, functional calls and providers share the same selection contract. An empty
    // preference deliberately selects the first configured level; values are never sorted.
    fun resolveRequest(
        enableThinking: Boolean,
        preferredOptionId: String = "",
        configuredOptionId: String = "",
    ): ResolvedThinkingRequest {
        // UNSUPPORTED means there are no JSON controls. Native providers still need the
        // requested flag for their chat templates; suppressing it breaks functional calls.
        val enabled = enableThinking || reasoningRequired
        return ResolvedThinkingRequest(
            enableThinking = enabled,
            thinkingOptionId = if (enabled) resolveOptionId(preferredOptionId, configuredOptionId) else "",
        )
    }
}

internal object ThinkingQualityMappingRegistry {
    fun resolve(providerTypeId: String, modelName: String): ThinkingQualityMapping =
        resolve(providerTypeId, modelName, "", "")

    fun resolve(
        providerTypeId: String,
        modelName: String,
        thinkingConfigurations: String
    ): ThinkingQualityMapping =
        resolve(providerTypeId, modelName, "", thinkingConfigurations)

    fun resolve(
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String
    ): ThinkingQualityMapping {
        // The JSON array order is the user-visible priority order: the first enabled rule
        // matching provider, model, and endpoint wins, and later rules are not evaluated.
        return resolveMatch(providerTypeId, modelName, apiEndpoint, thinkingConfigurations)
            ?.mapping ?: ThinkingQualityMapping.unsupported()
    }

    fun resolveMatch(
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String,
    ): ThinkingRuleMatch? {
        val rules = rulesArray(thinkingConfigurations)
        for (index in 0 until rules.length()) {
            val rule = ThinkingConfigurationRule.fromJson(rules.getJSONObject(index))
            if (rule.enabled && rule.matches(providerTypeId, modelName, apiEndpoint)) {
                return ThinkingRuleMatch(index, rule.id, rule.toMapping())
            }
        }
        return null
    }

    suspend fun resolveForModel(
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String = ""
    ): ThinkingQualityMapping {
        return resolve(providerTypeId, modelName, apiEndpoint, thinkingConfigurations)
    }

    fun validateConfigurations(thinkingConfigurations: String) {
        val rules = rulesArray(thinkingConfigurations)
        for (index in 0 until rules.length()) {
            val json = rules.getJSONObject(index)
            val rule = ThinkingConfigurationRule.fromJson(json)
            if (!rule.enabled) continue
            val name = rule.id.ifBlank { (index + 1).toString() }
            require(json.optString("control", "unsupported").trim().lowercase(Locale.US) in setOf("levels", "toggle", "toggle_only", "unsupported")) {
                "Thinking rule $name: unsupported control"
            }
            for (key in listOf("modelRegex", "lastSegmentRegex")) {
                ((json.optJSONObject("match") ?: JSONObject()).stringList(key) + json.stringList(key))
                    .forEach { Regex(it, RegexOption.IGNORE_CASE) }
            }
            if (rule.control == ThinkingQualityControl.UNSUPPORTED) continue
            listOf("enable", "enabledActions", "on", "disable", "disabledActions", "off").forEach { key ->
                validateActions(json.opt(key), name)
            }
            json.optJSONArray("options")?.let { options ->
                for (optionIndex in 0 until options.length()) {
                    validateActions(options.getJSONObject(optionIndex).opt("actions"), name)
                }
            }
            val actions = rule.enabledActions + rule.disabledActions + rule.options.flatMap { it.actions }
            actions.forEach { action ->
                require(action.path.split('.').all { it.isNotBlank() }) {
                    "Thinking rule $name: invalid parameter path ${action.path}"
                }
            }
            if (rule.control == ThinkingQualityControl.LEVELS) {
                val options = json.optJSONArray("options")
                require(options != null && options.length() > 0 && options.length() == rule.options.size) {
                    "Thinking rule $name: levels require non-empty options with valid IDs"
                }
                require(rule.options.map { it.id }.distinct().size == rule.options.size) {
                    "Thinking rule $name: duplicate option ID"
                }
                for (optionIndex in 0 until options.length()) {
                    val option = options.getJSONObject(optionIndex)
                    val path = option.optString("path", rule.parameterLabel).trim()
                    require(!option.has("value") || option.isNull("value") || path.isNotEmpty() || rule.options[optionIndex].actions.isNotEmpty()) {
                        "Thinking rule $name: an option value needs a parameter path or actions"
                    }
                }
            }
        }
    }

    private fun validateActions(value: Any?, rule: String) {
        if (value == null || value == JSONObject.NULL) return
        val actions = when (value) {
            is JSONObject -> listOf(value)
            is JSONArray -> List(value.length()) { value.getJSONObject(it) }
            else -> throw IllegalArgumentException("Thinking rule $rule: actions must be objects")
        }
        actions.forEach { action ->
            val path = action.optString("path", "").trim()
            require(path.isNotEmpty() && path.split('.').all { it.isNotBlank() } && action.has("value")) {
                "Thinking rule $rule: each action needs a valid path and a value"
            }
        }
    }

    fun formatConfigurations(thinkingConfigurations: String): String {
        val text = normalizedJsonText(thinkingConfigurations)
        return when {
            text.startsWith("[") -> JSONArray(text).toString(2)
            text.startsWith("{") -> JSONObject(text).toString(2)
            else -> JSONArray(text).toString(2)
        }
    }

    private fun rulesArray(thinkingConfigurations: String): JSONArray {
        val text = normalizedJsonText(thinkingConfigurations)
        return when {
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> {
                val objectValue = JSONObject(text)
                objectValue.optJSONArray("rules") ?: JSONArray().put(objectValue)
            }
            else -> JSONArray(text)
        }
    }

    private fun normalizedJsonText(thinkingConfigurations: String): String =
        thinkingConfigurations.trim().ifEmpty { "[]" }
}

private data class ThinkingConfigurationRule(
    val id: String,
    val enabled: Boolean,
    val providerIds: List<String>,
    val matcher: ThinkingModelMatcher,
    val control: ThinkingQualityControl,
    val parameterLabel: String,
    val reasoningRequired: Boolean,
    val endpointSuffixes: List<String>,
    val disabledValue: String?,
    val enabledActions: List<ThinkingQualityJsonAction>,
    val disabledActions: List<ThinkingQualityJsonAction>,
    val options: List<ThinkingQualityOption>,
) {
    fun matches(providerTypeId: String, modelName: String, apiEndpoint: String): Boolean {
        val provider = providerTypeId.trim().uppercase(Locale.US)
        val providerMatches =
            providerIds.isEmpty() || providerIds.any { it.equals(provider, ignoreCase = true) }
        return providerMatches && matcher.matches(modelName) && endpointMatches(apiEndpoint)
    }

    private fun endpointMatches(apiEndpoint: String): Boolean {
        if (endpointSuffixes.isEmpty()) return true

        val endpoint = normalizedEndpoint(apiEndpoint)
        if (endpoint.isEmpty()) return false

        return endpointSuffixes.any { suffix ->
            val normalizedSuffix = normalizedEndpoint(suffix)
            normalizedSuffix.isNotEmpty() && endpoint.endsWith(normalizedSuffix)
        }
    }

    private fun normalizedEndpoint(value: String): String =
        value.trim()
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .lowercase(Locale.US)

    fun toMapping(): ThinkingQualityMapping =
        ThinkingQualityMapping(
            control = control,
            parameterLabel = parameterLabel,
            options = options,
            reasoningRequired = reasoningRequired,
            disabledValue = disabledValue,
            enabledActions = enabledActions,
            disabledActions = disabledActions,
        )

    companion object {
        fun fromJson(json: JSONObject): ThinkingConfigurationRule {
            val parameterLabel = json.optString("parameterLabel", json.optString("label", "")).trim()
            val enabledActions = json.actionList("enable", "enabledActions", "on")
            val disabledActions = json.actionList("disable", "disabledActions", "off")
            val options = json.optionList(parameterLabel)
            val disabledValue = json.optString("disabledValue", "").trim().ifEmpty {
                disabledActions.firstOrNull { it.path == parameterLabel }?.value as? String ?: ""
            }.ifEmpty { null }

            return ThinkingConfigurationRule(
                id = json.optString("id", ""),
                enabled = json.optBoolean("enabled", true),
                providerIds = json.stringList("providers") + json.stringList("providerTypeIds"),
                matcher = ThinkingModelMatcher.fromJson(json.optJSONObject("match") ?: JSONObject(), json),
                control = when (json.optString("control", "unsupported").trim().lowercase(Locale.US)) {
                    "levels" -> ThinkingQualityControl.LEVELS
                    "toggle_only", "toggle" -> ThinkingQualityControl.TOGGLE_ONLY
                    else -> ThinkingQualityControl.UNSUPPORTED
                },
                parameterLabel = parameterLabel,
                reasoningRequired = json.optBoolean("required", json.optBoolean("reasoningRequired", false)),
                endpointSuffixes = (json.optJSONObject("match") ?: JSONObject()).stringList("endpointSuffix") +
                    json.stringList("endpointSuffix"),
                disabledValue = disabledValue,
                enabledActions = enabledActions,
                disabledActions = disabledActions,
                options = options,
            )
        }
    }
}

private data class ThinkingModelMatcher(
    val modelPrefix: List<String>,
    val modelContains: List<String>,
    val modelSuffix: List<String>,
    val modelRegex: List<String>,
    val firstSegment: List<String>,
    val lastSegmentPrefix: List<String>,
    val lastSegmentContains: List<String>,
    val lastSegmentRegex: List<String>,
) {
    fun matches(modelName: String): Boolean {
        if (isEmpty()) return true

        val model = modelName.trim().lowercase(Locale.US)
        val segments = model.split('/').filter(String::isNotEmpty)
        val first = segments.firstOrNull().orEmpty()
        val last = segments.lastOrNull() ?: model

        return modelPrefix.any { model.startsWith(it) } ||
            modelContains.any { model.contains(it) } ||
            modelSuffix.any { model.endsWith(it) } ||
            modelRegex.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(modelName) } ||
            firstSegment.any { first == it } ||
            lastSegmentPrefix.any { last.startsWith(it) } ||
            lastSegmentContains.any { last.contains(it) } ||
            lastSegmentRegex.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(last) }
    }

    private fun isEmpty(): Boolean =
        modelPrefix.isEmpty() &&
            modelContains.isEmpty() &&
            modelSuffix.isEmpty() &&
            modelRegex.isEmpty() &&
            firstSegment.isEmpty() &&
            lastSegmentPrefix.isEmpty() &&
            lastSegmentContains.isEmpty() &&
            lastSegmentRegex.isEmpty()

    companion object {
        fun fromJson(match: JSONObject, root: JSONObject): ThinkingModelMatcher =
            ThinkingModelMatcher(
                modelPrefix = match.stringList("modelPrefix") + root.stringList("modelPrefix"),
                modelContains = match.stringList("modelContains") + root.stringList("modelContains"),
                modelSuffix = match.stringList("modelSuffix") + root.stringList("modelSuffix"),
                modelRegex = match.stringList("modelRegex") + root.stringList("modelRegex"),
                firstSegment = match.stringList("firstSegment") + root.stringList("firstSegment"),
                lastSegmentPrefix = match.stringList("lastSegmentPrefix") + root.stringList("lastSegmentPrefix"),
                lastSegmentContains = match.stringList("lastSegmentContains") + root.stringList("lastSegmentContains"),
                lastSegmentRegex = match.stringList("lastSegmentRegex") + root.stringList("lastSegmentRegex"),
            )
    }
}

internal object ThinkingConfigurationApplier {
    fun apply(
        context: Context,
        requestJson: JSONObject,
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String,
        enableThinking: Boolean,
        // The selected option belongs to the model configuration; never read a global preference here.
        optionId: String,
    ) {
        apply(
            requestJson = requestJson,
            providerTypeId = providerTypeId,
            modelName = modelName,
            apiEndpoint = apiEndpoint,
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = optionId,
        )
    }

    fun apply(
        requestJson: JSONObject,
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String,
        enableThinking: Boolean,
        optionId: String,
    ): ThinkingQualityMapping {
        val mapping = ThinkingQualityMappingRegistry.resolve(
            providerTypeId,
            modelName,
            apiEndpoint,
            thinkingConfigurations
        )
        if (mapping.control == ThinkingQualityControl.UNSUPPORTED) return mapping

        val thinking = mapping.resolveRequest(enableThinking, optionId)
        val modeActions = if (thinking.enableThinking) mapping.enabledActions else mapping.disabledActions
        modeActions.forEach { applyAction(requestJson, it) }

        if (thinking.enableThinking && mapping.control == ThinkingQualityControl.LEVELS) {
            val selected = requireNotNull(mapping.optionFor(thinking.thinkingOptionId))
            selected.actions.forEach { applyAction(requestJson, it) }
        }
        return mapping
    }

    fun modelParameters(
        providerTypeId: String,
        modelName: String,
        apiEndpoint: String,
        thinkingConfigurations: String,
        enableThinking: Boolean,
        optionId: String,
        protocol: ApiProviderType,
    ): Pair<ThinkingQualityMapping, List<ModelParameter<*>>> {
        val requestJson = JSONObject()
        val mapping = apply(
            requestJson = requestJson,
            providerTypeId = providerTypeId,
            modelName = modelName,
            apiEndpoint = apiEndpoint,
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = optionId,
        )
        return mapping to requestJson.toModelParameters(protocol)
    }

    private fun applyAction(root: JSONObject, action: ThinkingQualityJsonAction) {
        val path = action.path.trim()
        if (path.isEmpty()) return
        if (!action.overwrite && root.hasJsonPath(path)) return
        root.putJsonPath(path, action.value, action.overwrite)
    }
}

private fun JSONObject.optionList(parameterLabel: String): List<ThinkingQualityOption> {
    val array = optJSONArray("options") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val option = array.optJSONObject(index) ?: continue
            val value = option.jsonValue("value")
            val id = option.optString("id", value?.toString().orEmpty()).trim()
            if (id.isEmpty()) continue
            val label = option.optString("label", id).trim()
            val path = option.optString("path", parameterLabel).trim()
            val wireValue = when (value) {
                is Number -> ThinkingQualityWireValue.Number(value.toInt())
                is String -> ThinkingQualityWireValue.Text(value)
                else -> ThinkingQualityWireValue.Omitted
            }
            val directAction =
                if (path.isNotEmpty() && value != null) {
                    listOf(
                        ThinkingQualityJsonAction(
                            path = path,
                            value = value,
                            overwrite = option.optBoolean("overwrite", false),
                        )
                    )
                } else {
                    emptyList()
                }
            add(
                ThinkingQualityOption(
                    id = id,
                    displayLabel = label,
                    wireValue = wireValue,
                    actions = directAction + option.actionList("actions"),
                )
            )
        }
    }
}

private fun JSONObject.actionList(vararg keys: String): List<ThinkingQualityJsonAction> =
    keys.flatMap { key -> actionsFromValue(opt(key)) }

private fun actionsFromValue(value: Any?): List<ThinkingQualityJsonAction> {
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                val item = value.optJSONObject(index) ?: continue
                item.toAction()?.let(::add)
            }
        }
        is JSONObject -> listOfNotNull(value.toAction())
        else -> emptyList()
    }
}

private fun JSONObject.toAction(): ThinkingQualityJsonAction? {
    val path = optString("path", "").trim()
    if (path.isEmpty()) return null
    return ThinkingQualityJsonAction(
        path = path,
        value = jsonValue("value"),
        overwrite = optBoolean("overwrite", false),
    )
}

private fun JSONObject.stringList(key: String): List<String> {
    val value = opt(key)
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                value.optString(index, "").trim().lowercase(Locale.US).takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        is String -> value.trim().lowercase(Locale.US).takeIf(String::isNotEmpty)?.let(::listOf).orEmpty()
        else -> emptyList()
    }
}

private fun JSONObject.jsonValue(key: String): Any? {
    if (!has(key) || isNull(key)) return null
    return opt(key)
}

private fun JSONObject.hasJsonPath(path: String): Boolean {
    val parts = path.split('.').filter(String::isNotEmpty)
    if (parts.isEmpty()) return false
    var current: JSONObject = this
    for (segment in parts.dropLast(1)) {
        val next = current.opt(segment)
        if (next !is JSONObject) return false
        current = next
    }
    return current.has(parts.last())
}

private fun JSONObject.putJsonPath(path: String, value: Any?, overwrite: Boolean) {
    val parts = path.split('.').filter(String::isNotEmpty)
    if (parts.isEmpty()) return

    var current: JSONObject = this
    for (segment in parts.dropLast(1)) {
        val next = current.opt(segment)
        current = when {
            next is JSONObject -> next
            current.has(segment) && !overwrite -> return
            else -> JSONObject().also { current.put(segment, it) }
        }
    }
    current.put(parts.last(), value.cloneJsonValue())
}

private fun Any?.cloneJsonValue(): Any =
    when (this) {
        null -> JSONObject.NULL
        is JSONObject -> JSONObject(toString())
        is JSONArray -> JSONArray(toString())
        else -> this
    }

private fun JSONObject.toModelParameters(protocol: ApiProviderType): List<ModelParameter<*>> {
    val result = mutableListOf<ModelParameter<*>>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = opt(key)
        val category =
            if (protocol.isGeminiProtocol() && key == "thinkingConfig") {
                ParameterCategory.GENERATION
            } else {
                ParameterCategory.OTHER
            }
        when (value) {
            is JSONObject, is JSONArray -> result += thinkingObjectParameter(key, value.toString(), category)
            is Boolean -> result += thinkingBooleanParameter(key, value, category)
            is Number -> {
                val number = value.toDouble()
                if (number % 1.0 == 0.0) {
                    result += thinkingIntParameter(key, value.toInt(), category)
                } else {
                    result += thinkingFloatParameter(key, value.toFloat(), category)
                }
            }
            is String -> result += thinkingStringParameter(key, value, category)
        }
    }
    return result
}

private fun ApiProviderType.isGeminiProtocol(): Boolean =
    this == ApiProviderType.GOOGLE || this == ApiProviderType.GEMINI_GENERIC

private fun thinkingStringParameter(
    apiName: String,
    value: String,
    category: ParameterCategory
): ModelParameter<String> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.STRING,
        category = category,
        isCustom = false,
    )

private fun thinkingIntParameter(
    apiName: String,
    value: Int,
    category: ParameterCategory
): ModelParameter<Int> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.INT,
        category = category,
        isCustom = false,
    )

private fun thinkingFloatParameter(
    apiName: String,
    value: Float,
    category: ParameterCategory
): ModelParameter<Float> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.FLOAT,
        category = category,
        isCustom = false,
    )

private fun thinkingBooleanParameter(
    apiName: String,
    value: Boolean,
    category: ParameterCategory
): ModelParameter<Boolean> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.BOOLEAN,
        category = category,
        isCustom = false,
    )

private fun thinkingObjectParameter(
    apiName: String,
    value: String,
    category: ParameterCategory
): ModelParameter<String> =
    ModelParameter(
        id = "thinking-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.OBJECT,
        category = category,
        isCustom = false,
    )
