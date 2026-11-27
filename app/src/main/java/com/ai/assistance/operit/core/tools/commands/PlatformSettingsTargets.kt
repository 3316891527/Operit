package com.ai.assistance.operit.core.tools.commands

import android.content.Context
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

internal data class PlatformSettingsTarget(
    val type: String,
    val id: String,
    val name: String,
    val prompt: ActivePrompt?,
)

/** 配置命令共用角色与会话归属，显式目标始终校验实际存在。 */
internal class PlatformSettingsTargets(context: Context) {
    private val context = context.applicationContext
    private val prompts = ActivePromptManager.getInstance(this.context)
    private val cards = CharacterCardManager.getInstance(this.context)
    private val groups = CharacterGroupCardManager.getInstance(this.context)

    suspend fun list(includeUser: Boolean = false): JSONArray {
        val active = prompts.getActivePrompt()
        val rows = JSONArray()
        cards.getAllCharacterCards().forEach { card ->
            rows.put(JSONObject().put("target_type", "card").put("target_id", card.id)
                .put("name", card.name).put("active", active == ActivePrompt.CharacterCard(card.id)))
        }
        groups.getAllCharacterGroupCards().forEach { group ->
            rows.put(JSONObject().put("target_type", "group").put("target_id", group.id)
                .put("name", group.name).put("active", active == ActivePrompt.CharacterGroup(group.id)))
        }
        if (includeUser) {
            rows.put(JSONObject().put("target_type", "user").put("name", "Global user profile").put("active", true))
        }
        return rows
    }

    suspend fun resolve(tool: AITool, parameters: Map<String, String>, allowUser: Boolean = false): PlatformSettingsTarget {
        val type = parameters["target_type"] ?: "current"
        val allowedTypes = if (allowUser) setOf("current", "card", "group", "user") else setOf("current", "card", "group")
        require(type in allowedTypes) {
            if (allowUser) "target_type must be current, card, group or user" else "target_type must be current, card or group"
        }
        val id = parameters["target_id"]
        if (type == "user") {
            require(id == null) { "user target does not accept target_id" }
            return PlatformSettingsTarget("user", "global", "Global user profile", null)
        }
        if (type == "card" || type == "group") {
            require(!id.isNullOrBlank() && id == id.trim()) { "Explicit card/group target requires an exact target_id" }
            return checkedTarget(if (type == "card") ActivePrompt.CharacterCard(id) else ActivePrompt.CharacterGroup(id))
        }
        require(id == null) { "current target does not accept target_id" }
        // 群聊设置属于角色组，不能把本轮发言角色的卡片 ID 当成设置目标。
        val chatId = tool.parameters.singleOrNull { it.name == "__operit_package_chat_id" }?.value?.takeIf { it.isNotBlank() }
        val cardId = tool.parameters.singleOrNull { it.name == "__operit_package_caller_card_id" }?.value?.takeIf { it.isNotBlank() }
        if (chatId != null) {
            val chat = requireNotNull(AppDatabase.getDatabase(context).chatDao().getChatById(chatId)) { "Caller conversation no longer exists; use an explicit settings target" }
            if (!chat.characterGroupId.isNullOrBlank()) return checkedTarget(ActivePrompt.CharacterGroup(chat.characterGroupId))
            if (cardId != null) return checkedTarget(ActivePrompt.CharacterCard(cardId))
            val name = chat.characterCardName
            if (!name.isNullOrBlank()) {
                val matches = cards.getAllCharacterCards().filter { it.name == name }
                require(matches.size == 1) { "Caller settings binding is missing or ambiguous; list available targets and use an explicit target" }
                return checkedTarget(ActivePrompt.CharacterCard(matches.single().id))
            }
            return checkedTarget(ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID))
        }
        if (cardId != null) return checkedTarget(ActivePrompt.CharacterCard(cardId))
        // 直接命令没有聊天归属时，current 明确定义为应用当前激活的角色或角色组。
        return checkedTarget(prompts.getActivePrompt())
    }

    private suspend fun checkedTarget(prompt: ActivePrompt): PlatformSettingsTarget = when (prompt) {
        is ActivePrompt.CharacterCard -> {
            require(prompt.id in cards.characterCardListFlow.first()) { "Unknown character card: ${prompt.id}" }
            PlatformSettingsTarget("card", prompt.id, cards.getCharacterCard(prompt.id).name, prompt)
        }
        is ActivePrompt.CharacterGroup -> {
            val group = requireNotNull(groups.getCharacterGroupCard(prompt.id)) { "Unknown character group: ${prompt.id}" }
            PlatformSettingsTarget("group", prompt.id, group.name, prompt)
        }
    }

}
