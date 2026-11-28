package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolExecutionTiming
import com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.data.model.MessageVariantEntity
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.ToolTimingIndex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.SharedStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 通过实际分段、持久化和 UI 索引检查计时归属，不只检查某个耗时字段是否仍存在。 */
class SteeredToolTimingAssociationTest {
    private val call = "<tool name=\"read_file\"><param name=\"path\">same</param></tool>"
    private val result = "<tool_result name=\"read_file\" status=\"success\"><content>done</content></tool_result>"
    private val completedCall = "$call\n$result\n"
    private val first = ToolCallExecution(0, "saved-clock", startedMs = 1_000L, durationMs = 100L, endedMs = 1_100L)
    private val second = ToolCallExecution(1, "saved-clock", startedMs = 20_000L, durationMs = 2_000L, endedMs = 22_000L)
    private val third = ToolCallExecution(2, "saved-clock", startedMs = 30_000L, durationMs = 3_000L, endedMs = 33_000L)

    private fun source(content: String, records: List<ToolCallExecution>): ChatMessage {
        val timings = ToolTimingState()
        records.forEach(timings::update)
        return ChatMessage(sender = "ai", timestamp = 1L, content = content, toolTimingState = timings)
    }

    private fun index(message: ChatMessage, live: Boolean = false): ToolTimingIndex {
        val sections = message.resolvedSections()
        val nodes = sections.mapNotNull { section ->
            val raw = when (section) {
                is MessageSection.ToolCall -> section.raw
                is MessageSection.ToolResult -> section.raw
                else -> null
            }
            raw?.let { MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, it, emptyList()) }
        }
        val records = if (live) message.localToolTimings(requireNotNull(message.toolTimingState).records.value)
            else sections.filterIsInstance<MessageSection.ToolCall>().mapNotNull { it.execution }
        return ToolTimingIndex.parse(nodes, records, live = live)
    }

    @Test
    fun liveSuffixSeesStartAndCompletionWithoutAnotherTextSnapshot() = runBlocking {
        val original = source(completedCall + call, listOf(first)).copy(
            contentStream = MutableSharedStream(replay = Int.MAX_VALUE),
        )
        val holder = requireNotNull(original.toolTimingState)
        var now = 1_000L
        val execution = ToolExecutionTiming(count = 1, publish = { holder.update(it) }, callOffset = 1, now = { now })
        val transcript = SteeredTranscript()
        transcript.add(completedCall, 3L, original.timestamp)
        try {
            val suffix = transcript.project(original).single()
            val stream = suffix.contentStream as SharedStream<String>
            assertSame(holder, suffix.toolTimingState)
            assertEquals(1, suffix.toolTimingStartIndex)
            assertTrue(suffix.localToolTimings(holder.records.value).isEmpty())
            assertNull(index(suffix, live = true).tool(0)?.elapsedMillis(now))

            // 不投影新消息、不追加正文，已有行仍能看到开始与完成事件。
            execution.start(0)
            val started = requireNotNull(index(suffix, live = true).tool(0))
            assertTrue(started.ticking())
            assertEquals(300L, started.elapsedMillis(now + 300L))
            assertEquals(800L, started.elapsedMillis(now + 800L))
            assertEquals(1_000L, suffix.resolvedSections().filterIsInstance<MessageSection.ToolCall>().single().execution?.startedMs)

            now = 2_000L
            execution.completed(0)
            val completed = requireNotNull(index(suffix, live = true).tool(0))
            assertFalse(completed.ticking())
            assertEquals(1_000L, completed.elapsedMillis(now + 5_000L))
            assertEquals(1_000L, suffix.resolvedSections().filterIsInstance<MessageSection.ToolCall>().single().execution?.durationMs)
            assertEquals(call, suffix.content)
            assertEquals(listOf(call), stream.replayCache)
            assertNull(suffix.sections.filterIsInstance<MessageSection.ToolCall>().single().execution)
            assertEquals(listOf(0, 1), holder.records.value.map { it.callIndex })
        } finally {
            transcript.closeStream()
        }
    }

    @Test
    fun liveBoundaryAddsSourceOffsetAndDoesNotCompactMissingSameNameCalls() {
        val state = ToolTimingState()
        val visible = second.copy(callIndex = 6)
        val later = third.copy(callIndex = 8)
        state.update(first.copy(callIndex = 5))
        state.update(visible)
        state.update(later)
        val original = ChatMessage(
            sender = "ai", timestamp = 1L, content = completedCall.repeat(4),
            contentStream = MutableSharedStream(replay = Int.MAX_VALUE),
            toolTimingState = state, toolTimingStartIndex = 5,
        )
        val transcript = SteeredTranscript()
        transcript.add(completedCall, 3L, original.timestamp)
        try {
            val secondSegment = transcript.project(original).single()
            assertSame(state, secondSegment.toolTimingState)
            assertEquals(6, secondSegment.toolTimingStartIndex)
            assertEquals(listOf(visible.copy(callIndex = 0), later.copy(callIndex = 2)),
                secondSegment.localToolTimings(state.records.value))

            transcript.add(completedCall.repeat(2), 5L, original.timestamp)
            val thirdSegment = transcript.project(original).single()
            assertSame(state, thirdSegment.toolTimingState)
            assertEquals(7, thirdSegment.toolTimingStartIndex)
            val calls = thirdSegment.resolvedSections().filterIsInstance<MessageSection.ToolCall>()
            assertEquals(listOf(null, later.copy(callIndex = 1)), calls.map { it.execution })

            val final = transcript.project(original.copy(contentStream = null)).single()
            assertNull(final.contentStream)
            assertNull(final.toolTimingState)
            assertEquals(0, final.toolTimingStartIndex)
            assertEquals(calls, final.sections.filterIsInstance<MessageSection.ToolCall>())
            assertEquals(listOf(5, 6, 8), state.records.value.map { it.callIndex })
        } finally {
            transcript.closeStream()
        }
    }

    @Test
    fun suffixWithRepeatedToolUsesLocalIndexAndItsOwnCompletedDuration() {
        val original = source(completedCall + completedCall, listOf(first, second))
        val transcript = SteeredTranscript()
        transcript.add(completedCall, 3L, original.timestamp)
        val suffix = transcript.project(original).single()
        val stored = suffix.sections.filterIsInstance<MessageSection.ToolCall>().single()

        assertEquals(second.copy(callIndex = 0), stored.execution)
        assertNull(suffix.toolTimingState)
        val summary = requireNotNull(index(suffix).tool(0))
        assertTrue(summary.complete)
        assertEquals(2_000L, summary.elapsedMillis(100_000L))
        assertFalse(summary.ticking())
        assertEquals(listOf(first, second), original.toolTimingState?.records?.value)
    }

    @Test
    fun multipleSuffixToolsDoNotSwapOwnersOrIncludeModelWaits() {
        val original = source(completedCall.repeat(3), listOf(first, second, third))
        val transcript = SteeredTranscript()
        transcript.add(completedCall, 3L, original.timestamp)
        val suffix = transcript.project(original).single()
        val records = suffix.sections.filterIsInstance<MessageSection.ToolCall>().map { it.execution }

        assertEquals(listOf(second.copy(callIndex = 0), third.copy(callIndex = 1)), records)
        val timing = index(suffix)
        assertEquals(2_000L, timing.tool(0)?.elapsedMillis(100_000L))
        assertEquals(3_000L, timing.tool(2)?.elapsedMillis(100_000L))
        assertEquals(5_000L, timing.group(0, 3).elapsedMillis(100_000L))
        assertTrue(timing.group(0, 3).complete)
    }

    @Test
    fun anotherSteeringBoundaryStartsTheNewSegmentAtZeroAgain() {
        val transcript = SteeredTranscript()
        transcript.add(completedCall, 3L, 1L)
        val secondSegment = transcript.project(source(completedCall.repeat(2), listOf(first, second))).single()
        transcript.add(completedCall.repeat(2), 5L, 1L)
        val thirdSegment = transcript.project(source(completedCall.repeat(3), listOf(first, second, third))).single()

        assertEquals(3L, secondSegment.timestamp)
        assertEquals(5L, thirdSegment.timestamp)
        assertEquals(third.copy(callIndex = 0), thirdSegment.sections.filterIsInstance<MessageSection.ToolCall>().single().execution)
        assertEquals(2_000L, index(secondSegment).tool(0)?.elapsedMillis(100_000L))
        assertEquals(3_000L, index(thirdSegment).tool(0)?.elapsedMillis(100_000L))
    }

    @Test
    fun projectedTimingRemainsVisibleAfterDatabaseAndVariantRoundTrips() {
        val original = source(completedCall.repeat(2), listOf(first, second))
        val transcript = SteeredTranscript()
        transcript.add(completedCall, 3L, original.timestamp)
        val suffix = transcript.project(original).single()
        val loaded = MessageEntity.fromChatMessage("timing-test", suffix, 0).toChatMessage()
        val variant = MessageVariantEntity.fromChatMessage("timing-test", suffix.timestamp, 1, suffix)
        val switched = variant.applyTo(loaded, 2)

        listOf(loaded, switched).forEach { message ->
            assertEquals(completedCall, message.content)
            assertEquals(second.copy(callIndex = 0), message.resolvedSections().filterIsInstance<MessageSection.ToolCall>().single().execution)
            assertEquals(2_000L, index(message).tool(0)?.elapsedMillis(100_000L))
            assertFalse(message.displayContent().contains("durationMs"))
        }
    }

    @Test
    fun previouslySavedNonzeroIndexIsNormalizedWithoutChangingItsTimingOrBody() {
        val oldExecution = second.copy(callIndex = 7)
        val sections = MessageSectionCodec.parse(completedCall).map { section ->
            if (section is MessageSection.ToolCall) section.copy(execution = oldExecution) else section
        }
        val decoded = MessageSectionStorage.decode(MessageSectionStorage.encode(sections))
        val loaded = ChatMessage("ai", completedCall, sections = decoded)

        assertEquals(7, decoded.filterIsInstance<MessageSection.ToolCall>().single().execution?.callIndex)
        assertEquals(second.copy(callIndex = 0), loaded.resolvedSections().filterIsInstance<MessageSection.ToolCall>().single().execution)
        assertEquals(completedCall, loaded.content)
        assertEquals(2_000L, index(loaded).tool(0)?.elapsedMillis(100_000L))
        assertEquals(7, decoded.filterIsInstance<MessageSection.ToolCall>().single().execution?.callIndex)
    }

    @Test
    fun missingHistoricalTimingKeepsItsPositionAndDoesNotBecomeZero() {
        var callIndex = 0
        val sections = MessageSectionCodec.parse(completedCall.repeat(2)).map { section ->
            if (section is MessageSection.ToolCall) {
                val local = callIndex++
                section.copy(execution = if (local == 0) null else second.copy(callIndex = 7))
            } else section
        }
        val loaded = ChatMessage("ai", completedCall.repeat(2), sections = sections)
        val resolved = loaded.resolvedSections().filterIsInstance<MessageSection.ToolCall>()
        assertNull(resolved[0].execution)
        assertEquals(second, resolved[1].execution)
        val timing = index(loaded)
        assertTrue(requireNotNull(timing.tool(0)).complete)
        assertNull(timing.tool(0)?.elapsedMillis(100_000L))
        assertEquals(2_000L, timing.tool(2)?.elapsedMillis(100_000L))
        assertTrue(timing.group(0, 3).complete)
        assertNull(timing.group(0, 3).elapsedMillis(100_000L))
    }
}