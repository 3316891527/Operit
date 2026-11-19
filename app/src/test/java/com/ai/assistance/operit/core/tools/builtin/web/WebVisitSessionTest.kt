package com.ai.assistance.operit.core.tools.builtin.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
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
        waitSeconds: Int = 0,
        read: suspend () -> String = { "page" },
        scroll: () -> Unit = {},
        scope: CoroutineScope = this,
    ) = WebVisitSession(scope, waitSeconds, read, scroll, { results.add(it) }, { testScheduler.currentTime })
        .also { it.start() }

    private fun reason(result: Result<String>) = (result.exceptionOrNull() as WebVisitException).reason

    @Test
    fun noPageCallbackFailsAtTheLoadDeadline() = runTest {
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
    fun repeatedRedirectsCannotRestartTheTenSecondBudget() = runTest {
        val results = mutableListOf<Result<String>>()
        val visit = session(results)
        repeat(3) {
            advanceTimeBy(3_000)
            visit.onPageStarted()
            visit.onPageFinished()
            advanceTimeBy(400)
            visit.onPageStarted()
        }
        advanceUntilIdle()
        assertEquals(WebVisitFailure.LOAD_TIMEOUT, reason(results.single()))
        assertEquals(10_200L, testScheduler.currentTime)
    }

    @Test
    fun missingJavascriptCallbackFailsAfterThreeSeconds() = runTest {
        val results = mutableListOf<Result<String>>()
        val visit = session(results, read = { awaitCancellation() })
        visit.onPageFinished()
        advanceTimeBy(WebVisitSession.SETTLE_MS + WebVisitSession.READ_TIMEOUT_MS)
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
        advanceTimeBy(WebVisitSession.SETTLE_MS)
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
        advanceTimeBy(WebVisitSession.SETTLE_MS)
        runCurrent()
        visit.fail(WebVisitFailure.CANCELLED)
        visit.onPageStarted()
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
        val caller = CoroutineScope(coroutineContext + Job())
        var readCancelled = false
        val visit = session(results, scope = caller, read = {
            try { awaitCancellation() } finally { readCancelled = true }
        })
        visit.onPageFinished()
        advanceTimeBy(WebVisitSession.SETTLE_MS)
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
        val visit = session(results, waitSeconds = 10, read = { reads++; "fresh" })
        visit.onPageFinished()
        advanceTimeBy(WebVisitSession.SETTLE_MS)
        runCurrent()
        visit.readNow()
        runCurrent()
        advanceUntilIdle()
        assertEquals("fresh", results.single().getOrThrow())
        assertEquals(1, reads)
    }

    @Test
    fun networkHttpCertificateRendererAndDisplayErrorsAreTerminal() = runTest {
        for (failure in listOf(WebVisitFailure.NETWORK, WebVisitFailure.HTTP, WebVisitFailure.CERTIFICATE,
            WebVisitFailure.RENDERER_GONE, WebVisitFailure.DISPLAY)) {
            val results = mutableListOf<Result<String>>()
            val visit = session(results)
            visit.fail(failure)
            visit.onPageFinished()
            advanceUntilIdle()
            assertEquals(failure, reason(results.single()))
        }
    }
}