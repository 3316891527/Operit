package com.ai.assistance.operit.util.streamnative

import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.StreamGroup
import com.ai.assistance.operit.util.stream.StreamLogger
import com.ai.assistance.operit.util.stream.asStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private fun Int.toMarkdownTypeOrNull(): MarkdownProcessorType? =
    MarkdownProcessorType.entries.getOrNull(this)

private suspend fun withNativeMarkdownSession(
    sessionFactory: () -> NativeMarkdownSplitter.Session,
    block: suspend (NativeMarkdownSplitter.Session) -> Unit,
) {
    var session: NativeMarkdownSplitter.Session? = null
    try {
        // 先登记句柄，跨调度器返回时发生取消也能释放原生会话。
        withContext(Dispatchers.Default) { session = sessionFactory() }
        block(checkNotNull(session))
    } finally {
        // 释放不能被调用方取消打断，也不能占用主线程。
        withContext(NonCancellable + Dispatchers.Default) { session?.destroy() }
    }
}

private fun <T> Stream<T>.nativeMarkdownSplitBySessionCore(
    sessionFactory: () -> NativeMarkdownSplitter.Session,
    debugTag: String,
    flushIntervalMs: Long?,
    maxDeltaChars: Int?,
    initialContent: String,
    appendValue: (StringBuilder, T) -> Unit,
    containsNewline: (T) -> Boolean,
): Stream<StreamGroup<MarkdownProcessorType?>> {
    val upstream = this

    return object : Stream<StreamGroup<MarkdownProcessorType?>> {
        override val isLocked: Boolean
            get() = upstream.isLocked
        override val bufferedCount: Int
            get() = upstream.bufferedCount
        override suspend fun lock() = upstream.lock()
        override suspend fun unlock() = upstream.unlock()
        override fun clearBuffer() = upstream.clearBuffer()

        override suspend fun collect(collector: StreamCollector<StreamGroup<MarkdownProcessorType?>>) {
            coroutineScope {
                val groupChannel = Channel<StreamGroup<MarkdownProcessorType?>>(Channel.UNLIMITED)
                launch {
                    try {
                        withNativeMarkdownSession(sessionFactory) { session ->
                            val fullContent = StringBuilder(initialContent)
                            val deltaBuffer = StringBuilder()
                            // 恢复解析状态并保留最后的真实块边界，不能仅凭类型相同合并新块。
                            val initialSegments =
                                if (initialContent.isNotEmpty()) {
                                    withContext(Dispatchers.Default) { session.push(initialContent) }
                                } else {
                                    intArrayOf()
                                }
                            val continuation = MarkdownGroupContinuation(initialSegments)
                            val mutex = Mutex()
                            val flushMutex = Mutex()
                            var defaultTextChannel: Channel<String>? = null
                            var activePluginChannel: Channel<String>? = null
                            var activeTag: MarkdownProcessorType? = null

                            suspend fun openDefaultChannel() {
                                if (defaultTextChannel == null) {
                                    val newChannel = Channel<String>(Channel.UNLIMITED)
                                    defaultTextChannel = newChannel
                                    groupChannel.send(
                                        StreamGroup(null, newChannel.consumeAsFlow().asStream(),
                                            continuesPrevious = continuation.takeFor(MarkdownProcessorType.PLAIN_TEXT))
                                    )
                                }
                            }

                            suspend fun closeDefaultChannel() {
                                defaultTextChannel?.close()
                                defaultTextChannel = null
                            }

                            suspend fun openPluginChannel(tag: MarkdownProcessorType) {
                                val newChannel = Channel<String>(Channel.UNLIMITED)
                                activePluginChannel = newChannel
                                activeTag = tag
                                groupChannel.send(
                                    StreamGroup(tag, newChannel.consumeAsFlow().asStream(),
                                        continuesPrevious = continuation.takeFor(tag))
                                )
                            }

                            suspend fun closePluginChannel() {
                                activePluginChannel?.close()
                                activePluginChannel = null
                                activeTag = null
                            }

                            suspend fun flushDelta() {
                                // 原生解析和分组发布共用一把锁，定时刷新不能越过上游刷新。
                                flushMutex.withLock {
                                    val delta = mutex.withLock {
                                        if (deltaBuffer.isEmpty()) {
                                            null
                                        } else {
                                            deltaBuffer.toString().also { deltaBuffer.setLength(0) }
                                        }
                                    } ?: return
                                    val segments = withContext(Dispatchers.Default) { session.push(delta) }
                                    if (segments.isEmpty()) return

                                    data class Action(val type: MarkdownProcessorType?, val text: String?)
                                    val actions = ArrayList<Action>(segments.size / 3)
                                    mutex.withLock {
                                        var i = 0
                                        while (i + 2 < segments.size) {
                                            val typeOrdinal = segments[i]
                                            val start = segments[i + 1]
                                            val end = segments[i + 2]
                                            i += 3
                                            if (typeOrdinal < 0) {
                                                actions.add(Action(type = null, text = null))
                                                continue
                                            }
                                            val type = typeOrdinal.toMarkdownTypeOrNull() ?: MarkdownProcessorType.PLAIN_TEXT
                                            if (start < 0 || end < 0 || start > end || end > fullContent.length) {
                                                continue
                                            }
                                            actions.add(Action(type = type, text = fullContent.substring(start, end)))
                                        }
                                    }
                                    for (action in actions) {
                                        if (action.type == null) {
                                            continuation.onBoundary()
                                            closeDefaultChannel()
                                            closePluginChannel()
                                            continue
                                        }
                                        val type = action.type
                                        val text = action.text ?: ""
                                        if (type == MarkdownProcessorType.PLAIN_TEXT) {
                                            if (activePluginChannel != null) closePluginChannel()
                                            openDefaultChannel()
                                            if (text.isNotEmpty()) defaultTextChannel?.send(text)
                                        } else {
                                            if (defaultTextChannel != null) closeDefaultChannel()
                                            if (activeTag == null) {
                                                openPluginChannel(type)
                                            } else if (activeTag != type) {
                                                closePluginChannel()
                                                openPluginChannel(type)
                                            }
                                            if (text.isNotEmpty()) activePluginChannel?.send(text)
                                        }
                                    }
                                }
                            }

                            val flushJob =
                                if (flushIntervalMs != null && flushIntervalMs > 0) {
                                    launch {
                                        while (true) {
                                            delay(flushIntervalMs)
                                            flushDelta()
                                        }
                                    }
                                } else {
                                    null
                                }
                            try {
                                // 上游仍在调用方上下文收集，界面持有的拦截器状态不会转移到后台线程。
                                upstream.collect { value ->
                                    val noBatching = flushIntervalMs == null && maxDeltaChars == null
                                    val shouldFlush = mutex.withLock {
                                        appendValue(fullContent, value)
                                        appendValue(deltaBuffer, value)
                                        containsNewline(value) ||
                                            (maxDeltaChars != null && maxDeltaChars > 0 && deltaBuffer.length >= maxDeltaChars)
                                    }
                                    if (noBatching || shouldFlush) flushDelta()
                                }
                                flushDelta()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                StreamLogger.e(debugTag, "nativeMarkdownSplitBy failed: ${e.message}", e)
                                throw e
                            } finally {
                                // JNI 调用本身不能被协程取消中断，必须等定时任务退出后再销毁句柄。
                                withContext(NonCancellable) {
                                    flushJob?.cancelAndJoin()
                                    closeDefaultChannel()
                                    closePluginChannel()
                                }
                            }
                        }
                    } finally {
                        // 初始化失败或初始化时被取消，也必须唤醒等待分组的消费者。
                        groupChannel.close()
                    }
                }
                for (group in groupChannel) collector.emit(group)
            }
        }
    }
}

