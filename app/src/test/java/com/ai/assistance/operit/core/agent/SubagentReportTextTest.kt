package com.ai.assistance.operit.core.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentReportTextTest {
    @Test
    fun reportKeepsLiteralUnicodeQuotesPathsAndMeasurements() {
        val expected = "原文：`=== 终端修改版 ===`\n`原内容`\n文件 `/sdcard/Download/test_file.txt`：228 B，8 行。"
        val raw = "准备检查<tool_a name=\"read_file\"></tool_a>" +
            "<tool_result_r name=\"read_file\" status=\"success\"><content>=== 终端修改版 ===\n原内容</content></tool_result_r>" + expected
        assertEquals(expected, subagentFinalReport(raw))
    }

    @Test
    fun reportDoesNotIncludeToolDocumentationOrReasoning() {
        val raw = "准备检查<tool_a name=\"use_package\"></tool_a>" +
            "<tool_result_r name=\"use_package\"><content>long package documentation</content></tool_result_r>" +
            "<think token=\"safe\">private reasoning</think token=\"safe\">" +
            "<meta provider=\"test\">private payload</meta>核验完成"
        assertEquals("核验完成", subagentFinalReport(raw))
    }

    @Test
    fun toolOnlyTurnCannotMasqueradeAsAFinalReport() {
        val raw = "准备检查<tool_a name=\"read_file\"></tool_a><tool_result_r name=\"read_file\">data</tool_result_r>"
        assertEquals("", subagentFinalReport(raw))
    }

    @Test
    fun codeExampleInsideFinalReportRemainsQuotedText() {
        val raw = "```xml\n<tool name=\"read_file\"></tool>\n```\n以上仅是示例。"
        assertEquals(raw, subagentFinalReport(raw))
    }

    @Test
    fun unfinishedToolCannotBeDeliveredAsASuccessfulFinalReport() {
        val raw = "准备调用<tool_a name=\"read_file\"><param name=\"path\">/unfinished"
        assertEquals("", subagentFinalReport(raw))
    }
}
