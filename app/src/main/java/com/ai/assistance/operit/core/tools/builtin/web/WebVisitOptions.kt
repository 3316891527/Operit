package com.ai.assistance.operit.core.tools.builtin.web

import kotlinx.coroutines.withTimeoutOrNull

/** 每次调用独立配置；阶段时限默认随整次时限变化，不再读取显示偏好。 */
internal data class WebVisitOptions(
    val timeoutSeconds: Long = 30,
    val loadTimeoutSeconds: Long = timeoutSeconds,
    val readTimeoutSeconds: Long = timeoutSeconds,
    val waitSeconds: Long = 0,
) {
    init {
        require(timeoutSeconds in 1..MAX_SECONDS) { "timeout_seconds must be a positive integer that can be represented in milliseconds" }
        require(loadTimeoutSeconds in 1..timeoutSeconds) { "load_timeout_seconds must be positive and must not exceed timeout_seconds" }
        require(readTimeoutSeconds in 1..timeoutSeconds) { "read_timeout_seconds must be positive and must not exceed timeout_seconds" }
        require(waitSeconds in 0 until timeoutSeconds) { "wait_seconds must be non-negative and less than timeout_seconds" }
    }

    val timeoutMillis: Long get() = timeoutSeconds * 1000L
    val loadTimeoutMillis: Long get() = loadTimeoutSeconds * 1000L
    val readTimeoutMillis: Long get() = readTimeoutSeconds * 1000L
    val waitMillis: Long get() = waitSeconds * 1000L

    companion object {
        // 仅防止秒转毫秒溢出，不设业务层的固定最大等待时间。
        private const val MAX_SECONDS = Long.MAX_VALUE / 1000L

        fun fromParameters(parameters: Map<String, String>): WebVisitOptions {
            fun seconds(name: String, default: Long): Long {
                val raw = parameters[name] ?: return default
                return raw.trim().toLongOrNull()
                    ?: throw WebVisitException(WebVisitFailure.INVALID_ARGUMENT, "$name must be an integer number of seconds")
            }
            val timeout = seconds("timeout_seconds", 30)
            return try {
                WebVisitOptions(
                    timeoutSeconds = timeout,
                    loadTimeoutSeconds = seconds("load_timeout_seconds", timeout),
                    readTimeoutSeconds = seconds("read_timeout_seconds", timeout),
                    waitSeconds = seconds("wait_seconds", 0),
                )
            } catch (error: IllegalArgumentException) {
                throw WebVisitException(WebVisitFailure.INVALID_ARGUMENT, error.message.orEmpty())
            }
        }
    }
}


/** 排队和整个访问共用一次调用期限，父协程取消保持取消语义。 */
internal suspend fun <T : Any> withWebVisitTimeout(options: WebVisitOptions, block: suspend () -> T): T =
    withTimeoutOrNull(options.timeoutMillis) { block() }
        ?: throw WebVisitException(WebVisitFailure.TOTAL_TIMEOUT, "timeout_seconds=${options.timeoutSeconds}")
