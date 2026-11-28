package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkToolsAutoExpansionTest {
    private fun xml(content: String) = MarkdownNodeStable(
        type = MarkdownProcessorType.XML_BLOCK,
        content = content,
        children = emptyList(),
    )

    private fun text(content: String) = xml(content).copy(type = MarkdownProcessorType.PLAIN_TEXT)

    private fun tools(count: Int): List<MarkdownNodeStable> = buildList {
        repeat(count) { index ->
            add(xml("<tool name=\"read_file\"><param name=\"path\">file-$index</param></tool>"))
            add(xml("<tool_result name=\"read_file\"><content>result-$index</content></tool_result>"))
        }
    }

    private fun rows(
        grouper: ThinkToolsXmlNodeGrouper,
        nodes: List<MarkdownNodeStable>,
        streaming: Boolean,
        overrides: Map<String, Boolean> = emptyMap(),
    ) = buildThinkToolsRows(grouper.group(nodes, "test"), nodes, overrides) {
        grouper.describeGroup(it, nodes, isMessageStreaming = streaming)
    }

    @Test
    fun completedXmlBlocksRemainExpandedWhileTheResponseWaitsForMoreTools() {
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = true)
        val nodes = tools(2)
        // 所有 XML 块均已闭合且没有子流，整段回复仍在继续，工具组不能在间隙收起。
        val rows = rows(grouper, nodes, streaming = true)

        assertTrue(rows.filterIsInstance<ThinkToolsRow.Header>().single().expanded)
        assertEquals(nodes.indices.toList(), rows.filterIsInstance<ThinkToolsRow.Content>().map { it.index })
    }

    @Test
    fun repeatedFastBatchesKeepTheGroupExpandedAndPreserveExistingRows() {
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = true)
        var previousKeys = emptyList<String>()
        for (count in 2..57) {
            val nodes = tools(count)
            val rows = rows(grouper, nodes, streaming = true)
            assertTrue("批次间隙仍保持展开：$count", rows.filterIsInstance<ThinkToolsRow.Header>().single().expanded)
            assertEquals(previousKeys, rows.take(previousKeys.size).map { it.key })
            assertEquals(nodes.size, rows.filterIsInstance<ThinkToolsRow.Content>().size)
            previousKeys = rows.map { it.key }
        }
    }

    @Test
    fun finishingTheResponseCollapsesAnUnchangedToolGroup() {
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = true)
        val nodes = tools(2)
        val before = rows(grouper, nodes, streaming = true)
        val after = rows(grouper, nodes, streaming = false)

        assertEquals(before.first().key, after.single().key)
        assertFalse((after.single() as ThinkToolsRow.Header).expanded)
    }

    @Test
    fun laterBatchesExtendTheSameGroupAcrossInterimTextAndCards() {
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = true)
        val firstBatch = tools(2) + listOf(text("这一批已完成，继续下一步。"), xml("<html>卡片</html>"))
        val before = rows(grouper, firstBatch, streaming = true)
        val nodes = firstBatch + tools(2) + text("最终正文")
        val after = rows(grouper, nodes, streaming = true)
        val beforeHeader = before.filterIsInstance<ThinkToolsRow.Header>().single()
        val afterHeader = after.filterIsInstance<ThinkToolsRow.Header>().single()
        assertEquals(beforeHeader.key, afterHeader.key)
        assertTrue(afterHeader.expanded)
        assertTrue(afterHeader.info.isMessageStreaming)
        assertEquals(4, afterHeader.info.toolCount)
        val beforeRows = before.filterIsInstance<ThinkToolsRow.Content>()
        val afterRows = after.filterIsInstance<ThinkToolsRow.Content>()
        assertEquals(beforeRows.map { it.key }, afterRows.take(beforeRows.size).map { it.key })
        assertEquals(nodes.indices.toList(), afterRows.map { it.index })
        assertTrue(afterRows[4].inGroup && afterRows[5].inGroup)
        assertFalse(afterRows.last().inGroup)
    }

    @Test
    fun defaultsRespectCollapseModeWithoutCreatingABatchGroup() {
        val readOnly = ThinkToolsXmlNodeGrouper(showThinkingProcess = true, toolCollapseMode = ToolCollapseMode.READ_ONLY)
        val all = ThinkToolsXmlNodeGrouper(showThinkingProcess = true, toolCollapseMode = ToolCollapseMode.ALL)
        val full = ThinkToolsXmlNodeGrouper(showThinkingProcess = true, toolCollapseMode = ToolCollapseMode.FULL)
        fun header(grouper: ThinkToolsXmlNodeGrouper, nodes: List<MarkdownNodeStable>, streaming: Boolean = false) =
            rows(grouper, nodes, streaming).filterIsInstance<ThinkToolsRow.Header>().single()

        val readBatch = tools(2)
        val mixed = readBatch + text("修改内容前说明") + listOf(
            xml("<tool name=\"write_file\"></tool>"),
            xml("<tool_result name=\"write_file\"><content>done</content></tool_result>"),
        )
        assertTrue(header(all, tools(1)).expanded)
        assertFalse(header(full, tools(1)).expanded)
        assertFalse(header(readOnly, readBatch).expanded)
        assertTrue(header(readOnly, mixed).expanded)
        assertFalse(header(all, mixed).expanded)
        assertTrue(header(full, mixed, streaming = true).expanded)
        assertEquals(header(readOnly, readBatch).key, header(readOnly, mixed).key)
    }

    @Test
    fun summaryUsesTheUnionAcrossBatchesAndCountsEachRequestOnce() {
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = true)
        val nodes = listOf(xml("<think>思考</think>")) + tools(2) + text("模型生成间隙") + tools(1) + text("答案")
        grouper.executionTimings = listOf(
            ToolCallExecution(0, "clock", startedMs = 100, durationMs = 100),
            ToolCallExecution(1, "clock", startedMs = 150, durationMs = 100),
            ToolCallExecution(2, "clock", startedMs = 900, durationMs = 50),
        )
        val info = rows(grouper, nodes, streaming = false).filterIsInstance<ThinkToolsRow.Header>().single().info
        assertEquals(3, info.toolCount)
        assertEquals(1, info.thoughtCount)
        assertEquals(listOf(1, 3, 6), info.timing.calls.map { it.nodeIndex })
        assertEquals(200L, info.timing.elapsedMillis(1_000L))
    }

    @Test
    fun hiddenThinkingDoesNotChangeToolCountsAndWaifuKeepsItsOwnEntryPoint() {
        val nodes = listOf(xml("<think>隐藏思考</think>"), text("前言")) + tools(1) +
            listOf(xml("<think>隐藏思考</think>"), text("答案"))
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = false)
        val group = grouper.group(nodes, "test").filterIsInstance<MarkdownGroupedItem.Group>().single()
        val info = grouper.describeGroup(group, nodes, isMessageStreaming = false)
        assertEquals(2, group.startIndex)
        assertEquals(3, group.endIndexInclusive)
        assertEquals(1, info.toolCount)
        assertEquals(0, info.thoughtCount)
        val waifu = ThinkToolsXmlNodeGrouper(showThinkingProcess = true, groupProcessNodes = false)
        assertEquals(nodes.indices.map { MarkdownGroupedItem.Single(it) }, waifu.group(nodes, "waifu"))
    }

    @Test
    fun manualExpansionChoicesTakePriorityDuringStreamingAndAfterCompletion() {
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = true)
        val nodes = tools(2)
        val group = grouper.group(nodes, "test").single() as MarkdownGroupedItem.Group

        for (streaming in listOf(true, false)) {
            for (expanded in listOf(true, false)) {
                val header = rows(grouper, nodes, streaming, mapOf(group.stableKey to expanded))
                    .filterIsInstance<ThinkToolsRow.Header>().single()
                assertEquals(expanded, header.expanded)
            }
        }
    }
}
