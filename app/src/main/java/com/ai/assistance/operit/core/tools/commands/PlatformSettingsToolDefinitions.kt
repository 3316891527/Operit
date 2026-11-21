package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.core.tools.prompt.ToolParameterSchema
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt

/** 为既有角色卡和性能宿主接口补全可查询参数，原接口不做重命名。 */
internal object PlatformSettingsToolDefinitions {
    private fun cardId() = ToolParameterSchema("character_card_id", "string", "Character card ID / 角色卡 ID", true)

    private fun cardFields(requireName: Boolean) = listOf(
        ToolParameterSchema("name", "string", "Card name / 角色卡名称", requireName),
        ToolParameterSchema("description", "string", "Description / 描述", false),
        ToolParameterSchema("character_setting", "string", "Character setting / 人物设定", false),
        ToolParameterSchema("opening_statement", "string", "Opening statement / 开场白", false),
        ToolParameterSchema("other_content_chat", "string", "Additional chat prompt / 对话附加内容", false),
        ToolParameterSchema("other_content_voice", "string", "Additional voice prompt / 语音附加内容", false),
        ToolParameterSchema("advanced_custom_prompt", "string", "Advanced prompt / 高级自定义提示词", false),
        ToolParameterSchema("marks", "string", "Marks / 标记", false),
        ToolParameterSchema("attached_tag_ids", "array", "Attached tag IDs / 附加标签 ID", false),
        ToolParameterSchema("chat_model_binding_mode", "string", "FOLLOW_GLOBAL or FIXED_CONFIG", false),
        ToolParameterSchema("chat_model_config_id", "string", "Bound model config ID; empty clears it / 模型绑定 ID，空串清除", false),
        ToolParameterSchema("chat_model_index", "integer", "Model index >= 0 / 模型下标", false),
        ToolParameterSchema("memory_profile_binding_mode", "string", "FOLLOW_GLOBAL or FIXED_PROFILE", false),
        ToolParameterSchema("memory_profile_id", "string", "Memory profile ID; empty clears it / 记忆档案 ID，空串清除", false),
        ToolParameterSchema("tool_access_enabled", "boolean", "Enable card tool restrictions / 启用角色卡工具限制", false),
        ToolParameterSchema("allowed_builtin_tools", "array", "Allowed built-in tools / 允许的内置工具", false),
        ToolParameterSchema("allowed_packages", "array", "Allowed packages / 允许的包", false),
        ToolParameterSchema("allowed_skills", "array", "Allowed Skills / 允许的 Skill", false),
        ToolParameterSchema("allowed_mcp_servers", "array", "Allowed MCP servers / 允许的 MCP 服务", false),
    )

    fun tools(english: Boolean): List<ToolPrompt> {
        fun text(en: String, zh: String) = if (english) en else zh
        return listOf(
            ToolPrompt("list_character_cards_settings", text("List character cards and active card", "读取角色卡列表及激活状态"), parametersStructured = emptyList()),
            ToolPrompt("get_character_card", text("Read a character card", "读取角色卡"), parametersStructured = listOf(cardId())),
            ToolPrompt("create_character_card", text("Create a character card", "创建角色卡"), parametersStructured = cardFields(true)),
            ToolPrompt("update_character_card", text("Update selected character card fields", "更新指定角色卡字段"), parametersStructured = listOf(cardId()) + cardFields(false)),
            ToolPrompt("delete_character_card", text("Delete a character card", "删除角色卡"), parametersStructured = listOf(cardId())),
            ToolPrompt("set_active_character_card", text("Activate a character card", "激活角色卡"), parametersStructured = listOf(cardId())),
            ToolPrompt("clear_active_character_card", text("Clear the active character card", "清除激活角色卡"), parametersStructured = emptyList()),
            ToolPrompt("import_character_card_from_tavern_json", text("Import Tavern character JSON", "导入酒馆角色卡 JSON"), parametersStructured = listOf(ToolParameterSchema("tavern_json", "string", "Tavern JSON content / 酒馆 JSON 内容", true))),
            ToolPrompt("export_character_card_to_tavern_json", text("Export Tavern character JSON", "导出酒馆角色卡 JSON"), parametersStructured = listOf(cardId())),
            ToolPrompt("get_performance_metrics", text("Read software or plugin performance metrics", "读取软件或插件性能"), parametersStructured = listOf(ToolParameterSchema("plugin_id", "string", "Optional plugin ID / 可选插件 ID", false))),
        )
    }
}