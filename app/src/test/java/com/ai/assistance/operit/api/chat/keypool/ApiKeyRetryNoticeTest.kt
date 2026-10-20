package com.ai.assistance.operit.api.chat.keypool

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.HttpStatusCodeException
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
            402 to "Insufficient Balance",
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
        assertEquals(300_000L, error.getLong("timeoutMs"))
        assertEquals(2, error.getJSONObject("retry").getInt("nextAttempt"))
        assertEquals(0L, error.getJSONObject("retry").getLong("delayMs"))
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
