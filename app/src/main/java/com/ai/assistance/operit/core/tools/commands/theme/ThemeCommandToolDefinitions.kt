package com.ai.assistance.operit.core.tools.commands.theme

import com.ai.assistance.operit.core.tools.prompt.ToolParameterSchema
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt

/** 命令只公开稳定操作，具体外观字段由 theme fields 按分组查询。 */
internal object ThemeCommandToolDefinitions {
    private fun targetFields() = listOf(
        ToolParameterSchema("target_type", "string", "current (caller conversation theme; direct calls use active theme), card, group, or user (global profile)", false),
        ToolParameterSchema("target_id", "string", "Exact card/group ID from theme targets; required only for explicit card/group", false),
    )

    fun tools(english: Boolean): List<ToolPrompt> {
        fun text(en: String, zh: String) = if (english) en else zh
        return listOf(
            ToolPrompt("list_theme_targets", text("List character and group theme targets and global user profile", "列出角色、角色组主题与全局用户资料目标"), parametersStructured = emptyList()),
            ToolPrompt("describe_theme_fields", text("Discover allowed appearance fields, types, limits and dependencies", "查询允许的外观字段、类型、范围和联动规则"), parametersStructured = listOf(
                ToolParameterSchema("section", "string", "Optional: colors, fonts, avatars, background, messages, info", false),
            )),
            ToolPrompt("get_theme_settings", text("Read theme preference values and revision; custom scripts may override rendering", "读取主题偏好及版本；自定义主题脚本可能覆盖渲染"), parametersStructured = targetFields() + listOf(
                ToolParameterSchema("section", "string", "Optional field group from theme fields; user target supports avatars only", false),
            )),
            ToolPrompt("set_theme_settings", text("Patch allowed appearance fields, import local assets and verify saved values", "增量修改外观、导入本地资源并核验保存值"), parametersStructured = targetFields() + listOf(
                ToolParameterSchema("expected_revision", "string", "Revision returned by theme show for the same target; stale writes are rejected", true),
                ToolParameterSchema("changes", "object", "Nonempty field/value object from theme fields; omitted fields are preserved; null only where documented", true),
            )),
        )
    }
}