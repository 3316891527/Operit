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
        assertEquals("先检查 **原始数据**。 再读取工具结果。", waifuThinkingPreview(content))
    }

    @Test
    fun unfinishedThinkingStillHasPreview() {
        assertEquals("正在检查后续步骤", waifuThinkingPreview("<thinking>正在检查后续步骤"))
    }

    @Test
    fun previewRespectsTokenBoundaries() {
        val content = ThinkingMarkup.openTag("token-A") + "保留普通 </think> 字样。" + ThinkingMarkup.closeTag("token-A")
        assertEquals("保留普通 </think> 字样。", waifuThinkingPreview(content))
    }

    @Test
    fun longPreviewIsBoundedWithoutSplittingUnicodeCodePoints() {
        val preview = waifuThinkingPreview("<think>" + "😀".repeat(300) + "</think>")
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 243)
        assertFalse(preview.dropLast(1).last().isHighSurrogate())
        assertFalse(preview.contains('\n'))
    }
}