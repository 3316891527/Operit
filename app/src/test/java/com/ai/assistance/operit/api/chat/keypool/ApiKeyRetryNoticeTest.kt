package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.error.FirstTokenTimeoutException
import android.content.Context
import com.ai.assistance.operit.api.chat.error.HttpStatusCodeException
import com.ai.assistance.operit.util.AppLogger
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ApiKeyRetryNoticeTest {
    private val context = mock(Context::class.java)
    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    @Before
    fun disableAndroidLogging() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun keySwitchPublishesEveryHttpFailureAfterRollback() = runTest {
        val failures = mapOf(
            400 to "INVALID_ARGUMENT: API key not valid. Please pass a valid API key. API_KEY_INVALID",
            401 to "Unauthorized",
            402 to "Insufficient Balance",
            404 to "model_not_found",
            429 to "Too Many Requests",
            502 to "Bad Gateway",
            503 to "Service Unavailable",
        )
        val decision = ApiKeyRetryDecision(exhausted = false, delayMs = 0L, switchedKey = true)
        for ((code, reason) in failures) {
            val events = mutableListOf<String>()
            val errorText = "HTTP $code: $reason"
            val result = withContext(ApiKeyRequestElement(RetrySession(decision))) {
                handleKeyAwareRetry(
                    context = context,
                    exception = httpFailure(code, errorText),
                    enableRetry = true,
                    onNonFatalError = { events += it },
                    onRetryAccepted = { events += "rollback" },
                    buildRetryMessage = { error, retry, delay -> "$error; retry=$retry; delayMs=$delay" },
                    logTag = "test",
                    attemptNumber = 1,
                )
            }

            assertEquals(decision, result)
            assertEquals(listOf("rollback", "$errorText; retry=1; delayMs=0"), events)
        }
    }

    @Test
    fun sameKeyRetryPublishesReasonAndWaitBeforeBackoff() = runTest {
        val notices = mutableListOf<String>()
        var noticedAt = -1L
        val decision = ApiKeyRetryDecision(exhausted = false, delayMs = 1500L, switchedKey = false)
        withContext(ApiKeyRequestElement(RetrySession(decision))) {
            handleKeyAwareRetry(
                context = context,
                exception = httpFailure(503, "HTTP 503: maintenance"),
                enableRetry = true,
                onNonFatalError = {
                    notices += it
                    noticedAt = testScheduler.currentTime
                },
                onRetryAccepted = {},
                buildRetryMessage = { error, retry, delay -> "$error; retry=$retry; delayMs=$delay" },
                logTag = "test",
                attemptNumber = 2,
            )
        }

        assertEquals(listOf("HTTP 503: maintenance; retry=2; delayMs=1500"), notices)
        assertEquals(0L, noticedAt)
        assertEquals(1500L, testScheduler.currentTime)
    }

    @Test
    fun keySwitchKeepsTransportErrorCodeAndRetryMetadata() = runTest {
        val notices = mutableListOf<String>()
        val decision = ApiKeyRetryDecision(exhausted = false, delayMs = 0L, switchedKey = true)
        withContext(ApiKeyRequestElement(RetrySession(decision))) {
            handleKeyAwareRetry(
                context = context,
                exception = FirstTokenTimeoutException(),
                enableRetry = true,
                onNonFatalError = { notices += it },
                onRetryAccepted = {},
                buildRetryMessage = { _, _, _ -> error("传输错误应保留结构化元数据") },
                logTag = "test",
                attemptNumber = 1,
            )
        }

        val error = JSONObject(notices.single()).getJSONObject("error")
        assertEquals(5004, error.getInt("code"))
        assertEquals(600_000L, error.getLong("timeoutMs"))
        assertEquals(2, error.getJSONObject("retry").getInt("nextAttempt"))
        assertEquals(0L, error.getJSONObject("retry").getLong("delayMs"))
    }

    @Test
    fun invalidParametersNeverPublishARetryEvenWhenTheErrorMentionsModelAccess() = runTest {
        listOf(
            httpFailure(400, "invalid_request_error: unsupported parameter temperature"),
            httpFailure(400, "invalid parameter temperature: not supported by model"),
            httpFailure(403, "unsupported parameter: not allowed for this model"),
            httpFailure(500, "unsupported_parameter: invalid value for model"),
            httpFailure(400, "invalid_request_error: Unsupported value: temperature does not support 0 with this model"),
        ).forEach { failureError ->
            val events = mutableListOf<String>()
            val decision = ApiKeyRetryDecision(exhausted = false, delayMs = 0L, switchedKey = true)
            val failure = runCatching {
                withContext(ApiKeyRequestElement(RetrySession(decision))) {
                    handleKeyAwareRetry(
                        context = context,
                        exception = failureError,
                        enableRetry = true,
                        onNonFatalError = { events += it },
                        onRetryAccepted = { events += "rollback" },
                        buildRetryMessage = { _, _, _ -> error("参数错误不应构造重试提示") },
                        logTag = "test",
                        attemptNumber = 1,
                    )
                }
            }.exceptionOrNull()
            assertEquals(failureError.message, failure?.message)
            assertEquals(emptyList<String>(), events)
        }
    }

    @Test
    fun invalidCredentialsStopWithoutARetryNoticeWhenNoOtherKeysRemain() = runTest {
        listOf(
            httpFailure(401, "HTTP 401: Unauthorized"),
            httpFailure(400, "INVALID_ARGUMENT: API key not valid. Please pass a valid API key. API_KEY_INVALID"),
            IOException("API key is invalid"),
            IOException("API key is not valid"),
            IOException("API key has expired"),
        ).forEach { failureError ->
            val decision = ApiKeyRetryPlanner.decide(
                errorClass = ApiKeyFailureClassifier.classify(failureError),
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 0,
                httpAttemptsIncludingThis = 1,
                maxHttpAttempts = 6,
            )
            val events = mutableListOf<String>()
            val failure = runCatching {
                withContext(ApiKeyRequestElement(RetrySession(decision))) {
                    handleKeyAwareRetry(
                        context = context,
                        exception = failureError,
                        enableRetry = true,
                        onNonFatalError = { events += it },
                        onRetryAccepted = { events += "rollback" },
                        buildRetryMessage = { _, _, _ -> error("无可用密钥时不应构造重试提示") },
                        logTag = "test",
                        attemptNumber = 1,
                    )
                }
            }.exceptionOrNull()
            assertEquals(true, decision.exhausted)
            assertEquals(failureError.message, failure?.message)
            assertEquals(emptyList<String>(), events)
        }
    }

    @Test
    fun balanceFailureRetainsItsRetryNoticeAndBackoff() = runTest {
        val decision = ApiKeyRetryPlanner.decide(
            errorClass = ApiKeyErrorClass.QUOTA,
            sameKeyAttempts = 1,
            remainingCandidatesAfterExclude = 0,
            httpAttemptsIncludingThis = 1,
            maxHttpAttempts = 6,
        )
        val events = mutableListOf<String>()
        withContext(ApiKeyRequestElement(RetrySession(decision))) {
            handleKeyAwareRetry(
                context = context,
                exception = httpFailure(402, "HTTP 402: Insufficient Balance"),
                enableRetry = true,
                onNonFatalError = { events += it },
                onRetryAccepted = { events += "rollback" },
                buildRetryMessage = { text, retry, delay -> "$text; retry=$retry; delayMs=$delay" },
                logTag = "test",
                attemptNumber = 1,
            )
        }
        assertEquals(false, decision.exhausted)
        assertEquals(listOf("rollback", "HTTP 402: Insufficient Balance; retry=1; delayMs=1000"), events)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    private fun httpFailure(code: Int, message: String): Exception =
        object : IOException(message), HttpStatusCodeException {
            override val statusCode: Int = code
        }

    private class RetrySession(private val decision: ApiKeyRetryDecision) : ApiKeyRequestSession {
        override suspend fun nextKey(): ApiKeySelection = error("本用例不选择密钥")
        override suspend fun reportOutcome(report: ApiKeyAttemptReport) = Unit
        override suspend fun maxAttempts(): Int = 6
        override fun triedSummary(): ApiKeyTriedSummary = ApiKeyTriedSummary()
        override suspend fun decideRetry(errorClass: ApiKeyErrorClass, retryAfterMs: Long?) = decision
    }
}
