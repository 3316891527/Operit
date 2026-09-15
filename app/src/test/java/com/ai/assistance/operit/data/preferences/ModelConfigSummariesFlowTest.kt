package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigGroup
import com.ai.assistance.operit.data.model.ModelConfigSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelConfigSummariesFlowTest {

    @Test
    fun `active config summaries subscription receives created and updated models`() = runBlocking {
        val preferences = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = TestPreferencesDataStore(preferences)
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val firstConfigId = "first-config"
        val thinkingConfigurations = """[{"id":"think-1","label":"Low"}]"""
        val firstConfig =
            ModelConfigData(
                id = firstConfigId,
                name = "first",
                modelName = "model-a",
                apiProviderType = ApiProviderType.OPENAI_GENERIC,
                thinkingConfigurations = thinkingConfigurations,
                thinkingOptionId = "think-1",
            )
        dataStore.updateData { current ->
            current.toMutablePreferences().apply {
                set(stringPreferencesKey("config_$firstConfigId"), json.encodeToString(firstConfig))
                set(ModelConfigManager.CONFIG_LIST_KEY, json.encodeToString(listOf(firstConfigId)))
            }
        }

        val context = Mockito.mock(Context::class.java)
        val manager = ModelConfigManager(context, dataStore)
        val emissions = Channel<List<ModelConfigSummary>>(Channel.UNLIMITED)
        val collector = launch {
            manager.configSummariesFlow.take(3).collect { emissions.send(it) }
        }

        val initialSummaries = emissions.receive()
        assertEquals(listOf(firstConfigId), initialSummaries.map { it.id })
        assertEquals("model-a", initialSummaries.single().modelName)
        assertEquals(thinkingConfigurations, initialSummaries.single().thinkingConfigurations)
        assertEquals("think-1", initialSummaries.single().thinkingOptionId)

        val secondConfigId = manager.createConfig("second")
        val afterCreateSummaries = emissions.receive()
        assertEquals(listOf(firstConfigId, secondConfigId), afterCreateSummaries.map { it.id })

        manager.updateConfigBase(firstConfigId, "renamed")
        val afterUpdateSummaries = emissions.receive()
        assertEquals("renamed", afterUpdateSummaries.first().name)
        assertEquals("second", afterUpdateSummaries.last().name)
        assertEquals(thinkingConfigurations, afterUpdateSummaries.first().thinkingConfigurations)
        assertEquals("think-1", afterUpdateSummaries.first().thinkingOptionId)
        collector.join()
    }

    @Test
    fun `legacy imports are committed once and remain visible in group summaries`() = runBlocking {
        val preferences = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = TestPreferencesDataStore(preferences)
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val validGroup = ModelConfigGroup(id = "group-a", name = "Group A")
        val existingConfig =
            ModelConfigData(id = "existing", name = "Existing", groupId = validGroup.id)

        dataStore.updateData { current ->
            current.toMutablePreferences().apply {
                set(ModelConfigManager.CONFIG_GROUPS_KEY, json.encodeToString(listOf(validGroup)))
                set(
                    stringPreferencesKey("config_${existingConfig.id}"),
                    json.encodeToString(existingConfig),
                )
                set(
                    ModelConfigManager.CONFIG_LIST_KEY,
                    json.encodeToString(listOf(existingConfig.id)),
                )
            }
        }

        val context = Mockito.mock(Context::class.java)
        val manager = ModelConfigManager(context, dataStore)
        val updatesBeforeImport = dataStore.updateCount
        val legacyPayload =
            """
            [
              {"id":"legacy","name":"Legacy"},
              {"id":"blank","name":"Blank","groupId":"   "},
              {"id":"orphan","name":"Orphan","groupId":"missing-group"},
              {"id":"grouped","name":"Grouped","groupId":"group-a"},
              {"id":"existing","name":"Existing updated"}
            ]
            """.trimIndent()

        assertEquals(Triple(4, 1, 0), manager.importConfigs(legacyPayload))
        assertEquals(updatesBeforeImport + 1, dataStore.updateCount)

        val summaries = manager.configSummariesFlow.first()
        assertEquals(
            listOf("existing", "legacy", "blank", "orphan", "grouped"),
            summaries.map { it.id },
        )
        assertEquals(
            listOf(null, null, null, null, validGroup.id),
            summaries.map { it.groupId },
        )
        assertEquals("Existing updated", summaries.first().name)
        assertEquals(null, manager.getModelConfig("existing")?.groupId)
        assertEquals(null, manager.getModelConfig("orphan")?.groupId)
    }

    private class TestPreferencesDataStore(
        private val preferences: MutableStateFlow<Preferences>
    ) : DataStore<Preferences> {
        var updateCount: Int = 0
            private set

        override val data = preferences.asStateFlow()

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            val updated = transform(preferences.value)
            updateCount++
            preferences.value = updated
            return updated
        }
    }
}
