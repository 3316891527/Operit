package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.core.chat.protocol.ThinkingMarkup
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage
import com.ai.assistance.operit.data.model.MessageSection
import org.junit.Assert.*
import org.junit.Test

class WaifuThinkingPresentationTest {
    @Test fun preservesThinkingBodyAndTokenBoundariesWithoutIncludingReplyText() {
        val body = "第一行\n**思考**，字面结束标签 </think> 仍在正文。"
        val source = "<think token=\"boundary\">$body</think token=\"boundary\">回答。"
        val snapshot = requireNotNull(WaifuThinkingPresentation.snapshot(source, true))
        val sections = MessageSectionCodec.parse(snapshot)
        assertEquals(listOf(MessageSection.Thinking(body)), sections)
        assertTrue(ThinkingMarkup.isClosed(snapshot))
        assertFalse(snapshot.contains("回答。"))
        assertFalse(WaifuThinkingPresentation.isInProgress(snapshot))
    }

    @Test fun openThinkingCanBeRenderedAndCancellationFinishesItsPresentation() {
        val source = "<thinking>正在思考\n- 保持 Markdown"
        val live = requireNotNull(WaifuThinkingPresentation.snapshot(source, false))
        val stopped = requireNotNull(WaifuThinkingPresentation.snapshot(source, true))
        assertTrue(WaifuThinkingPresentation.isInProgress(live))
        assertFalse(WaifuThinkingPresentation.isInProgress(stopped))
        assertEquals(ThinkingMarkup.body(live), ThinkingMarkup.body(stopped))
        assertFalse(live.contains("duration"))
        assertFalse(stopped.contains("duration"))
    }

    @Test fun codeAndToolResultExamplesAreNotInterpretedAsThinking() {
        val code = "```xml\n<think>代码示例</think>\n```"
        val tool = "<tool_result name=\"read_file\"><content><think>工具里的示例</think></content></tool_result>"
        assertNull(WaifuThinkingPresentation.snapshot(code + "\n" + tool, false))
        assertNull(WaifuThinkingPresentation.snapshot("普通回答。", true))
    }

    @Test fun multipleThinkingBlocksPreserveTheirOrderAndOriginalBodies() {
        val source = "<think>一</think>正文<thinking>二</thinking>后文"
        val snapshot = requireNotNull(WaifuThinkingPresentation.snapshot(source, true))
        assertEquals(listOf("一", "二"), MessageSectionCodec.parse(snapshot)
            .filterIsInstance<MessageSection.Thinking>().map { it.content })
        assertFalse(WaifuThinkingPresentation.isInProgress(snapshot))
    }

    @Test fun reloadStoresOnlyExistingThinkingSectionsWithoutRuntimeFlagsOrTiming() {
        val snapshot = requireNotNull(WaifuThinkingPresentation.snapshot("<think>原文</think>", true))
        val stored = MessageSectionStorage.encode(MessageSectionCodec.parse(snapshot))
        assertFalse(stored.contains("data-waifu-thinking-open"))
        assertFalse(stored.contains("duration"))
        assertEquals(listOf(MessageSection.Thinking("原文")), MessageSectionStorage.decode(stored))
    }

    @Test fun sourceAttributesCannotKeepACompletedThinkingBlockInProgress() {
        val source = "<think data-waifu-thinking-open=\"true\">原文</think>"
        val snapshot = requireNotNull(WaifuThinkingPresentation.snapshot(source, true))
        assertFalse(WaifuThinkingPresentation.isInProgress(snapshot))
        assertEquals("原文", ThinkingMarkup.body(snapshot))
    }
}
