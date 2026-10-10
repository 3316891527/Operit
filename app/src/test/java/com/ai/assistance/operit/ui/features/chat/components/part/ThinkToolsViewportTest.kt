package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkToolsViewportTest {
    private fun node(content: String, xml: Boolean = true) = MarkdownNodeStable(
        type = if (xml) MarkdownProcessorType.XML_BLOCK else MarkdownProcessorType.PLAIN_TEXT,
        content = content,
        children = emptyList(),
    )

    private fun tools(count: Int): List<MarkdownNodeStable> = buildList {
        repeat(count) { index ->
            add(node("<tool name=\"read_file\"><param name=\"path\">sample-$index</param></tool>"))
            add(node("<tool_result name=\"read_file\" status=\"success\"><content>result-$index</content></tool_result>"))
        }
    }

    private fun grouper(enabled: Boolean = true, export: Boolean = false) = ThinkToolsXmlNodeGrouper(
        showThinkingProcess = true,
        enableBoundedViewport = enabled,
        forceExpandGroups = export,
    )

    @Test
    fun oneLargeGroup_keepsEveryToolAndResultAsIndependentRows() {
        val nodes = tools(100)
        val grouper = grouper()
        val groups = grouper.group(nodes, "test")
        val group = groups.single() as MarkdownGroupedItem.Group
        val rows = buildThinkToolsViewportRows(groups, nodes, mapOf(group.stableKey to true)) {
            grouper.describeGroup(it, nodes) { null }
        }
        assertEquals(201, rows.size)
        assertEquals(nodes.indices.toList(), rows.filterIsInstance<ThinkToolsViewportRow.Content>().map { it.index })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun manyExpandedGroups_keepLateResultsReachableWithoutCollapsingEarlierGroups() {
        val nodes = buildList {
            repeat(50) { index ->
                add(node("<think>思考 $index</think>"))
                addAll(tools(2))
                add(node("正文分隔 $index", xml = false))
            }
        }
        val grouper = grouper()
        val groups = grouper.group(nodes, "test")
        val headers = groups.filterIsInstance<MarkdownGroupedItem.Group>()
        val overrides = headers.associate { it.stableKey to true }
        val rows = buildThinkToolsViewportRows(groups, nodes, overrides) {
            grouper.describeGroup(it, nodes) { null }
        }
        assertEquals(50, rows.filterIsInstance<ThinkToolsViewportRow.Header>().size)
        assertEquals(nodes.indices.toList(), rows.filterIsInstance<ThinkToolsViewportRow.Content>().map { it.index })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun manualCollapse_overridesStreamingAutoExpansionWithoutHidingOtherGroups() {
        val nodes = tools(4)
        val groups = listOf(
            MarkdownGroupedItem.Group(0, 3, "first"),
            MarkdownGroupedItem.Group(4, 7, "second"),
        )
        val rows = buildThinkToolsViewportRows(groups, nodes, mapOf("first" to false, "second" to true)) {
            ThinkToolsViewportGroup(it, 2, 0, autoExpand = true)
        }
        assertEquals(listOf(false, true), rows.filterIsInstance<ThinkToolsViewportRow.Header>().map { it.expanded })
        assertEquals(listOf(4, 5, 6, 7), rows.filterIsInstance<ThinkToolsViewportRow.Content>().map { it.index })
    }

    @Test
    fun appendToStreamingGroup_preservesExistingRowKeys() {
        val grouper = grouper()
        fun rows(nodes: List<MarkdownNodeStable>): List<ThinkToolsViewportRow> {
            val groups = grouper.group(nodes, "test")
            return buildThinkToolsViewportRows(groups, nodes, groups.filterIsInstance<MarkdownGroupedItem.Group>().associate {
                it.stableKey to true
            }) { grouper.describeGroup(it, nodes) { null } }
        }
        val before = rows(tools(12))
        val after = rows(tools(13))
        assertEquals(before.map { it.key }, after.take(before.size).map { it.key })
    }

    @Test
    fun viewportSelection_preservesShortMessagesAndFullImageExport() {
        assertFalse(grouper().shouldUseViewport(tools(11)))
        assertTrue(grouper().shouldUseViewport(tools(12)))
        assertFalse(grouper(enabled = false).shouldUseViewport(tools(100)))
        assertFalse(grouper(export = true).shouldUseViewport(tools(100)))
        assertTrue(grouper().shouldUseViewport(listOf(node("<think>${"思考".repeat(20_000)}</think>"))))
        assertFalse(grouper().shouldUseViewport(listOf(node("正文".repeat(20_000), xml = false))))
    }
}