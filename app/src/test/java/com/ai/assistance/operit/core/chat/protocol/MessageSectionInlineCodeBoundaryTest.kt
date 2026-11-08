package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.data.model.MessageSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MessageSectionInlineCodeBoundaryTest {
    private fun protocol(payload: String = "opaque-payload") =
        "<meta provider=\"openai:responses_reasoning\">$payload</meta>"

    @Test
    fun unmatchedTicksOnEarlierLinesDoNotSwallowToolsOrProtocol() {
        for (marker in listOf("`", "``")) for (ending in listOf("\n", "\r\n", "\r")) {
            val prefix = "> 空值为 $marker，继续说明。$ending"
            val tool = "<tool_R1 name=\"read_file\"><param name=\"path\">/example</param></tool_R1>"
            val result = "<tool_result_R1 name=\"read_file\"><content>源码 ` 和 ``</content></tool_result_R1>"
            val raw = prefix + tool + result + protocol() + "本轮正文 ``"
            val sections = MessageSectionCodec.parse(raw)
            assertEquals(1, sections.filterIsInstance<MessageSection.ToolCall>().size)
            assertEquals(1, sections.filterIsInstance<MessageSection.ToolResult>().size)
            assertEquals(1, sections.filterIsInstance<MessageSection.Protocol>().size)
            assertEquals(raw, MessageSectionCodec.render(sections))
            assertFalse(sections.filterIsInstance<MessageSection.Text>().any { it.content.contains("opaque-payload") })
        }
    }

    @Test
    fun ticksOnALaterLineCannotCloseAnEarlierInlineExample() {
        val raw = "未闭合 `` 文本\n" + protocol() + "下一段 `` 文本"
        val sections = MessageSectionCodec.parse(raw)
        assertEquals(1, sections.filterIsInstance<MessageSection.Protocol>().size)
        assertEquals(raw, MessageSectionCodec.render(sections))
    }

    @Test
    fun validSingleLineExamplesRemainText() {
        for (marker in listOf("`", "``", "````")) {
            val example = "示例 $marker" + protocol("example") + marker + "\n"
            val raw = example + protocol() + "正文"
            val sections = MessageSectionCodec.parse(raw)
            assertEquals(1, sections.filterIsInstance<MessageSection.Protocol>().size)
            assertEquals("opaque-payload", sections.filterIsInstance<MessageSection.Protocol>().single().payload)
            assertEquals(example, (sections.first() as MessageSection.Text).content)
            assertEquals(raw, MessageSectionCodec.render(sections))
        }
    }

    @Test
    fun fencedExamplesRemainTextAfterAnUnmatchedInlineMarker() {
        for (marker in listOf("```", "~~~")) {
            val example = "> 空值 ``，继续\n$marker xml\n" + protocol("example") + "\n$marker\n"
            val raw = example + protocol() + "正文"
            val sections = MessageSectionCodec.parse(raw)
            assertEquals(1, sections.filterIsInstance<MessageSection.Protocol>().size)
            assertEquals(example, (sections.first() as MessageSection.Text).content)
            assertEquals(raw, MessageSectionCodec.render(sections))
        }
    }

    @Test
    fun codeMarkersInsideAnActualToolResultDoNotAffectLaterSections() {
        val result = "<tool_result_R1 name=\"read_file\"><content>``\n```xml\n源码</content></tool_result_R1>"
        val raw = result + protocol() + "正常正文"
        val sections = MessageSectionCodec.parse(raw)
        assertEquals(1, sections.filterIsInstance<MessageSection.ToolResult>().size)
        assertEquals(1, sections.filterIsInstance<MessageSection.Protocol>().size)
        assertEquals("正常正文", (sections.last() as MessageSection.Text).content)
        assertEquals(raw, MessageSectionCodec.render(sections))
    }

    @Test
    fun repeatedRoundsAfterEmptyTicksKeepAllProtocolStructured() {
        val raw = buildString {
            append("> 空值转成 ``，继续说明。\n")
            repeat(61) { round ->
                append("<tool_result_R$round name=\"read_file\"><content>源码带未配对 ` 和 ```\n</content></tool_result_R$round>\n")
                append(protocol())
                append("第 $round 轮正文\n")
            }
            append("最终正文 ``\n")
        }
        val sections = MessageSectionCodec.parse(raw)
        assertEquals(61, sections.filterIsInstance<MessageSection.ToolResult>().size)
        assertEquals(61, sections.filterIsInstance<MessageSection.Protocol>().size)
        assertFalse(sections.filterIsInstance<MessageSection.Text>().any { it.content.contains("opaque-payload") })
        assertEquals(raw, MessageSectionCodec.render(sections))
    }
}