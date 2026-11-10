package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.chat.protocol.ThinkingMarkup
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType.ROLLBACK
import com.ai.assistance.operit.util.stream.TextStreamEventType.SAVEPOINT
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withEventChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaifuThinkingStreamTest {
    private var previousLogEnabled = true
    @Before fun disableAndroidLog() { previousLogEnabled = AppLogger.enableSystemLog; AppLogger.enableSystemLog = false }
    @After fun restoreAndroidLog() { AppLogger.enableSystemLog = previousLogEnabled }
    private data class Bubble(val id: Int, val text: String)

    @Test fun thinkingAppearsBeforeBodyWithoutSpeakingOrDelayingTheFirstSentence() = runTest {
        val messages = mutableListOf<Bubble>()
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val resume = CompletableDeferred<Unit>()
        var nextId = 0
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { Bubble(++nextId, it) },
            reviseMessage = { message, text -> message.copy(text = text) },
            appendMessage = { message, interrupt -> spoken += message.text to interrupt },
            publishThinking = {}, removeMessages = {},
        )
        val source = stream {
            emit("<think>思考原文")
            resume.await()
            emit("</think>第一句|第二句|")
        }
        val job = launch {
            streamWaifuUpdates(
                sourceStream = source,
                splitSnapshot = { content, final ->
                    val parts = ThinkingMarkup.remove(content).split('|')
                    (if (final) parts else parts.dropLast(1)).filter { it.isNotEmpty() }
                },
                typingDelayMs = { _, first -> if (first) 0L else 500L },
                nowMillis = { testScheduler.currentTime },
                thinkingSnapshot = WaifuThinkingPresentation::snapshot,
            ).collect { store.apply(it) }
        }
        runCurrent()
        assertEquals(1, messages.size)
        val thinkingId = messages.single().id
        assertTrue(WaifuThinkingPresentation.isInProgress(messages.single().text))
        assertTrue(spoken.isEmpty())
        resume.complete(Unit)
        runCurrent()
        assertEquals(listOf("第一句" to true), spoken)
        advanceUntilIdle()
        job.join()
        assertEquals(thinkingId, messages.first().id)
        assertFalse(WaifuThinkingPresentation.isInProgress(messages.first().text))
        assertEquals(listOf("第一句" to true, "第二句" to false), spoken)
        assertEquals(3, messages.size)
    }

    @Test fun cancellingAnOpenThinkingStreamPublishesItsFinishedState() = runTest {
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream { emit("<think>部分思考"); awaitCancellation() }
        val job = launch {
            streamWaifuUpdates(
                sourceStream = source, splitSnapshot = { _, _ -> emptyList() },
                typingDelayMs = { _, _ -> 0L },
                thinkingSnapshot = WaifuThinkingPresentation::snapshot,
            ).collect { received += it }
        }
        runCurrent()
        val first = requireNotNull((received.first() as WaifuSegmentUpdate.Thinking).content)
        assertTrue(WaifuThinkingPresentation.isInProgress(first))
        job.cancelAndJoin()
        val last = requireNotNull((received.last() as WaifuSegmentUpdate.Thinking).content)
        assertFalse(WaifuThinkingPresentation.isInProgress(last))
        assertEquals(ThinkingMarkup.body(first), ThinkingMarkup.body(last))
        assertFalse(received.any { it is WaifuSegmentUpdate.Append })
    }

    @Test fun rollbackRemovesThinkingFromTheRetractedAttempt() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val retry = CompletableDeferred<Unit>()
        val messages = mutableListOf<Bubble>()
        val removed = mutableListOf<Int>()
        var nextId = 0
        val store = WaifuSegmentMessageStore(
            messages = messages, createMessage = { Bubble(++nextId, it) },
            reviseMessage = { message, text -> message.copy(text = text) },
            appendMessage = { _, _ -> }, publishThinking = {},
            removeMessages = { bubbles -> removed += bubbles.map { it.id } },
        )
        val old = "<think>失效思考"
        val source = stream {
            revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
            emit(old)
            retry.await()
            revisions.emit(TextStreamEvent(ROLLBACK, "attempt", 0, old.length))
            emit("新回复|")
        }.withEventChannel(revisions)
        val job = launch {
            streamWaifuUpdates(
                sourceStream = source,
                splitSnapshot = { content, final ->
                    val parts = ThinkingMarkup.remove(content).split('|')
                    (if (final) parts else parts.dropLast(1)).filter { it.isNotEmpty() }
                },
                typingDelayMs = { _, _ -> 0L },
                thinkingSnapshot = WaifuThinkingPresentation::snapshot,
            ).collect { store.apply(it) }
        }
        runCurrent()
        val oldId = messages.single().id
        retry.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertEquals(listOf("新回复"), messages.map { it.text })
        assertTrue(oldId in removed)
        assertEquals(0, revisions.subscriptionCount)
    }

    @Test fun retractingBodyKeepsThinkingAndHandlesDuplicateSentencesByIndex() = runTest {
        val messages = mutableListOf<Bubble>()
        var nextId = 0
        val store = WaifuSegmentMessageStore(
            messages = messages, createMessage = { Bubble(++nextId, it) },
            reviseMessage = { message, text -> message.copy(text = text) },
            appendMessage = { _, _ -> }, removeMessages = {}, publishThinking = {},
        )
        store.apply(WaifuSegmentUpdate.Append("保留"))
        store.apply(WaifuSegmentUpdate.Thinking("思考"))
        val thinkingId = messages.last().id
        store.apply(WaifuSegmentUpdate.Append("重复"))
        store.apply(WaifuSegmentUpdate.Append("重复"))
        store.apply(WaifuSegmentUpdate.Retain(1))
        assertEquals(listOf("保留", "思考"), messages.map { it.text })
        store.apply(WaifuSegmentUpdate.Retain(0))
        assertEquals(thinkingId, messages.single().id)
        store.apply(WaifuSegmentUpdate.Preview("新"))
        val previewId = messages.last().id
        store.apply(WaifuSegmentUpdate.Thinking(null))
        store.apply(WaifuSegmentUpdate.Append("新回复"))
        assertEquals(listOf(Bubble(previewId, "新回复")), messages)
    }

    @Test fun aThinkingUpdateDoesNotOverwriteAnAlreadyVisibleBodyPreview() = runTest {
        val messages = mutableListOf<Bubble>()
        val spoken = mutableListOf<String>()
        var nextId = 0
        val store = WaifuSegmentMessageStore(
            messages = messages, createMessage = { Bubble(++nextId, it) },
            reviseMessage = { message, text -> message.copy(text = text) },
            appendMessage = { message, _ -> spoken += message.text }, removeMessages = {}, publishThinking = {},
        )
        store.apply(WaifuSegmentUpdate.Preview("正文预览"))
        val previewId = messages.single().id
        store.apply(WaifuSegmentUpdate.Thinking("后来的思考"))
        val thinkingId = messages.last().id
        store.apply(WaifuSegmentUpdate.Append("完整正文"))
        store.apply(WaifuSegmentUpdate.Thinking("更新的思考"))
        assertEquals(listOf(Bubble(previewId, "完整正文"), Bubble(thinkingId, "更新的思考")), messages)
        assertEquals(listOf("完整正文"), spoken)
    }
}
