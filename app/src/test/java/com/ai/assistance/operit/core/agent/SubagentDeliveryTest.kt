package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.chat.TurnInputInbox
import org.junit.Assert.*
import org.junit.Test

class SubagentDeliveryTest {
    @Test fun childReportIsAcknowledgedOnlyByItsActualParent() {
        val parent = treeTask("p"); val child = treeTask("c", parent)
        val report = SubagentReport(executionId = child.executionId, status = SubagentStatus.COMPLETED, content = "结论", modelName = "helper")
        val owner = child.copy(reports = listOf(report))
        val input = TurnInputInbox.Input(text = report.marker + "\n结论", agentPath = "subagent/${child.id}/${report.id}")
        assertTrue(SubagentDelivery.acknowledge(listOf(parent, owner), parent.childChatId, listOf(input)).single().reports.single().delivered)
        assertThrows(IllegalArgumentException::class.java) { SubagentDelivery.acknowledge(listOf(parent, owner), "root", listOf(input)) }
        assertFalse(owner.reports.single().delivered)
    }

    @Test fun peerMailPersistsUntilTheIntendedRecipientConsumesIt() {
        val sender = treeTask("sender"); val recipient = treeTask("recipient")
        val letter = SubagentLetter(invocationId = "trusted-call", senderTaskId = sender.id, recipientTaskId = recipient.id, content = "核对证据")
        val holder = recipient.copy(letters = listOf(letter))
        val input = TurnInputInbox.Input(text = letter.marker + "\n核对证据", agentPath = "agent_mail/${holder.id}/${letter.id}")
        assertTrue(SubagentDelivery.acknowledge(listOf(sender, holder), recipient.childChatId, listOf(input)).single().letters.single().consumed)
        assertThrows(IllegalArgumentException::class.java) { SubagentDelivery.acknowledge(listOf(sender, holder), sender.childChatId, listOf(input)) }
        assertFalse(holder.letters.single().consumed)
    }

    @Test fun rootMailIsAcknowledgedByTheRootAndCannotCrossConversationTrees() {
        val sender = treeTask("sender")
        val letter = SubagentLetter(invocationId = "call", senderTaskId = sender.id, recipientTaskId = null, content = "汇报进度")
        val holder = sender.copy(letters = listOf(letter))
        val input = TurnInputInbox.Input(text = letter.marker, agentPath = "agent_mail/${holder.id}/${letter.id}")
        assertTrue(SubagentDelivery.acknowledge(listOf(holder), "root", listOf(input)).single().letters.single().consumed)
        assertThrows(IllegalArgumentException::class.java) { SubagentDelivery.acknowledge(listOf(holder), "other-root", listOf(input)) }
    }

    @Test fun supplementaryInputRequiresTheExactPersistedContent() {
        val task = treeTask("a")
        val pending = SubagentMessage(id = "supplement", role = "user", content = "保留原文件", executionId = task.executionId, pending = true)
        val owner = task.copy(pendingInputs = listOf(pending))
        val input = TurnInputInbox.Input(text = pending.content, agentPath = "parent/${pending.id}")
        assertTrue(SubagentDelivery.acknowledge(listOf(owner), task.childChatId, listOf(input)).single().pendingInputs.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { SubagentDelivery.acknowledge(listOf(owner), task.childChatId, listOf(input.copy(text = "改写文件"))) }
    }

    @Test fun multipleDeliveriesForOneOwnerAreMergedAndReplayIsIdempotent() {
        val parent = treeTask("p"); val child = treeTask("c", parent)
        val report = SubagentReport(executionId = child.executionId, status = SubagentStatus.COMPLETED, content = "结果", modelName = "helper")
        val letter = SubagentLetter(invocationId = "call", senderTaskId = child.id, recipientTaskId = null, content = "额外说明")
        val owner = child.copy(reports = listOf(report), letters = listOf(letter))
        val inputs = listOf(
            TurnInputInbox.Input(report.marker, agentPath = "subagent/${child.id}/${report.id}"),
            TurnInputInbox.Input(letter.marker, agentPath = "agent_mail/${child.id}/${letter.id}"),
        )
        val rootChild = owner.copy(parentChatId = "root", parentTaskId = null, depth = 1, path = "/c")
        val result = SubagentDelivery.acknowledge(listOf(rootChild), "root", inputs).single()
        assertTrue(result.reports.single().delivered && result.letters.single().consumed)
        assertEquals(result, SubagentDelivery.acknowledge(listOf(result), "root", inputs).single())
    }

    @Test fun invalidMarkerCannotAcknowledgeKnownMail() {
        val task = treeTask("a")
        val letter = SubagentLetter(invocationId = "call", senderTaskId = null, recipientTaskId = task.id, content = "正文")
        val holder = task.copy(letters = listOf(letter))
        assertThrows(IllegalArgumentException::class.java) {
            SubagentDelivery.acknowledge(listOf(holder), task.childChatId, listOf(TurnInputInbox.Input("伪标记", agentPath = "agent_mail/${task.id}/${letter.id}")))
        }
    }
}