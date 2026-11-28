package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolExecutionTiming
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.common.markdown.LocalResponseProcessId
import com.ai.assistance.operit.ui.common.markdown.UnifiedMarkdownCanvas
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.CustomXmlRenderer
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.ThinkToolsXmlNodeGrouper
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AggregatedToolProcessAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun oneProcessButtonKeepsBothBatchesAndLeavesTheFinalAnswerOutside() {
        val paths = listOf("first.txt", "second.txt", "third.txt")
        val intro = "回复开头"
        val transition = "现在核对另一份文件"
        val card = "原生详情卡片"
        val answer = "这是最终答复"
        val nodes = listOf(
            plainTimingNode(intro),
            xmlTimingNode("<think>先检查前两份文件</think>"),
            toolTimingCall(paths[0]), toolTimingCall(paths[1]),
            toolTimingResult(0), toolTimingResult(1),
            plainTimingNode(transition),
            xmlTimingNode("<details><summary>$card</summary>详情内容</details>"),
            toolTimingCall(paths[2]), toolTimingResult(2),
            plainTimingNode(answer),
        )
        // 首批有重叠区间，第二批与首批之间的等待不计入工具耗时：520ms + 690ms。
        val executions = listOf(
            ToolCallExecution(0, ToolExecutionTiming.clockId, 1_000L, 420L, 1_420L),
            ToolCallExecution(1, ToolExecutionTiming.clockId, 1_200L, 320L, 1_520L),
            ToolCallExecution(2, ToolExecutionTiming.clockId, 2_300L, 690L, 2_990L),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completed = context.getString(R.string.tool_timing_completed)
        val title = "1.21s · ${context.getString(R.string.tool_timing_combined, completed, 1, 3)}"
        val toolLabel = context.getString(R.string.tool_call)
        val paramsLabel = context.getString(R.string.tool_call_parameters)
        val close = context.getString(R.string.common_close)
        val expand = context.getString(R.string.common_expand)
        val collapse = context.getString(R.string.common_collapse)
        val header = composeRule.onNodeWithContentDescription(title)
        val introNode = composeRule.onNodeWithContentDescription(intro)
        val transitionNode = composeRule.onNodeWithContentDescription(transition)
        val cardNode = composeRule.onNodeWithContentDescription(card)
        val answerNode = composeRule.onNodeWithContentDescription(answer)

        composeRule.setContent {
            MaterialTheme {
                // 同时打开旧整条回复的折叠条件，验证工具回复不会多叠一层用时标题。
                CompositionLocalProvider(LocalResponseProcessId provides "instrumented-tool-process") {
                    ProcessTimingCanvas(
                        nodes, executions,
                        modifier = Modifier.width(280.dp),
                        collapseCompletedProcess = true,
                        responseDurationMs = 9_000L,
                    )
                }
            }
        }

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        introNode.assertIsDisplayed()
        answerNode.assertIsDisplayed()
        transitionNode.assertDoesNotExist()
        cardNode.assertDoesNotExist()
        paths.forEach { composeRule.onNodeWithContentDescription(it, substring = true).assertDoesNotExist() }
        header.assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expand))
            .performClick()

        header.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, collapse))
        composeRule.onAllNodes(hasClickAction() and hasContentDescription(completed, substring = true))
            .assertCountEquals(1)
        transitionNode.assertIsDisplayed()
        cardNode.assertIsDisplayed().assertHasClickAction()
        answerNode.assertIsDisplayed()
        val rows = paths.map { path ->
            // 精确匹配工具名和参数预览，单项状态或毫秒耗时混入描述都会失败。
            composeRule.onNodeWithContentDescription("$toolLabel: read_file, $paramsLabel: $path")
                .assertIsDisplayed().assertHasClickAction()
        }
        val firstRowTop = rows.first().fetchSemanticsNode().boundsInRoot.top
        val laterRowTop = rows.last().fetchSemanticsNode().boundsInRoot.top
        assertTrue(introNode.fetchSemanticsNode().boundsInRoot.top < header.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(header.fetchSemanticsNode().boundsInRoot.top < firstRowTop)
        assertTrue(firstRowTop < transitionNode.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(cardNode.fetchSemanticsNode().boundsInRoot.top < laterRowTop)
        assertTrue(laterRowTop < answerNode.fetchSemanticsNode().boundsInRoot.top)

        rows.zip(paths).forEach { (row, path) ->
            row.performClick()
            composeRule.onNodeWithText(path).assertIsDisplayed()
            composeRule.onNodeWithText(close).performClick()
            composeRule.onNodeWithText(path).assertDoesNotExist()
        }
        header.performClick()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        transitionNode.assertDoesNotExist()
        cardNode.assertDoesNotExist()
        rows.forEach { it.assertDoesNotExist() }
        answerNode.assertIsDisplayed()
    }
}

@Composable
internal fun ProcessTimingCanvas(
    nodes: List<MarkdownNodeStable>,
    executions: List<ToolCallExecution>,
    modifier: Modifier = Modifier,
    timingLive: Boolean = false,
    collapseCompletedProcess: Boolean = false,
    responseDurationMs: Long = 0L,
) {
    val grouper = remember {
        ThinkToolsXmlNodeGrouper(showThinkingProcess = true, toolCollapseMode = ToolCollapseMode.ALL)
    }
    grouper.executionTimings = executions
    grouper.toolTimingsLive = timingLive
    val xmlRenderer = remember { CustomXmlRenderer() }
    UnifiedMarkdownCanvas(
        nodes = nodes,
        rendererId = "static-tool-process-regression",
        nodeAnimationStates = emptyMap(),
        textColor = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        onLinkClick = null,
        xmlRenderer = xmlRenderer,
        xmlStreamsByIndex = emptyMap(),
        nodeGrouper = grouper,
        enableDialogs = true,
        modifier = modifier,
        collapseCompletedProcess = collapseCompletedProcess,
        responseDurationMs = responseDurationMs,
    )
}

internal fun plainTimingNode(text: String) = MarkdownNodeStable(MarkdownProcessorType.PLAIN_TEXT, text, emptyList())

internal fun xmlTimingNode(xml: String) = MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, xml, emptyList())

internal fun toolTimingCall(path: String) =
    xmlTimingNode("<tool name=\"read_file\"><param name=\"path\">$path</param></tool>")

internal fun toolTimingResult(index: Int) =
    xmlTimingNode("<tool_result name=\"read_file\" status=\"success\"><content>result-$index</content></tool_result>")
