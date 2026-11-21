package com.ai.assistance.operit.ui.features.conversation.workspace.changes

/** 来源随实际执行传递；文件事件不依据包名或命令文本猜测调用者。 */
internal data class TerminalChangeOrigin(
    val chatId: String,
    val toolName: String,
    val executionId: String?,
) {
    fun merge(other: TerminalChangeOrigin): TerminalChangeOrigin = if (this == other) this else TerminalChangeOrigin(
        chatId.takeIf { it == other.chatId }.orEmpty(), "terminal", null,
    )
}

internal fun terminalChangeOrigin(origins: Collection<TerminalChangeOrigin>): TerminalChangeOrigin? =
    origins.reduceOrNull { result, next -> result.merge(next) }
