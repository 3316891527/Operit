package com.ai.assistance.operit.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 使用独立内存数据库验证标记投影，不读取设备上的真实聊天。 */
@RunWith(AndroidJUnit4::class)
class AgentMessageMarkersAndroidTest {
    private lateinit var database: AppDatabase
    private var order = 0

    @Before fun setUp() {
        runBlocking {
            database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
            ).build()
            database.chatDao().insertChat(ChatEntity(id = "root", title = "主聊天"))
            database.chatDao().insertChat(ChatEntity(id = "other", title = "另一个聊天"))
        }
    }

    @After fun close() { database.close() }

    private suspend fun insert(sender: String, text: String, chatId: String = "root") {
        database.messageDao().insertMessage(MessageEntity.fromChatMessage(
            chatId, ChatMessage(sender = sender, content = text, timestamp = 1000L + order), order++,
        ))
    }

    @Test fun persistedReportBeforeSummaryStillPreventsRedelivery() = runBlocking {
        val marker = "[Subagent report already-persisted]"
        insert("agent", "$marker\n已落库的报告")
        insert("summary", "压缩后的历史")
        assertEquals(setOf(marker), database.messageDao().findPersistedAgentMessageMarkers(
            "root", setOf(marker, "[Agent message not-yet-persisted]"),
        ))
    }

    @Test fun markerCannotComeFromAnotherConversationOrSender() = runBlocking {
        val marker = "[Agent message isolated]"
        insert("agent", "$marker\n其他会话", "other")
        insert("user", "$marker\n用户引用")
        insert("ai", "$marker\n助手引用")
        insert("summary", "$marker\n摘要引用")
        assertTrue(database.messageDao().findPersistedAgentMessageMarkers("root", setOf(marker)).isEmpty())
    }

    @Test fun quotedOrPrefixOnlyMarkersDoNotAcknowledgeDelivery() = runBlocking {
        val marker = "[Agent message exact]"
        insert("agent", "引用\n$marker")
        insert("agent", "${marker}suffix\n另一条消息")
        assertTrue(database.messageDao().findPersistedAgentMessageMarkers("root", setOf(marker)).isEmpty())
        insert("agent", marker)
        assertEquals(setOf(marker), database.messageDao().findPersistedAgentMessageMarkers("root", setOf(marker)))
    }

    @Test fun lineEndingsMatchTheOriginalFirstLineSemantics() = runBlocking {
        val markers = listOf("[Agent message lf]", "[Agent message crlf]", "[Agent message cr]")
        insert("agent", "${markers[0]}\n正文")
        insert("agent", "${markers[1]}\r\n正文")
        insert("agent", "${markers[2]}\r正文")
        assertEquals(markers.toSet(), database.messageDao().findPersistedAgentMessageMarkers("root", markers.toSet()))
    }

    @Test fun duplicateRowsAndOtherPersistedReportsDoNotExpandTheResult() = runBlocking {
        val marker = "[Subagent report requested]"
        insert("agent", "$marker\n第一次落库")
        insert("agent", "$marker\n重复落库")
        insert("agent", "[Agent message unrelated]\n另一条消息")
        assertEquals(listOf(marker), database.messageDao().getPersistedAgentMessageMarkers("root", listOf(marker)))
    }

    @Test fun oversizedHistoryAndReportBodiesNeverEnterTheResultCursor() = runBlocking {
        // 正文大于常见 CursorWindow，恢复只投影首行，不能重新读取 sections 或整条报告。
        val hugeBody = "长".repeat(3 * 1024 * 1024)
        val marker = "[Subagent report large]"
        insert("ai", hugeBody)
        insert("agent", "$marker\n$hugeBody")
        insert("summary", "已总结")
        assertEquals(setOf(marker), database.messageDao().findPersistedAgentMessageMarkers("root", setOf(marker)))
    }

    @Test fun moreThanOneBatchPreservesAllPersistedMarkers() = runBlocking {
        val markers = (0 until 1001).map { "[Agent message $it]" }.toSet()
        markers.forEach { insert("agent", "$it\n正文") }
        assertEquals(markers, database.messageDao().findPersistedAgentMessageMarkers("root", markers))
    }
}
