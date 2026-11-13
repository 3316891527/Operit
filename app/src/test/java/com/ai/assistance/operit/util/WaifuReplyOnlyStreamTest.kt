package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.streamOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaifuReplyOnlyStreamTest {
    private var systemLog = true
    private var fileLog = true

    @Before
    fun disableAndroidLogging() {
        systemLog = AppLogger.enableSystemLog
        fileLog = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = systemLog
        AppLogger.enableFileLogging = fileLog
    }

    private fun TestScope.replyUpdates(source: Stream<String>) = streamWaifuUpdates(
        sourceStream = source,
        splitSnapshot = { content, final ->
            // 验证真实思考过滤与流式队列；测试文本仅用句号分句，避免依赖 Android JNI 库。
            WaifuMessageProcessor.buildRenderableContentForWaifu(content)
                .split(Regex("(?<=。)"))
                .filter { it.isNotBlank() && (final || it.endsWith("。")) }
        },
        typingDelayMs = { _, first -> if (first) 0L else 500L },
        nowMillis = { testScheduler.currentTime },
    )

    @Test
    fun thinkingCreatesNoBubbleAndOnlyReplySentencesAreSpoken() = runTest {
        val messages = mutableListOf<String>()
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val resume = CompletableDeferred<Unit>()
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { it },
            appendMessage = { message, interrupt -> spoken += message to interrupt },
            removeMessages = {},
        )
        val source = stream {
            emit("<think>不应展示或朗读的思考")
            resume.await()
            emit("</think>第一句。第二句。")
        }
        val job = launch { replyUpdates(source).collect { store.apply(it) } }
        runCurrent()
        assertTrue(messages.isEmpty())
        assertTrue(spoken.isEmpty())

        resume.complete(Unit)
        runCurrent()
        assertEquals(listOf("第一句。"), messages)
        assertEquals(listOf("第一句。" to true), spoken)
        advanceUntilIdle()
        job.join()
        assertEquals(listOf("第一句。", "第二句。"), messages)
        assertEquals(listOf("第一句。" to true, "第二句。" to false), spoken)
    }

    @Test
    fun completedThinkingOnlyResponsesCreateNoUpdates() = runTest {
        for (tag in listOf("think", "thinking")) {
            val received = mutableListOf<WaifuSegmentUpdate>()
            replyUpdates(streamOf("<$tag>仅有内部思考</$tag>")).collect { received += it }
            assertTrue(received.isEmpty())
        }
    }

    @Test
    fun cancellingAnOpenThinkingResponseLeavesNoBubble() = runTest {
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            emit("<think>尚未结束的思考")
            awaitCancellation()
        }
        val job = launch { replyUpdates(source).collect { received += it } }
        runCurrent()
        assertTrue(received.isEmpty())
        job.cancelAndJoin()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertTrue(received.isEmpty())
    }
}
