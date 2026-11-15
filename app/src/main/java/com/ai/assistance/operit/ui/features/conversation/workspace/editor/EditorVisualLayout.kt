package com.ai.assistance.operit.ui.features.conversation.workspace.editor

/** 显示行只记录原文区间，换行不修改文档、撤销历史或一基逻辑行号。 */
internal data class EditorVisualRow(
    val start: Int,
    val end: Int,
    val logicalLine: Int,
    val first: Boolean,
)

/** 不可变快照可交给绘制线程，坐标计算与绘制使用相同的符号和单元格边界。 */
internal class EditorVisualLayout(val text: CharSequence, val columns: Int, val version: Int, val wrapWords: Boolean = false, val tabWidth: Int = 4) {
    private val source = if (text is EditorTextSnapshot) text else
        EditorTextSnapshot(EditorLineIndex.fromText(text.toString(), tabWidth), version, Any())
    val rows = EditorVisualRows(source.lines.root, EditorWrapSettings(columns, wrapWords, tabWidth))

    fun cellWidth(offset: Int, end: Int = text.length): Int = if (text[offset] == '\t') tabWidth else editorCellWidth(text, offset, end)

    fun rowForOffset(offset: Int): Int {
        val safe = offset.coerceIn(0, text.length)
        var low = 0
        var high = rows.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (rows[middle].start <= safe) low = middle + 1 else high = middle - 1
        }
        return high.coerceAtLeast(0)
    }

    fun columnForOffset(offset: Int, rowIndex: Int = rowForOffset(offset)): Int {
        val row = rows[rowIndex]
        val end = offset.coerceIn(row.start, row.end)
        var index = row.start
        var cells = 0
        while (index < end) {
            val next = editorNextSymbolOffset(text, index, row.end)
            if (next > end) break
            cells += cellWidth(index, row.end)
            index = next
        }
        return cells
    }

    fun offsetForColumn(rowIndex: Int, column: Float): Int {
        val row = rows[rowIndex.coerceIn(0, rows.lastIndex)]
        var cells = 0
        var index = row.start
        while (index < row.end) {
            val next = editorNextSymbolOffset(text, index, row.end)
            val advance = cellWidth(index, row.end)
            if (column < cells + advance / 2f) return index
            if (column < cells + advance) return next
            cells += advance
            index = next
        }
        return row.end
    }
}
