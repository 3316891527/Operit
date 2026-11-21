package com.ai.assistance.operit.core.agent

import kotlinx.serialization.Serializable

/** 并发限制用于后续调度，执行时限在每次派发或继续时单独快照。 */
@Serializable
data class SubagentLimits(
    val maxRunning: Int = 6,
    val maxPerChat: Int = 3,
    val executionTimeoutMinutes: Int = 20,
    val maxDepth: Int = 3,
) {
    fun validate(): SubagentLimits = apply {
        require(maxDepth in 1..8) { "Agent depth must be between 1 and 8" }
        require(maxRunning in 1..64) { "Global subagent concurrency must be between 1 and 64" }
        require(maxPerChat in 1..maxRunning) { "Per-chat concurrency must be between 1 and the global limit" }
        require(executionTimeoutMinutes in 1..120) { "Execution timeout must be between 1 and 120 minutes" }
    }
}
