package com.ai.assistance.operit.api.chat.decorator

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.config.model.ModelOption
import com.ai.assistance.operit.data.config.model.ModelParameter
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.mock
import com.ai.assistance.operit.api.chat.AIService

import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.stats.TokenRequestSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
class RateLimitedAIServiceSourceTest {
    @Test
    fun sendingSourceSurvivesDelayedCollectionAndSharedService() = runBlocking {
        val delegate = RecordingAIService()
        val service = RateLimitedAIService(delegate, rateLimiter = null, concurrencySemaphore = null)
        val context = mock<Context>()
        val translated = withContext(TokenRequestSource(FunctionType.TRANSLATION)) { service.sendMessage(context) }
        val delegated = withContext(TokenRequestSource(null, "worker-model")) { service.sendMessage(context) }
        // 反向收集同一缓存服务的请求，发送时的用途与执行模型仍应各自独立。
        val collectorSource = TokenRequestSource(FunctionType.CHAT)
        withContext(collectorSource) {
            delegated.collect { assertSame(collectorSource, currentCoroutineContext()[TokenRequestSource]) }
            translated.collect { assertSame(collectorSource, currentCoroutineContext()[TokenRequestSource]) }
        }
        assertEquals(2, delegate.sources.size)
        assertNull(delegate.sources[0]?.functionType)
        assertEquals("worker-model", delegate.sources[0]?.executionModel)
        assertEquals(FunctionType.TRANSLATION, delegate.sources[1]?.functionType)
        assertNull(delegate.sources[1]?.executionModel)
    }
    @Test
    fun missingSendingSourceDoesNotInheritCollectorSource() = runBlocking {
        val delegate = RecordingAIService()
        val service = RateLimitedAIService(delegate, rateLimiter = null, concurrencySemaphore = null)
        val response = service.sendMessage(mock<Context>())
        withContext(TokenRequestSource(FunctionType.SUMMARY)) { response.collect {} }
        assertEquals(1, delegate.sources.size)
        assertNull(delegate.sources.single()?.functionType)
        assertNull(delegate.sources.single()?.executionModel)
    }
    private class RecordingAIService : AIService {
        val sources = mutableListOf<TokenRequestSource?>()

        override val inputTokenCount: Long = 0L
        override val cachedInputTokenCount: Long = 0L
        override val outputTokenCount: Long = 0L
        override val providerModel: String = "OPENAI:test"

        override fun resetTokenCounts() = Unit

        override fun cancelStreaming() = Unit

        override suspend fun getModelsList(context: Context): Result<List<ModelOption>> =
            Result.success(emptyList())

        override suspend fun sendMessage(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            thinkingOptionId: String?,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
            onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
            onUsageReported: (suspend (ProviderUsageSnapshot, attempt: Int) -> Unit)?,
            onNonFatalError: suspend (error: String) -> Unit,
            enableRetry: Boolean,
            recordTokenUsage: Boolean,
            onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
        ): Stream<String> {
            sources.add(currentCoroutineContext()[TokenRequestSource])
            return com.ai.assistance.operit.util.stream.stream { emit("answer") }
        }

        override suspend fun testConnection(context: Context): Result<String> = Result.success("ok")

        override suspend fun calculateInputTokens(
            chatHistory: List<PromptTurn>,
            availableTools: List<ToolPrompt>?,
        ): Long = 0L
    }
}
