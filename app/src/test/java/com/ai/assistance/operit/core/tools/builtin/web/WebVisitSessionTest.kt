package com.ai.assistance.operit.core.tools.builtin.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebVisitSessionTest {
    private fun TestScope.session(
        results: MutableList<Result<String>>,
        waitSeconds: Long = 0,
        options: WebVisitOptions = WebVisitOptions(30, 10, 3, waitSeconds),
        read: suspend () -> String = { "page" },
        scroll: () -> Unit = {},
        inspect: suspend () -> WebVisitException? = { null },
        scope: CoroutineScope = this,
    ) = WebVisitSession(
        scope, options, read, scroll, { results.add(it) }, inspect, { testScheduler.currentTime },
    ).also { it.start() }

    private fun reason(result: Result<String>) = (result.exceptionOrNull() as WebVisitException).reason

    @Test
    fun noPageCallbackFailsAtTheConfiguredLoadDeadline() = runTest {
        val results = mutableListOf<Result<String>>()
        session(results)
        advanceTimeBy(9_999)
        runCurrent()
        assertTrue(results.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(WebVisitFailure.LOAD_TIMEOUT, reason(results.single()))
    }

    @Test
    fun repeatedRedirectsCannotRestartTheConfiguredBudget() = runTest {
        val results = mutableListOf<Result<String>>()
        val visit = session(results)
        repeat(3) {
            advanceTimeBy(3_000)
            visit.onPageStarted()
        }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(WebVisitFailure.LOAD_TIMEOUT, reason(results.single()))
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test
    fun missingJavascriptCallbackRespectsTheConfiguredReadDeadline() = runTest {
        val results = mutableListOf<Result<String>>()
        val visit = session(results, read = { awaitCancellation() })
        visit.onPageFinished()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(WebVisitFailure.READ_TIMEOUT, reason(results.single()))
        assertEquals(WebVisitPhase.DONE, visit.phase.value)
    }

    @Test
    fun waitingReadsFreshContentOnlyAfterScrollingAndTheConfiguredDelay() = runTest {
        val results = mutableListOf<Result<String>>()
        var body = "before"
        var scrollCount = 0
        val visit = session(results, waitSeconds = 2, read = { body }, scroll = { scrollCount++ })
        visit.onPageFinished()
        runCurrent()
        assertEquals(WebVisitPhase.WAITING, visit.phase.value)
        assertEquals(1, scrollCount)
        body = "after lazy content loaded"
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(body, results.single().getOrThrow())
    }

    @Test
    fun cancellationWinsAgainstLatePageCallbacksAndReadRequests() = runTest {
        val results = mutableListOf<Result<String>>()
        val visit = session(results, waitSeconds = 2)
        visit.onPageFinished()
        runCurrent()
        visit.fail(WebVisitFailure.CANCELLED)
        visit.onPageStarted()
        visit.onPageVisible()
        visit.onPageFinished()
        visit.readNow()
        visit.fail(WebVisitFailure.NETWORK)
        advanceUntilIdle()
        assertEquals(WebVisitFailure.CANCELLED, reason(results.single()))
        assertFalse(visit.stop())
    }

    @Test
    fun stoppingTheCallerCancelsAnInFlightReadWithoutPublishingSuccess() = runTest {
        val results = mutableListOf<Result<String>>()
        val caller = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        var readCancelled = false
        val visit = session(results, scope = caller, read = {
            try { awaitCancellation() } finally { readCancelled = true }
        })
        visit.onPageFinished()
        runCurrent()
        caller.cancel()
        runCurrent()
        visit.stop()
        assertTrue(readCancelled)
        assertTrue(results.isEmpty())
    }

    @Test
    fun readNowSkipsOnlyTheLazyWaitAndFinishesOnce() = runTest {
        val results = mutableListOf<Result<String>>()
        var reads = 0
        val visit = session(results, waitSeconds = 20, read = { reads++; "fresh" })
        visit.onPageFinished()
        runCurrent()
        visit.readNow()
        runCurrent()
        advanceUntilIdle()
        assertEquals("fresh", results.single().getOrThrow())
        assertEquals(1, reads)
    }

    @Test
    fun explicitFailuresAreTerminalWithoutWaitingForTheDeadline() = runTest {
        for (failure in listOf(WebVisitFailure.NETWORK, WebVisitFailure.HTTP, WebVisitFailure.CERTIFICATE,
            WebVisitFailure.RENDERER_GONE, WebVisitFailure.DISPLAY, WebVisitFailure.ACCESS_BLOCKED,
            WebVisitFailure.CLOUDFLARE_CHALLENGE, WebVisitFailure.CLOUDFLARE_BLOCKED)) {
            val results = mutableListOf<Result<String>>()
            val visit = session(results)
            visit.fail(failure)
            visit.onPageFinished()
            advanceUntilIdle()
            assertEquals(failure, reason(results.single()))
        }
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun extendedLoadingContinuesPastTheOldTenSecondLimit() = runTest {
        val results = mutableListOf<Result<String>>()
        session(results, options = WebVisitOptions(timeoutSeconds = 90))
        advanceTimeBy(89_999)
        runCurrent()
        assertTrue(results.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(WebVisitFailure.LOAD_TIMEOUT, reason(results.single()))
    }

    @Test
    fun lazyContentWaitCanExceedTheOldTenSecondLimit() = runTest {
        val results = mutableListOf<Result<String>>()
        val visit = session(results, waitSeconds = 20)
        visit.onPageFinished()
        advanceTimeBy(19_999)
        runCurrent()
        assertTrue(results.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals("page", results.single().getOrThrow())
    }

    @Test
    fun extendedReadsContinuePastTheOldThreeSecondLimit() = runTest {
        val results = mutableListOf<Result<String>>()
        val visit = session(results, options = WebVisitOptions(90, 90, 10), read = {
            delay(6_000)
            "slow content"
        })
        visit.onPageFinished()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals("slow content", results.single().getOrThrow())
    }

    @Test
    fun cloudflareAppearingDuringLoadingFailsBeforePageFinishedOrTimeout() = runTest {
        val results = mutableListOf<Result<String>>()
        var challenged = false
        val visit = session(results, inspect = {
            if (challenged) WebVisitException(WebVisitFailure.CLOUDFLARE_CHALLENGE) else null
        })
        visit.onPageVisible()
        advanceTimeBy(2_000)
        challenged = true
        runCurrent()
        assertEquals(WebVisitFailure.CLOUDFLARE_CHALLENGE, reason(results.single()))
        assertEquals(2_000L, testScheduler.currentTime)
        visit.onPageFinished()
        advanceUntilIdle()
        assertEquals(1, results.size)
    }

    @Test
    fun aPolicyBlockInterruptsTheLazyWaitImmediately() = runTest {
        val results = mutableListOf<Result<String>>()
        var blocked = false
        val visit = session(results, waitSeconds = 20, inspect = {
            if (blocked) WebVisitException(WebVisitFailure.ACCESS_BLOCKED) else null
        })
        visit.onPageFinished()
        advanceTimeBy(1_000)
        blocked = true
        runCurrent()
        assertEquals(WebVisitFailure.ACCESS_BLOCKED, reason(results.single()))
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun aChallengeOnACompletedHttp200PageIsNotExtractedAsSuccess() = runTest {
        val results = mutableListOf<Result<String>>()
        var reads = 0
        val visit = session(results, inspect = {
            WebVisitException(WebVisitFailure.CLOUDFLARE_CHALLENGE)
        }, read = { reads++; "challenge text" })
        visit.onPageFinished()
        runCurrent()
        assertEquals(WebVisitFailure.CLOUDFLARE_CHALLENGE, reason(results.single()))
        assertEquals(0, reads)
    }

    @Test
    fun callerCancellationStopsInspectionWithoutMisreportingAReadTimeout() = runTest {
        val results = mutableListOf<Result<String>>()
        val caller = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        var cancelled = false
        val visit = session(results, scope = caller, inspect = {
            try { awaitCancellation() } finally { cancelled = true }
        })
        visit.onPageVisible()
        runCurrent()
        caller.cancel()
        runCurrent()
        visit.stop()
        assertTrue(cancelled)
        assertTrue(results.isEmpty())
    }

    @Test
    fun duplicateFinishedCallbacksDoNotRestartTheWait() = runTest {
        val results = mutableListOf<Result<String>>()
        var scrolls = 0
        val visit = session(results, waitSeconds = 2, scroll = { scrolls++ })
        visit.onPageFinished()
        visit.onPageFinished()
        advanceUntilIdle()
        assertEquals(1, scrolls)
        assertEquals("page", results.single().getOrThrow())
        assertEquals(2_000L, testScheduler.currentTime)
    }
}