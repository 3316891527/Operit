package com.ai.assistance.operit.ui.features.management.settings.model

import com.ai.assistance.operit.api.chat.model.ThinkingQualityMappingRegistry
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingRuleEditorTest {
    @Test
    fun everyBuiltinRuleSurvivesOpeningAndSavingTheEditor() {
        val source = ModelThinkingConfigDefaults.DEFAULT_JSON
        val saved = serializeThinkingRuleEditors(parseThinkingRuleEditors(source))
        assertEquals(tree(JSONArray(source)), tree(JSONArray(saved)))
        ThinkingQualityMappingRegistry.validateConfigurations(saved)
    }

    @Test
    fun editingOneFieldPreservesRequiredCompoundMatchesActionsAndUnknownFields() {
        val rule = parseThinkingRuleEditors(complexRules).single()
        val saved = rule.copy(enabled = false, matchValues = "changed-model").toJson()
        assertFalse(saved.getBoolean("enabled"))
        assertTrue(saved.getBoolean("required"))
        assertEquals("/responses", saved.getJSONObject("match").getString("endpointSuffix"))
        assertEquals("extra", saved.getJSONObject("match").getString("modelSuffix"))
        assertEquals("changed-model", saved.getJSONObject("match").getJSONArray("modelContains").getString(0))
        assertTrue(saved.getJSONArray("enable").getJSONObject(0).getBoolean("overwrite"))
        assertEquals("kept", saved.getString("extension"))
        assertEquals(tree(JSONArray(complexRules).getJSONObject(0).getJSONArray("options")), tree(saved.getJSONArray("options")))
    }

    @Test
    fun editingActionAndOptionValuesPreservesTheirAdvancedFields() {
        val rule = parseThinkingRuleEditors(complexRules).single()
        val saved = rule.copy(
            enabledActions = listOf(rule.enabledActions.single().copy(value = "false")),
            options = listOf(rule.options.single().copy(label = "Renamed")),
        ).toJson()
        val action = saved.getJSONArray("enable").getJSONObject(0)
        assertFalse(action.getBoolean("value"))
        assertTrue(action.getBoolean("overwrite"))
        val option = saved.getJSONArray("options").getJSONObject(0)
        assertEquals("Renamed", option.getString("label"))
        assertTrue(option.getBoolean("overwrite"))
        assertEquals("bool-as-text", option.getJSONArray("actions").getJSONObject(0).getString("path"))
    }

    @Test
    fun unsupportedRuleAndWrapperMetadataArePreserved() {
        val source = """{"revision":7,"rules":[{"id":"off","control":"unsupported","match":{"modelPrefix":["gpt-4"]},"custom":true}]}"""
        val rules = parseThinkingRuleEditors(source)
        assertEquals("unsupported", rules.single().control)
        assertEquals(tree(JSONObject(source)), tree(JSONObject(serializeThinkingRuleEditors(rules, source))))
    }

    @Test
    fun duplicateHasANewIdAndRetainsTheCompleteRule() {
        val original = parseThinkingRuleEditors(complexRules).single()
        val copy = original.duplicate()
        assertNotEquals(original.id, copy.id)
        val left = original.toJson().apply { remove("id") }
        val right = copy.toJson().apply { remove("id") }
        assertEquals(tree(left), tree(right))
        assertEquals("complex", original.id)
    }

    @Test
    fun unchangedStringValuesDoNotBecomeBooleansOrNumbersOnSave() {
        val source = """[{"id":"types","control":"toggle_only","enable":[{"path":"flag","value":"false"},{"path":"count","value":"123"}]}]"""
        val rules = parseThinkingRuleEditors(source)
        assertEquals(tree(JSONArray(source)), tree(JSONArray(serializeThinkingRuleEditors(rules))))
    }

    @Test
    fun editingAMatchTypeCannotOverwriteAnAdditionalCondition() {
        val rule = parseThinkingRuleEditors(complexRules).single()
        assertThrows(IllegalArgumentException::class.java) {
            rule.copy(matchField = "modelSuffix", matchValues = "replacement").toJson()
        }
        assertEquals("extra", rule.toJson().getJSONObject("match").getString("modelSuffix"))
    }

    private fun tree(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { tree(value.get(it)) }
        is JSONArray -> List(value.length()) { tree(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }

    private val complexRules = """[{
        "id":"complex", "control":"levels", "required":true, "parameterLabel":"effort", "extension":"kept",
        "match":{"modelContains":["model"],"modelSuffix":"extra","endpointSuffix":"/responses"},
        "enable":[{"path":"thinking","value":true,"overwrite":true}],
        "options":[{"id":"custom-low","label":"Custom low","path":"effort","value":"small","overwrite":true,
            "actions":[{"path":"bool-as-text","value":"false","overwrite":true}]}]
    }]"""
}
