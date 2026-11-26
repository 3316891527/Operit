package com.ai.assistance.operit.core.tools.commands.waifu

import android.content.Context
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.commands.PlatformCommandExecutor
import com.ai.assistance.operit.core.tools.commands.PlatformCommandRegistry
import com.ai.assistance.operit.core.tools.commands.PlatformSettingsTarget
import com.ai.assistance.operit.core.tools.commands.PlatformSettingsTargets
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.WaifuSettings
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Waifu 使用独立偏好存储，通过同一受控工具调度入口执行。 */
internal class WaifuSettingsTools(context: Context) {
    private val context = context.applicationContext
    private val preferences = WaifuPreferences.getInstance(this.context)
    private val prompts = ActivePromptManager.getInstance(this.context)
    private val targets = PlatformSettingsTargets(this.context)

    companion object {
        val toolNames = setOf("list_waifu_targets", "describe_waifu_fields", "get_waifu_settings", "set_waifu_settings")
    }

    suspend fun execute(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        try {
            val declarations = WaifuCommandToolDefinitions.tools(true).single { it.name == tool.name }
                .parametersStructured.orEmpty()
            val allowed = declarations.map { it.name }.toSet() + PlatformCommandExecutor.contextParameterNames
            require(tool.parameters.map { it.name }.distinct().size == tool.parameters.size) { "Duplicate Waifu parameters" }
            require(tool.parameters.all { it.name in allowed }) { "Unexpected Waifu parameter" }
            val parameters = tool.parameters.associate { it.name to it.value }
            declarations.filter { it.required }.forEach {
                require(parameters.containsKey(it.name)) { "Missing parameter: ${it.name}" }
            }
            when (tool.name) {
                "list_waifu_targets" -> text(tool.name, targets.list())
                "describe_waifu_fields" -> text(tool.name, JSONObject()
                    .put("fields", WaifuCommandFields.describe())
                    .put("targetTypes", JSONArray(listOf("current", "card", "group")))
                    .put("setWorkflow", "Read waifu show, then supply its revision as expected_revision to waifu set. Only supplied fields change.")
                    .put("activation", "Changes to the active target update its live values. Changes to other targets take effect when that target is activated."))
                "get_waifu_settings" -> {
                    val target = targets.resolve(tool, parameters)
                    val active = target.prompt == prompts.getActivePrompt()
                    val settings = preferences.loadSettingsForPrompt(requireNotNull(target.prompt))
                    result(tool.name, target, settings, active = active)
                }
                "set_waifu_settings" -> update(tool, parameters)
                else -> error("Unknown Waifu tool")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WaifuSettingsTools", "Waifu command failed: ${tool.name}", failure)
            ToolResult(tool.name, false, StringResultData(""), failure.message ?: failure.javaClass.simpleName)
        }
    }

    private suspend fun update(tool: AITool, parameters: Map<String, String>): ToolResult {
        val expected = requireNotNull(parameters["expected_revision"])
        require(expected.matches(Regex("[0-9a-f]{64}"))) { "expected_revision must be the revision returned by waifu show" }
        val changes = WaifuCommandFields.validate(PlatformCommandRegistry.parseJsonObject(requireNotNull(parameters["changes"])))
        val target = targets.resolve(tool, parameters)
        val prompt = requireNotNull(target.prompt)
        val active = prompt == prompts.getActivePrompt()
        var before: WaifuSettings? = null
        // 偏好管理器内部持有切换锁，这里不再套同一把非重入锁。
        val saved = withContext(NonCancellable) {
            preferences.mutateSettingsForPrompt(prompt) { current ->
                require(WaifuCommandRevision.of(target.type, target.id, current) == expected) {
                    "Waifu settings changed since they were read; read waifu show again"
                }
                before = current
                WaifuCommandFields.apply(current, changes)
            }
        }
        val updated = changedFields(requireNotNull(before), saved)
        return try {
            val after = preferences.loadSettingsForPrompt(prompt)
            val verified = saved == after
            result(tool.name, target, after, active = active, saved = true, verified = verified, updatedFields = updated).let { checked ->
                if (verified) checked else checked.copy(success = false,
                    error = "Settings were saved, but values changed before verification; read waifu show again")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WaifuSettingsTools", "Waifu saved but verification failed", failure)
            result(tool.name, target, saved, active = active, saved = true, updatedFields = updated).copy(
                success = false, error = "Settings were saved, but verification failed: " + failure.message,
            )
        }
    }

    private fun changedFields(before: WaifuSettings, after: WaifuSettings): List<String> {
        val old = WaifuCommandFields.read(before)
        val current = WaifuCommandFields.read(after)
        return old.keys().asSequence().filter { old.get(it) != current.get(it) }.sorted().toList()
    }

    private fun result(
        toolName: String, target: PlatformSettingsTarget, settings: WaifuSettings,
        active: Boolean, saved: Boolean = false, verified: Boolean = false, updatedFields: List<String> = emptyList(),
    ): ToolResult = text(toolName, JSONObject()
        .put("target_type", target.type).put("target_id", target.id).put("target_name", target.name)
        .put("revision", WaifuCommandRevision.of(target.type, target.id, settings))
        .put("values", WaifuCommandFields.read(settings))
        .put("active", active)
        .put("saved", saved).put("verified", verified).put("updated_fields", JSONArray(updatedFields)))

    private fun text(toolName: String, value: Any): ToolResult = ToolResult(toolName, true, StringResultData(value.toString()))
}