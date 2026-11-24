package com.ai.assistance.operit.api.chat.decorator

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.config.model.ModelOption
import com.ai.assistance.operit.data.config.model.ModelParameter
import com.ai.assistance.operit.data.model.TokenRequestLogEntity
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import com.ai.assistance.operit.data.stats.TokenRequestSource
import java.util.UUID
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.data.stats.TokenUsageRepository
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.RevisableTextStream
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.api.chat.AIService

/** 请求日志与成功用量分离；只有提供商确认的成功用量进入原账本。 */
class TokenTrackingAIService(
    private val delegate: AIService,
    context: Context,
    private val configId: String,
    private val beginRecording: suspend () -> Long = TokenUsageRepository.getInstance(context.applicationContext)::beginRequest,
    private val recordingSink: suspend (TokenRequestLogEntity, TokenUsageRecordEntity?, Long) -> Boolean =
        TokenUsageRepository.getInstance(context.applicationContext)::recordRequest,
) : AIService {
    private val activeRequests = ConcurrentHashMap.newKeySet<RequestTracker>()
    private val cancellationLock = Any()
    private var cancellationEpoch = 0L

    override val inputTokenCount: Long get() = delegate.inputTokenCount
    override val cachedInputTokenCount: Long get() = delegate.cachedInputTokenCount
    override val outputTokenCount: Long get() = delegate.outputTokenCount
    override val providerModel: String get() = delegate.providerModel

    override fun resetTokenCounts() = delegate.resetTokenCounts()
    override fun cancelStreaming() {
        synchronized(cancellationLock) {
            cancellationEpoch += 1
            activeRequests.forEach { request -> request.cancel() }
        }
        delegate.cancelStreaming()
    }
    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> =
        delegate.getModelsList(context)

    override suspend fun calculateInputTokens(
        chatHistory: List<PromptTurn>,
        availableTools: List<ToolPrompt>?,
    ): Long = delegate.calculateInputTokens(chatHistory, availableTools)

    override fun release() = delegate.release()

    override suspend fun sendMessage(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        thinkingOptionId: String?,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
        onUsageReported: (suspend (ProviderUsageSnapshot, attempt: Int) -> Unit)?,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean,
        recordTokenUsage: Boolean,
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
    ): Stream<String> {
        val requestEpoch = synchronized(cancellationLock) { cancellationEpoch }
        val epoch = if (recordTokenUsage) beginRecording() else 0L
        val source = if (recordTokenUsage) currentCoroutineContext()[TokenRequestSource] else null
        val request = RequestTracker(configId, providerModel, epoch, source, recordTokenUsage)
        val onStarted: () -> Unit = {
            synchronized(cancellationLock) {
                if (cancellationEpoch != requestEpoch) request.cancel()
                activeRequests.add(request)
                Unit
            }
        }
        val onFinished: () -> Unit = {
            synchronized(cancellationLock) {
                activeRequests.remove(request)
                Unit
            }
        }
        try {
            val inner = delegate.sendMessage(
                    context = context,
                    chatHistory = chatHistory,
                    modelParameters = modelParameters,
                    enableThinking = enableThinking,
                    thinkingOptionId = thinkingOptionId,
                    stream = stream,
                    availableTools = availableTools,
                    preserveThinkInHistory = preserveThinkInHistory,
                    onTokensUpdated = onTokensUpdated,
                    onUsageReported = if (recordTokenUsage) {
                        { usage, attempt ->
                            request.onUsage(usage, attempt)
                            forwardUsageObserver(onUsageReported, usage, attempt)
                        }
                    } else onUsageReported,
                    onNonFatalError = onNonFatalError,
                    enableRetry = enableRetry,
                    recordTokenUsage = recordTokenUsage,
                    onUsageFinalized = if (recordTokenUsage) {
                        { attempt ->
                            request.onSuccess(attempt)
                            forwardUsageFinalizedObserver(onUsageFinalized, attempt)
                        }
                    } else onUsageFinalized,
                )
            return wrapStream(inner, request, onStarted, onFinished)
        } catch (error: CancellationException) {
            request.cancel()
            persist(recordingSink, request, TokenRequestLogEntity.CANCELLED)
            throw error
        } catch (error: Exception) {
            persist(recordingSink, request, TokenRequestLogEntity.FAILED)
            throw error
        }
    }
    override suspend fun testConnection(context: Context): Result<String> =
        delegate.testConnection(context)

    private suspend fun forwardUsageObserver(
        observer: (suspend (ProviderUsageSnapshot, Int) -> Unit)?,
        usage: ProviderUsageSnapshot,
        attempt: Int,
    ) {
        val callback = observer ?: return
        try {
            callback(usage, attempt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "usage observer failed", e)
        }
    }

    private suspend fun forwardUsageFinalizedObserver(
        observer: (suspend (Int?) -> Unit)?,
        attempt: Int?,
    ) {
        val callback = observer ?: return
        try {
            callback(attempt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "usage completion observer failed", e)
        }
    }

    private fun wrapStream(
        inner: Stream<String>,
        request: RequestTracker,
        onStarted: () -> Unit,
        onFinished: () -> Unit,
    ): Stream<String> =
        if (inner is TextStreamEventCarrier) {
            TrackingRevisableStream(inner, inner.eventChannel, request, recordingSink, onStarted, onFinished)
        } else {
            TrackingStream(inner, request, recordingSink, onStarted, onFinished)
        }

    private class TrackingStream(
        private val inner: Stream<String>,
        private val request: RequestTracker,
        private val recordingSink: suspend (TokenRequestLogEntity, TokenUsageRecordEntity?, Long) -> Boolean,
        private val onStarted: () -> Unit,
        private val onFinished: () -> Unit,
    ) : Stream<String> {
        override val isLocked: Boolean get() = inner.isLocked
        override val bufferedCount: Int get() = inner.bufferedCount
        override suspend fun lock() = inner.lock()
        override suspend fun unlock() = inner.unlock()
        override fun clearBuffer() = inner.clearBuffer()

        override suspend fun collect(collector: StreamCollector<String>) {
            collectTracked(inner, collector, request, recordingSink, onStarted, onFinished)
        }
    }

    private class TrackingRevisableStream(
        private val inner: Stream<String>,
        override val eventChannel: SharedStream<TextStreamEvent>,
        private val request: RequestTracker,
        private val recordingSink: suspend (TokenRequestLogEntity, TokenUsageRecordEntity?, Long) -> Boolean,
        private val onStarted: () -> Unit,
        private val onFinished: () -> Unit,
    ) : RevisableTextStream {
        override val isLocked: Boolean get() = inner.isLocked
        override val bufferedCount: Int get() = inner.bufferedCount
        override suspend fun lock() = inner.lock()
        override suspend fun unlock() = inner.unlock()
        override fun clearBuffer() = inner.clearBuffer()

        override suspend fun collect(collector: StreamCollector<String>) {
            collectTracked(inner, collector, request, recordingSink, onStarted, onFinished)
        }
    }

    internal class RequestTracker(
        private val configId: String,
        private val providerModel: String,
        val epoch: Long,
        private val source: TokenRequestSource?,
        val recordingEnabled: Boolean = true,
    ) {
        private val requestId = UUID.randomUUID().toString()
        private val startedAtMs = System.currentTimeMillis()
        private val startedAtNs = System.nanoTime()
        private var firstTokenLatencyMs: Long? = null
        private var successFinalized = false
        private val lock = Any()
        private val attempts = linkedMapOf<Int, ProviderUsageSnapshot>()
        private val finished = AtomicBoolean(false)
        private val cancelled = AtomicBoolean(false)
        private var successfulAttempt: Int? = null

        fun onUsage(usage: ProviderUsageSnapshot, attempt: Int) {
            synchronized(lock) {
                if (attempt < 1) return
                val key = attempt
                attempts[key] = merge(attempts[key], usage)
            }
        }

        fun onSuccess(attempt: Int?) {
            synchronized(lock) {
                successFinalized = true
                successfulAttempt = attempt?.takeIf { it > 0 }
            }
        }

        fun cancel() {
            cancelled.set(true)
        }

        fun throwIfCancelled() {
            if (cancelled.get()) throw CancellationException("AI request was cancelled")
        }

        private fun successFact(): TokenUsageRecordEntity? {
            if (cancelled.get()) return null
            val snapshot =
                synchronized(lock) {
                    successfulAttempt?.let(attempts::get)
                } ?: return null
            if (cancelled.get()) return null
            if (!snapshot.hasKnownFields()) return null

            val separator = providerModel.indexOf(':')
            require(separator > 0 && separator < providerModel.lastIndex) {
                "provider:model is required for token usage events"
            }
            // 推理不再作为独立统计列落库；provider 明确将其独立于 output 上报时，
            // 先并入持久化 output，保持总量与费用口径完整。
            val reportedOutputTokens = snapshot.outputTokens
            val persistedOutputTokens = when {
                reportedOutputTokens != null && snapshot.reasoningIncludedInOutput == false ->
                    snapshot.reasoningTokens?.let { saturatedAdd(reportedOutputTokens, it) }
                reportedOutputTokens != null -> reportedOutputTokens
                else -> null
            }
            if (snapshot.uncachedInputTokens == null &&
                snapshot.cachedInputTokens == null &&
                snapshot.cacheWriteTokens == null &&
                snapshot.totalInputTokens == null &&
                persistedOutputTokens == null
            ) {
                return null
            }
            return TokenUsageRecordEntity(
                requestId = requestId,
                occurredAtMs = startedAtMs,
                configId = configId,
                provider = providerModel.substring(0, separator),
                model = providerModel.substring(separator + 1),
                requestCount = 1L,
                uncachedInputTokens = snapshot.uncachedInputTokens,
                cachedInputTokens = snapshot.cachedInputTokens,
                cacheWriteTokens =
                    if (snapshot.cacheWriteSeparateBilling) snapshot.cacheWriteTokens else 0L,
                totalInputTokens = snapshot.totalInputTokens,
                outputTokens = persistedOutputTokens,
            )
        }

        fun onText(value: String) {
            if (value.isEmpty()) return
            synchronized(lock) {
                if (firstTokenLatencyMs == null) firstTokenLatencyMs = elapsedMs()
            }
        }

        private fun elapsedMs(): Long = (System.nanoTime() - startedAtNs) / 1_000_000L

        fun finish(terminalStatus: String?): RequestCompletion? = synchronized(lock) {
            if (!finished.compareAndSet(false, true)) return@synchronized null
            val status = when {
                cancelled.get() -> TokenRequestLogEntity.CANCELLED
                terminalStatus != null -> terminalStatus
                successFinalized -> TokenRequestLogEntity.SUCCESS
                else -> TokenRequestLogEntity.COMPLETED
            }
            val usageAttempt = if (status == TokenRequestLogEntity.SUCCESS) successfulAttempt else attempts.keys.maxOrNull()
            val snapshot = usageAttempt?.let(attempts::get)
            val separator = providerModel.indexOf(':')
            require(separator > 0 && separator < providerModel.lastIndex)
            val fact = if (status == TokenRequestLogEntity.SUCCESS) successFact() else null
            val output = snapshot?.outputTokens?.let { reported ->
                if (snapshot.reasoningIncludedInOutput == false)
                    snapshot.reasoningTokens?.let { saturatedAdd(reported, it) }
                else reported
            }
            RequestCompletion(
                TokenRequestLogEntity(
                    requestId = requestId,
                    startedAtMs = startedAtMs,
                    endedAtMs = System.currentTimeMillis(),
                    durationMs = elapsedMs(),
                    firstTokenLatencyMs = firstTokenLatencyMs,
                    configId = configId,
                    provider = providerModel.substring(0, separator),
                    model = providerModel.substring(separator + 1),
                    functionType = source?.functionType?.name,
                    executionModel = source?.executionModel,
                    status = status,
                    successfulAttempt = successfulAttempt,
                    observedAttemptNumbers = (attempts.keys + listOfNotNull(successfulAttempt))
                        .distinct().sorted().takeIf { it.isNotEmpty() }?.joinToString(","),
                    usageAttempt = usageAttempt,
                    uncachedInputTokens = snapshot?.uncachedInputTokens,
                    cachedInputTokens = snapshot?.cachedInputTokens,
                    cacheWriteTokens = snapshot?.cacheWriteTokens,
                    totalInputTokens = snapshot?.totalInputTokens,
                    outputTokens = output,
                    reasoningTokens = snapshot?.reasoningTokens,
                    reasoningIncludedInOutput = snapshot?.reasoningIncludedInOutput,
                    cacheWriteSeparateBilling = snapshot?.cacheWriteSeparateBilling,
                    usageSource = snapshot?.source,
                ),
                fact,
            )
        }

        private fun merge(
            previous: ProviderUsageSnapshot?,
            update: ProviderUsageSnapshot,
        ): ProviderUsageSnapshot {
            if (previous == null || update.completeSnapshot) return update
            return update.copy(
                uncachedInputTokens = update.uncachedInputTokens ?: previous.uncachedInputTokens,
                cachedInputTokens = update.cachedInputTokens ?: previous.cachedInputTokens,
                cacheWriteTokens = update.cacheWriteTokens ?: previous.cacheWriteTokens,
                totalInputTokens = update.totalInputTokens ?: previous.totalInputTokens,
                outputTokens = update.outputTokens ?: previous.outputTokens,
                reasoningTokens = update.reasoningTokens ?: previous.reasoningTokens,
            )
        }
    }

    internal data class RequestCompletion(val log: TokenRequestLogEntity, val fact: TokenUsageRecordEntity?)

    companion object {
        private const val TAG = "TokenTrackingAIService"

        private suspend fun collectTracked(
            inner: Stream<String>,
            collector: StreamCollector<String>,
            request: RequestTracker,
            recordingSink: suspend (TokenRequestLogEntity, TokenUsageRecordEntity?, Long) -> Boolean,
            onStarted: () -> Unit,
            onFinished: () -> Unit,
        ) {
            try {
                onStarted()
                request.throwIfCancelled()
                inner.collect { value ->
                    if (request.recordingEnabled) request.onText(value)
                    collector.emit(value)
                }
                currentCoroutineContext().ensureActive()
                request.throwIfCancelled()
                persist(recordingSink, request, null)
            } catch (error: CancellationException) {
                request.cancel()
                persist(recordingSink, request, TokenRequestLogEntity.CANCELLED)
                throw error
            } catch (error: Exception) {
                persist(recordingSink, request, TokenRequestLogEntity.FAILED)
                throw error
            } finally {
                onFinished()
            }
        }

        private suspend fun persist(
            recordingSink: suspend (TokenRequestLogEntity, TokenUsageRecordEntity?, Long) -> Boolean,
            request: RequestTracker,
            status: String?,
        ) {
            if (!request.recordingEnabled) return
            val completed = request.finish(status) ?: return
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    recordingSink(completed.log, completed.fact, request.epoch)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e(TAG, "token request insert failed", error)
                }
            }
        }

        private fun saturatedAdd(left: Long, right: Long): Long =
            if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
