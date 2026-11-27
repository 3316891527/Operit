package com.ai.assistance.operit.data.preferences

class ThemeDraftConflictException(val fields: Set<String>) :
    IllegalStateException("Theme draft conflicts: ${fields.joinToString()}")

/** 按原始字段值合并，资源地址、脚本和字段删除都参与比较。 */
object ThemeDraftMerge {
    fun mergeGlobalUserName(
        baseline: String,
        draft: String,
        current: Map<String, String>,
    ): Map<String, String> {
        val original = baseline.takeUnless(String::isBlank).orEmpty()
        val edited = draft.takeUnless(String::isBlank).orEmpty()
        val persisted = current["global_user_name"].orEmpty()
        if (edited == original) return current
        if (persisted != original && persisted != edited) {
            throw ThemeDraftConflictException(setOf("strings:global_user_name"))
        }
        return if (edited.isEmpty()) current - "global_user_name"
        else current + ("global_user_name" to edited)
    }

    fun merge(
        baseline: ThemePreferenceValues,
        draft: ThemePreferenceValues,
        current: ThemePreferenceValues,
    ): ThemePreferenceValues {
        val conflicts = linkedSetOf<String>()
        val merged = mergeValues(baseline, draft, current) { conflicts += it }
        if (conflicts.isNotEmpty()) throw ThemeDraftConflictException(conflicts.toSet())
        return merged
    }

    /** 保存期间新增的编辑继续作为草稿，不因持久值合并而丢失。 */
    fun rebaseAfterSave(
        submitted: ThemePreferenceValues,
        draft: ThemePreferenceValues,
        saved: ThemePreferenceValues,
    ): ThemePreferenceValues = mergeValues(submitted, draft, saved) { }

    private fun mergeValues(
        baseline: ThemePreferenceValues,
        draft: ThemePreferenceValues,
        current: ThemePreferenceValues,
        onConflict: (String) -> Unit,
    ): ThemePreferenceValues = ThemePreferenceValues(
        strings = mergeMap("strings", baseline.strings, draft.strings, current.strings, onConflict),
        booleans = mergeMap("booleans", baseline.booleans, draft.booleans, current.booleans, onConflict),
        ints = mergeMap("ints", baseline.ints, draft.ints, current.ints, onConflict),
        floats = mergeMap("floats", baseline.floats, draft.floats, current.floats, onConflict),
    )

    private fun <T> mergeMap(
        type: String,
        baseline: Map<String, T>,
        draft: Map<String, T>,
        current: Map<String, T>,
        onConflict: (String) -> Unit,
    ): Map<String, T> {
        val merged = current.toMutableMap()
        (baseline.keys + draft.keys).forEach { key ->
            val original = baseline[key]
            val edited = draft[key]
            if (edited != original) {
                val persisted = current[key]
                if (persisted != original && persisted != edited) onConflict("$type:$key")
                if (draft.containsKey(key)) {
                    merged[key] = draft.getValue(key)
                } else {
                    merged.remove(key)
                }
            }
        }
        return merged
    }
}
