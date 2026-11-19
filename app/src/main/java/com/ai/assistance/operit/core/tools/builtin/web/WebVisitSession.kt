package com.ai.assistance.operit.core.tools.builtin.web

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal enum class WebVisitFailure {
    LOAD_TIMEOUT, READ_TIMEOUT, TOTAL_TIMEOUT, CANCELLED, NETWORK, HTTP,
    CERTIFICATE, RENDERER_GONE, EMPTY_CONTENT, INVALID_CONTENT, EXTRACTION, DISPLAY,
}

internal class WebVisitException(val reason: WebVisitFailure, val detail: String = "") :
    IOException("${reason.name.lowercase()}: $detail")

internal enum class WebVisitPhase { LOADING, WAITING, READING, DONE }

/** 借鉴 Operit Ry 的整次访问预算；计时独立于组合，重定向不能重置加载时限。 */
internal class WebVisitSession(
    private val scope: CoroutineScope,
    val waitSeconds: Int,
    private val read: suspend () -> String,
    private val scroll: () -> Unit,
    private val finish: (Result<String>) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    companion object {
        const val LOAD_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 3_000L
        const val TOTAL_TIMEOUT_MS = 30_000L
        const val SETTLE_MS = 800L
        const val MAX_WAIT_SECONDS = 10
    }

    private val mutablePhase = MutableStateFlow(WebVisitPhase.LOADING)
    val phase = mutablePhase.asStateFlow()
    private var done = false
    private var loadDeadline = 0L
    private var loadJob: Job? = null
    private var settleJob: Job? = null
    private var workJob: Job? = null

    fun start() {
        loadDeadline = nowMillis() + LOAD_TIMEOUT_MS
        armLoadDeadline()
    }

    private fun armLoadDeadline() {
        loadJob?.cancel()
        loadJob = scope.launch {
            delay((loadDeadline - nowMillis()).coerceAtLeast(0))
            fail(WebVisitFailure.LOAD_TIMEOUT)
        }
    }

    fun onPageStarted() {
        if (done) return
        settleJob?.cancel()
        workJob?.cancel()
        mutablePhase.value = WebVisitPhase.LOADING
        armLoadDeadline()
    }

    fun onPageFinished() {
        if (done || phase.value != WebVisitPhase.LOADING) return
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(SETTLE_MS)
            beginRead(waitForContent = true)
        }
    }

    fun readNow() {
        if (!done && phase.value == WebVisitPhase.WAITING) beginRead(waitForContent = false)
    }

    private fun beginRead(waitForContent: Boolean) {
        if (done) return
        loadJob?.cancel()
        settleJob?.cancel()
        workJob?.cancel()
        workJob = scope.launch {
            try {
                if (waitForContent && waitSeconds > 0) {
                    mutablePhase.value = WebVisitPhase.WAITING
                    scroll()
                    delay(waitSeconds * 1000L)
                }
                mutablePhase.value = WebVisitPhase.READING
                val content = withTimeout(READ_TIMEOUT_MS) { read() }
                complete(Result.success(content))
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                fail(WebVisitFailure.READ_TIMEOUT)
            } catch (error: CancellationException) {
                throw error
            } catch (error: WebVisitException) {
                complete(Result.failure(error))
            } catch (error: Exception) {
                fail(WebVisitFailure.EXTRACTION, error.message.orEmpty())
            }
        }
    }

    fun fail(reason: WebVisitFailure, detail: String = "") {
        complete(Result.failure(WebVisitException(reason, detail)))
    }

    private fun complete(result: Result<String>) {
        if (!stop()) return
        finish(result)
    }

    /** 完成、取消及异常共用终态，晚到的页面与脚本回调不能再次结束访问。 */
    fun stop(): Boolean {
        if (done) return false
        done = true
        loadJob?.cancel()
        settleJob?.cancel()
        workJob?.cancel()
        mutablePhase.value = WebVisitPhase.DONE
        return true
    }
}
