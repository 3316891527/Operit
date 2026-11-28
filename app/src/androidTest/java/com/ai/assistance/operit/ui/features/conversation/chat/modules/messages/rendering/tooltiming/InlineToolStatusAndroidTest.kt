package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolExecutionTiming
import com.ai.assistance.operit.ui.common.markdown.lazy.LocalMarkdownCardNodeIndex
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.CompactToolDisplay
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.CustomXmlRenderer
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineToolStatusAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun productionRendererKeepsCompletedTimingInsideTheToolRow() {
        val body = "<tool name=\"read_file_part\"></tool>"
        val result = "<tool_result name=\"read_file_part\" status=\"success\"><content>ok</content></tool_result>"
        val nodes = listOf(body, result).map { MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, it, emptyList()) }
        val timings = ToolTimingIndex.parse(nodes, listOf(ToolCallExecution(0, ToolExecutionTiming.clockId, 1_000L, 4_800L, 5_800L)))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completed = context.getString(R.string.tool_timing_completed)
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.width(280.dp)) {
                    Box(Modifier.testTag("plain-tool")) { CompactToolDisplay("read_file_part", "", Color.White) }
                    Box(Modifier.testTag("timed-tool")) {
                        CompositionLocalProvider(LocalToolTimingIndex provides timings, LocalMarkdownCardNodeIndex provides 0) {
                            CustomXmlRenderer().RenderXmlContent(body, Modifier, Color.White, null, "inline-test")
                        }
                    }
                }
            }
        }
        val plainHeight = composeRule.onNodeWithTag("plain-tool").fetchSemanticsNode().boundsInRoot.height
        val timedHeight = composeRule.onNodeWithTag("timed-tool").fetchSemanticsNode().boundsInRoot.height
        assertEquals(plainHeight, timedHeight, 0.5f)
        val description = composeRule.onNodeWithContentDescription(completed, substring = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
        assertFalse(description.contains("1次"))
        composeRule.onNodeWithText(completed, substring = true).assertDoesNotExist()
    }

    @Test
    fun timedToolKeepsParameterDialogClickTarget() {
        composeRule.setContent {
            MaterialTheme {
                CompactToolDisplay(
                    "read_file_part", "<param name=\"path\">sample.txt</param>", Color.White,
                    statusLabel = "已完成（42ms）",
                )
            }
        }
        composeRule.onNodeWithContentDescription("已完成（42ms）", substring = true)
            .assertHasClickAction().performClick()
        composeRule.onNodeWithText("sample.txt", substring = true).assertExists()
    }
}