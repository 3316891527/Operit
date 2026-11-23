package com.ai.assistance.operit.core.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.ai.assistance.operit.core.chat.message.ChatMessageTimestampAllocator
import java.util.UUID

@Serializable
enum class SubagentStatus { QUEUED, RUNNING, WAITING, COMPLETED, CANCELLED, FAILED, INTERRUPTED }

val SubagentStatus.isActive: Boolean get() = this == SubagentStatus.QUEUED || this == SubagentStatus.RUNNING || this == SubagentStatus.WAITING

@Serializable
data class SubagentMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val executionId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val pending: Boolean = false,
)

@Serializable
data class SubagentEvent(val text: String, val at: Long = System.currentTimeMillis(), val toolName: String? = null)

@Serializable
data class SubagentReport(
    val id: String = UUID.randomUUID().toString(),
    val executionId: String,
    val status: SubagentStatus,
    val content: String,
    val modelName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val delivered: Boolean = false,
) {
    val marker: String get() = "[Subagent report $id]"
}

@Serializable
data class SubagentLetter(
    val id: String = UUID.randomUUID().toString(),
    val invocationId: String,
    val senderTaskId: String?,
    val recipientTaskId: String?,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val consumed: Boolean = false,
) {
    val marker: String get() = "[Agent message $id]"
}

@Serializable
data class SubagentTask(
    val id: String = UUID.randomUUID().toString(),
    val parentChatId: String,
    val childChatId: String = UUID.randomUUID().toString(),
    val parentToolCallId: String,
    val title: String,
    val goal: String,
    val preset: SubagentPreset,
    val model: SubagentModel,
    val workspacePath: String? = null,
    val workspaceEnv: String? = null,
    val roleCardId: String? = null,
    val memorySpaceId: String? = null,
    val executionId: String = UUID.randomUUID().toString(),
    val thinking: SubagentThinking = SubagentThinking(),
    val autoArchiveOnCompletion: Boolean = false,
    val forkTurns: String = "none",
    val executionTimeoutMinutes: Int = 20,
    val reportOnly: Boolean = false,
    val reportMissing: Boolean = false,
    val attempt: Int = 1,
    val status: SubagentStatus = SubagentStatus.QUEUED,
    val requestTimestamp: Long = ChatMessageTimestampAllocator.next(),
    val assistantTimestamp: Long = 0L,
    val executionInputBase: Long = 0L,
    val executionOutputBase: Long = 0L,
    val pendingInputs: List<SubagentMessage> = emptyList(),
    @Transient val messages: List<SubagentMessage> = emptyList(),
    val events: List<SubagentEvent> = emptyList(),
    val reports: List<SubagentReport> = emptyList(),
    @Transient val currentOutput: String = "",
    val error: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long = 0,
    val completedAt: Long = 0,
    val toolCount: Int = 0,
    val schemaVersion: Int = 2,
    val rootChatId: String = parentChatId,
    val parentTaskId: String? = null,
    val agentName: String = id.take(8),
    val path: String = "/$agentName",
    val depth: Int = 1,
    val letters: List<SubagentLetter> = emptyList(),
    val waitingFor: List<String> = emptyList(),
    val queuedAt: Long = createdAt,
)

data class SubagentState(
    val tasks: List<SubagentTask> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null,
)
