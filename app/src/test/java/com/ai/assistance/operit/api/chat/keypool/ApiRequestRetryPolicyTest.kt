package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.llmprovider.HttpStatusCodeException
import com.ai.assistance.operit.api.chat.llmprovider.LlmRetryPolicy
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRequestRetryPolicyTest {
    @Test
    fun invalidParametersStopWithoutRotatingKeys() {
        listOf(
            HttpFailure(400, "invalid_request_error: unsupported parameter temperature"),
            HttpFailure(422, "request validation failed"),
            IOException("invalid_argument: 参数错误"),
            IOException("context_length_exceeded: maximum context length is 8192"),
            IOException("request failed", IOException("unsupported_parameter")),
        ).forEach { error ->
            assertEquals(error.message, ApiRequestRetryMode.STOP, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun invalidKeysRemainKeyFailuresWhenGenericRequestTypeIsPresent() {
        listOf(
            HttpFailure(401, "invalid_request_error: invalid API key"),
            HttpFailure(400, "invalid_request_error: incorrect API key"),
            HttpFailure(403, "permission denied"),
            IOException("request failed", IOException("invalid_api_key")),
        ).forEach { error ->
            assertEquals(error.message, ApiRequestRetryMode.ROTATE_KEY, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun unavailableModelsCanUseAnotherCandidateKey() {
        assertEquals(
            ApiRequestRetryMode.ROTATE_KEY,
            ApiRequestRetryPolicy.mode(HttpFailure(404, "invalid_request_error: model_not_found")),
        )
    }

    @Test
    fun quotaAndBalanceFailuresRetainRetries() {
        listOf(
            HttpFailure(400, "invalid_request_error: insufficient_quota"),
            HttpFailure(402, "Payment required"),
            HttpFailure(403, "invalid_request_error: 余额不足"),
            HttpFailure(429, "insufficient_quota"),
            IOException("invalid_request_error", IOException("insufficient quota")),
        ).forEach { error ->
            assertEquals(error.message, ApiRequestRetryMode.RETRY, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun transientHttpFailuresKeepRetriesDespiteGenericRequestErrors() {
        listOf(408, 409, 425, 429, 500, 503).forEach { status ->
            assertEquals(
                "HTTP $status",
                ApiRequestRetryMode.RETRY,
                ApiRequestRetryPolicy.mode(HttpFailure(status, "invalid_request_error")),
            )
        }
    }

    @Test
    fun transportAndFirstTokenTimeoutsKeepRetries() {
        listOf(SocketTimeoutException("timeout"), FirstTokenTimeoutException()).forEach { error ->
            assertEquals(ApiRequestRetryMode.RETRY, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun singleKeyTransientFailuresAllowExactlyFiveRetries() {
        val maxAttempts = ApiKeyRetryPlanner.maxHttpAttempts(1, poolMode = false)
        assertEquals(LlmRetryPolicy.MAX_RETRY_ATTEMPTS + 1, maxAttempts)
        listOf(
            ApiKeyErrorClass.QUOTA,
            ApiKeyErrorClass.RATE_LIMIT,
            ApiKeyErrorClass.SERVER,
            ApiKeyErrorClass.NETWORK,
            ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT,
        ).forEach { errorClass ->
            for (attempt in 1..maxAttempts) {
                val decision = ApiKeyRetryPlanner.decide(
                    errorClass = errorClass,
                    sameKeyAttempts = attempt,
                    remainingCandidatesAfterExclude = 0,
                    httpAttemptsIncludingThis = attempt,
                    maxHttpAttempts = maxAttempts,
                )
                assertEquals("$errorClass attempt=$attempt", attempt == maxAttempts, decision.exhausted)
                assertFalse(decision.switchedKey)
                if (attempt == maxAttempts) assertEquals(0L, decision.delayMs)
            }
        }
    }

    @Test
    fun invalidKeysAndModelsStopAfterFirstFailureWithoutOtherCandidates() {
        listOf(ApiKeyErrorClass.AUTH, ApiKeyErrorClass.MODEL).forEach { errorClass ->
            assertTrue(ApiKeyRetryPlanner.switchesKeyImmediately(errorClass, 0))
            val decision = ApiKeyRetryPlanner.decide(
                errorClass = errorClass,
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 0,
                httpAttemptsIncludingThis = 1,
                maxHttpAttempts = 6,
            )
            assertTrue(decision.exhausted)
            assertFalse(decision.switchedKey)
            assertEquals(0L, decision.delayMs)
        }
    }

    @Test
    fun invalidKeysAndModelsRotateWithoutRepeatingTheFailedKey() {
        listOf(ApiKeyErrorClass.AUTH, ApiKeyErrorClass.MODEL).forEach { errorClass ->
            val decision = ApiKeyRetryPlanner.decide(
                errorClass = errorClass,
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 1,
                httpAttemptsIncludingThis = 1,
                maxHttpAttempts = 2,
            )
            assertFalse(decision.exhausted)
            assertTrue(decision.switchedKey)
            assertEquals(0L, decision.delayMs)
        }
    }

    private class HttpFailure(
        override val statusCode: Int,
        message: String,
    ) : IOException(message), HttpStatusCodeException
}
