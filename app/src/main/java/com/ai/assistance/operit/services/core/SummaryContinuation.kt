package com.ai.assistance.operit.services.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal enum class SummaryContinuationResult { DISPATCHED, SUPERSEDED, CANCELLED, TIMED_OUT }

/** 总结交接等待发送任务的完整生命周期，不依赖仅在成功时递增的业务计数。 */
internal suspend fun continueAfterSummary(
    previousTurn: Job?,
    isCurrent: () -> Boolean,
    isChatLoading: () -> Boolean,
    dispatch: suspend () -> Unit,
    timeoutMs: Long = 180_000L,
): SummaryContinuationResult {
    if (!isCurrent()) return SummaryContinuationResult.SUPERSEDED
    if (previousTurn != null) {
        val settled = withTimeoutOrNull(timeoutMs) {
            previousTurn.join()
            true
        } == true
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) return SummaryContinuationResult.SUPERSEDED
        if (!settled) return SummaryContinuationResult.TIMED_OUT
        // 停止操作不能因为随后收到任务完成信号而重新启动请求。
        if (previousTurn.isCancelled) return SummaryContinuationResult.CANCELLED
    }
    if (!isCurrent()) return SummaryContinuationResult.SUPERSEDED
    check(!isChatLoading()) { "总结交接时会话仍被其他发送任务占用" }
    currentCoroutineContext().ensureActive()
    dispatch()
    return SummaryContinuationResult.DISPATCHED
}
