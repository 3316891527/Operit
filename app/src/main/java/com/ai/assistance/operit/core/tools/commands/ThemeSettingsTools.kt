package com.ai.assistance.operit.core.tools.commands

import android.content.Context
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ThemeSettingsResultData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject

/** 主题命令复用偏好事务和资源归属，不触发语言切换或 Activity 重建。 */
internal class ThemeSettingsTools(context: Context) {
    private val context = context.applicationContext
    private val preferences = UserPreferencesManager.getInstance(this.context)
    private val display = DisplayPreferencesManager.getInstance(this.context)
    private val prompts = ActivePromptManager.getInstance(this.context)
    private val cards = CharacterCardManager.getInstance(this.context)
    private val groups = CharacterGroupCardManager.getInstance(this.context)
    private val assets = ThemeCommandAssets(this.context)

    companion object {
        val toolNames = setOf("list_theme_targets", "describe_theme_fields", "get_theme_settings", "set_theme_settings")
    }

    private data class Target(val type: String, val id: String, val name: String, val prompt: ActivePrompt?)

    suspend fun execute(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        try {
            val declarations = ThemeCommandToolDefinitions.tools(true).single { it.name == tool.name }
                .parametersStructured.orEmpty()
            val allowed = declarations.map { it.name }.toSet() + PlatformCommandExecutor.contextParameterNames
            require(tool.parameters.map { it.name }.distinct().size == tool.parameters.size) { "Duplicate theme parameters" }
            require(tool.parameters.all { it.name in allowed }) { "Unexpected theme parameter" }
            val parameters = tool.parameters.associate { it.name to it.value }
            declarations.filter { it.required }.forEach { require(parameters.containsKey(it.name)) { "Missing parameter: ${it.name}" } }
            val section = parameters["section"]
            require(section == null || section in ThemeCommandFields.sections) {
                "section must be one of ${ThemeCommandFields.sections.joinToString()}"
            }
            when (tool.name) {
                "list_theme_targets" -> listTargets(tool.name)
                "describe_theme_fields" -> ToolResult(tool.name, true, StringResultData(
                    JSONObject().put("sections", JSONArray(ThemeCommandFields.sections.toList()))
                        .put("fields", ThemeCommandFields.describe(section))
                        .put("targetTypes", JSONArray(listOf("current", "card", "group", "user")))
                        .put("setWorkflow", "Read theme show, then supply its revision as expected_revision to theme set. Only supplied fields change.")
                        .put("resourceSources", "Readable absolute Android path, file URI or granted content URI; imported into managed private storage. No network downloads.")
                        .put("valueSource", "Preferences, including their defaults; custom theme scripts may override rendered appearance.")
                        .toString()
                ))
                "get_theme_settings" -> prompts.runThemeTransition {
                    val target = resolveTarget(tool, parameters)
                    require(target.type != "user" || section == null || section == "avatars") { "user target only supports avatars/profile fields" }
                    val values = readValues(target)
                    result(tool.name, target, values, active = target.prompt == null || target.prompt == prompts.getActivePrompt(), section = section)
                }
                "set_theme_settings" -> update(tool, parameters)
                else -> error("Unknown theme tool")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("ThemeSettingsTools", "Theme command failed: ${tool.name}", failure)
            ToolResult(tool.name, false, StringResultData(""), failure.message ?: failure.javaClass.simpleName)
        }
    }

    private suspend fun listTargets(toolName: String): ToolResult {
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
        rows.put(JSONObject().put("target_type", "user").put("name", "Global user profile").put("active", true))
        return ToolResult(toolName, true, StringResultData(rows.toString()))
    }

    private suspend fun resolveTarget(tool: AITool, parameters: Map<String, String>): Target {
        val type = parameters["target_type"] ?: "current"
        require(type in setOf("current", "card", "group", "user")) { "target_type must be current, card, group or user" }
        val id = parameters["target_id"]
        if (type == "user") {
            require(id == null) { "user target does not accept target_id" }
            return Target("user", "global", "Global user profile", null)
        }
        if (type == "card" || type == "group") {
            require(!id.isNullOrBlank() && id == id.trim()) { "Explicit card/group target requires an exact target_id" }
            return checkedTarget(if (type == "card") ActivePrompt.CharacterCard(id) else ActivePrompt.CharacterGroup(id))
        }
        require(id == null) { "current target does not accept target_id" }
        // 群聊的视觉主题属于角色组，不能把本轮发言角色的卡片 ID 当成主题目标。
        val chatId = tool.parameters.singleOrNull { it.name == "__operit_package_chat_id" }?.value?.takeIf { it.isNotBlank() }
        val cardId = tool.parameters.singleOrNull { it.name == "__operit_package_caller_card_id" }?.value?.takeIf { it.isNotBlank() }
        if (chatId != null) {
            val chat = requireNotNull(AppDatabase.getDatabase(context).chatDao().getChatById(chatId)) { "Caller conversation no longer exists; use an explicit theme target" }
            if (!chat.characterGroupId.isNullOrBlank()) return checkedTarget(ActivePrompt.CharacterGroup(chat.characterGroupId))
            if (cardId != null) return checkedTarget(ActivePrompt.CharacterCard(cardId))
            val name = chat.characterCardName
            if (!name.isNullOrBlank()) {
                val matches = cards.getAllCharacterCards().filter { it.name == name }
                require(matches.size == 1) { "Caller theme binding is missing or ambiguous; use theme targets and an explicit target" }
                return checkedTarget(ActivePrompt.CharacterCard(matches.single().id))
            }
            return checkedTarget(ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID))
        }
        if (cardId != null) return checkedTarget(ActivePrompt.CharacterCard(cardId))
        // 直接命令没有聊天归属时，current 明确定义为应用当前激活的主题。
        return checkedTarget(prompts.getActivePrompt())
    }

