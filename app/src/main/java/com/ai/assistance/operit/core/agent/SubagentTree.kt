package com.ai.assistance.operit.core.agent

/** 同一根会话内的树关系只按稳定身份解析，路径用于展示与明确寻址。 */
internal object SubagentTree {
    private val namePattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,47}")

    fun validateName(name: String) {
        require(namePattern.matches(name)) { "Agent name must contain 1–48 ASCII letters, digits, underscores or hyphens" }
    }

    fun rootFor(tasks: List<SubagentTask>, chatId: String): String =
        tasks.singleOrNull { it.childChatId == chatId }?.rootChatId ?: chatId

    fun descendants(tasks: List<SubagentTask>, parentChatId: String): List<SubagentTask> {
        val selected = mutableListOf<SubagentTask>()
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        pending.add(parentChatId)
        while (pending.isNotEmpty()) {
            val chat = pending.removeFirst()
            check(visited.add(chat)) { "Cycle in subagent conversation tree" }
            tasks.filter { it.parentChatId == chat }.forEach { node ->
                selected.add(node)
                pending.add(node.childChatId)
            }
        }
        return selected
    }

    fun canControl(tasks: List<SubagentTask>, chatId: String, target: SubagentTask): Boolean =
        target.rootChatId == chatId || descendants(tasks, chatId).any { it.id == target.id }

    fun validate(tasks: List<SubagentTask>) {
        require(tasks.size <= 4096) { "Too many subagent records" }
        val byId = tasks.associateBy { it.id }
        val childIds = tasks.map { it.childChatId }.toSet()
        require(tasks.none { it.rootChatId in childIds }) { "A tree root cannot also be a child conversation" }
        require(tasks.all { it.id.isNotBlank() && it.childChatId.isNotBlank() && it.rootChatId.isNotBlank() }) { "Missing tree identity" }
        require(byId.size == tasks.size && tasks.map { it.childChatId }.toSet().size == tasks.size) { "Duplicate task or child conversation identity" }
        require(tasks.map { it.rootChatId to it.path }.toSet().size == tasks.size) { "Duplicate agent path" }
        tasks.forEach { node ->
            validateName(node.agentName)
            require(node.depth in 1..8 && node.rootChatId != node.childChatId) { "Invalid agent depth or root identity" }
            if (node.parentTaskId == null) {
                require(node.parentChatId == node.rootChatId && node.depth == 1 && node.path == "/${node.agentName}") { "Invalid root child relationship" }
            } else {
                val parent = requireNotNull(byId[node.parentTaskId]) { "Missing parent task" }
                require(parent.childChatId == node.parentChatId && parent.rootChatId == node.rootChatId &&
                    node.depth == parent.depth + 1 && node.path == "${parent.path}/${node.agentName}") { "Invalid child relationship" }
            }
            require(node.waitingFor.all { it in byId && byId.getValue(it).rootChatId == node.rootChatId && it != node.id }) { "Invalid wait dependency" }
            require(node.letters.map { it.id }.toSet().size == node.letters.size) { "Duplicate agent message identity" }
            node.letters.forEach { letter ->
                require(letter.id.isNotBlank() && letter.invocationId.isNotBlank()) { "Missing agent message identity" }
                require(letter.content.isNotBlank() && letter.content.length <= 10000) { "Invalid agent message" }
                require(letter.senderTaskId == null || byId[letter.senderTaskId]?.rootChatId == node.rootChatId) { "Invalid message sender" }
                require(letter.recipientTaskId == node.id || (letter.recipientTaskId == null && letter.senderTaskId == node.id)) { "Message stored by the wrong owner" }
            }
        }
        tasks.filter { it.waitingFor.isNotEmpty() }.forEach { requireAcyclicWait(tasks, it.id, it.waitingFor) }
    }

    fun requireAcyclicWait(tasks: List<SubagentTask>, callerId: String, targets: List<String>) {
        val byId = tasks.associateBy { it.id }
        val pending = ArrayDeque<String>()
        targets.forEach(pending::addLast)
        val seen = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            require(id != callerId) { "This wait would create a dependency cycle" }
            if (seen.add(id)) byId[id]?.waitingFor.orEmpty().forEach(pending::addLast)
        }
    }

    fun queuePosition(tasks: List<SubagentTask>, taskId: String): Int {
        val root = tasks.find { it.id == taskId }?.rootChatId ?: return 0
        return tasks.filter { it.status == SubagentStatus.QUEUED && it.rootChatId == root }
            .sortedWith(compareBy<SubagentTask> { it.queuedAt }.thenBy { it.id })
            .indexOfFirst { it.id == taskId }.let { if (it < 0) 0 else it + 1 }
    }
}
