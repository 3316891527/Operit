package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.SubagentConfiguration
import com.ai.assistance.operit.core.agent.SubagentLimits
import com.ai.assistance.operit.core.agent.SubagentModel
import com.ai.assistance.operit.core.agent.SubagentPreset
import com.ai.assistance.operit.core.agent.validate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.subagentDataStore by versionedPreferencesDataStore(
    name = "subagent_configuration",
    currentVersion = 1,
) {
    preferenceSchemaMigration { version, _ ->
        if (version != 0) missingPreferencesSchemaMigration(version)
    }
}

/** 全局配置独立于主聊天的单选功能映射，所有修改在 DataStore 事务中合并。 */
class SubagentPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.subagentDataStore
    private val key = stringPreferencesKey("configuration")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun read(preferences: Preferences): SubagentConfiguration {
        val raw = preferences[key]
        if (raw != null) return json.decodeFromString<SubagentConfiguration>(raw).also { it.limits.validate() }
        return SubagentConfiguration(
            presets = listOf(SubagentPreset(
                id = "general",
                name = appContext.getString(R.string.subagent_default_name),
                description = appContext.getString(R.string.subagent_default_description),
                systemPrompt = appContext.getString(R.string.subagent_default_prompt),
            )),
        )
    }

    val configuration: Flow<SubagentConfiguration> = store.data.map(::read)

    suspend fun savePreset(preset: SubagentPreset) {
        preset.validate()
        store.edit { preferences ->
            val current = read(preferences)
            val exists = current.presets.any { it.id == preset.id }
            require(exists || current.presets.size < 32) { "Too many subagent presets" }
            val next = if (exists) current.presets.map { if (it.id == preset.id) preset else it }
                else current.presets + preset
            preferences[key] = json.encodeToString(current.copy(presets = next))
        }
    }

    suspend fun deletePreset(id: String) {
        store.edit { preferences ->
            val current = read(preferences)
            preferences[key] = json.encodeToString(current.copy(presets = current.presets.filterNot { it.id == id }))
        }
    }

    suspend fun saveModel(model: SubagentModel) {
        model.validate()
        store.edit { preferences ->
            val current = read(preferences)
            require(current.models.none { it.id != model.id && it.configId == model.configId && it.modelName == model.modelName }) {
                "This model is already in the subagent pool"
            }
            val exists = current.models.any { it.id == model.id }
            require(exists || current.models.size < 64) { "Too many subagent models" }
            val next = if (exists) current.models.map { if (it.id == model.id) model else it }
                else current.models + model
            preferences[key] = json.encodeToString(current.copy(models = next))
        }
    }

    suspend fun deleteModel(id: String) {
        store.edit { preferences ->
            val current = read(preferences)
            preferences[key] = json.encodeToString(current.copy(models = current.models.filterNot { it.id == id }))
        }
    }

    suspend fun setAutoArchiveCompleted(enabled: Boolean) {
        store.edit { preferences ->
            preferences[key] = json.encodeToString(read(preferences).copy(autoArchiveCompleted = enabled))
        }
    }

    suspend fun setLimits(limits: SubagentLimits) {
        limits.validate()
        store.edit { preferences ->
            preferences[key] = json.encodeToString(read(preferences).copy(limits = limits))
        }
    }

    suspend fun clearModels() {
        store.edit { preferences ->
            preferences[key] = json.encodeToString(read(preferences).copy(models = emptyList()))
        }
    }
}
