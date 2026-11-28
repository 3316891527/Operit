package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedProcessTest {
    private fun text(value: String) = MarkdownNodeStable(MarkdownProcessorType.PLAIN_TEXT, value, emptyList())
    private fun xml(value: String) = MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, value, emptyList())

    @Test fun toolProcessNeverGetsASecondHeader() {
        for (process in listOf(
            "<tool_a name=\"read\"></tool_a>",
            "<tool_result_a><content>result</content></tool_result_a>",
            "\u003Csearch>query</search>",
        )) {
            assertEquals(-1, completedProcessEnd(listOf(xml("<think>reasoning</think>"), xml(process), text("Answer"))))
            assertEquals(-1, completedProcessEnd(listOf(xml(process))))
        }
    }

    @Test fun reasoningIsFoldedOnlyWhenAnAnswerFollows() {
        assertEquals(-1, completedProcessEnd(listOf(text("One"), text("Two"))))
        assertEquals(0, completedProcessEnd(listOf(xml("<think>reasoning</think>"), text("Answer"))))
        assertEquals(-1, completedProcessEnd(listOf(xml("<think>reasoning</think>"))))
    }

    @Test fun includesWhitespaceAndMetadataAfterPureReasoning() {
        val nodes = listOf(xml("<think>work</think>"), text("\n"),
            xml("<meta>hidden</meta>"), text("\n"), text("Final answer"))
        assertEquals(3, completedProcessEnd(nodes))
    }

    @Test fun visibleXmlIsPartOfTheFinalAnswer() {
        for (tag in listOf("html", "font", "details")) {
            assertEquals(1, completedProcessEnd(listOf(
                xml("<think>work</think>"), text("\n"),
                xml("<$tag>Final content</$tag>"), text("More explanation"))))
        }
    }

    @Test fun disabledStreamingOrHiddenThinkingDoesNotCreateTheHeader() {
        val nodes = listOf(xml("<think>work</think>"), text("answer"))
        assertEquals(-1, completedProcessEnd(nodes, collapseEnabled = false))
        assertEquals(-1, completedProcessEnd(nodes, streaming = true))
        assertEquals(-1, completedProcessEnd(listOf(text("Preface")) + nodes, showThinkingProcess = false))
    }

    @Test fun collapsedProjectionRetainsTheWholeAnswer() {
        assertFalse(responseProcessItemVisible(2, 2, false))
        assertTrue(responseProcessItemVisible(3, 2, false))
        assertTrue(responseProcessItemVisible(20, 2, false))
        assertTrue(responseProcessItemVisible(0, 2, true))
        assertTrue(responseProcessItemVisible(0, -1, false))
    }

    @Test fun expansionIdentitySeparatesMessagesAndSelectedVariants() {
        assertNotEquals(responseProcessKey(10, 0), responseProcessKey(11, 0))
        assertNotEquals(responseProcessKey(10, 0), responseProcessKey(10, 1))
    }
}