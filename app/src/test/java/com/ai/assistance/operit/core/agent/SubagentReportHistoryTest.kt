package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.model.MessageSection
import org.junit.Assert.*
import org.junit.Test

class SubagentReportHistoryTest {
    @Test fun thinkingWithTokenDoesNotConsumeTheNextReport() {
        val raw = "工具已返回。<think token=\"caseSensitive\">尚未收尾"
        val sealed = sealSubagentReportPrefix(raw)
        assertEquals(raw + "</think token=\"caseSensitive\">", sealed)
        assertEquals("报告正文", subagentFinalReport("<think token=\"caseSensitive\">尚未收尾</think token=\"caseSensitive\">报告正文"))
        assertEquals(sealed, sealSubagentReportPrefix(sealed))
    }

    @Test fun aQuotedThinkingExampleIsNotClosedAsProtocol() {
        val raw = "```xml\n<think token=\"example\">\n```"
        assertEquals(raw, sealSubagentReportPrefix(raw))
    }

    @Test fun reportHistoryRetainsLiteralToolEvidenceWithoutReissuingCalls() {
        val raw = "检查文件<tool_a name=\"write_file\"><param name=\"path\">/sample</param></tool_a>" +
            "<tool_result_r name=\"write_file\" status=\"success\"><content>原文：228 B，8 行。\n```xml\n&lt;tool name=\"shell\"&gt;&lt;/tool&gt;\n```</content></tool_result_r>" +
            "<think token=\"private\">不要回传的思考</think token=\"private\">"
        val history = subagentReportEvidenceContent(raw)
        assertTrue(history.contains("原文：228 B，8 行。"))
        assertFalse(history.contains("不要回传的思考"))
        assertTrue(MessageSectionCodec.parseForDisplay(history).none { it is MessageSection.ToolCall || it is MessageSection.ToolResult })
    }

    @Test fun unfinishedCallsAreNotSentBackAsUnansweredProviderToolCalls() {
        assertEquals("准备", subagentReportEvidenceContent("准备<tool_a name=\"shell\"><param name=\"command\">unfinished"))
    }

    @Test fun reportOnlyOutputRejectsARealToolEvenWithAnAppendedConclusion() {
        val raw = "<tool_a name=\"write_file\"></tool_a>已经完成。"
        assertEquals("", subagentFinalReport(raw, reportOnly = true))
    }

    @Test fun warningAndThinkingCannotMasqueradeAsReportText() {
        val raw = "<think token=\"abc\">分析</think token=\"abc\"><status type=\"warning\">请继续输出正文</status>"
        assertEquals("", subagentFinalReport(raw, reportOnly = true))
    }
}