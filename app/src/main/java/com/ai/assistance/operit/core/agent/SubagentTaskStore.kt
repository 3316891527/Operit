package com.ai.assistance.operit.core.agent

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.core.chat.context.TokenUsageBreakdown
import com.ai.assistance.operit.data.chat.archive.OperitArchivedChat
import com.ai.assistance.operit.data.chat.archive.OperitArchivedMessage
import com.ai.assistance.operit.data.chat.archive.OperitArchivedMessageVariant
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatKind
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.ui.common.a2ui.A2uiStateStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 运行快照与消息共用 Room 事务；已消费的会话内容只有 messages 一份。 */
internal class SubagentTaskStore(private val context: Context, private val database: AppDatabase = AppDatabase.getDatabase(context)) {
    private val chats = database.chatDao()
    private val runs = database.subagentRunDao()
    private val messages = database.messageDao()
    private val content = database.chatContentDao()
    private val json = Json { encodeDefaults = true }

    suspend fun load(): List<SubagentTask> = database.withTransaction {
        val stored = runs.getAll().map(::decode)
        SubagentTree.validate(stored)
        stored.map { task ->
            requireChild(task)
            project(task)
        }
    }

    suspend fun create(task: SubagentTask, inheritedContext: ChatMessage? = null): SubagentTask = database.withTransaction {
        check(runs.getById(task.id) == null) { "Task already exists" }
        val parent = requireNotNull(chats.getChatById(task.parentChatId)) { "Parent conversation no longer exists" }
        val all = runs.getAll().map(::decode)
        SubagentTree.validate(all + task)
        check(chats.getChatById(task.rootChatId)?.chatKind == ChatKind.NORMAL) { "Invalid tree root" }
        check((task.parentTaskId == null) == (parent.chatKind == ChatKind.NORMAL)) { "Invalid parent conversation kind" }
        check(chats.getChatById(task.childChatId) == null) { "Child conversation identity already exists" }
        val child = ChatEntity(
            id = task.childChatId, title = task.title,
            createdAt = task.createdAt, updatedAt = task.createdAt,
            parentChatId = parent.id, chatKind = ChatKind.SUBAGENT,
            group = parent.group, folderId = parent.folderId,
            workspace = task.workspacePath, workspaceEnv = task.workspaceEnv,
            characterCardName = parent.characterCardName, characterGroupId = parent.characterGroupId,
        )
        chats.insertChat(child)
        runs.insert(entity(task))
        require((inheritedContext == null) == (task.forkTurns == "none")) { "Context snapshot does not match the selected fork" }
        if (inheritedContext != null) {
            check(inheritedContext.sender == "summary" && inheritedContext.timestamp < task.requestTimestamp) { "Inherited context must precede the new brief" }
            persistMessage(task.childChatId, inheritedContext)
        }
        persistMessage(task.childChatId, ChatMessage(sender = "user", content = task.goal, timestamp = task.requestTimestamp))
        project(task)
    }

    suspend fun save(
        task: SubagentTask,
        expectedExecutionId: String,
        changedMessages: List<ChatMessage> = emptyList(),
        usage: TokenUsageBreakdown? = null,
        windowSize: Long? = null,
    ): SubagentTask = database.withTransaction {
        val previous = decode(requireNotNull(runs.getById(task.id)) { "Task no longer exists" })
        check(previous.executionId == expectedExecutionId) { "The stored execution identity changed" }
        SubagentTransitions.validate(previous, task)
        val child = requireChild(task)
        val next = if (previous.executionId != task.executionId)
            task.copy(executionInputBase = child.inputTokens, executionOutputBase = child.outputTokens) else task
        changedMessages.forEach { persistMessage(next.childChatId, it) }
        if (usage != null) {
            val input = next.executionInputBase + usage.inputTokens
            val output = next.executionOutputBase + usage.outputTokens
            val total = TokenUsageBreakdown.advance(child.usageBreakdownJson, child.inputTokens, child.outputTokens, input, output, usage)
            chats.updateChatTokenUsage(next.childChatId, input, output,
                windowSize ?: child.currentWindowSize, total.encode())
        }
        chats.updateChatArchived(next.childChatId, next.archived)
        check(runs.update(entity(next)) == 1) { "Task disappeared while saving" }
        project(next)
    }

