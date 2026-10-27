package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.core.tools.PackageActivationErrorCode
import com.ai.assistance.operit.core.tools.PackageActivationErrorData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkupManagerFailureTest {
    @Test
    fun `activation failures appear once for every structured error code`() {
        val message = "激活包失败：缺少必需环境变量 GITHUB_TOKEN"
        for (code in PackageActivationErrorCode.values()) {
            val result = ToolResult(
                toolName = "use_package",
                success = false,
                result = PackageActivationErrorData(code, "github", message),
                error = message,
            )
            val markup = ConversationMarkupManager.formatToolResultForMessage(result)
            assertTrue(markup.contains("status=\"error\""))
            assertTrue(markup.contains("<error>$message</error>"))
            assertEquals(1, Regex(Regex.escape(message)).findAll(markup).count())
        }
    }

    @Test
    fun `identical messages are compared after existing whitespace normalization`() {
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("probe", false, StringResultData("  same error\n"), error = "same error"),
        )
        assertTrue(markup.contains("<error>same error</error>"))
    }

    @Test
    fun `terminal details survive alongside a different failure summary`() {
        val output = "stdout: complete\nstderr: detail\nexitCode=7"
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("terminal_exec", false, StringResultData(output), error = "command failed"),
        )
        assertTrue(markup.contains("<error>command failed\n\n$output</error>"))
    }

    @Test
    fun `details containing the summary are not discarded`() {
        val detail = "command failed\nstdout: useful output\nexitCode=7"
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("terminal_exec", false, StringResultData(detail), error = "command failed"),
        )
        assertTrue(markup.contains("command failed\n\n$detail"))
    }

    @Test
    fun `repeated successful output is unchanged`() {
        val output = "same line\nsame line"
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("terminal_exec", true, StringResultData(output)),
        )
        assertTrue(markup.contains("status=\"success\""))
        assertTrue(markup.contains("<content>$output</content>"))
    }
}
