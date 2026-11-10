package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface WaifuSegmentUpdate {
    data class Append(val content: String) : WaifuSegmentUpdate
    data class Retain(val count: Int) : WaifuSegmentUpdate
    data class Preview(val content: String) : WaifuSegmentUpdate
    data class Thinking(val content: String?) : WaifuSegmentUpdate
}

private data class WaifuPendingSnapshot(val segments: List<String>, val preview: String?, val thinking: String?)

/** 逐句展示以最新修订快照为准，撤回失效气泡并清理待发送的旧句子。 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun streamWaifuUpdates(
    sourceStream: Stream<String>,
    splitSnapshot: (content: String, final: Boolean) -> List<String>,
    typingDelayMs: (segmentLength: Int, first: Boolean) -> Long,
    nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    firstSegmentPreview: ((String) -> String?)? = null,
    thinkingSnapshot: ((String, Boolean) -> String?)? = null,
): Stream<WaifuSegmentUpdate> = stream {
    coroutineScope {
        val snapshots = Channel<WaifuPendingSnapshot>(Channel.CONFLATED)
        val tracker = TextStreamRevisionTracker()
        val trackerMutex = Mutex()
        val carrier = sourceStream as? TextStreamEventCarrier
        var sourceCompleted = false
        fun currentSnapshot(final: Boolean, finishing: Boolean = false): WaifuPendingSnapshot {
            val content = tracker.currentContent().toString()
            val segments = splitSnapshot(content, final).toList()
            val preview = if (!final && segments.isEmpty()) firstSegmentPreview?.invoke(content) else null
            return WaifuPendingSnapshot(segments, preview?.takeIf { it.isNotBlank() }, thinkingSnapshot?.invoke(content, final || finishing))
        }
        val producer = launch {
            var failure: Exception? = null
            var lastSnapshot: WaifuPendingSnapshot? = null

            suspend fun publish(final: Boolean) {
                tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                val snapshot = currentSnapshot(final)
                if (snapshot != lastSnapshot) {
                    snapshots.send(snapshot)
                    lastSnapshot = snapshot
                }
            }

            try {
                coroutineScope {
                    val revisions = carrier?.let {
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            it.eventChannel.collect {
                                trackerMutex.withLock { publish(final = false) }
                            }
                        }
                    }
                    try {
                        sourceStream.collect { chunk ->
                            trackerMutex.withLock {
                                tracker.append(chunk)
                                publish(final = false)
                            }
                        }
                        sourceCompleted = true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failure = error
                    } finally {
                        withContext(NonCancellable) { revisions?.cancelAndJoin() }
                    }
                    trackerMutex.withLock {
                        tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                        publish(final = failure == null)
                    }
                }
            } catch (error: CancellationException) {
                failure = error
                throw error
            } catch (error: Exception) {
                failure = error
            } finally {
                snapshots.close(failure)
            }
        }

        val displayed = mutableListOf<String>()
        var target = emptyList<String>()
        var sourceClosed = false
        var pending: String? = null
        var dueAt = 0L
        var previewContent: String? = null
        var thinkingContent: String? = null

        fun scheduleNext() {
            val next = target.getOrNull(displayed.size)
            if (next != pending) {
                pending = next
                dueAt = if (next == null) 0L else
                    nowMillis() + typingDelayMs(next.length, displayed.isEmpty())
            }
        }

        suspend fun accept(snapshot: WaifuPendingSnapshot, allowPreview: Boolean = true) {
            if (snapshot.thinking != thinkingContent) {
                emit(WaifuSegmentUpdate.Thinking(snapshot.thinking))
                thinkingContent = snapshot.thinking
            }
            val segments = snapshot.segments
            var common = 0
            while (common < displayed.size && common < segments.size &&
                displayed[common] == segments[common]
            ) {
                common++
            }
            if (common < displayed.size) {
                emit(WaifuSegmentUpdate.Retain(common))
                displayed.subList(common, displayed.size).clear()
                pending = null
                if (common == 0) previewContent = null
            }
            if (displayed.isEmpty() && segments.isEmpty()) {
                val preview = snapshot.preview
                if (allowPreview && preview != null && preview != previewContent) {
                    emit(WaifuSegmentUpdate.Preview(preview))
                    previewContent = preview
                } else if (previewContent != null && previewContent != preview) {
                    emit(WaifuSegmentUpdate.Retain(0))
                    previewContent = null
                }
            }
            target = segments
            scheduleNext()
        }

        suspend fun appendNext() {
            val revisedSnapshot = trackerMutex.withLock {
                tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                val latest = currentSnapshot(sourceCompleted)
                if (latest.segments != target ||
                    (displayed.isEmpty() && latest.segments.isEmpty() && latest.preview != previewContent)
                ) {
                    snapshots.tryReceive()
                    latest
                } else {
                    null
                }
            }
            if (revisedSnapshot != null) {
                accept(revisedSnapshot)
                return
            }
            val segment = target[displayed.size]
            displayed += segment
            emit(WaifuSegmentUpdate.Append(segment))
            previewContent = null
            pending = null
            scheduleNext()
        }

        var completed = false
        try {
            while (true) {
                val incoming = snapshots.tryReceive()
                if (incoming.isSuccess) {
                    accept(incoming.getOrThrow())
                    continue
                }
                if (incoming.isClosed) {
                    incoming.exceptionOrNull()?.let { throw it }
                    sourceClosed = true
                }
                if (displayed.size == target.size) {
                    if (sourceClosed) break
                    val next = snapshots.receiveCatching()
                    if (next.isSuccess) {
                        accept(next.getOrThrow())
                    } else {
                        next.exceptionOrNull()?.let { throw it }
                        sourceClosed = true
                    }
                    continue
                }
                val remainingDelay = (dueAt - nowMillis()).coerceAtLeast(0L)
                if (remainingDelay == 0L) {
                    appendNext()
                } else if (sourceClosed) {
                    delay(remainingDelay)
                    appendNext()
                } else {
                    select<Unit> {
                        snapshots.onReceiveCatching { next ->
                            if (next.isSuccess) {
                                accept(next.getOrThrow())
                            } else {
                                next.exceptionOrNull()?.let { throw it }
                                sourceClosed = true
                            }
                        }
                        onTimeout(remainingDelay) { appendNext() }
                    }
                }
            }
            producer.join()
            completed = true
        } finally {
            withContext(NonCancellable) {
                producer.cancelAndJoin()
                if (!completed) {
                    val finalSnapshot = trackerMutex.withLock {
                        tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                        currentSnapshot(sourceCompleted, finishing = true)
                    }
                    accept(finalSnapshot, allowPreview = false)
                }
            }
        }
    }
}

internal class WaifuSegmentMessageStore<T>(
    private val messages: MutableList<T>,
    private val createMessage: (String) -> T,
    private val appendMessage: suspend (message: T, interruptSpeech: Boolean) -> Unit,
    private val removeMessages: suspend (List<T>) -> Unit,
    private val reviseMessage: ((T, String) -> T)? = null,
    private val publishPreview: suspend (T) -> Unit = {},
    private val publishThinking: suspend (T) -> Unit = publishPreview,
) {
    private var interruptNextSpeech = false
    private var hasPreview = false
    private var thinkingIndex: Int? = null
    private val bodyCount: Int get() = messages.size - if (thinkingIndex == null) 0 else 1
    private val lastBodyIndex: Int get() =
        if (thinkingIndex == messages.lastIndex) messages.lastIndex - 1 else messages.lastIndex

    suspend fun apply(update: WaifuSegmentUpdate) {
        when (update) {
            is WaifuSegmentUpdate.Thinking -> {
                val index = thinkingIndex
                if (update.content == null) {
                    if (index != null) {
                        removeMessages(listOf(messages[index]))
                        messages.removeAt(index)
                        thinkingIndex = null
                    }
                } else {
                    val message = if (index == null) {
                        createMessage(update.content).also {
                            messages.add(it)
                            thinkingIndex = messages.lastIndex
                        }
                    } else {
                        checkNotNull(reviseMessage)(messages[index], update.content).also { messages[index] = it }
                    }
                    // 思考只更新展示，不进入正文的打字队列或自动朗读回调。
                    publishThinking(message)
                }
            }
            is WaifuSegmentUpdate.Append -> {
                val interruptSpeech = bodyCount == 0 || hasPreview || interruptNextSpeech
                val message = if (hasPreview) {
                    val index = lastBodyIndex
                    checkNotNull(reviseMessage)(messages[index], update.content).also { messages[index] = it }
                } else {
                    createMessage(update.content).also { messages += it }
                }
                appendMessage(message, interruptSpeech)
                hasPreview = false
                interruptNextSpeech = false
            }
            is WaifuSegmentUpdate.Preview -> {
                val message = if (hasPreview) {
                    val index = lastBodyIndex
                    checkNotNull(reviseMessage)(messages[index], update.content).also { messages[index] = it }
                } else {
                    check(bodyCount == 0)
                    createMessage(update.content).also { messages += it }
                }
                hasPreview = true
                publishPreview(message)
            }
            is WaifuSegmentUpdate.Retain -> {
                require(update.count in 0..bodyCount)
                val removedIndices = messages.indices.filter { it != thinkingIndex }.drop(update.count)
                if (removedIndices.isNotEmpty()) {
                    removeMessages(removedIndices.map(messages::get))
                    val newThinkingIndex = thinkingIndex?.let { index -> index - removedIndices.count { it < index } }
                    removedIndices.asReversed().forEach { messages.removeAt(it) }
                    thinkingIndex = newThinkingIndex
                    interruptNextSpeech = true
                    hasPreview = false
                }
            }
        }
    }
}
