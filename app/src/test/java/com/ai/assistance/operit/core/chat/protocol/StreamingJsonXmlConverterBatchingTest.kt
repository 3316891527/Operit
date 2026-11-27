package com.ai.assistance.operit.core.chat.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingJsonXmlConverterBatchingTest {
    private fun render(events: List<StreamingJsonXmlConverter.Event>) = events.joinToString("") { event ->
        when (event) {
            is StreamingJsonXmlConverter.Event.Content -> event.text
            is StreamingJsonXmlConverter.Event.Tag -> event.text
        }
    }

    @Test
    fun longParameterUsesOneContentEventPerInputBlock() {
        val body = "长参数<&>\"'和代码\n".repeat(8_000)
        val converter = StreamingJsonXmlConverter()
        val events = converter.feed(JSONObject().put("value", body).toString())
        assertEquals(3, events.size)
        assertEquals(1, events.filterIsInstance<StreamingJsonXmlConverter.Event.Content>().size)
        val escaped = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
        assertEquals("\n  <param name=\"value\">$escaped</param>", render(events))
        assertFalse(converter.hasUnfinishedParam())
    }

    @Test
    fun incompleteStringStillPublishesEachAvailableBlockImmediately() {
        val converter = StreamingJsonXmlConverter()
        val first = converter.feed("{\"value\":\"第一块")
        assertEquals("\n  <param name=\"value\">第一块", render(first))
        assertTrue(converter.hasUnfinishedParam())
        assertEquals("第二块", render(converter.feed("第二块")))
        assertEquals("</param>", render(converter.feed("\"}")))
        assertFalse(converter.hasUnfinishedParam())
    }

    @Test
    fun parameterTagsKeepTheirPositionBetweenAdjacentRuns() {
        val converter = StreamingJsonXmlConverter()
        val events = converter.feed("{\"first\":\"abc\",\"second\":{\"n\":1},\"last\":\"xyz\"}")
        assertEquals(9, events.size)
        assertEquals(
            "\n  <param name=\"first\">abc</param>\n  <param name=\"second\">{&quot;n&quot;:1}</param>\n  <param name=\"last\">xyz</param>",
            render(events),
        )
    }

    @Test
    fun escapesAndSurrogatePairsSurviveEveryPossibleFeedBoundary() {
        val input = "{\"value\":\"a\\n\\u4E2D\\uD83D\\uDE00<&>\\\"\\\\z\"}"
        val expected = "\n  <param name=\"value\">a\n中😀&lt;&amp;&gt;&quot;\\z</param>"
        for (boundary in 0..input.length) {
            val converter = StreamingJsonXmlConverter()
            val events = converter.feed(input.substring(0, boundary)) + converter.feed(input.substring(boundary))
            assertEquals("切分位置=$boundary", expected, render(events))
            assertFalse(converter.hasUnfinishedParam())
        }
    }

    @Test
    fun completionFlushDoesNotDuplicateAlreadyPublishedStringContent() {
        val converter = StreamingJsonXmlConverter()
        val events = converter.feed("{\"text\":\"published\",\"count\":12")
        assertEquals("\n  <param name=\"text\">published</param>\n  <param name=\"count\">", render(events))
        assertEquals("12</param>", render(converter.flush()))
        assertTrue(converter.flush().isEmpty())
    }
}
