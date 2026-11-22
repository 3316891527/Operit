package com.ai.assistance.operit.core.agent

import java.util.Locale
import kotlinx.serialization.Serializable

/** 任务快照只保存选择结果，不依赖模型配置解析和运行服务。 */
@Serializable
data class SubagentThinking(val enabled: Boolean = false, val optionId: String = "")

internal sealed interface SubagentFork {
    data object None : SubagentFork
    data object All : SubagentFork
    data class LastTurns(val count: Int) : SubagentFork

    val parameter: String
        get() = when (this) {
            None -> "none"
            All -> "all"
            is LastTurns -> count.toString()
        }

    companion object {
        fun parse(value: String): SubagentFork {
            val normalized = value.trim().lowercase(Locale.ROOT)
            return when (normalized) {
                "none" -> None
                "all" -> All
                else -> {
                    val count = requireNotNull(normalized.toIntOrNull()) { "fork_turns must be none, all, or an integer from 1 to 64" }
                    require(count in 1..64) { "fork_turns must be between 1 and 64 user turns" }
                    LastTurns(count)
                }
            }
        }
    }
}

/** 每次派发独立校验期限，省略时继续使用全局配置。 */
internal fun subagentExecutionTimeout(requested: Int?, configured: Int): Int = (requested ?: configured).also {
    require(it in 1..120) { "execution_timeout_minutes must be an integer from 1 to 120" }
}
