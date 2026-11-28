package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSummaryLayoutTest {
    @Test
    fun wideRowKeepsNameAndTimingBeforeParameterPreview() {
        val widths = toolSummaryWidths(400, 120, 140, 48, 8)
        assertEquals(ToolSummaryWidths(120, 140, 124, 8, 8), widths)
    }

    @Test
    fun narrowRowShrinksPreviewAndNameBeforeTiming() {
        val widths = toolSummaryWidths(180, 120, 100, 48, 8)
        assertEquals(100, widths.status)
        assertEquals(72, widths.title)
        assertEquals(0, widths.summary)
        assertEquals(0, widths.summaryGap)
    }

    @Test
    fun tinyAndLargeFontWidthsNeverOverlapOrExceedAvailableSpace() {
        // 大字体的状态宽度按实际测量传入；所有可见槽位仍在同一行可用范围内。
        for (available in listOf(0, 1, 16, 48, 72, 180, 400)) {
            for (status in listOf(0, 40, 140, 280)) {
                val widths = toolSummaryWidths(available, 120, status, 48, 8)
                val slots = listOf(widths.title, widths.status, widths.summary, widths.statusGap, widths.summaryGap)
                assertTrue(slots.all { it >= 0 })
                assertEquals(available, slots.sum())
            }
        }
    }

    @Test
    fun rowsWithoutTimingKeepExistingNameAndPreviewLayout() {
        assertEquals(ToolSummaryWidths(120, 0, 52, 0, 8), toolSummaryWidths(180, 120, 0, 48, 8))
    }
}