package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming

import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolExecutionTiming
import com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownRenderMode
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.waifuElapsedLabel
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveToolTimingAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    private fun verifyStaticBodyKeepsTiming(waifu: Boolean) {
        val body = "<tool name=\"read_file\"><param name=\"path\">same-file</param></tool>"
        val state = ToolTimingState()
        val message = ChatMessage(sender = "ai", content = body, toolTimingState = state, toolTimingStartIndex = 1)
        val node = MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, body, emptyList())
        val observed = AtomicReference<Pair<Long?, String>?>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalMarkdownRenderMode provides MarkdownRenderMode.STATIC) {
                val records = rememberMessageToolExecutions(message)
                val summary = checkNotNull(ToolTimingIndex.parse(listOf(node), records, live = true).tool(0))
                val millis = rememberToolElapsedMillis(summary)
                val label = if (waifu) waifuElapsedLabel(summary) else toolCallTimingLabel(summary)
                SideEffect { observed.set(millis to label) }
                Text(label)
            }
        }
        composeRule.waitForIdle()
        assertNull(observed.get()?.first)
        val start = SystemClock.elapsedRealtime()
        composeRule.runOnIdle {
            // 前一分段的同名调用不能占用当前卡片，正文和消息对象在整个测试中保持不变。
            state.update(ToolCallExecution(0, ToolExecutionTiming.clockId, start - 20_000L, 10_000L, start - 10_000L))
            state.update(ToolCallExecution(1, ToolExecutionTiming.clockId, startedMs = start))
        }
        composeRule.waitUntil(10_000L) {
            composeRule.mainClock.advanceTimeBy(100L)
            val value = observed.get()?.first
            value != null && value in 0L..5_000L
        }
        val first = checkNotNull(observed.get()?.first)
        composeRule.waitUntil(10_000L) {
            composeRule.mainClock.advanceTimeBy(100L)
            (observed.get()?.first ?: 0L) >= first + 1_000L
        }
        assertTrue(observed.get()?.second?.isNotBlank() == true)
        composeRule.runOnIdle {
            state.update(ToolCallExecution(1, ToolExecutionTiming.clockId, start, 3_210L, start + 3_210L))
        }
        composeRule.waitUntil(10_000L) {
            composeRule.mainClock.advanceTimeBy(100L)
            observed.get()?.first == 3_210L
        }
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.waitForIdle()
        assertEquals(3_210L, observed.get()?.first)
        assertEquals(body, message.content)
        assertNull(message.contentStream)
        assertFalse(checkNotNull(ToolTimingIndex.parse(
            listOf(node), message.localToolTimings(state.records.value), live = true,
        ).tool(0)).ticking())
    }

    @Test
    fun ordinaryTitleAdvancesWithStaticMarkdownAndNoNewText() {
        verifyStaticBodyKeepsTiming(waifu = false)
    }

    @Test
    fun waifuTitleAdvancesWithStaticMarkdownAndNoNewText() {
        verifyStaticBodyKeepsTiming(waifu = true)
    }
}
