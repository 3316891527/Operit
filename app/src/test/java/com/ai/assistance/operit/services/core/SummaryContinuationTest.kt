package com.ai.assistance.operit.services.core

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryContinuationTest {
    @Test
    fun loadingFlagClearedBeforeStatisticsDoesNotStartANewRequestEarly() = runTest {
        val persistStatistics = CompletableDeferred<Unit>()
        var loading = true
        val events = mutableListOf<String>()
        val previous = launch {
            loading = false
            events += "response-saved"
            persistStatistics.await()
            events += "statistics-saved"
        }
        val continuation = async {
            continueAfterSummary(previous, { true }, { loading }, { events += "request" })
        }
        runCurrent()
        assertEquals(listOf("response-saved"), events)
        assertFalse(continuation.isCompleted)
        persistStatistics.complete(Unit)
        runCurrent()
        assertEquals(SummaryContinuationResult.DISPATCHED, continuation.await())
        assertEquals(listOf("response-saved", "statistics-saved", "request"), events)
    }

    @Test
    fun handledFailureWithoutASuccessCounterStillSettlesTheOwnedTask() = runTest {
        val release = CompletableDeferred<Unit>()
        var loading = true
        var requests = 0
        val previous = launch {
            release.await()
            try {
                throw IOException("上游失败已经由发送边界处理")
            } catch (_: IOException) {
                loading = false
            }
        }
        val continuation = async {
            continueAfterSummary(previous, { true }, { loading }, { requests++ })
        }
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertEquals(SummaryContinuationResult.DISPATCHED, continuation.await())
        assertEquals(1, requests)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun taskCompletedBeforeSubscriptionDoesNotLoseItsCompletionSignal() = runTest {
        val previous = launch { }
        previous.join()
        var requests = 0
        assertEquals(
            SummaryContinuationResult.DISPATCHED,
            continueAfterSummary(previous, { true }, { false }, { requests++ }),
        )
        assertEquals(1, requests)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun cancelledOldTurnWaitsForCleanupAndNeverRestartsTheRequest() = runTest {
        var cleaned = false
        val previous = launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(500); cleaned = true }
            }
        }
        runCurrent()
        val continuation = async {
            continueAfterSummary(previous, { true }, { !cleaned }, { fail("停止后不能自动重启") })
        }
        previous.cancel()
        runCurrent()
        advanceTimeBy(499)
        runCurrent()
        assertFalse(continuation.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SummaryContinuationResult.CANCELLED, continuation.await())
        assertTrue(cleaned)
    }

    @Test
    fun replacingTheQueuedContinuationDoesNotSendTheStaleRequest() = runTest {
        val release = CompletableDeferred<Unit>()
        val previous = launch { release.await() }
        var current = true
        val continuation = async {
            continueAfterSummary(previous, { current }, { false }, { fail("旧队列不得派发") })
        }
        runCurrent()
        current = false
        release.complete(Unit)
        runCurrent()
        assertEquals(SummaryContinuationResult.SUPERSEDED, continuation.await())
    }

    @Test
    fun cancellingTheWaitDoesNotCancelThePreviousSendOrDispatchAnother() = runTest {
        val previous = launch { awaitCancellation() }
        val continuation = launch {
            continueAfterSummary(previous, { true }, { true }, { fail("已取消等待不得派发") })
        }
        runCurrent()
        continuation.cancelAndJoin()
        assertTrue(previous.isActive)
        previous.cancelAndJoin()
    }

    @Test
    fun timeoutReportsAnUnfinishedTurnAndDoesNotForceAConcurrentSend() = runTest {
        val previous = launch { awaitCancellation() }
        val continuation = async {
            continueAfterSummary(previous, { true }, { true }, { fail("超时不得绕过旧任务") }, timeoutMs = 1000)
        }
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(SummaryContinuationResult.TIMED_OUT, continuation.await())
        assertTrue(previous.isActive)
        previous.cancelAndJoin()
    }

    @Test
    fun idleChatWithNoPreviousTaskContinuesImmediately() = runTest {
        var requests = 0
        assertEquals(
            SummaryContinuationResult.DISPATCHED,
            continueAfterSummary(null, { true }, { false }, { requests++ }),
        )
        assertEquals(1, requests)
    }

    @Test
    fun anotherBusyTurnCannotBeOverwrittenByAStaleSummary() = runTest {
        val previous = launch { }
        previous.join()
        try {
            continueAfterSummary(previous, { true }, { true }, { fail("新回合仍忙，不能并发发送") })
            fail("必须检测会话被占用")
        } catch (_: IllegalStateException) { }
    }
}
