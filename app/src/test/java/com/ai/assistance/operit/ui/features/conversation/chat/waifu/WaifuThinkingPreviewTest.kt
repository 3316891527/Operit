package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.core.chat.protocol.ThinkingMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaifuThinkingPreviewTest {
    @Test
    fun previewUsesOriginalOpeningAndCollapsesWhitespace() {
        val content = "<think>\n  先检查 **原始数据**。\n再读取工具结果。  </think>"
        assertEquals("先检查 **原始…", waifuThinkingPreview(content))
    }

    @Test
    fun unfinishedThinkingStillHasPreview() {
        assertEquals("正在检查后续步骤", waifuThinkingPreview("<thinking>正在检查后续步骤"))
        assertEquals("正在检查后续步骤…", waifuThinkingPreview("<thinking>正在检查后续步骤与结果"))
    }

    @Test
    fun previewRespectsTokenBoundaries() {
        val content = ThinkingMarkup.openTag("token-A") + "保留普通 </think> 字样。" + ThinkingMarkup.closeTag("token-A")
        assertEquals("保留普通 </t…", waifuThinkingPreview(content))
    }

    @Test
    fun longPreviewIsBoundedWithoutSplittingUnicodeCodePoints() {
        val preview = waifuThinkingPreview("<think>" + "😀".repeat(300) + "</think>")
        assertTrue(preview.endsWith("…"))
        assertEquals("😀".repeat(8) + "…", preview)
        assertEquals(9, preview.codePointCount(0, preview.length))
        assertFalse(preview.dropLast(1).last().isHighSurrogate())
        assertFalse(preview.contains('\n'))
    }

    @Test
    fun eightCharactersWithTrailingWhitespaceHaveNoEllipsis() {
        assertEquals("一二三四五六七八", waifuThinkingPreview("<think>一二三四五六七八 \n\t\u00A0</think>"))
    }

    @Test
    fun ninthCharacterAddsExactlyOneEllipsis() {
        assertEquals("一二三四五六七八…", waifuThinkingPreview("<think>一二三四五六七八九十</think>"))
    }

    @Test
    fun normalizedWhitespaceCountsTowardLimit() {
        assertEquals("一二 三四 五六…", waifuThinkingPreview("<think> \n一二\t 三四\u00A0\u2003五六七 </think>"))
        assertEquals("一二三四五六七 …", waifuThinkingPreview("<think>一二三四五六七\n 八</think>"))
    }

    @Test
    fun supplementaryCharacterAtBoundaryStaysWhole() {
        assertEquals("一二三四五六七😀…", waifuThinkingPreview("<think>一二三四五六七😀九</think>"))
        assertEquals("😀".repeat(8), waifuThinkingPreview("<think>" + "😀".repeat(8) + " \n</think>"))
    }

    @Test
    fun emptyOrShortBodyDoesNotGainEllipsis() {
        assertEquals("", waifuThinkingPreview("<think> \n\u00A0</think>"))
        assertEquals("短 思考", waifuThinkingPreview("<think> 短\n\t思考 </think>"))
        assertEquals("", waifuThinkingPreview("普通正文"))
    }

    @Test
    fun previewStopsAtMatchingTokenBeforeFollowingContent() {
        val content = ThinkingMarkup.openTag("token-A") + "短思考" + ThinkingMarkup.closeTag("token-A") + "后面的普通正文很长"
        assertEquals("短思考", waifuThinkingPreview(content))
    }
}
