package com.ai.assistance.operit.api.chat.keypool

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONObject


internal data class ApiTransportErrorDescription(
    val code: Int,
    val type: String,
    val message: String,
    val cause: String,
    val detail: String?,
    val timeoutMs: Long? = null,
) {
    data class RetryInfo(val nextAttempt: Int, val delayMs: Long)

    fun json(
        attempt: Int,
        retry: RetryInfo? = null,
        retryable: Boolean = true,
    ): String {
        val error = JSONObject()
            .put("code", code)
            .put("type", type)
            .put("message", message)
            .put("retryable", retryable)
            .put("attempt", attempt)
            .put("cause", cause)
        detail?.takeIf { it.isNotBlank() }?.let { error.put("detail", it) }
        timeoutMs?.let { error.put("timeoutMs", it) }
        retry?.let {
            error.put(
                "retry",
                JSONObject()
                    .put("nextAttempt", it.nextAttempt)
                    .put("delayMs", it.delayMs),
            )
        }
        return JSONObject().put("error", error).toString()
    }
}

internal object ApiTransportError {
    fun describe(exception: Throwable): ApiTransportErrorDescription? {
        val chain = causeChain(exception)
        // HTTP 状态异常已经携带服务端错误，不把它伪装成传输层错误。
        if (chain.any { it is HttpStatusCodeException }) return null

        val firstToken = chain.firstOrNull { it is FirstTokenTimeoutException }
        val streamReset = chain.firstOrNull { it.isStreamReset() }
        val connectionAborted = chain.firstOrNull { it.isConnectionAborted() }
        val hostResolution = chain.firstOrNull { it.isHostResolutionFailure() }
        val timeout = chain.firstOrNull { it.isConnectionTimeout() }
        val tls = chain.firstOrNull { it.isTlsFailure() }
        val connection = chain.firstOrNull { it.isConnectionFailure() }
        val source = chain.drop(1).firstOrNull { !it.message.isNullOrBlank() } ?: chain.first()

        return when {
            hostResolution != null -> description(
                code = 5000,
                type = "host_resolution_failed",
                message = "DNS resolution failed for the API host.",
                failure = hostResolution,
                detailSource = hostResolution,
            )
            firstToken != null -> description(
                code = 5004,
                type = "first_token_timeout",
                message = "No first token was received within the configured timeout.",
                failure = firstToken,
                detailSource = source,
                timeoutMs = LlmRetryPolicy.FIRST_TOKEN_TIMEOUT_MS,
            )
            timeout != null -> description(
                code = 5001,
                type = "connection_timeout",
                message = "The connection timed out before the request completed.",
                failure = timeout,
                detailSource = timeout,
            )
            streamReset != null -> description(
                code = 5002,
                type = "stream_reset",
                message = "The HTTP/2 streaming connection was reset by the peer or an intermediary.",
                failure = streamReset,
                detailSource = streamReset,
            )
            connectionAborted != null -> description(
                code = 5003,
                type = "connection_aborted",
                message = "The operating system or network stack aborted the connection.",
                failure = connectionAborted,
                detailSource = connectionAborted,
            )
            tls != null -> description(
                code = 5005,
                type = "tls_handshake_failed",
                message = "The TLS handshake or certificate validation failed.",
                failure = tls,
                detailSource = tls,
            )
            connection != null -> description(
                code = 5006,
                type = "connection_failed",
                message = "The client could not establish a connection to the server.",
                failure = connection,
                detailSource = connection,
            )
            chain.any { it is IOException } -> description(
                code = 5006,
                type = "connection_failed",
                message = "The request failed in the transport layer.",
                failure = exception,
                detailSource = source,
            )
            else -> null
        }
    }

    private fun description(
        code: Int,
        type: String,
        message: String,
        failure: Throwable,
        detailSource: Throwable,
        timeoutMs: Long? = null,
    ) = ApiTransportErrorDescription(
        code = code,
        type = type,
        message = message,
        cause = failure.javaClass.simpleName.ifBlank { failure.javaClass.name },
        detail = detailSource.message?.trim()?.take(500),
        timeoutMs = timeoutMs,
    )

    private fun causeChain(exception: Throwable): List<Throwable> {
        val result = mutableListOf<Throwable>()
        var current: Throwable? = exception
        var depth = 0
        while (current != null && depth++ < 32) {
            result += current
            current = current.cause
        }
        return result
    }

    private fun Throwable.messageText() = message.orEmpty().lowercase()

    private fun Throwable.isHostResolutionFailure(): Boolean =
        this is UnknownHostException || messageText().containsAny(
            "unable to resolve host",
            "no address associated with hostname",
            "unknown host",
            "failed to resolve",
        )

    private fun Throwable.isConnectionTimeout(): Boolean =
        this is SocketTimeoutException || messageText().containsAny("timeout", "timed out")

    private fun Throwable.isStreamReset(): Boolean =
        javaClass.simpleName == "StreamResetException" ||
            messageText().containsAny("stream was reset", "stream reset")

    private fun Throwable.isConnectionAborted(): Boolean =
        messageText().contains("software caused connection abort")

    private fun Throwable.isTlsFailure(): Boolean =
        this is SSLException || messageText().containsAny("ssl", "tls", "certificate", "certpath")

    private fun Throwable.isConnectionFailure(): Boolean =
        this is ConnectException ||
            this is NoRouteToHostException ||
            this is PortUnreachableException ||
            messageText().containsAny(
                "failed to connect",
                "connection refused",
                "network is unreachable",
                "no route to host",
                "port unreachable",
            )

    private fun String.containsAny(vararg values: String): Boolean = values.any { value -> contains(value) }
}
