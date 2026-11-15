package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import org.junit.Assert.*
import org.junit.Test

class EditorIncrementalLayoutTest {
    @Test fun editingOneOfTwoThousandLinesOnlyReflowsThatLine() {
        val text = (0 until 2000).joinToString("\n") { "line $it abcdef" }
        val index = EditorLineIndex.fromText(text)
        val owner = Any()
        val before = EditorVisualLayout(EditorTextSnapshot(index, 0, owner), 7, 0, wrapWords = true)
        assertEquals(2000, before.rows.computedLines)
        val offset = index.start(1000) + 2
        val updated = index.replace(offset, offset + 1, "中👋")
        val after = EditorVisualLayout(EditorTextSnapshot(updated, 1, owner), 7, 1, wrapWords = true)
        assertEquals(1, after.rows.computedLines)
        val fresh = EditorVisualLayout(text.replaceRange(offset, offset + 1, "中👋"), 7, 1, wrapWords = true)
        assertEquals(fresh.rows.toList(), after.rows.toList())
    }

    @Test fun insertingNewlinesUpdatesFollowingOffsetsWithoutReflowingTheirContent() {
        val index = EditorLineIndex.fromText("abcdef\n尾\nlast\n")
        val owner = Any()
        val before = EditorVisualLayout(EditorTextSnapshot(index, 0, owner), 3, 0)
        assertEquals(4, before.rows.computedLines)
        val afterIndex = index.replace(3, 3, "\n")
        val after = EditorVisualLayout(EditorTextSnapshot(afterIndex, 1, owner), 3, 1)
        assertEquals(2, after.rows.computedLines)
        assertEquals(listOf("abc", "def", "尾", "las", "t", ""), after.rows.map { after.text.substring(it.start, it.end) })
        assertEquals(listOf(0, 1, 2, 3, 3, 4), after.rows.map { it.logicalLine })
        assertSame(index.line(2), afterIndex.line(3))
    }

    @Test fun layoutChangesInvalidateTheRelevantCacheButKeepTheOriginalText() {
        val owner = Any()
        val index = EditorLineIndex.fromText("\t中文\nalpha beta\n")
        val snapshot = EditorTextSnapshot(index, 0, owner)
        EditorVisualLayout(snapshot, 4, 0, tabWidth = 2)
        val same = EditorVisualLayout(snapshot, 4, 0, tabWidth = 2)
        assertEquals(0, same.rows.computedLines)
        val changed = EditorVisualLayout(snapshot, 9, 0, wrapWords = true, tabWidth = 4)
        assertEquals(3, changed.rows.computedLines)
        assertEquals("\t中文\nalpha beta\n", changed.text.toString())
    }

    @Test fun cachedRowsKeepGraphemeOffsetsAfterEditsBeforeTheVisibleRow() {
        val text = "head\n👨‍👩‍👧‍👦e\u0301🇨🇳尾"
        val index = EditorLineIndex.fromText(text)
        val owner = Any()
        EditorVisualLayout(EditorTextSnapshot(index, 0, owner), 4, 0)
        val after = EditorVisualLayout(EditorTextSnapshot(index.replace(0, 0, "中\n"), 1, owner), 4, 1)
        assertEquals(2, after.rows.computedLines)
        var offset = after.text.toString().indexOf("👨")
        while (true) {
            val row = after.rowForOffset(offset)
            assertEquals(offset, after.offsetForColumn(row, after.columnForOffset(offset, row).toFloat()))
            if (offset == after.text.length) break
            offset = editorNextSymbolOffset(after.text, offset)
        }
    }
}