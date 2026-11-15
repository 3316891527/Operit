package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import org.junit.Assert.*
import org.junit.Test

class EditorVisualLayoutTest {
    @Test fun wrappedRowsKeepOriginalOffsetsAndLogicalLineNumbers() {
        val layout = EditorVisualLayout("abcdefghij\n中文测试\n", 4, 1)
        assertEquals(listOf("abcd", "efgh", "ij", "中文", "测试", ""), layout.rows.map { layout.text.substring(it.start, it.end) })
        assertEquals(listOf(0, 0, 0, 1, 1, 2), layout.rows.map { it.logicalLine })
        assertEquals(listOf(true, false, false, true, false, true), layout.rows.map { it.first })
        assertEquals(1, layout.rowForOffset(4))
        assertEquals(0, layout.columnForOffset(4))
        assertEquals(4, layout.columnForOffset(4, 0))
    }
    @Test fun everySymbolBoundaryRoundTripsAcrossTabsCjkAndEmoji() {
        val text = "a\t中👨‍👩‍👧‍👦e\u0301🇨🇳尾"
        val layout = EditorVisualLayout(text, 5, 1)
        var offset = 0
        while (true) {
            val row = layout.rowForOffset(offset)
            val column = layout.columnForOffset(offset, row)
            assertEquals(offset, layout.offsetForColumn(row, column.toFloat()))
            if (offset == text.length) break
            offset = editorNextSymbolOffset(text, offset)
        }
        for (row in layout.rows) {
            assertFalse(row.start < text.length && Character.isLowSurrogate(text[row.start]))
            assertFalse(row.end < text.length && Character.isLowSurrogate(text[row.end]))
        }
    }
    @Test fun disabledWrapPreservesLogicalRowsAndEmptyFilesHaveOneRow() {
        val layout = EditorVisualLayout("abcdefghij\nlast", Int.MAX_VALUE, 1)
        assertEquals(2, layout.rows.size)
        assertEquals(1, EditorVisualLayout("", 1, 0).rows.size)
        assertEquals(0, EditorVisualLayout("", 1, 0).offsetForColumn(0, 100f))
    }
    @Test fun reflowOnlyChangesRowsAndKeepsTheOriginalText() {
        val original = "长行abcdef👋尾"
        val narrow = EditorVisualLayout(original, 3, 1)
        val wide = EditorVisualLayout(original, 9, 1)
        assertEquals(original, narrow.text)
        assertEquals(original, wide.text)
        assertTrue(narrow.rows.size > wide.rows.size)
    }
}
