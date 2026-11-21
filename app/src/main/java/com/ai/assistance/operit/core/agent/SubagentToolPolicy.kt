package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.core.tools.runtime.ToolExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** 注册时包住实际执行器，包代理和 JS 再次调用内置工具也要经过同一限制。 */
internal object SubagentToolPolicy {
    private val sharedTools = setOf(
        "click_element", "tap", "long_press", "swipe", "press_key", "set_input_text", "get_page_info", "capture_screenshot", "run_ui_subagent",
        "close_all_virtual_displays", "start_app", "stop_app", "install_app", "uninstall_app", "open_file", "share_file", "modify_system_setting",
        "start_chat_service", "stop_chat_service", "create_new_chat", "switch_chat", "delete_chat", "update_chat_title", "send_message_to_ai", "send_message_to_ai_streaming", "call_chat_model",
        "create_model_config", "update_model_config", "delete_model_config", "set_function_model_config", "set_tool_permission_mode", "set_sandbox_package_enabled",
        "create_character_card", "update_character_card", "delete_character_card", "set_active_character_card", "clear_active_character_card", "import_character_card_from_tavern_json",
    )
    fun isAllowedName(name: String): Boolean = name !in sharedTools &&
        !name.startsWith("a2ui_") && !name.startsWith("browser_") && !name.startsWith("music_") &&
        !name.startsWith("automatic_ui_subagent:") && !name.startsWith("subagent:")

    fun denial(tool: AITool, delegatedTask: Boolean): ToolResult? {
        if (!delegatedTask || isAllowedName(tool.name)) return null
        return ToolResult(tool.name, false, StringResultData(""), error = "This helper cannot use shared UI, change chat/model settings, or operate outside the configured collaboration tree. Return the requirement to the main agent.")
    }
    fun guard(delegate: ToolExecutor): ToolExecutor = object : ToolExecutor {
        override fun validateParameters(tool: AITool) = delegate.validateParameters(tool)
        override fun invoke(tool: AITool): ToolResult {
            val denied = denial(tool, ToolExecutionManager.currentToolRuntimeContext()?.delegatedTask == true)
            return denied ?: delegate.invoke(tool)
        }
        override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
            val denied = denial(tool, ToolExecutionManager.currentToolRuntimeContext()?.delegatedTask == true)
            if (denied != null) emit(denied) else delegate.invokeAndStream(tool).collect { emit(it) }
        }
    }
}