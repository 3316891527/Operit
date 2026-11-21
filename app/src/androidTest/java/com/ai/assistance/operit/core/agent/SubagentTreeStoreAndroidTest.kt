package com.ai.assistance.operit.core.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatKind
import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 Android SQLite 验证事务与外键；测试存储采用独立内存数据库，不调用真实模型和终端。 */
@RunWith(AndroidJUnit4::class)
class SubagentTreeStoreAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var store: SubagentTaskStore
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = SubagentTaskStore(context, database)
    }

    @After fun close() { database.close() }

    private fun task(id: String, parent: SubagentTask? = null) = SubagentTask(
        id = id, parentChatId = parent?.childChatId ?: "root", childChatId = "$id-chat", parentToolCallId = "$id-call",
        title = id, goal = "保留任务与消息", preset = SubagentPreset(id = "p", name = "帮手", description = "分析", systemPrompt = "独立工作"),
        model = SubagentModel(id = "m", configId = "c", modelName = "helper", capabilities = "分析"),
        rootChatId = "root", parentTaskId = parent?.id, agentName = id, path = (parent?.path ?: "") + "/$id", depth = (parent?.depth ?: 0) + 1,
        status = SubagentStatus.COMPLETED,
    )

    @Test fun nestedCreateExportImportAndLeafFirstDeletePreserveGraph() = runBlocking {
        database.chatDao().insertChat(ChatEntity(id = "root", title = "主聊天"))
        val a = store.create(task("a")); val b = store.create(task("b", a))
        assertEquals(ChatKind.SUBAGENT, database.chatDao().getChatById(b.childChatId)!!.chatKind)
        assertEquals(a.childChatId, database.chatDao().getChatById(b.childChatId)!!.parentChatId)
        database.messageDao().insertMessage(com.ai.assistance.operit.data.model.MessageEntity.fromChatMessage("root", ChatMessage("user", "主会话快照"), 0))
        val root = requireNotNull(store.exportArchive("root"))
        assertEquals("主会话快照", root.messages.single().baseMessage.content)
        val imported = SubagentGraphTransfer.remap(root, "中断")
        val restored = store.importGraph(imported)
        assertEquals(2, restored.size)
        assertTrue(restored.none { it.id == a.id || it.id == b.id })
        assertEquals(2, store.deleteForParent("root").size)
        assertNull(database.chatDao().getChatById(a.childChatId))
        assertNull(database.chatDao().getChatById(b.childChatId))
        assertEquals(2, store.load().size)
        assertNotNull(database.chatDao().getChatById(imported.id))
    }

    @Test fun failedBatchDoesNotPartiallyArchiveAnyNode() = runBlocking {
        database.chatDao().insertChat(ChatEntity(id = "root", title = "主聊天"))
        val a = store.create(task("a")); val b = store.create(task("b", a))
        var rejected = false
        try { store.saveAll(listOf(a.copy(archived = true), b.copy(archived = true)), mapOf(a.id to a.executionId, b.id to "stale")) }
        catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertTrue(store.load().none { it.archived })
        assertFalse(database.chatDao().getChatById(a.childChatId)!!.archived)
    }

    @Test fun mailConsumptionAndRecipientMessageCommitTogether() = runBlocking {
        database.chatDao().insertChat(ChatEntity(id = "root", title = "主聊天"))
        val a = store.create(task("a")); val b = store.create(task("b"))
        val letter = SubagentLetter(invocationId = "trusted", senderTaskId = a.id, recipientTaskId = b.id, content = "核对结果")
        val holder = store.save(b.copy(letters = listOf(letter)), b.executionId)
        val input = com.ai.assistance.operit.core.chat.TurnInputInbox.Input(letter.marker + "\n核对结果", agentPath = "agent_mail/${b.id}/${letter.id}")
        val updates = SubagentDelivery.acknowledge(listOf(a, holder), b.childChatId, listOf(input))
        var rejected = false
        try { store.saveAll(updates, mapOf(b.id to "stale"), b.id, listOf(ChatMessage(sender = "agent", content = input.text))) }
        catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertFalse(store.load().single { it.id == b.id }.letters.single().consumed)
        assertFalse(store.transcript(holder).any { it.sender == "agent" })
        store.saveAll(updates, mapOf(b.id to b.executionId), b.id, listOf(ChatMessage(sender = "agent", content = input.text)))
        val restored = store.load().single { it.id == b.id }
        assertTrue(restored.letters.single().consumed)
        assertTrue(store.transcript(restored).any { it.sender == "agent" && it.content.startsWith(letter.marker) })
    }
}