package com.ai.assistance.operit.data.storage

import android.content.Context
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class ConfigurationResourceKind {
    CHARACTER_CARD,
    MODEL_CONFIG,
    CHARACTER_ASSETS,
}

data class ConfigurationResourceEntry(
    val id: String,
    val kind: ConfigurationResourceKind,
    val name: String,
    val subtitle: String,
    val bytes: Long,
    val boundCount: Int,
    val inUse: Boolean,
    val locked: Boolean,
)

data class ConfigurationResourceSnapshot(
    val cards: List<ConfigurationResourceEntry>,
    val configs: List<ConfigurationResourceEntry>,
    val assets: List<ConfigurationResourceEntry>,
    val totalBytes: Long,
    val scannedAtMillis: Long,
)

class ConfigurationResourceInventory(context: Context) {
    private val appContext = context.applicationContext
    private val characterCardManager = CharacterCardManager.getInstance(appContext)
    private val modelConfigManager = ModelConfigManager(appContext)
    private val functionalConfigManager = FunctionalConfigManager(appContext)
    private val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
    private val storageRepository = DataStorageRepository(appContext)
    private val cleaner = SafeDirectoryCleaner()

    suspend fun load(): ConfigurationResourceSnapshot = withContext(Dispatchers.IO) {
        val cards = characterCardManager.getAllCharacterCards()
        val chats = chatHistoryManager.chatHistoriesFlow.first()
        val currentChatId = chatHistoryManager.currentChatIdFlow.first()
        val currentCardName = chats.firstOrNull { it.id == currentChatId }?.characterCardName
        val functionMapping = functionalConfigManager.functionConfigMappingFlow.first()
        val configSummaries = modelConfigManager.getAllConfigSummaries()
        val emojiRoot = File(appContext.filesDir, "custom_emoji")

        val cardEntries = cards.map { card ->
            val bound = chats.count { it.characterCardName == card.name }
            val assetDir = File(emojiRoot, "character_card_${card.id}")
            val bytes = estimateTextBytes(
                (card.characterSetting.length + card.description.length + card.openingStatement.length).toLong(),
            ) + assetDir.computeStorageStats().bytes
            ConfigurationResourceEntry(
                id = "card:${card.id}",
                kind = ConfigurationResourceKind.CHARACTER_CARD,
                name = card.name,
                subtitle = card.description,
                bytes = bytes,
                boundCount = bound,
                inUse = currentCardName == card.name,
                locked = card.id == CharacterCardManager.DEFAULT_CHARACTER_CARD_ID || card.isDefault,
            )
        }

        val configEntries = configSummaries.map { config ->
            val inUse = functionMapping.values.contains(config.id)
            ConfigurationResourceEntry(
                id = "config:${config.id}",
                kind = ConfigurationResourceKind.MODEL_CONFIG,
                name = config.name,
                subtitle = listOfNotNull(
                    config.apiProviderType.takeIf { it != ApiProviderType.OTHER }?.name,
                    config.modelName.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                bytes = estimateTextBytes((config.name.length + config.modelName.length).toLong()),
                boundCount = functionMapping.values.count { it == config.id },
                inUse = inUse,
                locked = config.id == ModelConfigManager.DEFAULT_CONFIG_ID || inUse,
            )
        }

        val assetEntries = listOf(
            File(appContext.filesDir, "custom_emoji"),
        ).filter { it.exists() }.map { directory ->
            val stats = directory.computeStorageStats()
            ConfigurationResourceEntry(
                id = "asset:${directory.canonicalOrAbsolute()}",
                kind = ConfigurationResourceKind.CHARACTER_ASSETS,
                name = directory.name,
                subtitle = directory.absolutePath,
                bytes = stats.bytes,
                boundCount = 0,
                inUse = false,
                locked = false,
            )
        }

        ConfigurationResourceSnapshot(
            cards = cardEntries,
            configs = configEntries,
            assets = assetEntries,
            totalBytes = cardEntries.sumOf { it.bytes } + configEntries.sumOf { it.bytes } + assetEntries.sumOf { it.bytes },
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(entries: List<ConfigurationResourceEntry>, onProgress: (String, Int, Int, Long) -> Unit): StorageDeleteBatchResult {
        var released = 0L
        var deleted = 0
        var failed = 0
        entries.forEachIndexed { index, entry ->
            onProgress(entry.name, index, entries.size, released)
            if (entry.locked) {
                failed++
                return@forEachIndexed
            }
            val ok = runCatching {
                when (entry.kind) {
                    ConfigurationResourceKind.CHARACTER_CARD -> {
                        characterCardManager.deleteCharacterCard(entry.id.removePrefix("card:"))
                        true
                    }
                    ConfigurationResourceKind.MODEL_CONFIG -> {
                        modelConfigManager.deleteConfig(entry.id.removePrefix("config:")).let { true }
                    }
                    ConfigurationResourceKind.CHARACTER_ASSETS -> {
                        val path = File(entry.subtitle.ifBlank { entry.name })
                        cleaner.cleanDirectory(path).failedEntryCount == 0 || !path.exists()
                    }
                }
            }.getOrDefault(false)
            if (ok) {
                deleted++
                released += entry.bytes
            } else {
                failed++
            }
        }
        storageRepository.invalidateCache()
        return StorageDeleteBatchResult(deleted, failed, released)
    }
}
