package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuSegmentMessageStore
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuSegmentUpdate.Append
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuSegmentUpdate.Preview
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuSegmentUpdate.Retain
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuSegmentUpdate.Revise
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WaifuSegmentToolTimingTest {
    private val call = "<tool name=\"same\"></tool>"
    private val result = "<tool_result name=\"same\"><content>$call</content></tool_result>"

    private class Fixture {
        val state = ToolTimingState()
        val segments = mutableListOf<ChatMessage>()
        val published = mutableMapOf<Long, ChatMessage>()
        var writes = 0
            private set

        private fun publish(message: ChatMessage) {
            published[message.timestamp] = message
            writes++
        }

        val store = WaifuSegmentMessageStore(
            messages = segments,
            createMessage = { ChatMessage("ai", content = it) },
            reviseMessage = { message, content -> message.copy(content = content, sections = emptyList()) },
            prepareMessage = { message, previous -> message.bindWaifuToolTimings(previous, state) },
            appendMessage = { message, _ -> publish(message) },
            publishPreview = { publish(it) },
            publishRevision = { publish(it) },
            removeMessages = { removed -> removed.forEach { published.remove(it.timestamp) } },
        )
    }

    private fun ChatMessage.executions() = resolvedSections().filterIsInstance<MessageSection.ToolCall>()
        .map { it.execution }

    private fun completed(index: Int, duration: Long) = ToolCallExecution(
        index, "test-clock", startedMs = 1_000L, durationMs = duration, endedMs = 1_000L + duration,
    )

    @Test
    fun unchangedSegmentObservesTimingWithoutAnotherStoreWrite() = runTest {
        val fixture = Fixture()
        fixture.store.apply(Append(call))
        val message = fixture.segments.single()
        val writes = fixture.writes
        assertNull(message.contentStream)
        assertSame(fixture.state, message.toolTimingState)
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            requireNotNull(message.toolTimingState).records.first { it.singleOrNull()?.startedMs != null }
        }

        val started = ToolCallExecution(0, "test-clock", startedMs = 1_000L)
        fixture.state.update(started)

        assertEquals(listOf(started), message.localToolTimings(observed.await()))
        assertEquals(listOf(started), message.executions())
        assertEquals(call, message.content)
        assertEquals(writes, fixture.writes)
        assertSame(message, fixture.segments.single())
    }

    @Test
    fun revisionsAndRollbackReprojectSameNameCallsWithoutCountingResultPayload() = runTest {
        val fixture = Fixture()
        listOf(50L, 100L, 150L).forEachIndexed { index, duration -> fixture.state.update(completed(index, duration)) }
        fixture.store.apply(Preview("未定稿正文"))
        fixture.store.apply(Append(call + result))
        fixture.store.apply(Append("正文。"))
        fixture.store.apply(Append(call))
        val later = fixture.segments.last()
        assertEquals(1, later.toolTimingStartIndex)
        assertEquals(listOf(100L), later.executions().map { it?.durationMs })

        fixture.store.apply(Revise(0, call + result + call))
        assertEquals(listOf(50L, 100L), fixture.segments.first().executions().map { it?.durationMs })
        val moved = fixture.published.getValue(later.timestamp)
        assertEquals(2, moved.toolTimingStartIndex)
        assertEquals(listOf(150L), moved.executions().map { it?.durationMs })

        fixture.store.apply(Retain(1))
        assertNull(fixture.published[later.timestamp])
        fixture.store.apply(Revise(0, call + result))
        fixture.store.apply(Append("新正文。"))
        fixture.store.apply(Append(call))
        val replacement = fixture.segments.last()
        assertNotEquals(later.timestamp, replacement.timestamp)
        assertEquals(1, replacement.toolTimingStartIndex)
        assertEquals(listOf(100L), replacement.executions().map { it?.durationMs })
    }

    @Test
    fun terminalSegmentsRoundTripOnlyTheirOwnCompletedAndStoppedRecords() = runTest {
        val fixture = Fixture()
        fixture.store.apply(Append(call))
        fixture.store.apply(Append("正文。"))
        fixture.store.apply(Append(call + call))
        fixture.state.update(completed(0, 50L))
        fixture.state.update(completed(1, 200L))
        fixture.state.update(ToolCallExecution(2, "test-clock", startedMs = 2_000L))
        fixture.state.stopActive(2_500L)

        val terminal = fixture.segments.last().freezeWaifuSegment(ChatMessage("ai", completedAt = 20_000L))
        fixture.state.update(completed(2, 2_000L))
        val reloaded = MessageEntity.fromChatMessage("chat", terminal, 0).toChatMessage()
        listOf(terminal, reloaded).forEach { message ->
            assertNull(message.toolTimingState)
            assertEquals(0, message.toolTimingStartIndex)
            assertEquals(call + call, message.content)
            val (completed, stopped) = message.executions().map(::requireNotNull)
            assertEquals(0, completed.callIndex)
            assertEquals(200L, completed.durationMs)
            assertEquals(1, stopped.callIndex)
            assertTrue(stopped.stopped)
            assertEquals(2_500L, stopped.endedMs)
            assertNull(stopped.durationMs)
        }
    }
}
