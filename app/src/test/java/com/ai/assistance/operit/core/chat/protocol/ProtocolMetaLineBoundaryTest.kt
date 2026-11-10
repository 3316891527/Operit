package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolMetaLineBoundaryTest {
    private val providers = listOf(
        "openai:responses_reasoning", "openai:responses_output_item", "gemini:thought_signature",
    )

    private fun protocol(provider: String = providers.first(), payload: String = "opaque-payload") =
        "<meta provider=\"$provider\">$payload</meta>"

    private fun assertLiveProjection(raw: String, expected: String) {
        for (size in listOf(1, 2, 7, 37, raw.length).distinct()) {
            val filter = ProtocolMetaDisplayFilter()
            val live = raw.chunked(size).joinToString("") { filter.append(it) }
            assertEquals("chunk size $size", expected, live)
            assertEquals("completion must not release a buffered answer", "", filter.finish())
        }
    }

    @Test
    fun unmatchedTicksOnEarlierLinesDoNotDelayOrExposeLaterProtocol() {
        for (provider in providers) for (marker in listOf("`", "``")) {
            for (ending in listOf("\n", "\r\n", "\r")) {
                val prefix = "> 空值显示为 $marker，继续说明。${ending}> 下一步继续。$ending"
                assertLiveProjection(prefix + protocol(provider) + "本轮正文", prefix + "本轮正文")
            }
        }
    }

    @Test
    fun incompleteInlineExampleReleasesVisibleTextAtTheLineBreak() {
        for (provider in providers) for (marker in listOf("`", "``", "````")) {
            for (ending in listOf("\n", "\r\n", "\r")) {
                val prefix = "未闭合示例 $marker"
                assertLiveProjection(prefix + protocol(provider) + ending + "下一行正文", prefix + ending + "下一行正文")
            }
        }
    }

    @Test
    fun laterTicksCannotReclassifyEarlierProtocolAsAnExample() {
        val prefix = "> 未配对的 `` 说明\n"
        val tail = "\n下一段含 `合法代码`，最后还有 ``。"
        assertLiveProjection(prefix + protocol() + tail, prefix + tail)
    }

    @Test
    fun aClosingMarkerAtTheLineBreakPreservesTheValidExample() {
        for (marker in listOf("`", "``", "````")) {
            val example = "示例 $marker" + protocol(payload = "example") + marker + "\n"
            assertLiveProjection(example + protocol() + "正文", example + "正文")
        }
    }

    @Test
    fun fencedExamplesAfterAnUnmatchedInlineMarkerRemainExamples() {
        for (marker in listOf("```", "~~~")) {
            val prefix = "> 未配对的 `` 文本\n"
            val example = "$marker xml\n" + protocol(payload = "example") + "\n$marker\n"
            assertLiveProjection(prefix + example + protocol() + "正文", prefix + example + "正文")
        }
    }

    @Test
    fun everyTwoChunkSplitKeepsTheSameLiveProjection() {
        val prefix = "> 空值 ``，后续说明。\n"
        val raw = prefix + protocol() + "本轮结果\n`新的代码`"
        val expected = prefix + "本轮结果\n`新的代码`"
        for (cut in 0..raw.length) {
            val filter = ProtocolMetaDisplayFilter()
            val live = filter.append(raw.substring(0, cut)) + filter.append(raw.substring(cut))
            assertEquals("split $cut", expected, live)
            assertEquals("", filter.finish())
        }
    }

    @Test
    fun repeatedToolRoundsPublishTheirTextBeforeCompletion() {
        val filter = ProtocolMetaDisplayFilter()
        val prefix = "> 空值 ``，后续说明。\n"
        assertEquals(prefix, filter.append(prefix))
        repeat(61) { round ->
            val result = "<tool_result_R$round name=\"read_file\"><content>源码含未配对 ` 和 ```\n</content></tool_result_R$round>\n"
            assertEquals(result, filter.append(result))
            assertEquals("第 $round 轮正文\n", filter.append(protocol() + "第 $round 轮正文\n"))
        }
        assertEquals("最终正文 ``\n", filter.append("最终正文 ``\n"))
        assertEquals("", filter.finish())
    }

    @Test
    fun restoringBeforeALineBreakDoesNotRestoreCrossLineProtection() {
        val filter = ProtocolMetaDisplayFilter()
        val prefix = "未闭合示例 ``" + protocol()
        assertEquals("未闭合示例 ``", filter.append(prefix))
        val saved = filter.checkpoint()
        assertEquals("\n撤销正文", filter.append("\n撤销正文"))
        filter.restore(saved)
        assertEquals("\r\n正确正文", filter.append("\r\n正确正文"))
        assertEquals("", filter.finish())
    }

    @Test
    fun ordinaryAndUnknownMetaRemainVisibleAfterTheLineBoundary() {
        val prefix = "> 空值 ``\n"
        val ordinary = "<meta provider=\"custom\">example</meta>"
        assertLiveProjection(prefix + ordinary + protocol() + "正文", prefix + ordinary + "正文")
    }
}