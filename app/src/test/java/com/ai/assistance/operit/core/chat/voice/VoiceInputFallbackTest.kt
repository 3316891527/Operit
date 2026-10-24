package com.ai.assistance.operit.core.chat.voice

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoiceInputFallbackTest {
    private suspend fun failure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            return error
        }
        throw AssertionError("预期抛出异常")
    }

    @Test
    fun configuredAudioModelWinsWithoutStt() = runBlocking {
        val calls = mutableListOf<String>()
        val result = resolveVoiceText(
            hasAudioModel = { true },
            audioModel = { calls.add("audio"); "  带有轻声语气的请求  " },
            stt = { calls.add("stt"); "转写" },
        )
        assertEquals(VoiceTextResult("带有轻声语气的请求", VoiceTextSource.AUDIO_MODEL), result)
        assertEquals(listOf("audio"), calls)
    }

    @Test
    fun unconfiguredAudioModelUsesStt() = runBlocking {
        val result = resolveVoiceText(
            hasAudioModel = { false },
            audioModel = { throw AssertionError("不应调用音频理解模型") },
            stt = { "  普通话转写  " },
        )
        assertEquals(VoiceTextResult("普通话转写", VoiceTextSource.STT), result)
    }

    @Test
    fun failedAudioModelUsesSttAndReportsItsError() = runBlocking {
        val audioError = IOException("音频理解不可用")
        var observed: Exception? = null
        val result = resolveVoiceText(
            hasAudioModel = { true },
            audioModel = { throw audioError },
            stt = { "转写成功" },
            onAudioModelFailure = { observed = it },
        )
        assertEquals(VoiceTextSource.STT, result.source)
        assertSame(audioError, observed)
    }

    @Test
    fun emptyAudioUnderstandingUsesStt() = runBlocking {
        val result = resolveVoiceText({ true }, { " \n " }, { "有效转写" })
        assertEquals(VoiceTextSource.STT, result.source)
        assertEquals("有效转写", result.text)
    }

    @Test
    fun unreadableAudioConfigurationUsesStt() = runBlocking {
        val result = resolveVoiceText(
            { throw IOException("配置读取失败") },
            { throw AssertionError("不应调用无效模型") },
            { "有效转写" },
        )
        assertEquals(VoiceTextSource.STT, result.source)
    }

    @Test
    fun audioModelCancellationDoesNotStartStt() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure {
            resolveVoiceText({ true }, { throw cancelled }, { throw AssertionError("取消后不应转写") })
        }
        assertSame(cancelled, error)
    }

    @Test
    fun configurationCancellationDoesNotStartStt() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure {
            resolveVoiceText({ throw cancelled }, { "音频" }, { throw AssertionError("取消后不应转写") })
        }
        assertSame(cancelled, error)
    }

    @Test
    fun sttCancellationIsPropagated() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure { resolveVoiceText({ false }, { "音频" }, { throw cancelled }) }
        assertSame(cancelled, error)
    }

    @Test
    fun bothFailuresKeepAudioErrorAsEvidence() = runBlocking {
        val audioError = IOException("音频错误")
        val sttError = IOException("识别错误")
        val error = failure { resolveVoiceText({ true }, { throw audioError }, { throw sttError }) }
        assertSame(sttError, error)
        assertEquals(listOf(audioError), error.suppressed.toList())
    }

    @Test
    fun emptySttIsAnError() = runBlocking {
        val error = failure { resolveVoiceText({ false }, { "音频" }, { "  " }) }
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun directAudioSuccessNeverStartsTextPath() = runBlocking {
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { emit -> emit("回答"); emit("完成") },
            text = { throw AssertionError("直接音频成功后不应切换") },
            emit = { output.add(it) },
        )
        assertEquals(listOf("回答", "完成"), output)
    }

    @Test
    fun directAudioFailureBeforeContentStartsTextPath() = runBlocking {
        val calls = mutableListOf<String>()
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { calls.add("native"); throw IOException("不支持音频") },
            text = { emit -> calls.add("text"); emit("文字回答") },
            emit = { output.add(it) },
        )
        assertEquals(listOf("native", "text"), calls)
        assertEquals(listOf("文字回答"), output)
    }

    @Test
    fun emptyDirectAudioCompletionStartsTextPath() = runBlocking {
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { emit -> emit("") },
            text = { emit -> emit("文字回答") },
            emit = { if (it.isNotEmpty()) output.add(it) },
        )
        assertEquals(listOf("文字回答"), output)
    }

    @Test
    fun partialAnswerOrToolContentIsNeverReplayed() = runBlocking {
        for (content in listOf("已经开始回答", "<tool name=\"terminal\">")) {
            val connectionError = IOException("响应中断")
            val output = mutableListOf<String>()
            val error = failure {
                collectVoiceResponse(
                    native = { emit -> emit(content); throw connectionError },
                    text = { throw AssertionError("已有内容时不能重放") },
                    emit = { output.add(it) },
                )
            }
            assertSame(connectionError, error)
            assertEquals(listOf(content), output)
        }
    }

    @Test
    fun directAudioCancellationDoesNotStartTextPath() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure {
            collectVoiceResponse(
                native = { throw cancelled },
                text = { throw AssertionError("取消后不能重试") },
                emit = {},
            )
        }
        assertSame(cancelled, error)
    }

    @Test
    fun emptyTextResponseFailsWithOriginalDirectAudioError() = runBlocking {
        val audioError = IOException("直接音频不可用")
        val error = failure {
            collectVoiceResponse(native = { throw audioError }, text = {}, emit = {})
        }
        assertTrue(error is IllegalStateException)
        assertEquals(listOf(audioError), error.suppressed.toList())
    }

    @Test
    fun textResponseCancellationIsNotConvertedToRequestFailure() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure {
            collectVoiceResponse(
                native = { throw IOException("直接音频不可用") },
                text = { throw cancelled },
                emit = {},
            )
        }
        assertSame(cancelled, error)
        assertTrue(error.suppressed.isEmpty())
    }

    @Test
    fun threePathsFollowUserSpecifiedOrder() = runBlocking {
        val calls = mutableListOf<String>()
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { calls.add("chat-audio"); throw IOException("直接音频失败") },
            text = { emit ->
                val resolved = resolveVoiceText(
                    hasAudioModel = { true },
                    audioModel = { calls.add("audio-model"); throw IOException("音频理解失败") },
                    stt = { calls.add("stt"); "转写内容" },
                )
                calls.add("chat-text")
                emit(resolved.text)
            },
            emit = { output.add(it) },
        )
        assertEquals(listOf("chat-audio", "audio-model", "stt", "chat-text"), calls)
        assertEquals(listOf("转写内容"), output)
    }
}
