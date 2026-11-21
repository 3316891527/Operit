package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.chat.archive.OperitArchivedChat
import com.ai.assistance.operit.data.model.ChatKind
import com.ai.assistance.operit.data.model.MessageSection
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class SubagentGraphArchive(
    val version: Int = 1,
    val tasks: List<SubagentTask>,
    val chats: List<OperitArchivedChat>,
)

/** 逻辑导入始终创建新树，验证后再一次性写入，不能覆盖正在使用的本机会话。 */
internal object SubagentGraphTransfer {
    fun validate(root: OperitArchivedChat) {
        val graph = requireNotNull(root.subagentGraph) { "Missing subagent graph" }
        require(root.chatKind == ChatKind.NORMAL && graph.version == 1 && graph.tasks.isNotEmpty()) { "Invalid conversation graph format" }
        SubagentTree.validate(graph.tasks)
        val children = graph.chats.associateBy { it.id }
        require(children.size == graph.chats.size && children.size == graph.tasks.size && root.id !in children) { "Duplicate or missing graph conversations" }
        require(graph.tasks.all { it.rootChatId == root.id }) { "The archive contains another root tree" }
        graph.tasks.forEach { task ->
            val child = requireNotNull(children[task.childChatId]) { "Missing child conversation" }
            require(child.chatKind == ChatKind.SUBAGENT && child.parentChatId == task.parentChatId && child.subagentGraph == null && child.archived == task.archived) { "Invalid child conversation ownership" }
            require(child.messages.any { it.baseMessage.sender == "user" && it.baseMessage.timestamp == task.requestTimestamp }) { "Missing execution brief" }
            require(task.schemaVersion == 2 && task.attempt > 0 && !(task.archived && task.status.isActive)) { "Invalid archived execution state" }
            require(task.parentToolCallId.isNotBlank() && task.executionId.isNotBlank() && task.reports.none { it.status.isActive }) { "Invalid execution identity or report state" }
            require(task.executionTimeoutMinutes in 1..120 && SubagentFork.parse(task.forkTurns).parameter == task.forkTurns) { "Invalid archived execution settings" }
            require(task.pendingInputs.size <= 64 && task.letters.size <= 1024 && task.pendingInputs.all { it.executionId.isNotBlank() && it.role == "user" && it.pending && it.content.isNotBlank() && it.content.length <= 10000 }) { "Invalid archived mailbox" }
        }
        val identities = graph.tasks.map { it.id } + graph.tasks.map { it.childChatId } + listOf(root.id) +
            graph.tasks.flatMap { task -> task.reports.map { it.id } + task.letters.map { it.id } + task.pendingInputs.map { it.id } }
        require(identities.size == identities.toSet().size && identities.all { it.isNotBlank() && it.length <= 128 }) { "Duplicate or invalid graph identity" }
        val executionOwners = mutableMapOf<String, String>()
        val entityIds = identities.toSet()
        graph.tasks.forEach { task ->
            (listOf(task.executionId) + task.reports.map { it.executionId } + task.pendingInputs.map { it.executionId }).forEach { execution ->
                require(execution.isNotBlank() && execution.length <= 128 && execution !in entityIds) { "Invalid execution identity" }
                require(executionOwners.putIfAbsent(execution, task.id).let { it == null || it == task.id }) { "Duplicate execution ownership" }
            }
            require(task.parentToolCallId.length <= 128 && task.letters.all { it.invocationId.length <= 128 }) { "Invalid invocation identity" }
        }
        (listOf(root) + graph.chats).forEach { chat ->
            require(chat.messages.map { it.baseMessage.timestamp }.toSet().size == chat.messages.size) { "Duplicate message timestamp" }
            require(chat.messages.all { row -> row.variants.map { it.variantIndex }.toSet().size == row.variants.size && row.variants.all { it.variantIndex > 0 } }) { "Invalid message variants" }
        }
        require(graph.tasks.sumOf { it.goal.length.toLong() + it.title.length + it.preset.systemPrompt.length + it.preset.description.length + it.model.capabilities.length + it.model.limitations.length + it.error.length + it.pendingInputs.sumOf { input -> input.content.length.toLong() } + it.events.sumOf { event -> event.text.length.toLong() } + it.reports.sumOf { report -> report.content.length.toLong() } + it.letters.sumOf { letter -> letter.content.length.toLong() } } +
            (listOf(root) + graph.chats).sumOf { chat -> chat.messages.sumOf { row -> row.baseMessage.content.length.toLong() + row.variants.sumOf { MessageSectionCodec.render(it.sections).length.toLong() } } } <= 32_000_000L) { "Subagent graph exceeds the 32000000 character limit" }
    }