    /** 继续执行严格读取真实子聊天，不能把读取失败解释成空历史。 */
    suspend fun transcript(task: SubagentTask): List<ChatMessage> = database.withTransaction {
        val stored = decode(requireNotNull(runs.getById(task.id)) { "Task no longer exists" })
        check(stored.executionId == task.executionId) { "The stored execution identity changed" }
        requireChild(stored)
        content.getMessagesForChat(stored.childChatId).map { it.toChatMessage() }
    }

    suspend fun deleteForParent(chatId: String): List<String> = database.withTransaction {
        val children = SubagentTree.descendants(runs.getAll().map(::decode), chatId).sortedByDescending { it.depth }.map { task ->
            val child = requireChild(task)
            check(!task.status.isActive) { "Stop the child execution before deleting its conversation" }
            check(!child.locked) { "Child conversation is locked" }
            child
        }
        // 先验证整组，再在同一事务删除，避免中途失败留下半组子会话。
        children.forEach { chats.deleteChat(it.id) }
        children.map { it.id }
    }

    suspend fun exportGraph(rootChatId: String): SubagentGraphArchive? = database.withTransaction {
        val tasks = SubagentTree.descendants(runs.getAll().map(::decode), rootChatId)
        if (tasks.isEmpty()) return@withTransaction null
        SubagentTree.validate(tasks)
        val children = tasks.map { task ->
            val child = requireChild(task)
            val rows = content.getMessagesForChat(child.id)
            val variants = content.getVariantsForMessages(child.id, rows.map { it.timestamp }).groupBy { it.messageTimestamp }
            OperitArchivedChat.fromChatHistory(child.toChatHistory(emptyList()), rows.map { row ->
                OperitArchivedMessage(row.toChatMessage(), variants[row.timestamp].orEmpty().map(OperitArchivedMessageVariant::fromEntity))
            })
        }
        val graph = SubagentGraphArchive(tasks = tasks, chats = children)
        SubagentGraphTransfer.validate(OperitArchivedChat(id = rootChatId, title = "", messages = emptyList(), subagentGraph = graph))
        graph
    }

    /** 主聊天与全部子节点在同一数据库快照中读取，导出期间的消息追加不会拆散图。 */
    suspend fun exportArchive(rootChatId: String): OperitArchivedChat? = database.withTransaction {
        val graph = exportGraph(rootChatId) ?: return@withTransaction null
        val root = requireNotNull(chats.getChatById(rootChatId)) { "Root conversation no longer exists" }
        val rows = content.getMessagesForChat(rootChatId)
        val variants = content.getVariantsForMessages(rootChatId, rows.map { it.timestamp }).groupBy { it.messageTimestamp }
        OperitArchivedChat.fromChatHistory(root.toChatHistory(emptyList()), rows.map { row ->
            OperitArchivedMessage(row.toChatMessage(), variants[row.timestamp].orEmpty().map(OperitArchivedMessageVariant::fromEntity))
        }).copy(subagentGraph = graph).also(SubagentGraphTransfer::validate)
    }

    suspend fun importGraph(root: OperitArchivedChat): List<SubagentTask> = database.withTransaction {
        SubagentGraphTransfer.validate(root)
        val graph = requireNotNull(root.subagentGraph)
        val allChats = listOf(root) + graph.chats
        check(allChats.all { chats.getChatById(it.id) == null } && graph.tasks.all { runs.getById(it.id) == null }) { "Imported graph identity already exists" }
        allChats.forEach { archived -> chats.insertChat(ChatEntity.fromChatHistory(archived.toChatHistory())) }
        allChats.forEach { archived ->
            archived.messages.forEach { row ->
                persistMessage(archived.id, row.baseMessage)
                database.messageVariantDao().insertVariants(row.variants.map { it.toEntity(archived.id, row.baseMessage.timestamp) })
            }
        }
        graph.tasks.forEach { runs.insert(entity(it)) }
        graph.tasks.map { project(it) }
    }

