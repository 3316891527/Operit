package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.tools.prompt.SystemToolPromptCategory
import com.ai.assistance.operit.core.tools.prompt.ToolParameterSchema
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt

/** 复用系统工具的中英双版定义模式，协议身份只定义一次。 */
internal object SubagentToolDefinitions {
    fun category(useEnglish: Boolean): SystemToolPromptCategory {
        fun text(english: String, chinese: String) = if (useEnglish) english else chinese
        fun parameter(name: String, english: String, chinese: String, required: Boolean = true, type: String = "string", default: String? = null) =
            ToolParameterSchema(name = name, type = type, description = text(english, chinese), required = required, default = default)
        val identity = listOf(parameter("task_id", "Task ID from spawn_task/list_tasks.", "spawn_task 或 list_tasks 返回的任务 ID。"))
        val execution = identity + parameter("execution_id", "Exact current execution ID; stale operations are rejected.", "当前执行的精确 ID；已过期的操作会被拒绝。")
        val thinking = listOf(
            parameter("enable_thinking", "Choose true/false for this execution using thinking_control and thinking_required from subagent_catalog.", "根据 subagent_catalog 的思考控制方式与必需状态，为本次执行选择 true 或 false。", type = "boolean"),
            parameter("thinking_option_id", "When thinking_control=levels and enabled, supply an exact ID from thinking_options. Omit for disabled thinking or toggle-only models.", "开启分档思考时，填写 thinking_options 中的精确 ID；关闭思考或仅支持开关的模型不填。", false),
            parameter("auto_archive", "Archive after successful completion. Omit to use the global setting; failures remain visible.", "成功完成后归档；不填则使用全局设置，失败任务继续保留。", false, "boolean"),
        )
        return SystemToolPromptCategory(
            categoryName = text("Subagent collaboration", "子代理协作"),
            tools = listOf(
                ToolPrompt("subagent_catalog", text("Read enabled prompt presets, allowed models, thinking options and runtime limits.", "查看已启用的提示词配置、可用模型、思考档位和运行限制。"), parametersStructured = emptyList()),
                ToolPrompt("spawn_task", text("Delegate independent work and return immediately with task and execution IDs. Optionally inherit parent conversation as background.", "派发独立子任务，立即返回任务和执行 ID。可选择继承主对话作为背景。"), parametersStructured = listOf(
                    parameter("title", "Short task title, 1–120 characters.", "简短任务标题，1～120 个字符。"),
                    parameter("goal", "Self-contained task brief, relevant context, expected report and assigned resources; 1–20000 characters.", "完整任务简报、相关背景、预期报告和资源分工，1～20000 个字符。"),
                    parameter("preset_id", "Enabled prompt preset ID from subagent_catalog.", "subagent_catalog 返回的已启用提示词配置 ID。"),
                    parameter("model_id", "Allowed model ID from subagent_catalog.", "subagent_catalog 返回的可用模型 ID。"),
                    parameter("fork_turns", "Parent background: none, all available context, or the most recent 1–64 user turns. Excludes system prompts, tools and reasoning; at most 200000 characters. Omit to keep independent context.", "主对话背景范围：none、all（当前可用的全部上下文），或最近 1～64 个用户轮次。排除系统提示、工具和思考内容，最多 200000 字符；不填则保持独立上下文。", false, default = "none"),
                ) + thinking),
                ToolPrompt("list_tasks", text("List this main conversation's tasks and current execution IDs.", "列出当前主对话的子任务及其当前执行 ID。"), parametersStructured = emptyList()),
                ToolPrompt("inspect_task", text("Read this conversation's task report, progress and recent history.", "查看当前对话中某个子任务的报告、进度和近期历史。"), parametersStructured = identity),
                ToolPrompt("supplement_task", text("Queue instructions for the current active execution without restarting it.", "为当前活动执行追加要求，在下一轮读取，保留已有工作。"), parametersStructured = execution + parameter("text", "Supplementary instructions, 1–10000 characters.", "补充要求，1～10000 个字符。")),
                ToolPrompt("followup_task", text("Start another execution of a finished task, retaining its history and inherited context. Restore archived tasks first.", "沿用已结束任务的历史和继承背景开始新执行；已归档任务需先恢复。"), parametersStructured = execution + listOf(
                    parameter("text", "Self-contained follow-up, 1–20000 characters.", "完整后续要求，1～20000 个字符。"),
                    parameter("model_id", "Explicit pool model for this execution; omit to use the same pool entry.", "本次明确使用的模型池条目；不填则沿用原条目。", false),
                ) + thinking.map { it.copy(required = false) }),
                ToolPrompt("stop_task", text("Stop only the specified current execution, preserving its records.", "停止指定任务的当前执行，保留已有记录。"), parametersStructured = execution),
                ToolPrompt("archive_task", text("Archive or restore an idle task while retaining its reports and child conversation. Stop active tasks first.", "归档或恢复已空闲的任务，保留报告与子会话；活动任务需先停止。"), parametersStructured = execution + parameter("archived", "true to archive, false to restore.", "true 表示归档，false 表示恢复。", type = "boolean")),
                ToolPrompt("wait_task", text("Wait once for an execution, up to 30 seconds. Reports also return automatically to the main turn.", "等待指定执行一次，最长 30 秒；报告也会自动送回主代理当前轮次。"), parametersStructured = execution + parameter("timeout_ms", "Integer from 0 to 30000.", "0～30000 的整数，单位毫秒。", type = "integer")),
            ),
        )
    }
}
