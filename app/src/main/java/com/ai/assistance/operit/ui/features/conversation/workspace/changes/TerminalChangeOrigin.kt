package com.ai.assistance.operit.ui.features.conversation.workspace.changes

/** 来源随实际执行传递；文件事件不依据包名或命令文本猜测调用者。 */
internal data class TerminalChangeOrigin(
    val chatId: String,
    val toolName: String,
    val executionId: String?,
    val subagentExecutionId: String? = null,
) {
    fun merge(other: TerminalChangeOrigin): TerminalChangeOrigin = if (this == other) this else TerminalChangeOrigin(
        chatId.takeIf { it == other.chatId }.orEmpty(), "terminal", null,
        // 多个命令可以属于同一子代理执行；混合来源时不得认领其他执行的产出。
        subagentExecutionId.takeIf { chatId == other.chatId && it == other.subagentExecutionId },
    )
}

internal fun terminalChangeOrigin(origins: Collection<TerminalChangeOrigin>): TerminalChangeOrigin? =
    origins.reduceOrNull { result, next -> result.merge(next) }
