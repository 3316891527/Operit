package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaifuProcessRowsTest {
    private fun xml(value: String) = MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, value, emptyList())
    private fun call() = xml("<tool name=\"same\"/>")
    private fun result() = xml("<tool_result name=\"same\" status=\"success\">完成</tool_result>")

    @Test
    fun manualCollapseKeepsOnlyOneHeaderWhenNewResultsArrive() {
        val nodes = listOf(call(), call(), result(), result())
        val rows = buildWaifuProcessRows(nodes, expanded = false, showThinkingProcess = true)
        assertEquals(listOf(ThinkToolsRow.WaifuHeader(2, 0, false)), rows)
    }

    @Test
    fun resultArrivalKeepsTheRequestCardKey() {
        val before = buildWaifuProcessRows(listOf(call()), true, true).filterIsInstance<ThinkToolsRow.WaifuTool>().single()
        val after = buildWaifuProcessRows(listOf(call(), result()), true, true).filterIsInstance<ThinkToolsRow.WaifuTool>().single()
        assertEquals(before.key, after.key)
        assertEquals(before.call.index, after.call.index)
        assertEquals(1, after.call.resultIndex)
    }

    @Test
    fun largeProcessesStayAsIndividuallyLazyCards() {
        val nodes = List(200) { call() } + List(200) { result() }
        val rows = buildWaifuProcessRows(nodes, true, true)
        assertEquals(201, rows.size)
        assertEquals(200, rows.filterIsInstance<ThinkToolsRow.WaifuTool>().size)
        assertEquals(201, rows.map { it.key }.distinct().size)
    }

    @Test
    fun hiddenThinkingDoesNotLeaveAnEmptyGroup() {
        assertTrue(buildWaifuProcessRows(listOf(xml("<think>隐藏</think>")), true, false).isEmpty())
        val rows = buildWaifuProcessRows(listOf(xml("<think>隐藏</think>"), call(), result()), true, false)
        assertEquals(ThinkToolsRow.WaifuHeader(1, 0, true), rows.first())
        assertEquals(2, rows.size)
    }

    @Test
    fun aSingleThoughtUsesOnlyItsOwnPreviewAndExpansionControl() {
        val nodes = listOf(xml("<think>原文</think>"))
        val open = buildWaifuProcessRows(nodes, true, true)
        val closed = buildWaifuProcessRows(nodes, false, true)
        assertEquals(1, open.size)
        assertTrue(open.single() is ThinkToolsRow.Content)
        assertEquals(open, closed)
        assertTrue(open.none { it is ThinkToolsRow.WaifuHeader })
    }

    @Test
    fun addingAToolPreservesTheThoughtsCardIdentity() {
        val thought = xml("<think>原文</think>")
        val before = buildWaifuProcessRows(listOf(thought), true, true).filterIsInstance<ThinkToolsRow.Content>().single()
        val after = buildWaifuProcessRows(listOf(thought, call()), true, true).filterIsInstance<ThinkToolsRow.Content>().single()
        assertEquals(before.key, after.key)
        assertEquals(before.index, after.index)
    }

    @Test
    fun multipleThoughtsKeepTheOverallProcessHeader() {
        val rows = buildWaifuProcessRows(listOf(xml("<think>一</think>"), xml("<think>二</think>")), false, true)
        assertEquals(listOf(ThinkToolsRow.WaifuHeader(0, 2, false)), rows)
    }

    @Test
    fun innerThinkingUsesTheSameProcessIndentationAsTools() {
        val rows = buildWaifuProcessRows(listOf(xml("<think>原文</think>"), call(), result()), true, true)
        val thought = rows.filterIsInstance<ThinkToolsRow.Content>().single()
        assertTrue(thought.inGroup)
        assertTrue(thought.waifuProcess)
        assertEquals(ThinkToolsRow.WaifuHeader(1, 1, true), rows.first())
    }
}
