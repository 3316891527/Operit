package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.common.markdown.NoopMarkdownNodeGrouper
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRendererState
import com.ai.assistance.operit.ui.common.markdown.XmlContentRenderer
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.rememberChatTimelineEntries
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import com.ai.assistance.operit.util.stream.Stream
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThinkToolsAutoExpansionAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    private val markerRenderer = object : XmlContentRenderer {
        @Composable
        override fun RenderXmlContent(
            xmlContent: String,
            modifier: Modifier,
            textColor: Color,
            xmlStream: Stream<String>?,
            renderInstanceKey: Any?,
        ) {
            Box(modifier.height(1.dp).semantics { contentDescription = "可见工具明细" })
        }
    }

    private fun toolPair(index: Int) =
        "\n<tool name=\"read_file\"><param name=\"path\">file-$index</param></tool>\n" +
            "\n<tool_result name=\"read_file\"><content>result-$index</content></tool_result>\n"

    private fun verifyBatchGaps(split: Boolean) {
        val source = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
        val message = mutableStateOf(ChatMessage(sender = "ai", timestamp = 100L, contentStream = source))
        val renderer = AtomicReference<StreamMarkdownRendererState>()
        val content = StringBuilder()
        composeRule.setContent {
            MaterialTheme {
                Column {
                    val entries = rememberChatTimelineEntries(
                        listOf(message.value), "tool-batch-gap", true, true, ToolCollapseMode.ALL,
                    )
                    SideEffect { renderer.set(entries.firstOrNull()?.slice?.state) }
                    for (entry in entries) key(entry.key) {
                        entry.slice?.fragment?.Render(
                            modifier = Modifier,
                            textColor = Color.Black,
                            fontSize = 14.sp,
                            onLinkClick = null,
                            xmlRenderer = markerRenderer,
                            nodeGrouper = NoopMarkdownNodeGrouper,
                            enableDialogs = false,
                            fillMaxWidth = true,
                        )
                    }
                }
            }
        }
        try {
            composeRule.waitUntil(20_000) { source.subscriptionCount > 0 }
            var count = if (split) 12 else 2
            val prefix = (0 until count).joinToString("") { toolPair(it) }
            content.append(prefix)
            runBlocking { source.emit(prefix) }

            repeat(4) { batch ->
                composeRule.waitUntil(20_000) {
                    renderer.get()?.let { state ->
                        state.renderNodes.count { it.type == MarkdownProcessorType.XML_BLOCK } == count * 2 &&
                            state.xmlNodeStreams.isEmpty()
                    } == true
                }
                // 明确等到所有 XML 子流清空，再检查同一父回复中的明细仍在。
                composeRule.onAllNodesWithContentDescription("可见工具明细", useUnmergedTree = true)
                    .assertCountEquals(count * 2)
                assertTrue("等待下一批时父回复仍在收集", source.subscriptionCount > 0)
                if (batch < 3) {
                    val next = toolPair(count++)
                    content.append(next)
                    runBlocking { source.emit(next) }
                }
            }

            source.close()
            composeRule.waitUntil(20_000) { renderer.get()?.streamParsingCompletedSuccessfully == true }
            composeRule.runOnIdle {
                message.value = message.value.copy(content = content.toString(), contentStream = null)
            }
            composeRule.onAllNodesWithContentDescription("可见工具明细", useUnmergedTree = true)
                .assertCountEquals(0)
        } finally {
            source.close()
        }
    }

    @Test
    fun anOrdinaryGroupStaysExpandedBetweenCompletedXmlBlocks() {
        verifyBatchGaps(split = false)
    }

    @Test
    fun aSplitGroupStaysExpandedBetweenCompletedXmlBlocks() {
        verifyBatchGaps(split = true)
    }
}