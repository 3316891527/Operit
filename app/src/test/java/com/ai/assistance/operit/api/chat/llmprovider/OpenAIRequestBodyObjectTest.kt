package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.AppLogger
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class OpenAIRequestBodyObjectTest {
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
    fun `request object keeps complete text and the existing chat payload`() {
        val content = "完整用户上下文原文\n".repeat(20_000).trimEnd()
        val history = listOf(
            PromptTurn(kind = PromptTurnKind.SYSTEM, content = "系统提示原文"),
            PromptTurn(kind = PromptTurnKind.USER, content = content),
        )
        val provider = object : OpenAIProvider(
            apiEndpoint = "https://example.test/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "test-model",
            client = OkHttpClient(),
        ) {
            fun build(context: Context): JSONObject = createRequestBodyObject(context, history, stream = false)
        }
        val body = provider.build(mock<Context>())
        val expected = JSONObject().put("model", "test-model").put("stream", false)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "系统提示原文"))
                .put(JSONObject().put("role", "user").put("content", content)))

        assertTrue(expected.similar(body))
        assertEquals(content, body.getJSONArray("messages").getJSONObject(1).getString("content"))
    }
}
