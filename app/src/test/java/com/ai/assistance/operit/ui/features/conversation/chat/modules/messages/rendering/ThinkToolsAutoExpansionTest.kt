package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

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
    fun laterBodyClosesThePreviousGroupAndOnlyTheNewGroupExpands() {
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = true)
        val nodes = tools(2) + text("这一批已完成，继续下一步。") + tools(2)
        val rows = rows(grouper, nodes, streaming = true)
        val headers = rows.filterIsInstance<ThinkToolsRow.Header>()

        assertEquals(listOf(false, true), headers.map { it.expanded })
        assertEquals(listOf(4, 5, 6, 7, 8), rows.filterIsInstance<ThinkToolsRow.Content>().map { it.index })
    }

    @Test
    fun whitespaceMetadataAndAnIncompleteNextToolDoNotCloseTheActiveGroup() {
        val grouper = ThinkToolsXmlNodeGrouper(showThinkingProcess = true, toolCollapseMode = ToolCollapseMode.READ_ONLY)
        val nodes = tools(2) + listOf(
            text("\n\n"),
            xml("<meta provider=\"openai:responses_reasoning\">协议</meta>"),
            xml("<tool"),
        )
        val group = grouper.group(nodes, "test").filterIsInstance<MarkdownGroupedItem.Group>().single()

        assertTrue(grouper.describeGroup(group, nodes, isMessageStreaming = true).autoExpand)
        val withWriteTool = nodes.dropLast(1) + xml("<tool name=\"write_file\">")
        assertFalse(grouper.describeGroup(group, withWriteTool, isMessageStreaming = true).autoExpand)
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
