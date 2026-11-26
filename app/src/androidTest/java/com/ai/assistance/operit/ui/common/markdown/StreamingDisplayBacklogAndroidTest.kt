package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withRollbackPrefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 只注入合成显示流，不调用模型、工具或设备文件。 */
@RunWith(AndroidJUnit4::class)
class StreamingDisplayBacklogAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    private fun call(name: String, body: String) =
        """<tool name="$name"><param name="value">$body</param></tool>"""

    private fun result(name: String, body: String) =
        """<tool_result name="$name" status="success"><content>$body</content></tool_result>"""

    private fun transcript(index: Int): Pair<String, List<String>> {
        val blocks = listOf("read_file", "shell", "package_proxy", "create_file").flatMap { name ->
            listOf(call(name, "参数-$index-" + "长参数".repeat(4_000)), result(name, "结果-$index-" + "长结果".repeat(4_000)))
        }
        return blocks.joinToString("") + "最终回复-$index。\n" to blocks
    }

    private fun visible(state: StreamMarkdownRendererState) = state.renderNodes.joinToString("") { it.content }

    @Test
    fun multipleToolsReachTheirFullResultsBeforeTheSourceCloses() {
        val source = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
        val state = StreamMarkdownRendererState()
        composeRule.setContent {
            MaterialTheme { StreamMarkdownChunkRenderer(source, state = state, renderContent = false) }
        }
        try {
            composeRule.waitUntil(20_000) { source.subscriptionCount > 0 }
            val (text, blocks) = transcript(0)
            runBlocking { text.chunked(4_096).forEach { source.emit(it) } }
            composeRule.waitUntil(20_000) { visible(state).contains("最终回复-0。") }
            composeRule.runOnIdle {
                assertFalse(state.streamParsingCompletedSuccessfully)
                assertEquals(blocks, state.renderNodes.take(blocks.size).map { it.content })
                assertEquals(text, state.collectedContent.toString())
            }
            source.close()
            composeRule.waitUntil(20_000) { state.streamParsingCompletedSuccessfully }
            assertEquals(text.trimEnd(), visible(state).trimEnd())
        } finally { source.close() }
    }

    @Test
    fun threeConcurrentLongDisplaysFinishWithoutCancellingAnyStream() {
        val sources = List(3) { MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE) }
        val states = List(3) { StreamMarkdownRendererState() }
        composeRule.setContent {
            MaterialTheme {
                sources.forEachIndexed { index, source ->
                    StreamMarkdownChunkRenderer(source, state = states[index], renderContent = false)
                }
            }
        }
        try {
            composeRule.waitUntil(20_000) { sources.all { it.subscriptionCount > 0 } }
            val texts = List(3) { transcript(it).first }
            runBlocking {
                sources.mapIndexed { index, source ->
                    async(Dispatchers.Default) { texts[index].chunked(2_048).forEach { source.emit(it) }; source.close() }
                }.awaitAll()
            }
            composeRule.waitUntil(30_000) { states.all { it.streamParsingCompletedSuccessfully } }
            composeRule.runOnIdle {
                states.forEachIndexed { index, state ->
                    assertEquals(texts[index], state.collectedContent.toString())
                    assertEquals(texts[index].trimEnd(), visible(state).trimEnd())
                }
            }
        } finally { sources.forEach { it.close() } }
    }

    @Test
    fun blockInputRestoresAnOpenToolAndKeepsTheNextResultSeparate() {
        val prefix = """<tool name="shell"><param name="value">保留"""
        val tail = "继续</param></tool>"
        val response = result("shell", "成功")
        val state = StreamMarkdownRendererState()
        val input = stream { emit(tail + response) }.withRollbackPrefix(prefix)
        composeRule.setContent {
            MaterialTheme { StreamMarkdownChunkRenderer(input, state = state, renderContent = false) }
        }
        composeRule.waitUntil(20_000) { state.streamParsingCompletedSuccessfully }
        composeRule.runOnIdle {
            assertEquals(prefix + tail + response, state.collectedContent.toString())
            assertEquals(listOf(prefix + tail, response), state.renderNodes.map { it.content })
            assertTrue(state.xmlNodeStreams.isEmpty())
        }
    }

    @Test
    fun cancellingTheDisplayDetachesWithoutCancellingItsSource() {
        val source = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
        val shown = mutableStateOf(true)
        val state = StreamMarkdownRendererState()
        composeRule.setContent {
            MaterialTheme {
                if (shown.value) StreamMarkdownChunkRenderer(source, state = state, renderContent = false)
            }
        }
        try {
            composeRule.waitUntil(20_000) { source.subscriptionCount > 0 }
            val pending = """<tool name="shell"><param name="value">未完成参数"""
            runBlocking { source.emit(pending) }
            composeRule.waitUntil(20_000) { state.collectedContent.toString() == pending }
            composeRule.runOnIdle { shown.value = false }
            composeRule.waitUntil(20_000) { source.subscriptionCount == 0 }
            assertTrue(source.tryEmit("后续文本"))
            assertEquals(pending + "后续文本", source.replayCache.joinToString(""))
        } finally { source.close() }
    }

    @Test
    fun returningToTheScreenReplaysTheLatestCompleteBlocks() {
        val source = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
        val shown = mutableStateOf(true)
        val state = StreamMarkdownRendererState()
        composeRule.setContent {
            MaterialTheme {
                if (shown.value) StreamMarkdownChunkRenderer(source, state = state, renderContent = false)
            }
        }
        try {
            composeRule.waitUntil(20_000) { source.subscriptionCount > 0 }
            runBlocking { source.emit(call("read_file", "首次") + "首次正文\n") }
            composeRule.waitUntil(20_000) { visible(state).contains("首次正文") }
            composeRule.runOnIdle { shown.value = false }
            composeRule.waitUntil(20_000) { source.subscriptionCount == 0 }
            runBlocking { source.emit(result("read_file", "完成") + "离屏后的完整正文\n") }
            source.close()
            composeRule.runOnIdle { shown.value = true }
            composeRule.waitUntil(20_000) {
                state.streamParsingCompletedSuccessfully && visible(state).contains("离屏后的完整正文")
            }
            assertTrue(visible(state).contains("首次正文"))
        } finally { source.close() }
    }
}
