package com.ai.assistance.operit.ui.features.conversation.chat.subagent

import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.model.MessageSection
import org.junit.Assert.*
import org.junit.Test

class SubagentTranscriptProjectionTest {
    private fun call(name: String, params: String = "", suffix: String = "c") =
        "<tool_$suffix name=\"$name\">$params</tool_$suffix>"
    private fun result(name: String, body: String, status: String = "success", suffix: String = "r") =
        "<tool_result_$suffix name=\"$name\" status=\"$status\"><content>$body</content></tool_result_$suffix>"
    private fun tools(raw: String, running: Boolean = false) =
        subagentTranscriptBlocks(raw, running).filterIsInstance<SubagentTranscriptBlock.Tool>()

    @Test
    fun proxyTargetIsVisibleBeforeTheCallAndParametersFinish() {
        val raw = "<tool_a name=\"package_proxy\"><param name=\"tool_name\">super_admin:terminal</param>" +
            "<param name=\"params\">{\"command\":\"wc -c"
        val tool = tools(raw, running = true).single()
        assertEquals("super_admin:terminal", tool.name)
        assertEquals("{\"command\":\"wc -c", tool.parameters)
        assertEquals(SubagentToolPhase.PREPARING, tool.phase)
        assertFalse(subagentTranscriptBlocks(raw, true).any { it is SubagentTranscriptBlock.Prose })
    }

    @Test
    fun proxyCallAndRealResultShareOneStableCard() {
        val raw = call("package_proxy", "<param name=\"tool_name\">super_admin:terminal</param><param name=\"params\">{}</param>")
        val before = tools(raw, true).single()
        val after = tools(raw + result("super_admin:terminal", "228 B\n8 行"), true).single()
        assertEquals(before.index, after.index)
        assertEquals("super_admin:terminal", after.name)
        assertEquals("228 B\n8 行", after.output)
        assertEquals(SubagentToolPhase.COMPLETED, after.phase)
    }

    @Test
    fun proxyAliasAndUnresolvedNamesDoNotExposeTheWrapper() {
        val tool = tools("<tool_a name=\"proxy\"><param name=\"tool_name\">github:terminal</param>", true).single()
        assertEquals("github:terminal", tool.name)
        assertEquals("", tools("<tool_a name=\"package_proxy\">", true).single().name)
    }

    @Test
    fun repeatedTargetsArePairedInCallOrder() {
        val raw = call("read_file", suffix = "a") + call("read_file", suffix = "b") +
            result("read_file", "first", suffix = "x") + result("read_file", "second", suffix = "y")
        val cards = tools(raw)
        assertEquals(2, cards.size)
        assertEquals(listOf("first", "second"), cards.map { it.output })
        assertTrue(cards.all { it.phase == SubagentToolPhase.COMPLETED })
    }

    @Test
    fun differentTargetsCannotStealEachOthersResult() {
        val raw = call("read_file") + call("list_files", suffix = "b") +
            result("list_files", "directory") + result("read_file", "file", suffix = "s")
        assertEquals(listOf("file", "directory"), tools(raw).map { it.output })
    }

    @Test
    fun aStandaloneResultRemainsVisible() {
        val tool = tools(result("super_admin:terminal", "finished")).single()
        assertEquals("super_admin:terminal", tool.name)
        assertEquals("finished", tool.output)
        assertEquals(SubagentToolPhase.COMPLETED, tool.phase)
    }

    @Test
    fun failuresEmptyOutputAndMissingResultsHaveDistinctStates() {
        assertEquals(SubagentToolPhase.FAILED, tools(call("read_file") + result("read_file", "denied", "error")).single().phase)
        val empty = tools(call("read_file") + result("read_file", "")).single()
        assertEquals("", empty.output)
        assertEquals(SubagentToolPhase.COMPLETED, empty.phase)
        val missing = tools(call("read_file")).single()
        assertNull(missing.output)
        assertEquals(SubagentToolPhase.STOPPED, missing.phase)
        assertEquals(SubagentToolPhase.RUNNING, tools(call("read_file"), true).single().phase)
    }

    @Test
    fun legacyResultsWithoutStatusDoNotPretendToBeSuccessfulOrFailed() {
        val raw = call("read_file") + "<tool_result name=\"read_file\">body</tool_result>"
        assertEquals(SubagentToolPhase.RETURNED, tools(raw).single().phase)
    }

    @Test
    fun incompleteResultDoesNotBecomeCompletedOnAnEarlySuccessAttribute() {
        val raw = call("read_file") + "<tool_result_a name=\"read_file\" status=\"success\"><content>streamed"
        val running = tools(raw, true).single()
        assertEquals(SubagentToolPhase.RUNNING, running.phase)
        assertEquals("streamed", running.output)
        assertEquals(SubagentToolPhase.STOPPED, tools(raw).single().phase)
    }

    @Test
    fun selfClosingXmlInsideAResultDoesNotCompleteTheResult() {
        val raw = call("read_file") + "<tool_result_a name=\"read_file\" status=\"success\"><content><svg/>"
        val tool = tools(raw, true).single()
        assertEquals("<svg/>", tool.output)
        assertEquals(SubagentToolPhase.RUNNING, tool.phase)
    }

