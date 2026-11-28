package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageSection
import org.junit.Assert.*
import org.junit.Test

class WaifuTimingHistoryRestoreTest {
    private val body = "<tool name=\"read_file\"><param name=\"path\">same</param></tool>"

    private fun runningMessage(timestamp: Long = 10L, start: Int = 2): ChatMessage = ChatMessage(
        sender = "ai", content = body, timestamp = timestamp,
        toolTimingState = ToolTimingState(), toolTimingStartIndex = start,
    )

    @Test
    fun reloadedSegmentKeepsObservingTheSameHolderWithoutNewText() {
        val running = runningMessage()
        val state = requireNotNull(running.toolTimingState)
        val loaded = MessageEntity.fromChatMessage("chat", running, 0).toChatMessage()
        assertNull(loaded.toolTimingState)
        assertEquals(0, loaded.toolTimingStartIndex)
        val restored = restoreLiveWaifuTimingRows("chat", listOf(loaded)) { listOf(running) }.single()
        assertSame(state, restored.toolTimingState)
        assertEquals(2, restored.toolTimingStartIndex)

        val started = ToolCallExecution(2, "clock", startedMs = 1_000L)
        state.update(started)
        assertEquals(started.copy(callIndex = 0),
            restored.resolvedSections().filterIsInstance<MessageSection.ToolCall>().single().execution)
        state.update(started.copy(durationMs = 2_000L, endedMs = 3_000L))
        assertEquals(2_000L,
            restored.resolvedSections().filterIsInstance<MessageSection.ToolCall>().single().execution?.durationMs)
        assertEquals(body, restored.content)
        assertNull(restored.contentStream)
    }

    @Test
    fun restorationUsesRequestedChatAndPreservesPageOrderAndFavoriteState() {
        val active = runningMessage()
        val loaded = ChatMessage("ai", "旧正文", timestamp = active.timestamp, isFavorite = true)
        val before = ChatMessage("user", "问题", timestamp = 9L)
        val after = ChatMessage("ai", "另一条", timestamp = 11L)
        val outside = runningMessage(timestamp = 20L)
        val snapshots = mapOf("current" to listOf(active, outside), "other" to listOf(runningMessage(30L)))
        var requested = ""
        val restored = restoreLiveWaifuTimingRows("current", listOf(before, loaded, after)) { chatId ->
            requested = chatId
            snapshots.getValue(chatId)
        }
        assertEquals("current", requested)
        assertEquals(listOf(9L, 10L, 11L), restored.map { it.timestamp })
        assertSame(before, restored.first())
        assertSame(after, restored.last())
        assertTrue(restored[1].isFavorite)
        assertFalse(active.isFavorite)
        assertEquals(body, restored[1].content)
        assertSame(active.toolTimingState, restored[1].toolTimingState)
        assertEquals("旧正文", loaded.content)
    }

    @Test
    fun completedOrMissingRuntimeNeverRevivesAStoredRow() {
        val active = runningMessage()
        val loaded = ChatMessage("ai", body, timestamp = active.timestamp)
        val terminal = active.freezeToolTimings()
        assertSame(loaded, restoreLiveWaifuTimingRows("chat", listOf(loaded)) { listOf(terminal) }.single())
        assertSame(loaded, restoreLiveWaifuTimingRows("chat", listOf(loaded)) { emptyList() }.single())
        assertNull(loaded.toolTimingState)
    }

    @Test
    fun emptyOrUnrelatedPagesDoNotInsertActiveSegments() {
        var requested = false
        val empty = emptyList<ChatMessage>()
        assertSame(empty, restoreLiveWaifuTimingRows("chat", empty) {
            requested = true
            listOf(runningMessage())
        })
        assertFalse(requested)
        val row = ChatMessage("ai", "历史正文", timestamp = 1L)
        val restored = restoreLiveWaifuTimingRows("chat", listOf(row)) { listOf(runningMessage()) }
        assertEquals(1, restored.size)
        assertSame(row, restored.single())
    }
}