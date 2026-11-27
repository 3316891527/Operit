package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.common.markdown.extractMarkdownImageUrl
import com.ai.assistance.operit.ui.common.markdown.parseMarkdownToNodes
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuSegmentUpdate.Append
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuSegmentUpdate.Retain
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType.ROLLBACK
import com.ai.assistance.operit.util.stream.TextStreamEventType.SAVEPOINT
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.streamOf
import com.ai.assistance.operit.util.stream.withEventChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WaifuSegmentedEmotionAndroidTest {
    private val happyTag = "<emotion>happy</emotion>"
    private val sadTag = "<emotion>sad</emotion>"
    private val happyUrl = "file:///waifu/happy%20%281%29.png"
    private val happyImage = "![]($happyUrl)"
    private val sadImage = "![](file:///waifu/sad.gif)"

    private fun catalog() = WaifuEmotionImages(
        mapOf("happy" to listOf(happyUrl), "sad" to listOf("file:///waifu/sad.gif")),
    )

    @Test
    fun replyPublishesClosedEmotionBeforeEofAndDoesNotReselectOrMergePunctuation() = runTest {
        val closeTag = CompletableDeferred<Unit>()
        val finishReply = CompletableDeferred<Unit>()
        var selected = 0
        var loaded = 0
        val images = WaifuEmotionImages(
            mapOf("happy" to listOf(happyUrl, "file:///waifu/alternate.png")),
            chooseIndex = { size -> selected++ % size },
        )
        val updates = mutableListOf<WaifuSegmentUpdate>()
        val source = stream<String> {
            emit("前文。<emo")
            emit("tion>happy</emot")
            closeTag.await()
            emit("ion>")
            finishReply.await()
            emit("。后文")
        }
        val job = launch {
            WaifuMessageProcessor.streamSegmentUpdatesWithTypingQueue(source, charDelayMs = 0) {
                loaded++
                images
            }.collect { updates += it }
        }
        runCurrent()
        assertEquals(listOf(Append("前文。")), updates)
        assertEquals(0, selected)

        closeTag.complete(Unit)
        runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(listOf(Append("前文。"), Append(happyImage)), updates)
        assertEquals(1, selected)

        finishReply.complete(Unit)
        job.join()
        assertEquals(listOf(Append("前文。"), Append(happyImage), Append("。"), Append("后文")), updates)
        assertEquals(1, loaded)
        assertEquals(1, selected)

        val nodes = parseMarkdownToNodes(happyImage)
        assertEquals(listOf(MarkdownProcessorType.IMAGE), nodes.map { it.type })
        assertEquals(happyUrl, extractMarkdownImageUrl(nodes.single().content.toString()))
        assertEquals("", WaifuMessageProcessor.cleanContentForWaifu(happyImage))
    }

    @Test
    fun appendOnlyStreamPublishesEmotionTightAgainstUnpunctuatedText() = runTest {
        val closeTag = CompletableDeferred<Unit>()
        val finishReply = CompletableDeferred<Unit>()
        val output = mutableListOf<String>()
        val source = stream<String> {
            emit("前文<emotion>ha")
            closeTag.await()
            emit("ppy</emotion>")
            finishReply.await()
            emit("后文")
        }
        val job = launch {
            WaifuMessageProcessor.streamAppendOnlySegments(source, emotionImagesProvider = { catalog() })
                .collect { output += it }
        }
        runCurrent()
        assertTrue(output.isEmpty())

        closeTag.complete(Unit)
        runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(listOf("前文", happyImage), output)

        finishReply.complete(Unit)
        job.join()
        assertEquals(listOf("前文", happyImage, "后文"), output)
    }

    @Test
    fun rollbackRetractsOnlyTheDiscardedImageAndRetryKeepsThePrefix() = runTest {
        val prefix = "留下。$happyTag"
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val rollback = CompletableDeferred<Unit>()
        val retry = CompletableDeferred<Unit>()
        val updates = mutableListOf<WaifuSegmentUpdate>()
        val source = stream<String> {
            emit(prefix)
            revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", prefix.length))
            emit(sadTag)
            rollback.await()
            revisions.emit(TextStreamEvent(ROLLBACK, "attempt", prefix.length, sadTag.length))
            retry.await()
            emit("改为：$happyTag")
        }.withEventChannel(revisions)
        val job = launch {
            WaifuMessageProcessor.streamSegmentUpdatesWithTypingQueue(source, charDelayMs = 0) { catalog() }
                .collect { updates += it }
        }
        runCurrent()
        val initial = listOf(Append("留下。"), Append(happyImage), Append(sadImage))
        assertEquals(initial, updates)

        rollback.complete(Unit)
        runCurrent()
        assertEquals(initial + Retain(2), updates)

        retry.complete(Unit)
        job.join()
        assertEquals(initial + listOf(Retain(2), Append("改为："), Append(happyImage)), updates)
        assertEquals(0, revisions.subscriptionCount)
    }

    @Test
    fun disabledMissingAndUnclosedEmotionsDoNotProducePicturesOrLeakControlText() = runTest {
        val source = "前文${happyTag}后文。<emotion>happy</emot"
        val empty = WaifuEmotionImages(mapOf("happy" to emptyList()), chooseIndex = { error("没有素材时不选图") })
        for (images in listOf(null, empty)) {
            assertEquals(listOf("前文后文。"), replySegments(source, images))
        }
        assertEquals(listOf("前文。"), replySegments("前文。<emotion>happy</emot", catalog()))
    }

    @Test
    fun codeAndEscapedEmotionExamplesStayLiteralAndOnlyTheRealDirectiveProducesAnImage() = runTest {
        val inlineAndEscaped = "示例 `$happyTag` 和 \\$sadTag。"
        for (fence in listOf("```", "~~~")) {
            val code = "${fence}xml\n$sadTag\n$fence"
            val content = "$inlineAndEscaped\n$code\n$happyTag"
            assertEquals(listOf(inlineAndEscaped, code, happyImage), replySegments(content, catalog()))
        }
    }

    @Test
    fun pendingAsciiPunctuationBeforeTheXmlStillBelongsToTheText() = runTest {
        assertEquals(listOf("Hi!", happyImage), replySegments("Hi!$happyTag", catalog()))
        assertEquals(listOf("Hi!", "后文。"), replySegments("Hi!${happyTag}后文。", null))
    }

    @Test
    fun appendOnlyKeepsIncompleteLinkAndLiteralExampleOutOfStableOutput() = runTest {
        val continueReply = CompletableDeferred<Unit>()
        val output = mutableListOf<String>()
        val source = stream<String> {
            emit("看：`$happyTag` 和 ")
            emit("[链接$sadTag")
            continueReply.await()
            emit("](https://example.com) 后文。")
        }
        val job = launch {
            WaifuMessageProcessor.streamAppendOnlySegments(source, emotionImagesProvider = { catalog() })
                .collect { output += it }
        }
        runCurrent()
        assertTrue(output.isEmpty())
        continueReply.complete(Unit)
        job.join()
        assertEquals(listOf("看：`$happyTag` 和 [链接](https://example.com) 后文。"), output)
    }

    @Test
    fun ordinaryMarkdownImageAndPublicSessionStayCompatible() = runTest {
        val markdown = "![示例](https://example.com/example.png)"
        assertEquals(listOf(markdown, happyImage), replySegments("$markdown\n$happyTag", catalog()))
        val processorClass = WaifuMessageProcessor.StreamingSession::class.java
        val standaloneSession = processorClass.getConstructor().newInstance()
        assertEquals(listOf("正文。"), standaloneSession.collectFinalSegments("正文。$happyTag"))
        assertEquals(
            listOf("正文"),
            WaifuMessageProcessor.StreamingSession(removePunctuation = true).collectFinalSegments("正文。"),
        )
    }

    private suspend fun replySegments(content: String, images: WaifuEmotionImages?): List<String> {
        val output = mutableListOf<String>()
        WaifuMessageProcessor.streamSegmentUpdatesWithTypingQueue(streamOf(content), charDelayMs = 0) { images }
            .collect { update -> if (update is Append) output += update.content }
        return output
    }
}
