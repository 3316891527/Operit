package com.ai.assistance.operit.core.tools.commands.waifu

import com.ai.assistance.operit.core.tools.prompt.ToolParameterSchema
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt

/** 具体字段由宿主帮助按需发现，脚本继续只公开 args。 */
internal object WaifuCommandToolDefinitions {
    private fun targetFields() = listOf(
        ToolParameterSchema("target_type", "string", "current (caller conversation; direct calls use active prompt), card, or group", false),
        ToolParameterSchema("target_id", "string", "Exact card/group ID from waifu targets; required only for explicit card/group", false),
    )

    fun tools(english: Boolean): List<ToolPrompt> {
        fun text(en: String, zh: String) = if (english) en else zh
        return listOf(
            ToolPrompt("list_waifu_targets", text("List character and group Waifu targets", "列出角色与角色组 Waifu 配置目标"), parametersStructured = emptyList()),
            ToolPrompt("describe_waifu_fields", text("Discover Waifu fields, types, limits and dependencies", "查询 Waifu 字段、类型、范围和使用条件"), parametersStructured = emptyList()),
            ToolPrompt("get_waifu_settings", text("Read target Waifu settings and revision", "读取目标 Waifu 配置与版本"), parametersStructured = targetFields()),
            ToolPrompt("set_waifu_settings", text("Patch target Waifu settings and verify saved values", "增量修改目标 Waifu 配置并核验保存值"), parametersStructured = targetFields() + listOf(
                ToolParameterSchema("expected_revision", "string", "Revision returned by waifu show for the same target; stale writes are rejected", true),
                ToolParameterSchema("changes", "object", "Nonempty field/value object from waifu fields; omitted fields are preserved; null is not accepted", true),
            )),
        )
    }
}