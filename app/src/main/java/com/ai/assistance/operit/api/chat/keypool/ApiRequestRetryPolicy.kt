package com.ai.assistance.operit.api.chat.keypool

import java.util.Locale

internal enum class ApiRequestRetryMode {
    STOP,
    ROTATE_KEY,
    RETRY,
}

/** 请求参数错误与密钥故障分别处理，避免重发同一份必然失败的请求。 */
internal object ApiRequestRetryPolicy {
    private val invalidParameterMarkers = listOf(
        "invalid_request_error",
        "invalid_parameter",
        "unsupported_parameter",
        "invalid_argument",
        "context_length_exceeded",
        "invalid parameter",
        "unsupported parameter",
        "unknown parameter",
        "unrecognized request argument",
        "maximum context length",
        "参数错误",
        "参数无效",
        "不支持的参数",
    )

    fun mode(exception: Throwable): ApiRequestRetryMode {
        val errorClass = ApiKeyFailureClassifier.classify(exception)
        // 余额和鉴权错误也可能使用通用 invalid_request 类型，必须先按具体原因处理。
        when (errorClass) {
            ApiKeyErrorClass.QUOTA, ApiKeyErrorClass.RATE_LIMIT -> return ApiRequestRetryMode.RETRY
            ApiKeyErrorClass.AUTH, ApiKeyErrorClass.MODEL -> return ApiRequestRetryMode.ROTATE_KEY
            else -> Unit
        }

        val status = ApiKeyFailureClassifier.httpStatus(exception)
        // 超时、并发冲突、过早请求与服务端故障不能仅因响应中的通用错误字段而停止重试。
        if (status == 408 || status == 409 || status == 425 || status != null && status in 500..599) {
            return ApiRequestRetryMode.RETRY
        }
        if (hasInvalidParameterCode(exception) || status != null && status in 400..499) {
            return ApiRequestRetryMode.STOP
        }
        return ApiRequestRetryMode.RETRY
    }

    private fun hasInvalidParameterCode(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            val message = current.message.orEmpty().lowercase(Locale.ROOT)
            if (invalidParameterMarkers.any(message::contains)) return true
            current = current.cause
        }
        return false
    }
}
