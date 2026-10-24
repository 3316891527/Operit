package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.llmprovider.HttpStatusCodeException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiKeyFailureClassifier {
    fun classify(exception: Throwable): ApiKeyErrorClass {
        if (poolExhausted(exception) != null) return ApiKeyErrorClass.NONE
        var current: Throwable? = exception
        while (current != null) {
            when (current) {
                is FirstTokenTimeoutException -> return ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT
                is SocketTimeoutException -> return ApiKeyErrorClass.NETWORK
                is UnknownHostException -> return ApiKeyErrorClass.NETWORK
                is HttpStatusCodeException -> return classifyHttp(current.statusCode, current.message)
            }
            current = current.cause
        }
        // 请求层可能包装异常，内层的余额或鉴权信息同样参与错误分类。
        current = exception
        while (current != null) {
            classifyByMessage(current.message.orEmpty())?.let { return it }
            current = current.cause
        }
        return ApiKeyErrorClass.NETWORK
    }

    fun poolExhausted(exception: Throwable): ApiKeyPoolExhaustedException? {
        var current: Throwable? = exception
        while (current != null) {
            if (current is ApiKeyPoolExhaustedException) return current
            current = current.cause
        }
        return null
    }

    fun isEndpointError(exception: Throwable): Boolean {
        val status = httpStatus(exception)
        return status == 405 || status == 404 && classify(exception) == ApiKeyErrorClass.NONE
    }

    fun retryAfterMs(exception: Throwable): Long? {
        var current: Throwable? = exception
        while (current != null) {
            val fromInterface = (current as? HttpStatusCodeException)?.retryAfterMs
            if (fromInterface != null && fromInterface > 0L) {
                return fromInterface
            }
            current = current.cause
        }
        return null
    }

    fun httpStatus(exception: Throwable): Int? {
        var current: Throwable? = exception
        while (current != null) {
            val status = (current as? HttpStatusCodeException)?.statusCode
            if (status != null) {
                return status
            }
            current = current.cause
        }
        return null
    }

    private fun classifyHttp(statusCode: Int, message: String?): ApiKeyErrorClass {
        if (statusCode == 405) return ApiKeyErrorClass.NONE
        classifyByMessage(message.orEmpty())?.let { fromMessage ->
            if (statusCode == 403 || statusCode == 400 || statusCode == 404) {
                return fromMessage
            }
        }
        return when (statusCode) {
            401 -> ApiKeyErrorClass.AUTH
            402 -> ApiKeyErrorClass.QUOTA
            403 -> classifyByMessage(message.orEmpty()) ?: ApiKeyErrorClass.AUTH
            // 只有明确的模型错误才参与换钥；普通路由 404 不应隔离整个池。
            404 -> classifyByMessage(message.orEmpty()) ?: ApiKeyErrorClass.NONE
            429 -> ApiKeyErrorClass.RATE_LIMIT
            in 500..599 -> ApiKeyErrorClass.SERVER
            else -> classifyByMessage(message.orEmpty()) ?: ApiKeyErrorClass.NETWORK
        }
    }

    private fun classifyByMessage(message: String): ApiKeyErrorClass? {
        val text = message.lowercase()
        if (text.isBlank()) return null
        if (looksLikeQuota(text)) return ApiKeyErrorClass.QUOTA
        if (looksLikeRateLimit(text)) return ApiKeyErrorClass.RATE_LIMIT
        if (looksLikeAuth(text)) return ApiKeyErrorClass.AUTH
        if (looksLikeModel(text)) return ApiKeyErrorClass.MODEL
        return null
    }

    private fun looksLikeQuota(text: String): Boolean {
        return text.contains("insufficient_quota") ||
            text.contains("insufficient quota") ||
            text.contains("quota exceeded") ||
            text.contains("quota_exceeded") ||
            text.contains("billing") ||
            text.contains("balance") ||
            text.contains("credit") ||
            text.contains("payment required") ||
            text.contains("pre_consume_token") ||
            text.contains("额度") ||
            text.contains("余额") ||
            text.contains("欠费")
    }

    private fun looksLikeRateLimit(text: String): Boolean {
        return text.contains("rate limit") ||
            text.contains("rate_limit") ||
            text.contains("too many requests") ||
            text.contains("tpm") && text.contains("limit") ||
            text.contains("rpm") && text.contains("limit") ||
            text.contains("限流")
    }

    private fun looksLikeAuth(text: String): Boolean {
        return text.contains("invalid api key") ||
            text.contains("incorrect api key") ||
            text.contains("invalid_api_key") ||
            text.contains("api key is invalid") ||
            text.contains("api key is not valid") ||
            text.contains("api key not valid") ||
            text.contains("api_key_invalid") ||
            text.contains("expired api key") ||
            text.contains("api key has expired") ||
            text.contains("invalid x-api-key") ||
            text.contains("authentication") ||
            text.contains("unauthorized") ||
            text.contains("permission denied") ||
            text.contains("not allowed")
    }

    private fun looksLikeModel(text: String): Boolean {
        return (text.contains("model") &&
            (text.contains("not found") ||
                text.contains("does not exist") ||
                text.contains("does not have access") ||
                text.contains("not supported") ||
                text.contains("unknown") ||
                text.contains("invalid"))) ||
            text.contains("model_not_found")
    }
}
