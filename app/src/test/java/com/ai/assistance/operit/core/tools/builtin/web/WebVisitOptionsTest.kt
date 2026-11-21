package com.ai.assistance.operit.core.tools.builtin.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WebVisitOptionsTest {
    private fun invalid(parameters: Map<String, String>) {
        try {
            WebVisitOptions.fromParameters(parameters)
            fail("非法时限不得静默改写或继续访问")
        } catch (error: WebVisitException) {
            assertEquals(WebVisitFailure.INVALID_ARGUMENT, error.reason)
        }
    }

    @Test
    fun omittedParametersKeepExistingCallsUsableWithoutASettingsDependency() {
        val options = WebVisitOptions.fromParameters(emptyMap())
        assertEquals(30_000L, options.timeoutMillis)
        assertEquals(options.timeoutMillis, options.loadTimeoutMillis)
        assertEquals(options.timeoutMillis, options.readTimeoutMillis)
        assertEquals(0L, options.waitMillis)
    }

    @Test
    fun extendingOnlyTheWholeVisitAlsoExtendsUnspecifiedStages() {
        val options = WebVisitOptions.fromParameters(mapOf("timeout_seconds" to "90"))
        assertEquals(90_000L, options.timeoutMillis)
        assertEquals(90_000L, options.loadTimeoutMillis)
        assertEquals(90_000L, options.readTimeoutMillis)
    }

    @Test
    fun explicitStagesAndLongLazyWaitArePreservedExactly() {
        val options = WebVisitOptions.fromParameters(mapOf(
            "timeout_seconds" to "120", "load_timeout_seconds" to "75",
            "read_timeout_seconds" to "20", "wait_seconds" to "15",
        ))
        assertEquals(75_000L, options.loadTimeoutMillis)
        assertEquals(20_000L, options.readTimeoutMillis)
        assertEquals(15_000L, options.waitMillis)
    }

    @Test
    fun longVisitsAreNotClampedToAnArbitraryBusinessMaximum() {
        val options = WebVisitOptions.fromParameters(mapOf("timeout_seconds" to "7200", "wait_seconds" to "300"))
        assertEquals(7_200_000L, options.timeoutMillis)
        assertEquals(300_000L, options.waitMillis)
    }

    @Test
    fun malformedZeroNegativeFractionalAndOverflowValuesFail() {
        for (value in listOf("", " ", "0", "-1", "1.5", "NaN", "Infinity", Long.MAX_VALUE.toString(), "999999999999999999999999")) {
            invalid(mapOf("timeout_seconds" to value))
        }
        invalid(mapOf("wait_seconds" to "-1"))
        invalid(mapOf("read_timeout_seconds" to "0"))
        invalid(mapOf("load_timeout_seconds" to "broken"))
    }

    @Test
    fun ContradictoryPhaseBudgetsFailInsteadOfBeingSilentlyClamped() {
        invalid(mapOf("timeout_seconds" to "10", "load_timeout_seconds" to "11"))
        invalid(mapOf("timeout_seconds" to "10", "read_timeout_seconds" to "11"))
        invalid(mapOf("timeout_seconds" to "10", "wait_seconds" to "10"))
    }

    @Test
    fun LargestRepresentableSecondsConvertWithoutOverflow() {
        val seconds = Long.MAX_VALUE / 1000L
        val options = WebVisitOptions.fromParameters(mapOf("timeout_seconds" to seconds.toString()))
        assertEquals(seconds * 1000L, options.timeoutMillis)
        invalid(mapOf("timeout_seconds" to (seconds + 1).toString()))
    }

    @Test
    fun aLongerOverallDeadlineAllowsWorkPastTheOldThirtySecondLimit() = runTest {
        val value = withWebVisitTimeout(WebVisitOptions(90)) {
            delay(45_000)
            "slow page"
        }
        assertEquals("slow page", value)
        assertEquals(45_000L, testScheduler.currentTime)
    }

    @Test
    fun queueTimeIsIncludedInTheOverallDeadline() = runTest {
        val slots = Semaphore(1, acquiredPermits = 1)
        try {
            withWebVisitTimeout(WebVisitOptions(2)) {
                slots.withPermit { "must not run" }
            }
            fail("排队也必须受调用期限约束")
        } catch (error: WebVisitException) {
            assertEquals(WebVisitFailure.TOTAL_TIMEOUT, error.reason)
            assertEquals(2_000L, testScheduler.currentTime)
        } finally {
            slots.release()
        }
    }

    @Test
    fun externalCancellationDoesNotBecomeATotalTimeoutError() = runTest {
        val entered = CompletableDeferred<Unit>()
        var cancelled = false
        val task = async {
            withWebVisitTimeout(WebVisitOptions(90)) {
                entered.complete(Unit)
                try { awaitCancellation() } catch (error: CancellationException) {
                    cancelled = true
                    throw error
                }
            }
        }
        runCurrent()
        entered.await()
        task.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(task.isCancelled)
        assertEquals(0L, testScheduler.currentTime)
    }
}