private fun Stream<Char>.nativeMarkdownSplitBySession(
    sessionFactory: () -> NativeMarkdownSplitter.Session,
    debugTag: String,
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
    initialContent: String = "",
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionCore(
        sessionFactory, debugTag, flushIntervalMs, maxDeltaChars, initialContent,
        appendValue = { builder, value -> builder.append(value) },
        containsNewline = { it == '\n' },
    )

private fun Stream<String>.nativeMarkdownSplitBySessionString(
    sessionFactory: () -> NativeMarkdownSplitter.Session,
    debugTag: String,
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
    initialContent: String = "",
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionCore(
        sessionFactory, debugTag, flushIntervalMs, maxDeltaChars, initialContent,
        appendValue = { builder, value -> builder.append(value) },
        containsNewline = { it.indexOf('\n') >= 0 },
    )

fun Stream<Char>.nativeMarkdownSplitByBlock(
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySession(
        sessionFactory = { NativeMarkdownSplitter.createBlockSession() },
        debugTag = "NativeMarkdownBlockSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
    )

internal fun Stream<Char>.nativeMarkdownSplitByBlock(
    flushIntervalMs: Long?,
    maxDeltaChars: Int?,
    initialContent: String,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySession(
        sessionFactory = { NativeMarkdownSplitter.createBlockSession() },
        debugTag = "NativeMarkdownBlockSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
        initialContent = initialContent,
    )

fun Stream<Char>.nativeMarkdownSplitByInline(
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySession(
        sessionFactory = { NativeMarkdownSplitter.createInlineSession() },
        debugTag = "NativeMarkdownInlineSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
    )

internal fun Stream<Char>.nativeMarkdownSplitByInline(
    flushIntervalMs: Long?,
    maxDeltaChars: Int?,
    initialContent: String,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySession(
        sessionFactory = { NativeMarkdownSplitter.createInlineSession() },
        debugTag = "NativeMarkdownInlineSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
        initialContent = initialContent,
    )

@JvmName("nativeMarkdownSplitByBlockString")
fun Stream<String>.nativeMarkdownSplitByBlock(
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionString(
        sessionFactory = { NativeMarkdownSplitter.createBlockSession() },
        debugTag = "NativeMarkdownBlockSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
    )

@JvmName("nativeMarkdownSplitByBlockStringWithInitialContent")
internal fun Stream<String>.nativeMarkdownSplitByBlock(
    flushIntervalMs: Long?,
    maxDeltaChars: Int?,
    initialContent: String,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionString(
        sessionFactory = { NativeMarkdownSplitter.createBlockSession() },
        debugTag = "NativeMarkdownBlockSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
        initialContent = initialContent,
    )

@JvmName("nativeMarkdownSplitByInlineString")
fun Stream<String>.nativeMarkdownSplitByInline(
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionString(
        sessionFactory = { NativeMarkdownSplitter.createInlineSession() },
        debugTag = "NativeMarkdownInlineSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
    )

@JvmName("nativeMarkdownSplitByInlineStringWithInitialContent")
internal fun Stream<String>.nativeMarkdownSplitByInline(
    flushIntervalMs: Long?,
    maxDeltaChars: Int?,
    initialContent: String,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionString(
        sessionFactory = { NativeMarkdownSplitter.createInlineSession() },
        debugTag = "NativeMarkdownInlineSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
        initialContent = initialContent,
    )
