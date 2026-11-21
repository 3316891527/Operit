package com.ai.assistance.operit.core.agent

/** 等待协程仍由协调器持有，但不占采样名额；同一根会话的所有层级共享限额。 */
internal object SubagentScheduling {
    fun next(tasks: List<SubagentTask>, jobIds: Set<String>, resumes: Set<String>, limits: SubagentLimits,
        blocked: (String) -> Boolean): SubagentTask? {
        val occupied = tasks.filter { it.id in jobIds && it.status != SubagentStatus.WAITING }
        if (occupied.size >= limits.maxRunning) return null
        val candidates = resumes.mapNotNull { id -> tasks.find { it.id == id && it.status == SubagentStatus.WAITING && it.id in jobIds } } +
            tasks.filter { it.status == SubagentStatus.QUEUED && it.id !in jobIds }.sortedWith(compareBy<SubagentTask> { it.queuedAt }.thenBy { it.id })
        return candidates.firstOrNull { candidate -> !blocked(candidate.parentChatId) && occupied.count { it.rootChatId == candidate.rootChatId } < limits.maxPerChat }
    }
}

/** 多个取消／删除清理协程可能覆盖同一节点，最后一个持有者离开时才打开门。 */
internal class SubagentStopGates {
    private val owners = mutableMapOf<String, Int>()
    fun contains(chatId: String): Boolean = chatId in owners
    fun close(chatId: String) { owners[chatId] = (owners[chatId] ?: 0) + 1 }
    fun open(chatId: String) {
        val remaining = checkNotNull(owners[chatId]) { "Unbalanced subtree gate" } - 1
        if (remaining == 0) owners.remove(chatId) else owners[chatId] = remaining
    }
}

internal object SubagentTransitions {
    fun validate(previous: SubagentTask, next: SubagentTask) {
        check(previous.id == next.id && previous.parentChatId == next.parentChatId && previous.childChatId == next.childChatId &&
            previous.parentToolCallId == next.parentToolCallId && previous.rootChatId == next.rootChatId &&
            previous.parentTaskId == next.parentTaskId && previous.agentName == next.agentName && previous.path == next.path && previous.depth == next.depth && previous.createdAt == next.createdAt) { "Task ownership cannot change" }
        check(next.executionTimeoutMinutes in 1..120 && SubagentFork.parse(next.forkTurns).parameter == next.forkTurns) { "Invalid execution settings" }
        if (previous.executionId != next.executionId) {
            check(!previous.status.isActive && next.status == SubagentStatus.QUEUED && next.attempt == previous.attempt + 1) { "Only an idle task can start another execution" }
        } else {
            check(previous.attempt == next.attempt && (previous.status == next.status || when (previous.status) {
                SubagentStatus.QUEUED -> next.status in setOf(SubagentStatus.RUNNING, SubagentStatus.CANCELLED, SubagentStatus.INTERRUPTED)
                SubagentStatus.RUNNING -> next.status == SubagentStatus.WAITING || !next.status.isActive
                SubagentStatus.WAITING -> next.status == SubagentStatus.RUNNING || !next.status.isActive
                else -> false
            })) { "Invalid subagent state transition" }
        }
    }
}

/** 先观察再等待，零超时也能准确区分已有输入和无待运行工作。 */
internal object SubagentWaiting {
    fun outcome(hasInput: Boolean, hasWork: Boolean): String? = when {
        hasInput -> "mailbox"
        !hasWork -> "idle"
        else -> null
    }
}
