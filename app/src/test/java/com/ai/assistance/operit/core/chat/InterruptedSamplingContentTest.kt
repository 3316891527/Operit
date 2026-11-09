package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.services.core.finalizeInterruptedStreamingContent
import com.ai.assistance.operit.util.stream.StreamLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InterruptedSamplingContentTest {
    @Test fun protectsTildeFencesAndLongerClosingFences() = runBlocking {
        val example = "~~~~xml\n<tool name=\"example\"/>\n~~~~~\n"
        assertEquals(example, retainBeforeUnexecutedTool(example + "<tool name=\"real\">"))
    }

    @Test fun protectsUnfinishedFencesAndMultiBacktickInlineCode() = runBlocking {
        val unfinished = "```xml\n<tool name=\"example\">"
        assertEquals(unfinished, retainBeforeUnexecutedTool(unfinished))
        val inline = "``<tool name=\"example\">`literal`</tool>``\n"
        assertEquals(inline, retainBeforeUnexecutedTool(inline + "<tool name=\"real\">"))
    }

    @Test fun codeMaskingPreservesOriginalUnicodeOffsets() = runBlocking {
        val prefix = "检查🙂\n```xml\n<tool name=\"example\"/>\n```\n继续\n"
        assertEquals(prefix, retainBeforeUnexecutedTool(prefix + "<tool_a name=\"real\">partial"))
    }

    @Test
    fun interruptedToolArgumentsAreNeverRepairedIntoHistory() = runBlocking {
        StreamLogger.setEnabled(false)
        try {
            for (tool in listOf(
                "<tool name=\"write_file\"><param name=\"path\">unfinished",
                "<tool_abcd name=\"write_file\"><param name=\"path\">x</param></tool_abcd>",
            )) {
                assertEquals("Answer\n", retainBeforeUnexecutedTool("Answer\n$tool"))
            }
            val examples = "```xml\n<tool name=\"example\"/>\n```\n`<tool name=\"inline\"/>`\n"
            assertEquals(examples, retainBeforeUnexecutedTool(examples))
            val reasoning = "<think>Example <tool name=\"example\"/></think>\n"
            assertEquals(reasoning, retainBeforeUnexecutedTool(reasoning))
        } finally {
            StreamLogger.setEnabled(true)
        }
    }

    @Test
    fun interruptedReasoningClosesWithoutChangingCodeExamples() {
        assertEquals("<think>reasoning</think>", finalizeInterruptedStreamingContent("<think>reasoning"))
        val example = "```xml\n<think>\n```"
        assertEquals(example, finalizeInterruptedStreamingContent(example))
        val completed = "<think>done</think>answer"
        assertEquals(completed, finalizeInterruptedStreamingContent(completed))
    }
}
