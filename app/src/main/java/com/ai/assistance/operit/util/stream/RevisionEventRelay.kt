package com.ai.assistance.operit.util.stream

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 按顺序转发修订事件并平移跨轮次坐标；结束、失败和取消时排空剩余事件。 */
internal suspend fun Stream<String>.collectWithRevisionRelay(
    eventChannel: MutableSharedStream<TextStreamEvent>,
    roundStartLength: Int,
    onRollback: ((String) -> Unit)? = null,
    onCompletion: ((String) -> Unit)? = null,
    onChunk: suspend (String) -> Unit,
) = coroutineScope {
    val providerEvents = (this@collectWithRevisionRelay as? TextStreamEventCarrier)?.eventChannel
    val tracker = if (onRollback != null || onCompletion != null) TextStreamRevisionTracker() else null
    val trackerMutex = Mutex()
    val collectionJob = coroutineContext.job
    val stoppingRelay = AtomicBoolean(false)
    var relayFailure: Throwable? = null
    var forwardedEventCount = 0

    suspend fun forwardEvent(event: TextStreamEvent) {
        eventChannel.emit(event.shiftedBy(roundStartLength))
        forwardedEventCount++
        if (tracker == null) return
        val rollbackContent =
            trackerMutex.withLock {
                when (event.eventType) {
                    TextStreamEventType.SAVEPOINT -> {
                        tracker.savepoint(event.id, event.contentLength)
                        null
                    }
                    TextStreamEventType.ROLLBACK ->
                        tracker.rollback(
                            event.id,
                            event.contentLength,
                            event.discardedLength,
                        )?.toString()
                }
            }
        if (rollbackContent != null) {
            onRollback?.invoke(rollbackContent)
        }
    }

    val relayJob =
        providerEvents?.let { events ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    events.collect { event -> forwardEvent(event) }
                } catch (error: Throwable) {
                    if (error !is CancellationException ||
                        (collectionJob.isActive && !stoppingRelay.get())
                    ) {
                        relayFailure = error
                        if (error is CancellationException) {
                            collectionJob.cancel(error)
                        }
                    }
                    throw error
                }
            }
        }

    try {
        collectionJob.ensureActive()
        this@collectWithRevisionRelay.collect { content ->
            if (tracker != null) trackerMutex.withLock { tracker.append(content) }
            onChunk(content)
        }
    } finally {
        withContext(NonCancellable) {
            stoppingRelay.set(true)
            relayJob?.cancelAndJoin()
            val history = providerEvents?.replayCache.orEmpty()
            try {
                relayFailure?.let { throw it }
                for (index in forwardedEventCount until history.size) {
                    forwardEvent(history[index])
                }
            } finally {
                if (tracker != null) onCompletion?.invoke(tracker.applyEventHistory(history).toString())
            }
        }
    }
}
