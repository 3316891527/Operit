package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.chat.TurnInputInbox

/** 消费计划只改变已验证的收件人记录；消息行与这些变更必须由调用方一起提交。 */
internal object SubagentDelivery {
    fun acknowledge(tasks: List<SubagentTask>, chatId: String, inputs: List<TurnInputInbox.Input>): List<SubagentTask> {
        val updates = linkedMapOf<String, SubagentTask>()
        val own = tasks.singleOrNull { it.childChatId == chatId }
        val root = own?.rootChatId ?: chatId
        fun current(id: String): SubagentTask = updates[id] ?: requireNotNull(tasks.singleOrNull { it.id == id }) { "Message owner no longer exists" }
        inputs.forEach { input ->
            val route = input.agentPath?.split('/') ?: return@forEach
            when (route.firstOrNull()) {
                "parent" -> {
                    require(route.size == 2 && own != null) { "Invalid supplementary input route" }
                    val task = current(own.id)
                    require(task.pendingInputs.any { it.id == route[1] && it.content == input.text }) { "Supplement does not belong to this execution" }
                    updates[task.id] = task.copy(pendingInputs = task.pendingInputs.filterNot { it.id == route[1] })
                }
                "subagent" -> {
                    require(route.size == 3) { "Invalid report route" }
                    val task = current(route[1])
                    require(task.parentChatId == chatId && task.rootChatId == root) { "Report belongs to another parent" }
                    val report = requireNotNull(task.reports.singleOrNull { it.id == route[2] }) { "Unknown report" }
                    require(input.text.startsWith(report.marker)) { "Report marker mismatch" }
                    updates[task.id] = task.copy(reports = task.reports.map { if (it.id == report.id) it.copy(delivered = true) else it })
                }
                "agent_mail" -> {
                    require(route.size == 3) { "Invalid message route" }
                    val task = current(route[1])
                    val letter = requireNotNull(task.letters.singleOrNull { it.id == route[2] }) { "Unknown agent message" }
                    require(task.rootChatId == root && letter.recipientTaskId == own?.id && input.text.startsWith(letter.marker)) { "Message belongs to another recipient" }
                    updates[task.id] = task.copy(letters = task.letters.map { if (it.id == letter.id) it.copy(consumed = true) else it })
                }
            }
        }
        return updates.values.toList()
    }
}