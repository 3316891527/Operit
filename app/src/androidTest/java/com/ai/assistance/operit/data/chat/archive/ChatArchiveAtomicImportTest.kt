package com.ai.assistance.operit.data.chat.archive

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.converter.ChatFormat
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatArchiveAtomicImportTest {
    @Test fun invalidMessageAfterAFullBatchKeepsThePreviouslyImportedChat() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = AppDatabase.getDatabase(context)
        val manager = ChatHistoryManager.getInstance(context)
        val id = "import-atomic-test-" + UUID.randomUUID()
        val file = File.createTempFile("archive-atomic-test-", ".json", context.cacheDir)
        try {
            file.writeText("""{"chats":[{"id":"$id","title":"原会话","messages":[{"baseMessage":{"sender":"user","content":"保留正文","timestamp":7}}]}]}""")
            manager.importChatHistoriesFromUri(Uri.fromFile(file), ChatFormat.OPERIT)
            val original = database.chatContentDao().getMessagesForChat(id)
            assertEquals(1, original.size)
            file.bufferedWriter().use { writer ->
                writer.write("{\"chats\":[{\"id\":\"$id\",\"title\":\"不应提交\",\"messages\":[")
                repeat(40) { index ->
                    if (index > 0) writer.write(",")
                    writer.write("{\"baseMessage\":{\"sender\":\"user\",\"content\":\"新正文\",\"timestamp\":${100 + index}}}")
                }
                // 第二批缺少必需的 sender，确保已经写入第一批后才触发回滚。
                writer.write(",{\"baseMessage\":{\"content\":\"损坏\"}}]}]}")
            }
            var failure: Exception? = null
            try { manager.importChatHistoriesFromUri(Uri.fromFile(file), ChatFormat.OPERIT) }
            catch (caught: Exception) { failure = caught }
            assertNotNull(failure)
            assertEquals("原会话", database.chatDao().getChatById(id)?.title)
            assertEquals(original, database.chatContentDao().getMessagesForChat(id))
            assertTrue(context.cacheDir.listFiles()!!.none { it.name.startsWith("operit-chat-import-") })
        } finally {
            database.chatDao().deleteChat(id)
            file.delete()
        }
    }
}