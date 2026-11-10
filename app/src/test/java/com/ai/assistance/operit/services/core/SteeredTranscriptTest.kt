package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class SteeredTranscriptTest {
    @Test fun suffixSectionsDoNotRepeatTheSealedPrefix() {
        val transcript = SteeredTranscript()
        transcript.add("Before\n", 3, 1)
        val original = ChatMessage(sender = "ai", timestamp = 1, content = "Before\nFinal paragraph")
        val suffix = transcript.project(original).single()
        assertEquals("Final paragraph", suffix.content)
        assertEquals("Final paragraph", suffix.displayContent())
    }

    @Test fun repeatedToolCallsKeepTheLaterExecutionTiming() {
        val call = "<tool name=\"read\"><param name=\"path\">same</param></tool>"
        val prefix = "$call\n"
        val timings = com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState()
        timings.update(com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution(0, "clock", durationMs = 111))
        timings.update(com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution(1, "clock", durationMs = 222))
        val original = ChatMessage(
            sender = "ai", timestamp = 1, content = "$prefix$call\nFinal", toolTimingState = timings,
        )
        val transcript = SteeredTranscript()
        transcript.add(prefix, 3, 1)
        val suffix = transcript.project(original).single()
        val saved = suffix.resolvedSections().filterIsInstance<com.ai.assistance.operit.data.model.MessageSection.ToolCall>().single()
        assertEquals(222L, saved.execution?.durationMs)
        assertNull(suffix.toolTimingState)
    }
    @Test fun rollbackAfterSteeringKeepsCanonicalPrefixAndNewReply() {
        val rawPrefix = "first<tool_result>result</tool_result>second"
        val canonicalPrefix = "first<tool_result>result</tool_result>\nsecond"
        val boundary = com.ai.assistance.operit.api.chat.EnhancedAIService.ToolExecutionBoundarySnapshot(
            canonicalPrefix, rawPrefix.length,
        )
        val tracker = com.ai.assistance.operit.util.stream.TextStreamRevisionTracker()
        tracker.append(rawPrefix)
        tracker.savepoint("new-response", rawPrefix.length)
        tracker.append("discarded response")
        tracker.rollback("new-response", rawPrefix.length, "discarded response".length)
        tracker.append("complete replacement")
        val merged = boundary.displayContent + tracker.currentContent().toString().substring(rawPrefix.length)
        val transcript = SteeredTranscript()
        transcript.add(canonicalPrefix, 3, 1)
        val rows = transcript.project(ChatMessage(sender = "ai", timestamp = 1, content = merged))
        assertEquals(listOf("complete replacement"), rows.map { it.content })
        assertEquals(listOf(3L), rows.map { it.timestamp })
    }
    @Test fun multipleSteersKeepAssistantSegmentsAfterTheirUserMessages() {
        val transcript = SteeredTranscript()
        transcript.add("tool call\n<tool_result>done</tool_result>", 3, 1)
        transcript.add("tool call\n<tool_result>done</tool_result>\nsecond reply", 5, 1)
        val rows = transcript.project(ChatMessage(
            sender = "ai", timestamp = 1,
            content = "tool call\n<tool_result>done</tool_result>\nsecond reply\nlast reply",
        ))
        assertEquals(listOf(5L), rows.map { it.timestamp })
        assertEquals(5L, transcript.finalTimestamp(1))
        assertEquals(10L, transcript.finalTimestamp(10))
        assertEquals(listOf("\nlast reply"),
            rows.map { it.content })
    }

    @Test fun staleReplaySnapshotCannotOverwriteSealedDisplayOrNewAssistant() {
        val transcript = SteeredTranscript()
        transcript.add("first\nsecond", 3, 1)
        val rows = transcript.project(ChatMessage(sender = "ai", timestamp = 1, content = "firstsecond"))
        assertTrue(rows.isEmpty())
    }

    @Test fun aggregateUsageIsAssignedOnlyToTheLastAssistantSegment() {
        val transcript = SteeredTranscript()
        transcript.add("first", 3, 1)
        transcript.add("firstsecond", 5, 1)
        val rows = transcript.project(ChatMessage(
            sender = "ai", timestamp = 1, content = "firstsecondlast", inputTokens = 100, outputTokens = 20,
            cachedInputTokens = 40, waitDurationMs = 5000, outputDurationMs = 20000,
            completedAt = 30000,
        ))
        assertEquals(100L, rows.sumOf { it.inputTokens })
        assertEquals(20L, rows.sumOf { it.outputTokens })
        assertEquals(40L, rows.last().cachedInputTokens)
        assertEquals(5000L, rows.last().waitDurationMs)
        assertEquals(20000L, rows.last().outputDurationMs)
        assertEquals(30000L, rows.last().completedAt)
    }

    @Test fun sealingPersistsOnlyCurrentSegmentWithoutAggregateStats() {
        val transcript = SteeredTranscript()
        val first = ChatMessage(
            sender = "ai", timestamp = 1, content = "first",
            displayMode = com.ai.assistance.operit.data.model.ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE,
            inputTokens = 100, outputTokens = 20, cachedInputTokens = 40,
            waitDurationMs = 5000, outputDurationMs = 20000, completedAt = 30000,
        )
        val sealedFirst = transcript.project(first).single()
        transcript.add("first", 3, 1)
        val sealedSecond = transcript.project(first.copy(content = "firstsecond")).single()
        assertEquals("first", sealedFirst.content)
        assertEquals("second", sealedSecond.content)
        assertEquals(3L, sealedSecond.timestamp)
        listOf(sealedFirst, sealedSecond).forEach {
            assertEquals(0, it.inputTokens)
            assertEquals(0, it.outputTokens)
            assertEquals(0, it.cachedInputTokens)
            assertEquals(0L, it.waitDurationMs)
            assertEquals(0L, it.outputDurationMs)
            assertEquals(0L, it.completedAt)
        }
    }

    @Test fun anotherResponseIsNotSplitUsingAPreviousTurnsBoundaries() {
        val transcript = SteeredTranscript()
        transcript.add("first", 3, 1)
        val regenerated = ChatMessage(sender = "ai", timestamp = 10, content = "new response")
        assertEquals(listOf(regenerated), transcript.project(regenerated))
    }
}
