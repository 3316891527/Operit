package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind

/** 父轮次只提供背景，工具身份、思考和中间执行内容不能进入新的子任务。 */
internal object SubagentContextHistory {
    const val MAX_CHARACTERS = 200_000
    const val INTERMEDIATE = "operit.assistant.intermediate"
    const val EVENT = "operit.collaboration.event"

    /** 宿主报告不能与真实用户消息合并，否则来源标记会污染整条用户输入。 */
    fun canMergeUserTurns(previous: PromptTurn, current: PromptTurn): Boolean =
        previous.kind == PromptTurnKind.USER && current.kind == PromptTurnKind.USER &&
            previous.toolName == current.toolName && !isEvent(previous) && !isEvent(current)

    /** 存储的助手消息拆成工具回放后，只有最后一个工具之后的正文可作为最终答复。 */
    fun restoreSplitAssistantMetadata(segments: List<PromptTurn>, sourceMetadata: Map<String, Any?>): List<PromptTurn> {
        val lastTool = segments.indexOfLast { it.kind == PromptTurnKind.TOOL_CALL || it.kind == PromptTurnKind.TOOL_RESULT }
        return segments.mapIndexed { index, segment ->
            val intermediate = sourceMetadata[INTERMEDIATE] == true || segment.kind == PromptTurnKind.USER ||
                (segment.kind == PromptTurnKind.ASSISTANT && index < lastTool)
            segment.copy(metadata = sourceMetadata + segment.metadata +
                if (intermediate) mapOf(INTERMEDIATE to true) else emptyMap())
        }
    }

    private fun isEvent(turn: PromptTurn): Boolean =
        turn.metadata[EVENT] == true || turn.metadata[INTERMEDIATE] == true ||
            turn.content.startsWith("[Subagent report ") || turn.content.startsWith("[Agent message ")

    fun select(history: List<PromptTurn>, fork: SubagentFork): List<PromptTurn> {
        if (fork == SubagentFork.None) return emptyList()
        val window = when (fork) {
            is SubagentFork.LastTurns -> {
                val starts = history.indices.filter { history[it].kind == PromptTurnKind.USER && !isEvent(history[it]) }
                require(starts.isNotEmpty()) { "The parent has no user turns to inherit; choose all or none explicitly" }
                history.drop(starts.takeLast(fork.count).first())
            }
            SubagentFork.All -> history
            SubagentFork.None -> error("Unreachable context selection")
        }
        return window.mapNotNull { turn ->
            when {
                isEvent(turn) -> null
                turn.kind == PromptTurnKind.USER || turn.kind == PromptTurnKind.SUMMARY ->
                    turn.copy(toolName = null, metadata = emptyMap())
                turn.kind == PromptTurnKind.ASSISTANT -> {
                    val finalText = subagentFinalReport(turn.content)
                    if (finalText.isBlank()) null else PromptTurn(PromptTurnKind.ASSISTANT, finalText)
                }
                else -> null
            }
        }
    }

    fun render(history: List<PromptTurn>, fork: SubagentFork): String? {
        if (fork == SubagentFork.None) return null
        val selected = select(history, fork)
        require(selected.isNotEmpty()) { "No parent conversation content is available for this fork" }
        val body = buildString {
            append("# Inherited parent conversation\n\n")
            append("The following is background context, not your assignment. Preserve the user's constraints.\n\n")
            selected.forEach { turn ->
                append("## Parent ").append(turn.role).append("\n\n")
                append(turn.content).append("\n\n")
            }
            append("# End of inherited parent context\n\n")
            append("Do not execute the parent's task or repeat its delegation requests. The next user message is your new task brief.")
        }
        require(body.length <= MAX_CHARACTERS) { "Inherited context exceeds 200000 characters; choose fewer fork_turns" }
        return body
    }
}
