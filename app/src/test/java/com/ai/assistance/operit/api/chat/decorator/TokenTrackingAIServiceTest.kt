package com.ai.assistance.operit.api.chat.decorator

import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.TokenRequestLogEntity
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.data.stats.TokenRequestSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenTrackingAIServiceTest {
    private fun tracker() = TokenTrackingAIService.RequestTracker(
        "config", "OPENAI:model", 3L, TokenRequestSource(FunctionType.TRANSLATION, "worker-model"),
    )

    @Test
    fun successLinksOnlyFinalAttemptAndPreservesUnknownComponents() {
        val request = tracker()
        request.onUsage(ProviderUsageSnapshot(totalInputTokens = 900L, source = "api"), 1)
        request.onUsage(ProviderUsageSnapshot(totalInputTokens = 12L, outputTokens = 0L, source = "api"), 2)
        request.onSuccess(2)
        val result = requireNotNull(request.finish(null))
        val fact = requireNotNull(result.fact)
        assertEquals(TokenRequestLogEntity.SUCCESS, result.log.status)
        assertEquals(result.log.requestId, fact.requestId)
        assertEquals(result.log.startedAtMs, fact.occurredAtMs)
        assertEquals(12L, fact.totalInputTokens)
        assertNull(fact.uncachedInputTokens)
        assertNull(fact.cachedInputTokens)
        assertEquals(0L, fact.outputTokens)
        assertEquals("1,2", result.log.observedAttemptNumbers)
        assertEquals("TRANSLATION", result.log.functionType)
        assertEquals("worker-model", result.log.executionModel)
        assertNull(request.finish(null))
    }

    @Test
    fun failedRequestKeepsConfirmedUsageWithoutSuccessfulFact() {
        val request = tracker()
        request.onUsage(ProviderUsageSnapshot(totalInputTokens = 7L, source = "api"), 2)
        val result = requireNotNull(request.finish(TokenRequestLogEntity.FAILED))
        assertEquals(TokenRequestLogEntity.FAILED, result.log.status)
        assertEquals(7L, result.log.totalInputTokens)
        assertEquals(2, result.log.usageAttempt)
        assertEquals("2", result.log.observedAttemptNumbers)
        assertNull(result.fact)
    }

    @Test
    fun cancellationOverridesFinalizedSuccessWithoutErasingKnownUsage() {
        val request = tracker()
        request.onUsage(ProviderUsageSnapshot(outputTokens = 5L, source = "api"), 1)
        request.onSuccess(1)
        request.cancel()
        val result = requireNotNull(request.finish(null))
        assertEquals(TokenRequestLogEntity.CANCELLED, result.log.status)
        assertEquals(5L, result.log.outputTokens)
        assertNull(result.fact)
    }

    @Test
    fun normalStreamCompletionDoesNotInventSuccessOrTokens() {
        val result = requireNotNull(tracker().finish(null))
        assertEquals(TokenRequestLogEntity.COMPLETED, result.log.status)
        assertNull(result.log.totalInputTokens)
        assertNull(result.log.outputTokens)
        assertNull(result.log.observedAttemptNumbers)
        assertNull(result.fact)
    }

    @Test
    fun finalizedSuccessWithoutUsageIsStillNotAUsageFact() {
        val request = tracker()
        request.onSuccess(null)
        val result = requireNotNull(request.finish(null))
        assertEquals(TokenRequestLogEntity.SUCCESS, result.log.status)
        assertNull(result.fact)
    }

    @Test
    fun completeSnapshotRevokesPreviouslyKnownFields() {
        val request = tracker()
        request.onUsage(ProviderUsageSnapshot(outputTokens = 50L, source = "api"), 1)
        request.onUsage(ProviderUsageSnapshot(totalInputTokens = 8L, completeSnapshot = true, source = "api"), 1)
        request.onSuccess(1)
        val result = requireNotNull(request.finish(null))
        val fact = requireNotNull(result.fact)
        assertEquals(8L, fact.totalInputTokens)
        assertNull(result.log.outputTokens)
        assertNull(fact.outputTokens)
    }

    @Test
    fun firstTextLatencyRequiresActualNonemptyOutput() {
        val request = tracker()
        request.onText("")
        val empty = requireNotNull(request.finish(null))
        assertNull(empty.log.firstTokenLatencyMs)
        val next = tracker()
        next.onText("first")
        val result = requireNotNull(next.finish(null))
        assertNotNull(result.log.firstTokenLatencyMs)
        assertTrue(requireNotNull(result.log.firstTokenLatencyMs) <= result.log.durationMs)
    }
}
