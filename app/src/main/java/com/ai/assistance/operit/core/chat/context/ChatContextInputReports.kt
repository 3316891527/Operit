package com.ai.assistance.operit.core.chat.context

import com.ai.assistance.operit.data.stats.ProviderUsageNormalizer
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一个会话最近正常完成的模型请求所确认的服务端输入，仅供上下文明细展示。 */
internal class ChatContextInputReports {
    private val lock = Any()
    private var generation = 0L
    private var nextRequest = 0L
    private var publishedThrough = 0L
    private val _inputTokens = MutableStateFlow<Long?>(null)
    val inputTokens: StateFlow<Long?> = _inputTokens.asStateFlow()

    fun startTurn(): Turn = synchronized(lock) { Turn(generation) }

    fun clear() = synchronized(lock) {
        generation += 1
        publishedThrough = nextRequest
        _inputTokens.value = null
    }

    inner class Turn internal constructor(private val startedInGeneration: Long) {
        fun startRequest(): Request? = synchronized(lock) {
            // 统计清除后，旧回答也不能在后续工具轮次重新登记。
            if (startedInGeneration != generation) null else Request(++nextRequest)
        }
    }

    inner class Request internal constructor(private val sequence: Long) {
        private var latestCall: ProviderCall? = null

        fun startProviderCall(): ProviderCall = synchronized(lock) {
            // 语音转写重发是新的 provider 调用，内部 attempt 可以重新从 1 开始。
            ProviderCall().also { latestCall = it }
        }

        fun finish(interrupted: Boolean, isActive: () -> Boolean) {
            synchronized(lock) {
                if (interrupted || sequence <= publishedThrough || !isActive()) return
                val result = latestCall?.completedInput() ?: return
                publishedThrough = sequence
                _inputTokens.value = result.tokens
            }
        }
    }

    data class CompletedInput(val tokens: Long?)

    inner class ProviderCall internal constructor() {
        private val usageByAttempt = mutableMapOf<Int, ProviderUsageSnapshot>()
        private var completed: CompletedInput? = null

        val onUsageReported: suspend (ProviderUsageSnapshot, Int) -> Unit = { snapshot, attempt ->
            synchronized<Unit>(lock) {
                if (attempt <= 0 || completed != null) return@synchronized
                if (snapshot.hasServerUsageSource()) {
                    usageByAttempt[attempt] = snapshot.mergeInputWith(usageByAttempt[attempt])
                } else {
                    usageByAttempt.remove(attempt)
                }
            }
        }
        val onUsageFinalized: suspend (Int?) -> Unit = { attempt ->
            synchronized(lock) {
                if (completed == null) {
                    // 成功但 attempt 或输入未知，区别于未收到完成信号。
                    completed = CompletedInput(
                        attempt?.let(usageByAttempt::get)?.totalInputTokens?.takeIf { it >= 0L }
                    )
                    usageByAttempt.clear()
                }
            }
        }

        fun completedInput(): CompletedInput? = synchronized(lock) { completed }
    }
}

private fun ProviderUsageSnapshot.mergeInputWith(previous: ProviderUsageSnapshot?): ProviderUsageSnapshot {
    if (completeSnapshot || previous == null || previous.source != source) return this
    if (source != ProviderUsageNormalizer.SOURCE_ANTHROPIC) {
        return if (totalInputTokens == null) previous else this
    }

    // Claude 的缓存分量可能在不同事件中出现；先合并输入分量，再确定总量。
    val uncached = uncachedInputTokens ?: previous.uncachedInputTokens
    val cached = cachedInputTokens ?: previous.cachedInputTokens
    val cacheWrite = cacheWriteTokens ?: previous.cacheWriteTokens
    return copy(
        uncachedInputTokens = uncached,
        cachedInputTokens = cached,
        cacheWriteTokens = cacheWrite,
        totalInputTokens = ProviderUsageNormalizer.anthropicTotalInputTokens(uncached, cached, cacheWrite),
    )
}

private fun ProviderUsageSnapshot.hasServerUsageSource(): Boolean = when (source) {
    ProviderUsageNormalizer.SOURCE_OPENAI_CHAT_COMPLETIONS,
    ProviderUsageNormalizer.SOURCE_OPENAI_RESPONSES,
    ProviderUsageNormalizer.SOURCE_ANTHROPIC,
    ProviderUsageNormalizer.SOURCE_GEMINI -> true
    else -> false
}
