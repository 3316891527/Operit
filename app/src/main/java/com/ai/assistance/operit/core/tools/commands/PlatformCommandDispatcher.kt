package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.protocol.ToolResult

/** 参数只能携带业务内容，调用身份由宿主运行上下文提供，权限决定后只执行一次。 */
internal class PlatformCommandDispatcher(
    private val trustedContext: List<ToolParameter>,
    private val isReserved: (String) -> Boolean,
    private val isAllowed: (AITool) -> Boolean,
    private val executeWithPermission: (AITool) -> ToolResult,
    private val accessDeniedMessage: String,
) {
    fun execute(target: String, parameters: Map<String, String>): ToolResult {
        if (target == PlatformCommandExecutor.TOOL_NAME || isReserved(target)) {
            return failure(target, "Command target is reserved: $target")
        }
        if (parameters.keys.any { it.startsWith("__operit_") }) {
            return failure(target, "Command parameters cannot supply system context")
        }
        val tool = AITool(target, parameters.map { (name, value) -> ToolParameter(name, value) } + trustedContext)
        if (!isAllowed(tool)) return failure(target, accessDeniedMessage)
        return executeWithPermission(tool)
    }

    private fun failure(name: String, error: String) = ToolResult(name, false, StringResultData(""), error)
}