    private suspend fun checkedTarget(prompt: ActivePrompt): Target = when (prompt) {
        is ActivePrompt.CharacterCard -> {
            require(prompt.id in cards.characterCardListFlow.first()) { "Unknown character card: ${prompt.id}" }
            Target("card", prompt.id, cards.getCharacterCard(prompt.id).name, prompt)
        }
        is ActivePrompt.CharacterGroup -> {
            val group = requireNotNull(groups.getCharacterGroupCard(prompt.id)) { "Unknown character group: ${prompt.id}" }
            Target("group", prompt.id, group.name, prompt)
        }
    }

    private suspend fun readValues(target: Target): ThemePreferenceValues = when (val prompt = target.prompt) {
        is ActivePrompt.CharacterCard -> preferences.resolveThemePreferenceSnapshot(characterCardId = prompt.id).values
        is ActivePrompt.CharacterGroup -> preferences.resolveThemePreferenceSnapshot(characterGroupId = prompt.id).values
        null -> ThemePreferenceValues(strings = display.globalUserProfile())
    }

    private suspend fun update(tool: AITool, parameters: Map<String, String>): ToolResult {
        val expected = requireNotNull(parameters["expected_revision"])
        require(expected.matches(Regex("[0-9a-f]{64}"))) { "expected_revision must be the revision returned by theme show" }
        val changes = ThemeCommandFields.validate(PlatformCommandRegistry.parseJsonObject(requireNotNull(parameters["changes"])))
        require(changes.isNotEmpty()) { "changes must contain at least one theme field" }
        return prompts.runThemeTransition {
            val target = resolveTarget(tool, parameters)
            if (target.type == "user") require(changes.keys.all { it in ThemeCommandFields.profileNames }) { "user target only accepts global_user_avatar_uri and global_user_name" }
            else require(changes.keys.none { it in ThemeCommandFields.profileNames }) { "Global profile fields require target_type=user" }
            val before = readValues(target)
            val active = target.prompt == null || target.prompt == prompts.getActivePrompt()
            require(ThemeCommandRevision.of(target.type, target.id, before) == expected) { "Theme settings changed since they were read; read theme show again" }
            val desired = if (target.prompt == null) before else ThemeCommandFields.apply(before, changes)
            val resourceChanges = resourceChanges(changes, desired)
            val prepared = assets.prepare(resourceChanges, before)
            try {
                // 资源引用落盘后必须完成提交标记，协程取消不能删除已经被设置引用的新文件。
                val saved = withContext(NonCancellable) {
                    if (target.prompt == null) {
                        val profile = display.mutateGlobalUserProfile { current ->
                            require(ThemeCommandRevision.of(target.type, target.id, ThemePreferenceValues(strings = current)) == expected) { "User profile changed since it was read; read theme show again" }
                            current.toMutableMap().apply {
                                prepared.changes.forEach { (key, value) ->
                                    if (value == null || value == "") remove(key) else put(key, value as String)
                                }
                            }
                        }
                        prepared.commit()
                        ThemePreferenceValues(strings = profile)
                    } else {
                        var persisted: ThemePreferenceValues? = null
                        preferences.mutateThemeForPrompt(target.prompt) { current ->
                            require(ThemeCommandRevision.of(target.type, target.id, current) == expected) { "Theme settings changed since they were read; read theme show again" }
                            ThemeCommandFields.apply(current, prepared.changes).also { persisted = it }
                        }
                        prepared.commit()
                        requireNotNull(persisted)
                    }
                }
                try {
                    val after = readValues(target)
                    val verified = ThemeCommandRevision.of(target.type, target.id, saved) == ThemeCommandRevision.of(target.type, target.id, after)
                    result(tool.name, target, after, active = active, saved = true, verified = verified,
                        updatedFields = changedFields(before, saved)).let { checked ->
                            if (verified) checked else checked.copy(success = false,
                                error = "Settings were saved, but values changed before verification; read theme show again")
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // 写入已成功时保留事实，即使独立读回失败也不能误报为完全未修改。
                    AppLogger.e("ThemeSettingsTools", "Theme saved but verification failed", failure)
                    result(tool.name, target, saved, active = active, saved = true,
                        updatedFields = changedFields(before, saved)).copy(
                            success = false, error = "Settings were saved, but verification failed: " + failure.message,
                        )
                }
            } finally {
                try {
                    prepared.discard()
                } catch (cleanupFailure: Exception) {
                    // 独立记录资源清理失败，不覆盖已经发生的保存或校验错误。
                    AppLogger.e("ThemeSettingsTools", "Cannot discard uncommitted theme resources", cleanupFailure)
                }
            }
        }
    }

    /** 启用既有资源或更改媒体类型时也验证实际内容，不能只检查地址字符串非空。 */
    private fun resourceChanges(changes: Map<String, Any?>, desired: ThemePreferenceValues): Map<String, Any?> {
        val prepared = changes.toMutableMap()
        fun retain(name: String) {
            if (name !in prepared) desired.string(name)?.let { prepared[name] = it }
        }
        if (changes["use_background_image"] == true || "background_media_type" in changes) {
            retain("background_image_uri")
        }
        for (prefix in listOf("", "bubble_user_", "bubble_ai_")) {
            val affected = listOf("use_custom_font", "font_type", "system_font_name").any { prefix + it in changes }
            if (affected && desired.boolean(prefix + "use_custom_font") == true && desired.string(prefix + "font_type") == "file") {
                retain(prefix + "custom_font_path")
            }
        }
        for (side in listOf("user", "ai")) {
            val prefix = "bubble_" + side + "_"
            val affected = listOf("use_image", "bubble_liquid_glass", "bubble_water_glass").any { prefix + it in changes }
            if (affected && desired.boolean(prefix + "use_image") == true) retain(prefix + "image_uri")
        }
        return prepared
    }

    private fun changedFields(before: ThemePreferenceValues, after: ThemePreferenceValues): List<String> {
        val old = before.strings + before.booleans + before.ints + before.floats
        val new = after.strings + after.booleans + after.ints + after.floats
        return (old.keys + new.keys).filter { old[it] != new[it] }.sorted()
    }

    private fun result(
        toolName: String, target: Target, values: ThemePreferenceValues,
        active: Boolean, section: String? = null, saved: Boolean = false, verified: Boolean = false,
        updatedFields: List<String> = emptyList(),
    ): ToolResult {
        val fields = if (target.type == "user") JSONObject().apply {
            ThemeCommandFields.profileNames.sorted().forEach { key -> put(key, values.string(key) ?: JSONObject.NULL) }
        } else ThemeCommandFields.read(values, section)
        val data = ThemeSettingsResultData(
            targetType = target.type, targetId = target.id, targetName = target.name,
            revision = ThemeCommandRevision.of(target.type, target.id, values),
            values = Json.parseToJsonElement(fields.toString()).jsonObject,
            active = active,
            section = section, saved = saved, verified = verified, updatedFields = updatedFields,
            hasThemeScript = !values.string(ThemePreferenceValues.SCRIPT_URI_KEY).isNullOrBlank(),
        )
        return ToolResult(toolName, true, data)
    }
}