package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.data.chat.archive.OperitArchivedChat
import com.ai.assistance.operit.data.chat.archive.OperitArchivedMessage
import com.ai.assistance.operit.data.chat.archive.OperitArchivedMessageVariant
import com.ai.assistance.operit.data.model.ChatKind
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SubagentGraphTransferTest {
    private fun archive(task: SubagentTask, messages: List<OperitArchivedMessage> = emptyList()) = OperitArchivedChat(
        id = task.childChatId, title = task.title, parentChatId = task.parentChatId, chatKind = ChatKind.SUBAGENT, archived = task.archived,
        messages = listOf(OperitArchivedMessage(ChatMessage(sender = "user", content = task.goal, timestamp = task.requestTimestamp))) + messages,
    )
    private fun graph(tasks: List<SubagentTask>, children: List<OperitArchivedChat> = tasks.map { archive(it) }) = OperitArchivedChat(
        id = "root", title = "主会话", messages = listOf(OperitArchivedMessage(ChatMessage(sender = "user", content = "继续工作"))),
        subagentGraph = SubagentGraphArchive(tasks = tasks, chats = children),
    )

    @Test fun nestedGraphsRoundTripAndImportCreatesIndependentIdentities() {
        val a = treeTask("a", status = SubagentStatus.COMPLETED); val b = treeTask("b", a, status = SubagentStatus.COMPLETED)
        val original = graph(listOf(a, b))
        val json = Json { encodeDefaults = true }
        val decoded = json.decodeFromString<OperitArchivedChat>(json.encodeToString(original))
        val result = SubagentGraphTransfer.remap(decoded, "中断")
        val restored = requireNotNull(result.subagentGraph)
        assertNotEquals(original.id, result.id)
        assertTrue(restored.tasks.none { it.id == a.id || it.id == b.id })
        assertEquals(result.id, restored.tasks.first().rootChatId)
        assertEquals(restored.tasks.first().childChatId, restored.tasks.last().parentChatId)
        assertEquals(restored.tasks.first().id, restored.tasks.last().parentTaskId)
        assertEquals(listOf(a.path, b.path), restored.tasks.map { it.path })
    }

    @Test fun activeExecutionsRestoreAsInterruptedAndNeverAutoRun() {
        for (status in listOf(SubagentStatus.QUEUED, SubagentStatus.RUNNING, SubagentStatus.WAITING)) {
            val task = treeTask("a", status = status)
            val restored = SubagentGraphTransfer.remap(graph(listOf(task)), "导入后中断").subagentGraph!!.tasks.single()
            assertEquals(SubagentStatus.INTERRUPTED, restored.status)
            assertTrue(restored.waitingFor.isEmpty())
            assertEquals("导入后中断", restored.reports.last().content)
            assertFalse(restored.reports.last().delivered)
        }
    }

    @Test fun forgedCrossRootMissingChildAndDuplicateMessagesAreRejected() {
        val a = treeTask("a"); val original = graph(listOf(a))
        val payload = requireNotNull(original.subagentGraph)
        for (invalid in listOf(payload.copy(chats = emptyList()), payload.copy(tasks = listOf(a.copy(rootChatId = "other"))),
            payload.copy(chats = listOf(payload.chats.single().copy(parentChatId = "wrong"))))) {
            assertThrows(IllegalArgumentException::class.java) { SubagentGraphTransfer.validate(original.copy(subagentGraph = invalid)) }
        }
        val duplicate = payload.chats.single().let { it.copy(messages = it.messages + it.messages.single()) }
        assertThrows(IllegalArgumentException::class.java) { SubagentGraphTransfer.validate(original.copy(subagentGraph = payload.copy(chats = listOf(duplicate)))) }
    }

    @Test fun reportsMailCallReferencesAndVariantsRemapWithoutChangingProviderPayload() {
        val a = treeTask("a", status = SubagentStatus.COMPLETED)
        val report = SubagentReport(id = "report-old", executionId = a.executionId, status = a.status, content = "证据", modelName = "helper", delivered = true)
        val letter = SubagentLetter(id = "mail-old", invocationId = "mail-call", senderTaskId = a.id, recipientTaskId = null, content = "说明", consumed = true)
        val task = a.copy(reports = listOf(report), letters = listOf(letter))
        val sections = listOf(MessageSection.ToolCall("inspect_task", mapOf("task_id" to a.id)),
            MessageSection.Protocol("openai:responses_reasoning", a.id), MessageSection.Text(report.marker + "\n" + letter.marker))
        val extra = OperitArchivedMessage(ChatMessage("ai", content = com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec.render(sections), sections = sections),
            variants = listOf(OperitArchivedMessageVariant(1, listOf(MessageSection.ToolCall("stop_task", mapOf("execution_id" to a.executionId))))))
        val root = graph(listOf(task), listOf(archive(task, listOf(extra))))
        val restored = SubagentGraphTransfer.remap(root, "中断").subagentGraph!!
        val next = restored.tasks.single(); val message = restored.chats.single().messages.last()
        assertEquals(next.id, (message.baseMessage.sections.first() as MessageSection.ToolCall).params["task_id"])
        assertEquals(a.id, (message.baseMessage.sections[1] as MessageSection.Protocol).payload)
        assertTrue(message.baseMessage.content.contains(next.reports.single().marker))
        assertTrue(message.baseMessage.content.contains(next.letters.single().marker))
        assertEquals(next.executionId, (message.variants.single().sections.single() as MessageSection.ToolCall).params["execution_id"])
        assertTrue(next.reports.single().delivered && next.letters.single().consumed)
    }

    @Test fun oldOrdinaryChatArchivesRemainReadableWithNoGraph() {
        val restored = Json.decodeFromString<OperitArchivedChat>("""{"id":"old","title":"旧聊天","messages":[]}""")
        assertEquals(ChatKind.NORMAL, restored.chatKind)
        assertNull(restored.subagentGraph)
    }

    @Test fun invalidMailboxOwnershipIsRejectedBeforeAnyImport() {
        val task = treeTask("a").copy(letters = listOf(SubagentLetter(invocationId = "call", senderTaskId = "missing", recipientTaskId = "a", content = "正文")))
        assertThrows(IllegalArgumentException::class.java) { SubagentGraphTransfer.validate(graph(listOf(task))) }
    }

    @Test fun remappingToolParametersPreservesCallAttributesAndResultPairing() {
        val task = treeTask("a", status = SubagentStatus.COMPLETED)
        val raw = """<tool name="stop_task" call_id="${task.parentToolCallId}"><param name="task_id">${task.id}</param><param name="execution_id">${task.executionId}</param></tool>"""
        val result = """<tool_result name="stop_task" call_id="${task.parentToolCallId}"><content>{"task_id":"${task.id}"}</content></tool_result>"""
        val message = OperitArchivedMessage(ChatMessage("ai", raw + result))
        val imported = SubagentGraphTransfer.remap(graph(listOf(task), listOf(archive(task, listOf(message)))), "中断").subagentGraph!!
        val next = imported.tasks.single()
        val restored = imported.chats.single().messages.last().baseMessage
        val call = restored.sections.first() as MessageSection.ToolCall
        assertEquals(next.id, call.params["task_id"])
        assertTrue(call.raw.contains("call_id=\"${next.parentToolCallId}\""))
        assertTrue(restored.content.contains("call_id=\"${next.parentToolCallId}\""))
        assertFalse(restored.content.contains("call_id=\"${task.parentToolCallId}\""))
        val parsed = com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec.parse(restored.content).first() as MessageSection.ToolCall
        assertEquals(next.executionId, parsed.params["execution_id"])
    }

    @Test fun unrelatedToolParametersKeepTheirOwnIdentityNamespace() {
        val task = treeTask("a", status = SubagentStatus.COMPLETED)
        val parameter = """{"task_id":"${task.id}"}"""
        val raw = """<tool name="external_tool"><param name="task_id">${task.id}</param><param name="payload">$parameter</param></tool>"""
        val section = MessageSection.ToolCall("external_tool", mapOf("task_id" to task.id, "payload" to parameter), raw)
        val message = OperitArchivedMessage(ChatMessage("ai", sections = listOf(section), content = com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec.render(listOf(section))))
        val imported = SubagentGraphTransfer.remap(graph(listOf(task), listOf(archive(task, listOf(message)))), "中断").subagentGraph!!
        val restored = imported.chats.single().messages.last().baseMessage
        assertEquals(task.id, (restored.sections.single() as MessageSection.ToolCall).params["task_id"])
        assertTrue(restored.content.contains(parameter))
    }

    @Test fun priorExecutionSupplementsAndMailboxInvocationsReceiveFreshIdentities() {
        val a = treeTask("a", status = SubagentStatus.COMPLETED)
        val pending = SubagentMessage(id = "pending", role = "user", content = "旧补充", executionId = "previous-execution", pending = true)
        val letter = SubagentLetter(id = "letter", invocationId = "send-call", senderTaskId = null, recipientTaskId = a.id, content = "新消息")
        val imported = SubagentGraphTransfer.remap(graph(listOf(a.copy(pendingInputs = listOf(pending), letters = listOf(letter)))), "中断").subagentGraph!!.tasks.single()
        assertNotEquals(pending.id, imported.pendingInputs.single().id)
        assertNotEquals(pending.executionId, imported.pendingInputs.single().executionId)
        assertNotEquals(letter.invocationId, imported.letters.single().invocationId)
        assertEquals(imported.id, imported.letters.single().recipientTaskId)
        assertFalse(imported.letters.single().consumed)
    }

    @Test fun streamedGraphArchiveRetainsRootMessagesAndNestedHistory() = kotlinx.coroutines.runBlocking {
        val directory = kotlin.io.path.createTempDirectory("operit-integrated-graph").toFile()
        try {
            val parent = treeTask("parent", status = SubagentStatus.COMPLETED)
            val child = treeTask("child", parent, status = SubagentStatus.COMPLETED)
            val original = graph(listOf(parent, child))
            val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
            val payload = json.encodeToString(com.ai.assistance.operit.data.chat.archive.OperitChatArchive(chats = listOf(original)))
            var decoded: OperitArchivedChat? = null
            com.ai.assistance.operit.data.chat.archive.OperitChatArchiveReader(json, directory).read(
                java.io.StringReader(payload), {}, { staged ->
                    assertTrue(staged.metadata.messages.isEmpty())
                    assertNotNull(staged.metadata.subagentGraph)
                    decoded = staged.readGraph()
                },
            )
            val imported = SubagentGraphTransfer.remap(requireNotNull(decoded), "中断")
            assertEquals(original.messages, requireNotNull(decoded).messages)
            assertEquals(2, requireNotNull(imported.subagentGraph).tasks.size)
            assertEquals(child.goal, requireNotNull(imported.subagentGraph).chats.last().messages.single().baseMessage.content)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }
}