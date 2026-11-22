package com.ai.assistance.operit.services.core

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingOptionUpdateTest {
    @Test
    fun unsupportedLevelIsReportedWithoutCancellingTheParentOrRefreshing() = runBlocking {
        val failure = IllegalArgumentException("Thinking option is not supported by the configured models: low")
        var reported: Exception? = null
        var refreshes = 0
        launchThinkingOptionUpdate(
            configId = "no-thinking-model",
            optionId = "low",
            save = { _, _ -> throw failure },
            onSaved = { refreshes++ },
            onFailure = { reported = it },
        ).join()
        assertSame(failure, reported)
        assertEquals(0, refreshes)
        assertTrue(coroutineContext[Job]!!.isActive)
    }

    @Test
    fun successfulSaveRefreshesOnlyAfterPersistingTheExactSelection() = runBlocking {
        val events = mutableListOf<String>()
        launchThinkingOptionUpdate(
            configId = "selected-config",
            optionId = "high",
            save = { configId, optionId -> events += "$configId:$optionId" },
            onSaved = { events += "refresh" },
            onFailure = { throw AssertionError("Unexpected update error", it) },
        ).join()
        assertEquals(listOf("selected-config:high", "refresh"), events)
    }

    @Test
    fun switchingConfigurationBeforeTheQueuedSaveDoesNotChangeItsTarget() = runBlocking {
        val target = MutableStateFlow("clicked-config")
        var saved: Pair<String, String>? = null
        val job = launchThinkingOptionUpdate(
            configId = target.value,
            optionId = "low",
            save = { configId, optionId -> saved = configId to optionId },
            onSaved = {},
            onFailure = { throw AssertionError("Unexpected update error", it) },
        )
        target.value = "new-config"
        job.join()
        assertEquals("clicked-config" to "low", saved)
        assertEquals("new-config", target.value)
    }

    @Test
    fun persistenceFailureIsReportedWithoutClaimingARefresh() = runBlocking {
        val failure = IOException("Preferences cannot be written")
        var reported: Exception? = null
        var refreshes = 0
        launchThinkingOptionUpdate(
            configId = "selected-config",
            optionId = "low",
            save = { _, _ -> throw failure },
            onSaved = { refreshes++ },
            onFailure = { reported = it },
        ).join()
        assertSame(failure, reported)
        assertEquals(0, refreshes)
    }

    @Test
    fun serviceRefreshFailureIsReportedAfterTheSuccessfulSave() = runBlocking {
        val failure = IOException("Service refresh failed")
        val events = mutableListOf<String>()
        var reported: Exception? = null
        launchThinkingOptionUpdate(
            configId = "selected-config",
            optionId = "high",
            save = { _, _ -> events += "save" },
            onSaved = { events += "refresh"; throw failure },
            onFailure = { reported = it },
        ).join()
        assertSame(failure, reported)
        assertEquals(listOf("save", "refresh"), events)
    }

    @Test
    fun cancellationDuringSaveDoesNotShowAnErrorOrRefresh() = runBlocking {
        var errors = 0
        var refreshes = 0
        val job = launchThinkingOptionUpdate(
            configId = "selected-config",
            optionId = "low",
            save = { _, _ -> throw CancellationException("Screen closed") },
            onSaved = { refreshes++ },
            onFailure = { errors++ },
        )
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, errors)
        assertEquals(0, refreshes)
    }

    @Test
    fun cancellationDuringRefreshDoesNotShowAnError() = runBlocking {
        var errors = 0
        val job = launchThinkingOptionUpdate(
            configId = "selected-config",
            optionId = "high",
            save = { _, _ -> },
            onSaved = { throw CancellationException("Screen closed") },
            onFailure = { errors++ },
        )
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, errors)
    }
}