    fun remap(root: OperitArchivedChat, interruptedReason: String, newId: () -> String = { UUID.randomUUID().toString() }): OperitArchivedChat {
        validate(root)
        val graph = requireNotNull(root.subagentGraph)
        val identities = linkedMapOf<String, String>()
        fun id(old: String): String = identities.getOrPut(old) { newId() }
        id(root.id)
        graph.tasks.forEach { task ->
            id(task.id); id(task.childChatId); id(task.parentToolCallId); id(task.executionId)
            task.pendingInputs.forEach { id(it.id); id(it.executionId) }
            task.reports.forEach { id(it.id); id(it.executionId) }
            task.letters.forEach { id(it.id); id(it.invocationId) }
        }
        val marker = Regex("\\[(Subagent report|Agent message) ([^]\\r\\n]+)]")
        val callId = Regex("(\\bcall_id\\s*=\\s*[\"'])([^\"']+)([\"'])")
        val identityField = Regex("(\"(?:task_id|execution_id|child_chat_id|root_chat_id|parent_chat_id|parent_task_id|message_id|parent_tool_call_id|invocation_id)\"\\s*:\\s*\")([^\"]+)(\")")
        val envelope = Regex("(?m)^(Task ID: |Execution: )([^\\r\\n]+)$")
        fun callAttributes(raw: String): String = callId.replace(raw) { match ->
            match.groupValues[1] + (identities[match.groupValues[2]] ?: match.groupValues[2]) + match.groupValues[3]
        }
        val waitingField = Regex("""("waiting_for"\s*:\s*\[)([^]]*)(])""")
        val quotedIdentity = Regex(""""([^"]+)"""")
        fun text(raw: String): String {
            var updated = marker.replace(raw) { match -> "[${match.groupValues[1]} ${identities[match.groupValues[2]] ?: match.groupValues[2]}]" }
            updated = callId.replace(updated) { match -> match.groupValues[1] + (identities[match.groupValues[2]] ?: match.groupValues[2]) + match.groupValues[3] }
            updated = identityField.replace(updated) { match -> match.groupValues[1] + (identities[match.groupValues[2]] ?: match.groupValues[2]) + match.groupValues[3] }
            updated = waitingField.replace(updated) { match -> match.groupValues[1] + quotedIdentity.replace(match.groupValues[2]) { value ->
                "\"" + (identities[value.groupValues[1]] ?: value.groupValues[1]) + "\""
            } + match.groupValues[3] }
            return envelope.replace(updated) { match -> match.groupValues[1] + (identities[match.groupValues[2]] ?: match.groupValues[2]) }
        }
        // 工具调用与结果必须共同获得新调用身份，其余提供方协议不参与重写。
        (listOf(root) + graph.chats).forEach { chat -> chat.messages.forEach { row ->
            (row.baseMessage.resolvedSections() + row.variants.flatMap { it.sections }).forEach { section ->
                val raw = when (section) {
                    is MessageSection.ToolCall -> section.raw
                    is MessageSection.ToolResult -> section.raw
                    else -> ""
                }
                callId.findAll(raw).forEach { id(it.groupValues[2]) }
            }
        } }
        val identityParameters = setOf("task_id", "execution_id", "target")
        val collaborationTools = setOf("spawn_task", "inspect_task", "supplement_task", "followup_task", "archive_task", "stop_task", "send_agent_message", "wait_task")
        val paramPattern = Regex("""(<param\s+name\s*=\s*["']([^"']+)["'][^>]*>)([\s\S]*?)(</param\s*>)""", RegexOption.IGNORE_CASE)
        fun escapeXml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun sections(source: List<MessageSection>): List<MessageSection> = source.map { section ->
            when (section) {
                is MessageSection.Text -> section.copy(content = text(section.content))
                is MessageSection.ToolCall -> {
                    val params = if (section.name in collaborationTools) section.params.mapValues { (key, value) ->
                        if (key in identityParameters) identities[value] ?: value else value
                    } else section.params
                    // 保留开标签与调用身份，清空 raw 会丢失工具和结果的配对属性。
                    val raw = paramPattern.replace(callAttributes(section.raw)) { match ->
                        val name = match.groupValues[2]
                        if (params[name] != section.params[name]) match.groupValues[1] + escapeXml(params.getValue(name)) + match.groupValues[4] else match.value
                    }
                    section.copy(params = params, raw = raw)
                }
                is MessageSection.ToolResult -> if (section.name in collaborationTools) section.copy(content = text(section.content), raw = text(section.raw))
                    else section.copy(raw = callAttributes(section.raw))
                // 提供方协议负载必须原样回传，不能对其中的字符串进行身份替换。
                else -> section
            }
        }
        fun chat(source: OperitArchivedChat): OperitArchivedChat = source.copy(
            id = id(source.id), parentChatId = source.parentChatId?.let { identities[it] ?: it }, subagentGraph = null,
            messages = source.messages.map { row ->
                val rendered = sections(row.baseMessage.resolvedSections())
                row.copy(baseMessage = row.baseMessage.copy(sections = rendered, content = MessageSectionCodec.render(rendered)),
                    variants = row.variants.map { it.copy(sections = sections(it.sections)) })
            },
        )
        val tasks = graph.tasks.map { task ->
            val active = task.status.isActive
            task.copy(id = id(task.id), parentChatId = id(task.parentChatId), childChatId = id(task.childChatId), rootChatId = id(task.rootChatId),
                parentTaskId = task.parentTaskId?.let(::id), parentToolCallId = id(task.parentToolCallId), executionId = id(task.executionId),
                goal = text(task.goal), events = task.events.map { it.copy(text = text(it.text)) },
                status = if (active) SubagentStatus.INTERRUPTED else task.status, waitingFor = emptyList(),
                completedAt = if (active) System.currentTimeMillis() else task.completedAt, error = if (active) interruptedReason else text(task.error),
                pendingInputs = task.pendingInputs.map { it.copy(id = id(it.id), executionId = id(it.executionId), content = text(it.content)) },
                reports = task.reports.map { it.copy(id = id(it.id), executionId = id(it.executionId), content = text(it.content)) } +
                    if (active) listOf(SubagentReport(executionId = id(task.executionId), status = SubagentStatus.INTERRUPTED, content = interruptedReason, modelName = task.model.modelName)) else emptyList(),
                letters = task.letters.map { it.copy(id = id(it.id), invocationId = id(it.invocationId), senderTaskId = it.senderTaskId?.let(::id), recipientTaskId = it.recipientTaskId?.let(::id), content = text(it.content)) },
            )
        }
        val result = chat(root).copy(parentChatId = root.parentChatId?.let { identities[it] },
            subagentGraph = graph.copy(tasks = tasks, chats = graph.chats.map(::chat)))
        validate(result)
        return result
    }
}