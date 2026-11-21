package com.ai.assistance.operit.core.agent

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SubagentStatus { QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED, INTERRUPTED }

val SubagentStatus.isActive: Boolean get() = this == SubagentStatus.QUEUED || this == SubagentStatus.RUNNING

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
data class SubagentTask(
    val id: String = UUID.randomUUID().toString(),
    val parentChatId: String,
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
    val attempt: Int = 1,
    val status: SubagentStatus = SubagentStatus.QUEUED,
    val messages: List<SubagentMessage> = emptyList(),
    val events: List<SubagentEvent> = emptyList(),
    val reports: List<SubagentReport> = emptyList(),
    val currentOutput: String = "",
    val error: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long = 0,
    val completedAt: Long = 0,
    val toolCount: Int = 0,
    val schemaVersion: Int = 1,
)

data class SubagentState(
    val tasks: List<SubagentTask> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null,
)

internal fun subagentFinalReport(raw: String): String {
    val sections = com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec.parse(raw)
    val lastTool = sections.indexOfLast { it is com.ai.assistance.operit.data.model.MessageSection.ToolCall || it is com.ai.assistance.operit.data.model.MessageSection.ToolResult }
    return sections.drop(lastTool + 1).filterIsInstance<com.ai.assistance.operit.data.model.MessageSection.Text>()
        .joinToString("") { it.content }.trim()
}