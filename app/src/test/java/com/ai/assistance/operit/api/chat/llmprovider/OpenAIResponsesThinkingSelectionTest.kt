package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.api.chat.keypool.SingleApiKeyProvider
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.util.logging.AppLogger
import okhttp3.OkHttpClient
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class OpenAIResponsesThinkingSelectionTest {
    @Test
    fun aRequestCanSelectTheLowestLevelWithoutChangingTheSavedHighLevel() {
        val systemLog = AppLogger.enableSystemLog
        val fileLog = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
        try {
            val provider = ExposedProvider()
            assertEquals("low", provider.json("low").getJSONObject("reasoning").getString("effort"))
            assertEquals("high", provider.json(null).getJSONObject("reasoning").getString("effort"))
        } finally {
            AppLogger.enableSystemLog = systemLog
            AppLogger.enableFileLogging = fileLog
        }
    }

    private class ExposedProvider : OpenAIResponsesProvider(
        responsesApiEndpoint = "https://example.test/v1/responses",
        apiKeyProvider = SingleApiKeyProvider("test-key"),
        modelName = "gpt-5",
        client = OkHttpClient(),
        thinkingConfigurations = ModelThinkingConfigDefaults.forProvider("OPENAI_RESPONSES"),
        thinkingOptionId = "high",
    ) {
        fun json(optionId: String?): JSONObject {
            val body = createRequestBody(
                context = mock<Context>(),
                chatHistory = listOf(PromptTurn(kind = PromptTurnKind.USER, content = "Hi")),
                modelParameters = emptyList(),
                enableThinking = true,
                thinkingOptionId = optionId,
                stream = false,
                availableTools = null,
                preserveThinkInHistory = false,
            )
            val buffer = Buffer()
            body.writeTo(buffer)
            return JSONObject(buffer.readUtf8())
        }
    }
}
