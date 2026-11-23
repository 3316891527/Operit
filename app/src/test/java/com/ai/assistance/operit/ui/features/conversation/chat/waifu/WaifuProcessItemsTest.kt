package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaifuProcessItemsTest {
    private fun xml(content: String) = MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, content, emptyList())
    private fun text(content: String) = MarkdownNodeStable(MarkdownProcessorType.PLAIN_TEXT, content, emptyList())
    private fun call(name: String) = xml("<tool name=\"$name\"/>")
    private fun result(name: String, value: String = "完成") = xml("<tool_result name=\"$name\" status=\"success\"><content>$value</content></tool_result>")

    @Test
    fun fourCallsAndFourResultsProduceFourCards() {
        val names = listOf("sleep", "list_tasks", "catalog", "a2ui_prepare")
        val nodes = names.map(::call) + names.map { result(it) }
        val process = waifuProcessItems(nodes, true)
        assertEquals(4, process.toolCount)
        assertEquals((0..3).map { WaifuProcessItem.Tool(it, it + 4) }, process.items)
    }

    @Test
    fun sameNameParallelCallsKeepTheirOriginalPositions() {
        val nodes = listOf(call("read_file"), call("read_file"), result("read_file", "第一个"), result("read_file", "第二个"))
        val calls = waifuProcessItems(nodes, true).items.filterIsInstance<WaifuProcessItem.Tool>()
        assertEquals(listOf(WaifuProcessItem.Tool(0, 2), WaifuProcessItem.Tool(1, 3)), calls)
        assertEquals("第一个", waifuToolContent(nodes[0].content, nodes[2].content).result)
        assertEquals("第二个", waifuToolContent(nodes[1].content, nodes[3].content).result)
    }

    @Test
    fun serialCallsKeepThinkingBetweenTheirCards() {
        val nodes = listOf(call("x"), result("x"), xml("<think>再检查</think>"), call("x"), result("x"))
        val process = waifuProcessItems(nodes, true)
        assertEquals(listOf(WaifuProcessItem.Tool(0, 1), WaifuProcessItem.Content(2), WaifuProcessItem.Tool(3, 4)), process.items)
        assertEquals(2, process.toolCount)
        assertEquals(1, process.thoughtCount)
    }

    @Test
    fun partialResultUpdatesTheExistingCallAnchor() {
        val request = call("x")
        val partial = xml("<tool_result name=\"x\" status=\"success\"><content><br/>")
        val pending = waifuProcessItems(listOf(request), true).items.single()
        val streaming = waifuProcessItems(listOf(request, partial), true).items.single()
        val complete = waifuProcessItems(listOf(request, result("x")), true).items.single()
        assertEquals(pending.index, streaming.index)
        assertEquals(streaming.index, complete.index)
        assertFalse(waifuToolContent(request.content, partial.content).resultClosed)
        assertNull(waifuToolContent(request.content, partial.content).status)
    }

    @Test
    fun resultOnlyHistoryDoesNotInventACallCount() {
        val process = waifuProcessItems(listOf(result("old")), true)
        assertEquals(listOf(WaifuProcessItem.Tool(null, 0)), process.items)
        assertEquals(0, process.toolCount)
        assertEquals("old", waifuToolContent(null, result("old").content).name)
    }

    @Test
    fun hiddenThinkingAndProtocolDoNotCreateExtraRows() {
        val nodes = listOf(xml("<think>隐藏思考</think>"), text("\n"), call("x"),
            xml("<meta provider=\"operit:tool_timing\">内部数据</meta>"), result("x"))
        val process = waifuProcessItems(nodes, false)
        assertEquals(listOf(WaifuProcessItem.Tool(2, 4)), process.items)
        assertEquals(0, process.thoughtCount)
        assertTrue(waifuProcessItems(listOf(xml("<think>隐藏思考</think>")), false).items.isEmpty())
    }

    @Test
    fun visibleBodyStopsCrossProcessPairing() {
        val process = waifuProcessItems(listOf(call("x"), text("正文。"), result("x")), true)
        assertEquals(listOf(WaifuProcessItem.Tool(0, null), WaifuProcessItem.Content(1), WaifuProcessItem.Tool(null, 2)), process.items)
    }

    @Test
    fun errorStatusAndToolNameComeOnlyFromOuterAttributes() {
        val request = "<tool name=\"real\"><param name=\"example\">name=\"fake\" status=\"success\"</param></tool>"
        val result = "<tool_result name=\"real\" status=\"error\"><content>name=\"fake\" status=\"success\"</content></tool_result>"
        val content = waifuToolContent(request, result)
        assertEquals("real", content.name)
        assertEquals("error", content.status)
        assertTrue(content.parameters.contains("name=\"fake\""))
    }

    @Test
    fun selfClosingResultsCompleteWithoutFabricatedText() {
        val content = waifuToolContent("<tool name=\"x\"/>", "<tool_result name=\"x\" status=\"success\"/>")
        assertTrue(content.resultClosed)
        assertEquals("", content.result)
        assertEquals("success", content.status)
    }

    @Test
    fun rawStructuredResultsRemainAvailableForCopying() {
        val body = "{\"compiled_json\":\"原始卡片内容\",\"reference\":\"card-1\",\"status\":\"ready\"}"
        assertEquals(body, waifuToolContent(null, result("a2ui_prepare", body).content).result)
    }

    @Test
    fun removingRetriedResultsLeavesIndependentPendingCalls() {
        val nodes = listOf(call("x"), call("x"), result("x"))
        assertEquals(listOf(WaifuProcessItem.Tool(0, 2), WaifuProcessItem.Tool(1, null)), waifuProcessItems(nodes, true).items)
        assertEquals(listOf(WaifuProcessItem.Tool(0, null), WaifuProcessItem.Tool(1, null)), waifuProcessItems(nodes.take(2), true).items)
    }

    @Test
    fun publishedA2uiCardsRemainVisibleBodyInsteadOfToolProcess() {
        val card = "```a2ui\n{\"version\":\"v0.10\",\"createSurface\":{\"surfaceId\":\"card\"}}\n```"
        assertFalse(WaifuMessageProcessor.isProcessOnlyMessage(card))
        val segments = WaifuProcessContent.mergeConsecutive(listOf(call("x").content, result("x").content, card))
        assertEquals(card, segments.last())
        assertEquals(2, segments.size)
        assertTrue(segments.any(WaifuMessageProcessor::isProcessOnlyMessage))
    }
}