    /** 多节点修改先在一个事务落盘，再由协调器一次发布，不能展示半组状态。 */
    suspend fun saveAll(tasks: List<SubagentTask>, expected: Map<String, String>, messageTaskId: String? = null,
        changedMessages: List<ChatMessage> = emptyList(), usage: TokenUsageBreakdown? = null, windowSize: Long? = null): List<SubagentTask> = database.withTransaction {
        tasks.map { task -> save(task, expected.getValue(task.id),
            if (task.id == messageTaskId) changedMessages else emptyList(),
            if (task.id == messageTaskId) usage else null, if (task.id == messageTaskId) windowSize else null) }
    }

    suspend fun clearDeletedState(childIds: List<String>) {
        childIds.forEach { A2uiStateStore.deleteChat(context, it) }
    }

    private suspend fun requireChild(task: SubagentTask): ChatEntity {
        val child = requireNotNull(chats.getChatById(task.childChatId)) { "Child conversation no longer exists" }
        check(child.chatKind == ChatKind.SUBAGENT && child.parentChatId == task.parentChatId) { "Invalid child conversation ownership" }
        check(chats.getChatById(task.rootChatId)?.chatKind == ChatKind.NORMAL) { "Invalid root conversation" }
        val parent = requireNotNull(chats.getChatById(task.parentChatId)) { "Missing direct parent" }
        check((task.parentTaskId == null) == (parent.chatKind == ChatKind.NORMAL)) { "Invalid parent conversation" }
        return child
    }

    private suspend fun persistMessage(chatId: String, message: ChatMessage) {
        val saved = content.getMessageByTimestamp(chatId, message.timestamp)
        val row = MessageEntity.fromChatMessage(
            chatId, message.freezeToolTimings(),
            saved?.orderIndex ?: ((messages.getMaxOrderIndex(chatId) ?: -1) + 1),
            saved?.messageId ?: 0L,
        )
        if (saved == null) messages.insertMessage(row) else messages.updateMessage(row)
    }

    private suspend fun project(task: SubagentTask): SubagentTask {
        val transcript = content.getMessagesForChat(task.childChatId).map { it.toChatMessage() }
        val active = transcript.singleOrNull { (task.status == SubagentStatus.RUNNING || task.status == SubagentStatus.WAITING) && it.timestamp == task.assistantTimestamp }
        return task.copy(
            messages = transcript.filterNot { it === active }.map { message ->
                SubagentMessage(id = message.timestamp.toString(), role = if (message.sender == "ai") "assistant" else message.sender,
                    content = message.content, executionId = task.executionId, createdAt = message.timestamp)
            } + task.pendingInputs,
            currentOutput = active?.content.orEmpty(),
        )
    }

    private fun entity(task: SubagentTask) = SubagentRunEntity(
        id = task.id, parentChatId = task.parentChatId, childChatId = task.childChatId,
        parentToolCallId = task.parentToolCallId, executionId = task.executionId,
        status = task.status.name, archived = task.archived, createdAt = task.createdAt,
        updatedAt = System.currentTimeMillis(), snapshotJson = json.encodeToString(task),
    )

    private fun decode(row: SubagentRunEntity): SubagentTask {
        val task = json.decodeFromString<SubagentTask>(row.snapshotJson)
        check(task.executionTimeoutMinutes in 1..120 && SubagentFork.parse(task.forkTurns).parameter == task.forkTurns) { "Invalid persisted subagent execution options" }
        check(task.schemaVersion == 2 && task.id == row.id && task.parentChatId == row.parentChatId &&
            task.childChatId == row.childChatId && task.parentToolCallId == row.parentToolCallId &&
            task.executionId == row.executionId && task.status.name == row.status && task.archived == row.archived) {
            "Invalid persisted subagent run: ${row.id}"
        }
        return task
    }
}
