package com.ai.assistance.operit.core.tools.builtin.web

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal enum class WebVisitFailure {
    LOAD_TIMEOUT, READ_TIMEOUT, TOTAL_TIMEOUT, CANCELLED, NETWORK, HTTP,
    CERTIFICATE, RENDERER_GONE, EMPTY_CONTENT, INVALID_CONTENT, EXTRACTION, DISPLAY,
    INVALID_ARGUMENT, ACCESS_BLOCKED, CLOUDFLARE_CHALLENGE, CLOUDFLARE_BLOCKED,
}

internal class WebVisitException(val reason: WebVisitFailure, val detail: String = "") :
    IOException("${reason.name.lowercase()}: $detail")

internal enum class WebVisitPhase { LOADING, WAITING, READING, DONE }

/** 期限由调用参数决定；计时独立于组合，重定向不能重新计时。 */
internal class WebVisitSession(
    private val scope: CoroutineScope,
    val options: WebVisitOptions,
    private val read: suspend () -> String,
    private val scroll: () -> Unit,
    private val finish: (Result<String>) -> Unit,
    private val inspect: suspend () -> WebVisitException? = { null },
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    companion object {
        // 检查节奏不是访问时限；只检查当前页面，不发送网络预检请求。
        internal const val BLOCK_CHECK_INTERVAL_MS = 500L
    }

    private val mutablePhase = MutableStateFlow(WebVisitPhase.LOADING)
    val phase = mutablePhase.asStateFlow()
    private var done = false
    private var loadStartedAt = 0L
    private var loadJob: Job? = null
    private var inspectionJob: Job? = null
    private var workJob: Job? = null

    fun start() {
        loadStartedAt = nowMillis()
        armLoadDeadline()
    }

    private fun armLoadDeadline() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val elapsed = nowMillis() - loadStartedAt
            delay((options.loadTimeoutMillis - elapsed).coerceAtLeast(0))
            fail(WebVisitFailure.LOAD_TIMEOUT, "load_timeout_seconds=${options.loadTimeoutSeconds}")
        }
    }

    fun onPageStarted() {
        if (done) return
        inspectionJob?.cancel()
        workJob?.cancel()
        mutablePhase.value = WebVisitPhase.LOADING
        armLoadDeadline()
    }

    /** 首次可见后检查，并在加载及等待期间捕获稍后出现的验证或拦截页。 */
    fun onPageVisible() {
        if (done || phase.value == WebVisitPhase.READING || inspectionJob?.isActive == true) return
        inspectionJob = scope.launch {
            try {
                while (!done && phase.value != WebVisitPhase.READING) {
                    val failure = withTimeout(options.readTimeoutMillis) { inspect() }
                    if (failure != null) {
                        complete(Result.failure(failure))
                        return@launch
                    }
                    delay(BLOCK_CHECK_INTERVAL_MS)
                }
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                fail(WebVisitFailure.READ_TIMEOUT, "read_timeout_seconds=${options.readTimeoutSeconds}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: WebVisitException) {
                complete(Result.failure(error))
            } catch (error: Exception) {
                fail(WebVisitFailure.EXTRACTION, error.message.orEmpty())
            }
        }
    }

    fun onPageFinished() {
        if (done || phase.value != WebVisitPhase.LOADING) return
        beginRead(waitForContent = true)
    }

    fun readNow() {
        if (!done && phase.value == WebVisitPhase.WAITING) beginRead(waitForContent = false)
    }

    private fun beginRead(waitForContent: Boolean) {
        if (done) return
        loadJob?.cancel()
        workJob?.cancel()
        val waiting = waitForContent && options.waitSeconds > 0
        mutablePhase.value = if (waiting) WebVisitPhase.WAITING else WebVisitPhase.READING
        if (waiting) onPageVisible() else inspectionJob?.cancel()
        workJob = scope.launch {
            try {
                if (waiting) {
                    scroll()
                    delay(options.waitMillis)
                }
                mutablePhase.value = WebVisitPhase.READING
                inspectionJob?.cancel()
                val content = withTimeout(options.readTimeoutMillis) {
                    // 提取前再检查一次，HTTP 200 的挑战页也不能成为成功内容。
                    val failure = inspect()
                    if (failure != null) throw failure
                    read()
                }
                complete(Result.success(content))
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                fail(WebVisitFailure.READ_TIMEOUT, "read_timeout_seconds=${options.readTimeoutSeconds}")
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
        inspectionJob?.cancel()
        workJob?.cancel()
        mutablePhase.value = WebVisitPhase.DONE
        return true
    }
}