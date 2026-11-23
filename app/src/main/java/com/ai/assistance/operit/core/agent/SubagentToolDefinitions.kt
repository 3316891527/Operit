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
                    parameter("agent_name", "Optional sibling-unique name: 1–48 ASCII letters/digits/underscores/hyphens. Paths are returned by list_tasks.", "可选的同级唯一名称：1～48 个英文字母、数字、下划线或连字符；完整路径由 list_tasks 返回。", false),
                    parameter("fork_turns", "Parent background: none, all available context, or the most recent 1–64 user turns. Excludes system prompts, tools and reasoning; at most 200000 characters. Omit to keep independent context.", "主对话背景范围：none、all（当前可用的全部上下文），或最近 1～64 个用户轮次。排除系统提示、工具和思考内容，最多 200000 字符；不填则保持独立上下文。", false, default = "none"),
                ) + thinking + parameter("execution_timeout_minutes", "Execution deadline in minutes, 1–120. Omit to use the global setting. Includes the reserved report window.", "本次执行期限，1～120 分钟；不填使用全局设置，包含预留的报告时间。", false, "integer")),
                ToolPrompt("list_tasks", text("List the current root tree, paths, statuses and execution IDs.", "列出当前根会话树的任务、路径、状态和执行 ID。"), parametersStructured = emptyList()),
                ToolPrompt("inspect_task", text("Read this conversation's task report, progress and recent history.", "查看当前对话中某个子任务的报告、进度和近期历史。"), parametersStructured = identity),
                ToolPrompt("supplement_task", text("Queue instructions for the current active execution without restarting it.", "为当前活动执行追加要求，在下一轮读取，保留已有工作。"), parametersStructured = execution + parameter("text", "Supplementary instructions, 1–10000 characters.", "补充要求，1～10000 个字符。")),
                ToolPrompt("followup_task", text("Start another execution of a finished task, retaining its history and inherited context. Restore archived tasks first.", "沿用已结束任务的历史和继承背景开始新执行；已归档任务需先恢复。"), parametersStructured = execution + listOf(
                    parameter("text", "Self-contained follow-up, 1–20000 characters.", "完整后续要求，1～20000 个字符。"),
                    parameter("model_id", "Explicit pool model for this execution; omit to use the same pool entry.", "本次明确使用的模型池条目；不填则沿用原条目。", false),
                ) + thinking.map { item ->
                    if (item.name == "enable_thinking") item.copy(required = false, description = text(
                        "Omit both thinking fields to retain previous settings. If thinking_option_id is supplied, explicitly set enable_thinking=true even for the same level.",
                        "同时省略两个思考字段时沿用原设置；只要传 thinking_option_id，即使档位不变，也须显式传 enable_thinking=true。"))
                    else item.copy(required = false)
                } + listOf(
                    parameter("execution_timeout_minutes", "Execution deadline in minutes, 1–120; omit to use the global setting.", "本次执行期限，1～120 分钟；不填使用全局设置。", false, "integer"),
                    parameter("report_only", "true writes a report from existing evidence with tools disabled and brief thinking. Defaults to true after a missing-report failure. Set false to resume normal work.", "true 只基于已有证据补报告，禁用工具并减少思考。上次因缺报告失败时默认 true；设 false 可恢复正常工作。", false, "boolean"),
                )),
                ToolPrompt("stop_task", text("Stop the specified execution and its entire descendant subtree, preserving records. Root or ancestor control is required.", "停止指定执行和整棵下级子树，保留记录；仅根代理或祖先可控制。"), parametersStructured = execution),
                ToolPrompt("archive_task", text("Archive or restore an idle subtree while retaining reports and conversations. Root or ancestor control is required; stop all active descendants first.", "归档或恢复已空闲的整棵子树，保留报告与子会话；仅根代理或祖先可控制，需先停止全部活动下级。"), parametersStructured = execution + parameter("archived", "true to archive, false to restore.", "true 表示归档，false 表示恢复。", type = "boolean")),
                ToolPrompt("send_agent_message", text("Persist a direct message to an agent in the same root tree. Optionally wake an idle unarchived helper.", "向同一根会话树内的代理发送持久消息，可明确唤醒空闲且未归档的帮手。"), parametersStructured = listOf(
                    parameter("target", "Exact task ID/path from list_tasks, or main for the root conversation.", "list_tasks 返回的精确任务 ID／路径，或 main 表示主对话。"),
                    parameter("text", "Collaboration message, 1–10000 characters.", "协作消息，1～10000 个字符。"),
                    parameter("wake", "true explicitly starts a new execution when the target is idle; false leaves the message pending. An idle main conversation is never auto-started.", "true 表示目标空闲时明确开始新执行；false 仅保留待收消息。空闲主对话始终在下一轮接收。", false, "boolean", "false"),
                )),
                ToolPrompt("wait_agents", text("Wait once for subtree work or inbox activity, releasing the helper execution slot until it can resume.", "等待子树工作或收件箱活动一次；等待期间释放帮手执行名额，恢复前重新取得。"), parametersStructured = listOf(
                    parameter("mode", "tree for descendants; mailbox for same-tree input. Cyclic waits are rejected.", "tree 等待下级工作，mailbox 等待同树输入；循环依赖会被拒绝。"),
                    parameter("timeout_ms", "Integer from 0 to 30000; do not busy-poll after idle.", "0～30000 的整数，单位毫秒；返回 idle 后不要忙轮询。", type = "integer"),
                )),
                ToolPrompt("wait_task", text("Wait once for an execution, up to 30 seconds. Reports also return automatically to the main turn.", "等待指定执行一次，最长 30 秒；报告也会自动送回主代理当前轮次。"), parametersStructured = execution + parameter("timeout_ms", "Integer from 0 to 30000.", "0～30000 的整数，单位毫秒。", type = "integer")),
            ),
        )
    }
}
