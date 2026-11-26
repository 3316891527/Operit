package com.ai.assistance.operit.core.tools.javascript

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** 只在运行线程调用；每批处理后把剩余任务排到队尾，给计时、取消和关闭留出执行机会。 */
internal class QuickJsPendingJobPump(
    private val executor: Executor,
    private val isClosed: () -> Boolean,
    private val executeJobs: (Int) -> Int,
    private val reportFailure: (Throwable) -> Unit
) {
    private var continuationQueued = false

    fun drain() {
        if (isClosed()) return
        // 达到预算还可能有 Promise 任务；不能等下一次外部调用或超时计时器才继续。
        if (executeJobs(JOB_BUDGET) == JOB_BUDGET) enqueueContinuation()
    }

    private fun enqueueContinuation() {
        if (continuationQueued || isClosed()) return
        continuationQueued = true
        try {
            executor.execute {
                continuationQueued = false
                if (!isClosed()) {
                    try {
                        drain()
                    } catch (failure: Throwable) {
                        reportFailure(failure)
                    }
                }
            }
        } catch (failure: RejectedExecutionException) {
            continuationQueued = false
            // 关闭引擎时队列已退役，不能再为其创建执行线程。
            if (!isClosed()) throw failure
        }
    }

    private companion object {
        const val JOB_BUDGET = 128
    }
}
