package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuMessageProcessor as ChatWaifuMessageProcessor
import com.ai.assistance.operit.util.stream.Stream

/** 已安装的 Java 桥接脚本继续使用旧类名，所有实现统一由聊天 Waifu 包提供。 */
@Deprecated("使用 ui.features.conversation.chat.waifu.WaifuMessageProcessor")
object WaifuMessageProcessor {
    class StreamingSession(removePunctuation: Boolean = false) {
        private val session = ChatWaifuMessageProcessor.StreamingSession(removePunctuation)

        fun collectFirstSegmentPreview(content: String): String? = session.collectFirstSegmentPreview(content)
        fun collectStableSegments(content: String): List<String> = session.collectStableSegments(content)
        fun collectFinalSegments(content: String): List<String> = session.collectFinalSegments(content)
    }

    fun initialize(appContext: Context) = ChatWaifuMessageProcessor.initialize(appContext)

    fun streamSegments(
        sourceStream: Stream<String>,
        removePunctuation: Boolean = false,
        onFirstSegmentPreview: (suspend (String) -> Unit)? = null,
    ): Stream<String> = ChatWaifuMessageProcessor.streamSegments(sourceStream, removePunctuation, onFirstSegmentPreview)

    fun streamSegmentsWithTypingQueue(
        sourceStream: Stream<String>,
        removePunctuation: Boolean = false,
        charDelayMs: Int,
        onFirstSegmentPreview: (suspend (String) -> Unit)? = null,
    ): Stream<String> = ChatWaifuMessageProcessor.streamSegmentsWithTypingQueue(sourceStream, removePunctuation, charDelayMs, onFirstSegmentPreview)

    fun streamTtsText(sourceStream: Stream<String>): Stream<Char> = ChatWaifuMessageProcessor.streamTtsText(sourceStream)

    fun splitMessageBySentences(content: String, removePunctuation: Boolean = false): List<String> =
        ChatWaifuMessageProcessor.splitMessageBySentences(content, removePunctuation)

    fun splitStableMessageSegments(content: String, removePunctuation: Boolean = false): List<String> =
        ChatWaifuMessageProcessor.splitStableMessageSegments(content, removePunctuation)

    fun buildRenderableContentForWaifu(content: String): String = ChatWaifuMessageProcessor.buildRenderableContentForWaifu(content)
    fun cleanContentForWaifu(content: String): String = ChatWaifuMessageProcessor.cleanContentForWaifu(content)
    fun cleanContentForWaifuDisplay(content: String): String = ChatWaifuMessageProcessor.cleanContentForWaifuDisplay(content)
    fun processEmotionTags(content: String): String = ChatWaifuMessageProcessor.processEmotionTags(content)
    fun separateEmotionAndText(content: String): List<String> = ChatWaifuMessageProcessor.separateEmotionAndText(content)
}