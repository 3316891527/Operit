package com.ai.assistance.operit.core.chat.context

import com.ai.assistance.operit.core.chat.voice.collectVoiceResponse
import com.ai.assistance.operit.data.stats.ProviderUsageNormalizer
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatContextInputReportsTest {
    @Test
    fun usageAndFinalizationStayPrivateUntilSamplingCompletes() = runBlocking {
        val reports = ChatContextInputReports()
        reports.publish(100L)
        val request = reports.newRequest()
        val call = request.startProviderCall()
        assertEquals(100L, reports.inputTokens.value)
        call.onUsageReported(usage(240L), 1)
        assertEquals(100L, reports.inputTokens.value)
        call.onUsageFinalized(1)
        assertEquals(100L, reports.inputTokens.value)
        request.finish(interrupted = false) { true }
        assertEquals(240L, reports.inputTokens.value)
    }

    @Test
    fun retriesSelectOnlyTheSuccessfulAttempt() = runBlocking {
        val reports = ChatContextInputReports()
        val request = reports.newRequest()
        val call = request.startProviderCall()
        call.onUsageReported(usage(941L), 1)
        call.onUsageReported(usage(183L), 2)
        call.onUsageFinalized(2)
        request.finish(interrupted = false) { true }
        assertEquals(183L, reports.inputTokens.value)
    }

    @Test
    fun successfulAttemptWithoutInputDoesNotReuseAFailedAttemptOrAnOlderRequest() = runBlocking {
        val reports = ChatContextInputReports()
        reports.publish(100L)
        val request = reports.newRequest()
        val call = request.startProviderCall()
        call.onUsageReported(usage(941L), 1)
        call.onUsageFinalized(2)
        request.finish(interrupted = false) { true }
        assertNull(reports.inputTokens.value)
    }

    @Test
    fun unknownSuccessfulAttemptIsNotInferredFromTheLastReport() = runBlocking {
        val reports = ChatContextInputReports()
        reports.publish(100L)
        val request = reports.newRequest()
        val call = request.startProviderCall()
        call.onUsageReported(usage(200L), 1)
        call.onUsageFinalized(null)
        request.finish(interrupted = false) { true }
        assertNull(reports.inputTokens.value)
    }

    @Test
    fun unconfirmedCompletionDoesNotReplacePreviousSuccessfulInput() = runBlocking {
        val reports = ChatContextInputReports()
        reports.publish(100L)
        val request = reports.newRequest()
        request.startProviderCall().onUsageReported(usage(200L), 1)
        request.finish(interrupted = false) { true }
        assertEquals(100L, reports.inputTokens.value)
    }

    @Test
    fun cancelledAndSteeredSamplesDoNotPublishEvenAfterProviderFinalization() = runBlocking {
        val reports = ChatContextInputReports()
        reports.publish(100L)
        val stopped = reports.newRequest()
        stopped.startProviderCall().complete(200L)
        stopped.finish(interrupted = false) { false }
        assertEquals(100L, reports.inputTokens.value)

        val steered = reports.newRequest()
        steered.startProviderCall().complete(300L)
        steered.finish(interrupted = true) { true }
        assertEquals(100L, reports.inputTokens.value)
    }

    @Test
    fun emptyAudioCompletionCannotContributeToTheTextResend() = runBlocking {
        val reports = ChatContextInputReports()
        reports.publish(100L)
        for (textInput in listOf(333L, null)) {
            val previousInput = reports.inputTokens.value
            val request = reports.newRequest()
            val output = mutableListOf<String>()
            collectVoiceResponse(
                native = { _, _ -> request.startProviderCall().complete(222L) },
                text = { emit, _ ->
                    assertEquals(previousInput, reports.inputTokens.value)
                    val call = request.startProviderCall()
                    if (textInput != null) call.onUsageReported(usage(textInput), 1)
                    emit("文字回复")
                    call.onUsageFinalized(1)
                },
                emit = { output.add(it); Unit },
            )
            assertEquals(listOf("文字回复"), output)
            assertEquals(previousInput, reports.inputTokens.value)
            request.finish(interrupted = false) { true }
            assertEquals(textInput, reports.inputTokens.value)
        }
    }

    @Test
    fun partialUpdatesPreserveInputAndExplicitZeroIsKnown() = runBlocking {
        val reports = ChatContextInputReports()
        reports.publish(100L)
        val request = reports.newRequest()
        val call = request.startProviderCall()
        val gemini = ProviderUsageNormalizer.SOURCE_GEMINI
        call.onUsageReported(usage(200L, source = gemini, complete = false), 1)
        call.onUsageReported(usage(0L, source = gemini, complete = false), 1)
        call.onUsageReported(usage(null, source = gemini, complete = false), 1)
        call.onUsageFinalized(1)
        request.finish(interrupted = false) { true }
        assertEquals(0L, reports.inputTokens.value)
    }

    @Test
    fun completeUsageReplacementCanWithdrawAFormerInputValue() = runBlocking {
        val reports = ChatContextInputReports()
        reports.publish(100L)
        val request = reports.newRequest()
        val call = request.startProviderCall()
        call.onUsageReported(usage(200L, complete = false), 1)
        call.onUsageReported(usage(null), 1)
        call.onUsageFinalized(1)
        request.finish(interrupted = false) { true }
        assertNull(reports.inputTokens.value)
    }

    @Test
    fun claudeRecomputesTotalWhenOnlyTheUncachedInputChanges() = runBlocking {
        val reports = ChatContextInputReports()
        val request = reports.newRequest()
        val call = request.startProviderCall()
        call.onUsageReported(anthropic("input_tokens" to 40L, "cache_read_input_tokens" to 350L, "cache_creation_input_tokens" to 150L), 1)
        call.onUsageReported(anthropic("input_tokens" to 80L, "output_tokens" to 12L), 1)
        call.onUsageFinalized(1)
        request.finish(interrupted = false) { true }
        assertEquals(580L, reports.inputTokens.value)
    }

    @Test
    fun claudeKeepsPartlyReportedCacheUnknownUntilAllRequiredComponentsArrive() = runBlocking {
        val reports = ChatContextInputReports()
        for (cacheWrite in listOf(null, 150L)) {
            reports.publish(100L)
            val request = reports.newRequest()
            val call = request.startProviderCall()
            call.onUsageReported(anthropic("input_tokens" to 40L), 1)
            call.onUsageReported(anthropic("cache_read_input_tokens" to 350L), 1)
            if (cacheWrite != null) call.onUsageReported(anthropic("cache_creation_input_tokens" to cacheWrite), 1)
            call.onUsageFinalized(1)
            request.finish(interrupted = false) { true }
            assertEquals(cacheWrite?.let { 390L + it }, reports.inputTokens.value)
        }
    }

    @Test
    fun onlyBuiltInRemoteUsageSourcesSupplyTheReportedInputRow() = runBlocking {
        val reports = ChatContextInputReports()
        for (source in listOf(
            ProviderUsageNormalizer.SOURCE_OPENAI_CHAT_COMPLETIONS,
            ProviderUsageNormalizer.SOURCE_OPENAI_RESPONSES,
            ProviderUsageNormalizer.SOURCE_ANTHROPIC,
            ProviderUsageNormalizer.SOURCE_GEMINI,
        )) {
            val request = reports.newRequest()
            request.startProviderCall().complete(200L, source)
            request.finish(interrupted = false) { true }
            assertEquals(200L, reports.inputTokens.value)
        }
        for (source in listOf(
            ProviderUsageNormalizer.SOURCE_MNN,
            ProviderUsageNormalizer.SOURCE_LLAMA,
            ProviderUsageNormalizer.SOURCE_TOOLPKG,
            "unknown_protocol",
        )) {
            reports.publish(100L)
            val request = reports.newRequest()
            request.startProviderCall().complete(200L, source)
            request.finish(interrupted = false) { true }
            assertNull(reports.inputTokens.value)
        }
    }

    @Test
    fun aLateOlderRequestCannotReplaceTheNewerCompletionEvenWhenItHasNoInput() = runBlocking {
        val reports = ChatContextInputReports()
        for (newInput in listOf(240L, null)) {
            val oldRequest = reports.newRequest()
            val oldCall = oldRequest.startProviderCall()
            val newRequest = reports.newRequest()
            newRequest.startProviderCall().complete(newInput)
            newRequest.finish(interrupted = false) { true }
            oldCall.complete(150L)
            oldRequest.finish(interrupted = false) { true }
            assertEquals(newInput, reports.inputTokens.value)
        }
    }

    @Test
    fun clearingAConversationRejectsOldRequestsAndTheirLaterToolHops() = runBlocking {
        val first = ChatContextInputReports()
        val second = ChatContextInputReports()
        first.publish(100L)
        second.publish(200L)
        val turn = first.startTurn()
        val inFlight = checkNotNull(turn.startRequest())
        val call = inFlight.startProviderCall()
        first.clear()
        call.complete(300L)
        inFlight.finish(interrupted = false) { true }
        assertNull(first.inputTokens.value)
        assertNull(turn.startRequest())
        assertEquals(200L, second.inputTokens.value)

        first.publish(400L)
        assertEquals(400L, first.inputTokens.value)
        assertEquals(200L, second.inputTokens.value)
    }

    private fun ChatContextInputReports.newRequest(): ChatContextInputReports.Request =
        checkNotNull(startTurn().startRequest())

    private suspend fun ChatContextInputReports.publish(tokens: Long) {
        val request = newRequest()
        request.startProviderCall().complete(tokens)
        request.finish(interrupted = false) { true }
    }

    private suspend fun ChatContextInputReports.ProviderCall.complete(
        input: Long?,
        source: String = ProviderUsageNormalizer.SOURCE_OPENAI_CHAT_COMPLETIONS,
    ) {
        onUsageReported(usage(input, source), 1)
        onUsageFinalized(1)
    }

    private fun usage(
        input: Long?,
        source: String = ProviderUsageNormalizer.SOURCE_OPENAI_CHAT_COMPLETIONS,
        complete: Boolean = true,
    ) = ProviderUsageSnapshot(totalInputTokens = input, completeSnapshot = complete, source = source)

    private fun anthropic(vararg fields: Pair<String, Long>): ProviderUsageSnapshot =
        checkNotNull(ProviderUsageNormalizer.anthropic(JSONObject().apply {
            fields.forEach { (key, value) -> put(key, value) }
        }))
}
