package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolExecutionTiming
import com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownRenderMode
import com.ai.assistance.operit.ui.features.conversation.chat.waifu.WaifuProcessHeader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveToolTimingAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun ordinaryProcessAccumulatesBatchesAndTicksWithoutNewMarkdown() {
        verifyProcessTiming(waifu = false)
    }

    @Test
    fun waifuProcessAccumulatesBatchesAndTicksWithoutNewMarkdown() {
        verifyProcessTiming(waifu = true)
    }

    private fun verifyProcessTiming(waifu: Boolean) {
        val state = ToolTimingState()
        val message = ChatMessage(sender = "ai", toolTimingState = state, toolTimingStartIndex = 1)
        val firstStart = SystemClock.elapsedRealtime() - 20_000L
        // 上一分段的同名记录不能污染本段汇总。
        state.update(ToolCallExecution(0, ToolExecutionTiming.clockId, firstStart - 10_000L, 7_000L))
        state.update(ToolCallExecution(1, ToolExecutionTiming.clockId, firstStart, 420L, firstStart + 420L))
        val renderNodes = mutableStateOf(listOf(toolTimingCall("same-file"), toolTimingResult(0)))
        val streaming = mutableStateOf(true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val running = context.getString(R.string.tool_timing_running)
        val completed = context.getString(R.string.tool_timing_completed)
        fun summary(status: String, count: Int) = context.getString(R.string.tool_timing_simple, status, count)
        fun titleMatcher(title: String, substring: Boolean = false): SemanticsMatcher =
            hasClickAction() and if (waifu) hasText(title, substring = substring)
            else hasContentDescription(title, substring = substring)
        fun assertTitle(title: String) {
            composeRule.onNode(titleMatcher(title)).assertHasClickAction().assertIsDisplayed()
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                val mode = if (streaming.value) MarkdownRenderMode.STREAMING else MarkdownRenderMode.STATIC
                val nodes = renderNodes.value
                val records = rememberMessageToolExecutions(message)
                CompositionLocalProvider(LocalMarkdownRenderMode provides mode) {
                    if (waifu) {
                        val timing = remember(nodes, records) {
                            ToolTimingIndex.parse(nodes, records, live = true).group(0, nodes.lastIndex)
                        }
                        WaifuProcessHeader(
                            thoughtCount = 0,
                            toolCount = timing.calls.size,
                            timing = timing,
                            expanded = true,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            onToggle = {},
                        )
                    } else {
                        ProcessTimingCanvas(nodes, records, timingLive = true)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        assertTitle("420ms · ${summary(running, 1)}")
        composeRule.runOnIdle {
            renderNodes.value = renderNodes.value + listOf(
                plainTimingNode("接下来核对相同文件"), toolTimingCall("same-file"),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        val activeSummary = summary(running, 2)
        composeRule.onAllNodes(titleMatcher(running, substring = true)).assertCountEquals(1)
        assertTitle("420ms · $activeSummary")

        val secondStart = SystemClock.elapsedRealtime()
        composeRule.runOnIdle {
            state.update(ToolCallExecution(2, ToolExecutionTiming.clockId, startedMs = secondStart))
        }
        fun readLiveElapsed(): Long? {
            val node = composeRule.onAllNodes(titleMatcher(activeSummary, substring = true))
                .fetchSemanticsNodes().singleOrNull() ?: return null
            val title = if (waifu) node.config[SemanticsProperties.Text].single().text
            else node.config[SemanticsProperties.ContentDescription].single()
            val elapsed = title.substringBefore(" · ")
            return when {
                elapsed.endsWith("ms") -> elapsed.removeSuffix("ms").toLongOrNull()
                elapsed.endsWith("s") -> elapsed.removeSuffix("s").toDoubleOrNull()?.let { (it * 1_000).toLong() }
                else -> null
            }
        }
        // 只推进时钟和计时状态，不追加正文；Compose 的时钟不替代生产的系统单调时钟。
        var firstLiveElapsed = 0L
        composeRule.waitUntil(10_000L) {
            composeRule.mainClock.advanceTimeBy(120L)
            firstLiveElapsed = readLiveElapsed() ?: 0L
            firstLiveElapsed > 420L
        }
        composeRule.waitUntil(10_000L) {
            composeRule.mainClock.advanceTimeBy(120L)
            (readLiveElapsed() ?: 0L) >= firstLiveElapsed + 100L
        }

        val secondEnd = secondStart + 790L
        composeRule.runOnIdle {
            state.update(ToolCallExecution(2, ToolExecutionTiming.clockId, secondStart, 790L, secondEnd))
        }
        // 最新一批计时完成但尚无结果正文；状态保持执行中，耗时已固定为两批之和。
        composeRule.waitUntil(10_000L) {
            composeRule.mainClock.advanceTimeBy(120L)
            composeRule.onAllNodes(titleMatcher("1.21s · $activeSummary")).fetchSemanticsNodes().size == 1
        }
        composeRule.runOnIdle {
            renderNodes.value = renderNodes.value + toolTimingResult(1)
            streaming.value = false
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        val finalTitle = "1.21s · ${summary(completed, 2)}"
        assertTitle(finalTitle)
        // 让真实单调时钟也跨过结束点；只快进 Compose 时钟不能证明耗时停止。
        composeRule.waitUntil(10_000L) {
            composeRule.mainClock.advanceTimeBy(120L)
            SystemClock.elapsedRealtime() >= secondEnd + 250L
        }
        composeRule.mainClock.advanceTimeBy(2_000L)
        assertTitle(finalTitle)
        composeRule.onAllNodes(titleMatcher(running, substring = true)).assertCountEquals(0)
    }
}