    @Test
    fun codeSamplesRemainProseInsteadOfInventingToolExecutions() {
        val sample = call("delete_file")
        for (text in listOf("`$sample`", "```xml\n$sample\n```", "~~~xml\n$sample\n~~~")) {
            assertTrue(tools(text).isEmpty())
            assertEquals(text, subagentTranscriptBlocks(text, false).filterIsInstance<SubagentTranscriptBlock.Prose>().single().content)
        }
    }

    @Test
    fun reasoningAndProtocolPayloadNeverBecomeOrdinaryProse() {
        val raw = "<think token=\"abc\">private</think token=\"abc\">" +
            "<meta provider=\"test\">private payload</meta>final"
        val blocks = subagentTranscriptBlocks(raw, false)
        assertEquals("private", blocks.filterIsInstance<SubagentTranscriptBlock.Reasoning>().single().content)
        assertEquals(listOf("final"), blocks.filterIsInstance<SubagentTranscriptBlock.Prose>().map { it.content })
    }

    @Test
    fun incompleteProtocolPayloadIsNotShownAsXml() {
        assertTrue(subagentTranscriptBlocks("<meta provider=\"test\">private", true).isEmpty())
    }

    @Test
    fun toolTagsInsideResultContentAreEvidenceRatherThanExecutions() {
        val body = "literal " + call("delete_file")
        val cards = tools(call("read_file") + result("read_file", body))
        assertEquals(1, cards.size)
        assertEquals(body, cards.single().output)
    }

    @Test
    fun longOutputsAreNotTruncatedByTheDisplayProjection() {
        val body = "原文🙂\n".repeat(20000)
        assertEquals(body, tools(call("read_file") + result("read_file", body)).single().output)
    }

    @Test
    fun displayParsingDoesNotAlterThePersistedIncompleteProtocol() {
        val raw = "prefix<tool_a name=\"read_file\"><param name=\"path\">/tmp/part"
        assertTrue(MessageSectionCodec.parse(raw).single() is MessageSection.Text)
        val display = MessageSectionCodec.parseForDisplay(raw)
        assertTrue(display.last() is MessageSection.ToolCall)
        assertEquals(raw, MessageSectionCodec.render(display))
        assertEquals(raw, MessageSectionCodec.render(MessageSectionCodec.parse(raw)))
    }

    @Test
    fun completeProtocolHasTheSameSectionsAndRawBytesInBothParsers() {
        val raw = "before" + call("read_file", "<param name=\"path\">/tmp/file</param>") + result("read_file", "body") + "after"
        assertEquals(MessageSectionCodec.parse(raw), MessageSectionCodec.parseForDisplay(raw))
        assertEquals(raw, MessageSectionCodec.render(MessageSectionCodec.parseForDisplay(raw)))
    }

    @Test
    fun UnicodeQuotesAndWhitespaceFromEvidenceRemainExact() {
        val original = "  === 终端修改版 ===\n原内容\n"
        assertEquals(original, tools(call("read_file") + result("read_file", original)).single().output)
        assertEquals("command:   echo 原文  ", tools(call("shell", "<param name=\"command\">  echo 原文  </param>")).single().parameters)
    }

    @Test
    fun parameterEntitiesAreDecodedOnceForDisplay() {
        val quote = "&" + "quot;"
        val amp = "&" + "amp;"
        val parameter = "{${quote}command${quote}:${quote}echo a ${amp} b${quote}}"
        val raw = call("package_proxy", "<param name=\"tool_name\">super_admin:terminal</param><param name=\"params\">$parameter</param>")
        assertEquals("{\"command\":\"echo a & b\"}", tools(raw).single().parameters)
    }

    @Test
    fun resultPrefixesKeepTheSameCardUntilTheOuterResultCloses() {
        val invocation = call("read_file")
        val output = result("read_file", "原内容🙂\n")
        val bodyStart = output.indexOf("<content>") + "<content>".length
        val expectedIndex = tools(invocation, true).single().index
        for (length in bodyStart until output.length) {
            val tool = tools(invocation + output.take(length), true).single()
            assertEquals(expectedIndex, tool.index)
            assertEquals(SubagentToolPhase.RUNNING, tool.phase)
            assertFalse(tool.output.orEmpty().contains("tool_result"))
            assertFalse(tool.output.orEmpty().contains("</content>"))
        }
        assertEquals(SubagentToolPhase.COMPLETED, tools(invocation + output, true).single().phase)
    }

    @Test
    fun historicalProxyEventsResolveWithoutChangingStoredNames() {
        val names = listOf("use_package", "package_proxy", "package_proxy")
        val first = call("package_proxy", "<param name=\"tool_name\">super_admin:terminal</param>")
        val second = call("package_proxy", "<param name=\"tool_name\">github:terminal</param>")
        val labels = subagentToolEventLabels(names, listOf(call("use_package") + first, second))
        assertEquals(listOf("use_package", "super_admin:terminal", "github:terminal"), labels)
        assertEquals(listOf("use_package", "package_proxy", "package_proxy"), names)
    }
}
