package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.util.assertPropagatedException
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withEventChannel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaifuContinuousProcessTest {
    @Test
    fun thinkingToolsAndResultsShareOneProcessUntilBodyAppears() {
        val thinking = "<think>先检查。</think>"
        val tool = "<tool name=\"package:tool\"><param name=\"a\">1</param></tool>"
        val result = "<tool_result name=\"package:tool\" status=\"success\">完成。</tool_result>"
        val moreThinking = "<thinking>继续核对。</thinking>"
        assertEquals(
            listOf("第一句。", "$thinking\n$tool\n$result\n$moreThinking", "第二句。", tool),
            WaifuProcessContent.mergeConsecutive(listOf("第一句。", thinking, tool, result, moreThinking, "第二句。", tool)),
        )
    }

    @Test
    fun processOnlyResponsesSupportOneKindAndManyUnknownTools() {
        val tools = (1..60).map { "<tool name=\"unknown_package:long_tool_$it\"/>" }
        assertEquals(listOf(tools.joinToString("\n")), WaifuProcessContent.mergeConsecutive(tools))
        assertEquals(listOf("<think>只有思考</think>"), WaifuProcessContent.mergeConsecutive(listOf("<think>只有思考</think>")))
        assertEquals(listOf("<tool_result>只有结果</tool_result>"), WaifuProcessContent.mergeConsecutive(listOf("<tool_result>只有结果</tool_result>")))
    }

    @Test
    fun bodyCodeImagesAndPluginCardsBreakTheProcess() {
        val process = "<think>过程</think>"
        for (body in listOf("没有标点的正文", "```xml\n<tool/>\n```", "![图片](https://example.com/a.png)", "<plan>插件卡片</plan>")) {
            assertEquals(listOf(process, body, process), WaifuProcessContent.mergeConsecutive(listOf(process, body, process)))
        }
    }

    @Test
    fun hiddenProtocolAndWhitespaceDoNotCreateBodyBoundaries() {
        val first = "<think>过程</think>"
        val second = "<tool name=\"x\"/>"
        for (provider in listOf("gemini:thought_signature", "openai:responses_reasoning", "openai:responses_output_item", "operit:tool_timing")) {
            val raw = "$first\n<meta provider=\"$provider\">隐藏内容</meta>\n$second"
            val visible = WaifuMessageProcessor.buildRenderableContentForWaifu(raw)
            assertTrue(WaifuMessageProcessor.isProcessOnlyMessage(raw))
            assertEquals("$first\n\n$second", visible)
        }
        assertFalse(WaifuMessageProcessor.isProcessOnlyMessage("<meta provider=\"operit:tool_timing\">隐藏</meta>"))
        assertFalse(WaifuMessageProcessor.isProcessOnlyMessage(" \n\t"))
    }

    @Test
    fun processDetectionKeepsRawToolDataAndRejectsBodyExamples() {
        val process = "<tool_ab12 name=\"x\"><param name=\"xml\"><![CDATA[</tool_ab12>]]></param></tool_ab12>"
        assertTrue(WaifuMessageProcessor.isProcessOnlyMessage(process))
        assertTrue(WaifuMessageProcessor.isProcessOnlyMessage("$process\n<tool_result_cd34>输出</tool_result_cd34>"))
        assertTrue(WaifuMessageProcessor.isProcessOnlyMessage("<thinking>尚未结束\n第二行"))
        assertFalse(WaifuMessageProcessor.isProcessOnlyMessage("正文。$process"))
        assertFalse(WaifuMessageProcessor.isProcessOnlyMessage("$process 后续正文"))
        assertFalse(WaifuMessageProcessor.isProcessOnlyMessage("```xml\n$process\n```"))
        assertFalse(WaifuMessageProcessor.isProcessOnlyMessage("`$process`"))
    }

    private fun TestScope.updates(source: Stream<String>) = streamWaifuUpdates(
        sourceStream = source,
        // 队列测试用显式正文边界隔离 JNI；真实原生分段另在下面验证。
        splitSnapshot = { content, final ->
            val pieces = content.split('|')
            WaifuProcessContent.mergeConsecutive(pieces.filterIndexed { index, piece ->
                piece.isNotBlank() && (final || index < pieces.lastIndex || WaifuProcessContent.isProcessOnly(piece))
            })
        },
        isProcessSegment = WaifuProcessContent::isProcessOnly,
        typingDelayMs = { length, first -> if (first) 0L else (length * 100L).coerceAtMost(3000L) },
        nowMillis = { testScheduler.currentTime },
    )

    private data class Message(val id: Int, val content: String)

    @Test
    fun processAfterBodyAppearsImmediatelyAndGrowsWithoutReplacingItsIdentity() = runTest {
        val grow = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val messages = mutableListOf<Message>()
        val persisted = linkedMapOf<Int, Message>()
        val removed = mutableListOf<Message>()
        val spoken = mutableListOf<String>()
        var nextId = 0
        var revisions = 0
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { Message(++nextId, it) },
            reviseMessage = { message, content -> message.copy(content = content) },
            appendMessage = { message, _ ->
                persisted[message.id] = message
                if (!WaifuProcessContent.isProcessOnly(message.content)) spoken += message.content
            },
            publishRevision = { message -> persisted[message.id] = message; revisions++ },
            removeMessages = { removed += it },
        )
        val source = stream {
            emit("开头。|<think>正在思考")
            grow.await()
            emit("</think>")
            repeat(40) { index ->
                emit("|<tool name=\"unknown:$index\"/>|<tool_result name=\"unknown:$index\">结果$index</tool_result>")
                delay(1)
            }
            finish.await()
            emit("|完成。|下一句。|<tool name=\"new_process\"/>")
        }
        val events = mutableListOf<Pair<Long, WaifuSegmentUpdate>>()
        val job = launch { updates(source).collect { events += testScheduler.currentTime to it; store.apply(it) } }
        runCurrent()
        assertEquals(listOf("开头。", "<think>正在思考"), messages.map { it.content })
        val processId = messages[1].id
        assertEquals(0L, events.last().first)

        grow.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, messages.size)
        assertEquals(processId, messages[1].id)
        assertTrue(messages[1].content.contains("unknown:39"))
        assertEquals(messages[1], persisted[processId])
        assertTrue(revisions > 1)
        assertTrue(removed.isEmpty())
        assertEquals(listOf("开头。"), spoken)

        finish.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertEquals(5, messages.size)
        assertEquals(processId, messages[1].id)
        assertEquals(listOf("开头。", "完成。", "下一句。"), spoken)
        assertEquals("<tool name=\"new_process\"/>", messages.last().content)
        assertTrue(messages.last().id != processId)
        assertFalse(events.any { it.second is WaifuSegmentUpdate.Retain })
    }

    @Test
    fun retryInsideAProcessRemovesAbandonedToolsWithoutChangingTheProcessId() = runTest {
        val channel = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val retry = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val prefix = "<think>保留的思考</think>|"
        val discarded = "<tool name=\"abandoned\"/>|"
        val messages = mutableListOf<Message>()
        val persisted = linkedMapOf<Int, Message>()
        var nextId = 0
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { Message(++nextId, it) },
            reviseMessage = { message, content -> message.copy(content = content) },
            appendMessage = { message, _ -> persisted[message.id] = message },
            publishRevision = { message -> persisted[message.id] = message },
            removeMessages = { removed -> removed.forEach { persisted.remove(it.id) } },
        )
        val source = stream {
            emit(prefix)
            channel.emit(TextStreamEvent(TextStreamEventType.SAVEPOINT, "attempt", prefix.length))
            emit(discarded)
            retry.await()
            channel.emit(TextStreamEvent(TextStreamEventType.ROLLBACK, "attempt", prefix.length, discarded.length))
            resume.await()
            emit("<tool name=\"kept\"/>")
        }.withEventChannel(channel)
        val job = launch { updates(source).collect { store.apply(it) } }
        runCurrent()
        val processId = messages.single().id
        assertTrue(messages.single().content.contains("abandoned"))
        retry.complete(Unit)
        runCurrent()
        assertEquals(Message(processId, "<think>保留的思考</think>"), messages.single())
        assertEquals(messages.single(), persisted[processId])
        resume.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertEquals(processId, messages.single().id)
        assertFalse(messages.single().content.contains("abandoned"))
        assertTrue(messages.single().content.contains("kept"))
    }

    @Test
    fun cancellationKeepsTheLatestOpenProcessAfterEarlierBody() = runTest {
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            emit("正文。|<tool name=\"running\"><param name=\"text\">未完成")
            awaitCancellation()
        }
        val job = launch { updates(source).collect { received += it } }
        runCurrent()
        assertEquals(2, received.size)
        assertEquals(WaifuSegmentUpdate.Append("正文。"), received.first())
        val process = WaifuSegmentUpdate.Append("<tool name=\"running\"><param name=\"text\">未完成")
        assertEquals(process, received.last())
        job.cancelAndJoin()
        advanceUntilIdle()
        assertEquals(process, received.last())
        assertFalse(received.any { it is WaifuSegmentUpdate.Retain })
    }

    @Test
    fun failureKeepsGeneratedProcessAndPropagatesTheOriginalError() = runTest {
        val received = mutableListOf<WaifuSegmentUpdate>()
        val release = CompletableDeferred<Unit>()
        val failure = IOException("process failed")
        val source = stream {
            emit("<think>已生成的过程")
            release.await()
            throw failure
        }
        var caught: Exception? = null
        val job = launch {
            try { updates(source).collect { received += it } } catch (error: IOException) { caught = error }
        }
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()
        job.join()
        // 协程堆栈恢复可能复制异常，沿因果链校验原始失败才能兼容 CI 的 JVM 断言配置。
        assertPropagatedException(failure, checkNotNull(caught))
        assertEquals(listOf(WaifuSegmentUpdate.Append("<think>已生成的过程")), received)
    }

    @Test
    fun immediateFailureStillPublishesTheGeneratedProcess() = runTest {
        val received = mutableListOf<WaifuSegmentUpdate>()
        val failure = IOException("immediate process failure")
        var caught: IOException? = null
        val source = stream {
            emit("<think>已经生成")
            throw failure
        }
        try { updates(source).collect { received += it } } catch (error: IOException) { caught = error }
        assertPropagatedException(failure, checkNotNull(caught))
        assertEquals(listOf(WaifuSegmentUpdate.Append("<think>已经生成")), received)
    }

    @Test
    fun processDoesNotWaitBehindQueuedBodyTypingDelays() = runTest {
        val resume = CompletableDeferred<Unit>()
        val end = CompletableDeferred<Unit>()
        val events = mutableListOf<Pair<Long, WaifuSegmentUpdate>>()
        val source = stream {
            emit("第一句。|还在排队的第二句。|")
            resume.await()
            emit("<tool name=\"x\">参数</tool>")
            end.await()
        }
        val job = launch { updates(source).collect { events += testScheduler.currentTime to it } }
        runCurrent()
        assertEquals(listOf(0L to WaifuSegmentUpdate.Append("第一句。")), events)
        resume.complete(Unit)
        runCurrent()
        assertEquals(listOf(0L, 0L, 0L), events.map { it.first })
        assertEquals(WaifuSegmentUpdate.Append("<tool name=\"x\">参数</tool>"), events.last().second)
        end.complete(Unit)
        advanceUntilIdle()
        job.join()
    }

    @Test
    fun nativeLiveProcessUpdatesStayInPlaceAtEveryCharacterBoundary() = runTest {
        requireNativeSplitter()
        val messages = mutableListOf<Message>()
        val ids = mutableListOf<Int>()
        val events = mutableListOf<WaifuSegmentUpdate>()
        var nextId = 0
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { Message(++nextId, it) },
            reviseMessage = { message, content -> message.copy(content = content) },
            appendMessage = { message, _ -> if (WaifuMessageProcessor.isProcessOnlyMessage(message.content)) ids += message.id },
            publishRevision = { message -> ids += message.id },
            publishPreview = {},
            removeMessages = {},
        )
        val raw = "开头。<think>思考中。\n再想一步。</think>\n" +
            "<tool_a1 name=\"unknown:many\"><param name=\"q\">原始参数</param></tool_a1>\n" +
            "<meta provider=\"operit:tool_timing\">隐藏协议</meta>\n" +
            "<tool_result_b2 name=\"unknown:many\" status=\"success\">结果。</tool_result_b2>" +
            "<thinking>接着想。</thinking>完成。下一句。"
        val source = stream { raw.forEach { emit(it.toString()); delay(1) } }
        WaifuMessageProcessor.streamSegmentUpdatesWithTypingQueue(source, charDelayMs = 0).collect {
            events += it
            store.apply(it)
        }
        assertEquals(4, messages.size)
        assertEquals(listOf("开头。", "完成。", "下一句。"), listOf(messages[0].content, messages[2].content, messages[3].content))
        assertTrue(WaifuMessageProcessor.isProcessOnlyMessage(messages[1].content))
        assertTrue(messages[1].content.contains("接着想。"))
        assertFalse(messages[1].content.contains("隐藏协议"))
        assertEquals(setOf(messages[1].id), ids.toSet())
        assertFalse(events.any { it is WaifuSegmentUpdate.Retain })
    }

    @Test
    fun nativeSelfClosingToolsStillStopAtAnUnpunctuatedBody() {
        requireNativeSplitter()
        val first = "<tool name=\"a\"/>"
        val last = "<tool name=\"b\"/>"
        assertEquals(
            listOf(first, "没有标点的正文", last),
            WaifuMessageProcessor.splitMessageBySentences(first + "没有标点的正文" + last),
        )
    }

    @Test
    fun nativeLiveSelfClosingToolsNeverAbsorbTheBody() = runTest {
        requireNativeSplitter()
        val messages = mutableListOf<String>()
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { it },
            reviseMessage = { _, content -> content },
            appendMessage = { _, _ -> },
            removeMessages = {},
        )
        val source = stream {
            "<tool name=\"a\"/>没有标点的正文<tool name=\"b\"/>".forEach {
                emit(it.toString())
                delay(1)
            }
        }
        WaifuMessageProcessor.streamSegmentUpdatesWithTypingQueue(source, charDelayMs = 0).collect { store.apply(it) }
        assertEquals(listOf("<tool name=\"a\"/>", "没有标点的正文", "<tool name=\"b\"/>"), messages)
    }

    @Test
    fun processMarkersInsideInlineCodeStayInTheBody() {
        val body = "`<tool/>` 与 ``<think>示例</think>`` 保留为正文。"
        val call = "<tool name=\"actual\"/>"
        assertEquals(
            listOf(WaifuProcessContent.Part(body, false), WaifuProcessContent.Part(call, true)),
            WaifuProcessContent.splitPlainText(body + call),
        )
    }

    private fun requireNativeSplitter() {
        try { System.loadLibrary("streamnative") } catch (error: UnsatisfiedLinkError) { assumeNoException(error) }
    }

    @Test
    fun nativeSplitGroupsContinuousProcessesAndKeepsEveryBodySentence() {
        requireNativeSplitter()
        val process = "<think>思考。</think><tool name=\"x\"/><tool_result name=\"x\">结果。</tool_result><thinking>再想。</thinking>"
        val segments = WaifuMessageProcessor.splitMessageBySentences("第一句。${process}第二句。第三句。<tool name=\"next\"/>")
        assertEquals(5, segments.size)
        assertEquals("第一句。", segments[0])
        assertTrue(WaifuMessageProcessor.isProcessOnlyMessage(segments[1]))
        assertTrue(segments[1].contains("再想。"))
        assertEquals(listOf("第二句。", "第三句。", "<tool name=\"next\"/>"), segments.drop(2))
    }

    @Test
    fun nativeStableAppendOnlyApiWaitsForBodyBeforeCommittingTheProcess() {
        requireNativeSplitter()
        val session = WaifuMessageProcessor.StreamingSession()
        val first = "<think>思考。</think>"
        assertTrue(session.collectStableSegments(first).isEmpty())
        val tools = first + "<tool name=\"x\"/><tool_result>结果</tool_result>"
        assertTrue(session.collectStableSegments(tools).isEmpty())
        val committed = session.collectStableSegments(tools + "正文。")
        assertEquals(2, committed.size)
        assertTrue(WaifuMessageProcessor.isProcessOnlyMessage(committed.first()))
        assertEquals("正文。", committed.last())
        assertTrue(session.collectFinalSegments(tools + "正文。").isEmpty())
    }
}
