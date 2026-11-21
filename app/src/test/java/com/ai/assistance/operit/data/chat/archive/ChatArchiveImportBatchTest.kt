package com.ai.assistance.operit.data.chat.archive

import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatArchiveImportBatchTest {
    private fun message(index: Int, size: Int = 1) = MessageEntity(chatId = "chat", sender = "user", sections = "x".repeat(size), searchText = "", timestamp = index.toLong(), orderIndex = index)

    @Test fun messageCountBoundsBatchesWithoutChangingOrder() = runBlocking {
        val sizes = mutableListOf<Int>()
        val order = mutableListOf<Int>()
        val batch = ChatArchiveImportBatch(maximumMessages = 3) { messages, _ -> sizes += messages.size; order += messages.map { it.orderIndex } }
        repeat(8) { batch.add(message(it), emptyList()) }
        batch.flush()
        assertEquals(listOf(3, 3, 2), sizes)
        assertEquals((0..7).toList(), order)
    }

    @Test fun characterBudgetIncludesVariantsAndOversizedMessagesAreWrittenAlone() = runBlocking {
        val sizes = mutableListOf<Int>()
        val variantIndices = mutableListOf<Int>()
        val batch = ChatArchiveImportBatch(maximumCharacters = 10) { messages, variants ->
            sizes += messages.size
            variantIndices += variants.map { it.variantIndex }
        }
        batch.add(message(0, 4), emptyList())
        batch.add(message(1, 4), listOf(MessageVariantEntity(chatId = "chat", messageTimestamp = 1, variantIndex = 1, sections = "1234", searchText = "")))
        batch.add(message(2, 11), emptyList())
        batch.add(message(3, 2), emptyList())
        batch.flush()
        assertEquals(listOf(1, 1, 1, 1), sizes)
        assertEquals(listOf(1), variantIndices)
    }
}
