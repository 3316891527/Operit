package com.ai.assistance.operit.ui.features.conversation.workspace.editor

internal data class EditorWrapSettings(val columns: Int, val words: Boolean, val tabWidth: Int)
internal class EditorWrappedLine(val settings: EditorWrapSettings, val ranges: List<IntRange>)
internal class EditorVisualNode(
    val source: EditorLineNode,
    val settings: EditorWrapSettings,
    val line: EditorWrappedLine,
    val left: EditorVisualNode?,
    val right: EditorVisualNode?,
) {
    val count: Int = line.ranges.size + (left?.count ?: 0) + (right?.count ?: 0)
}

/** 显示行通过累计长度定位，后续行偏移变化不会触发整份列表重建。 */
internal class EditorVisualRows(root: EditorLineNode, settings: EditorWrapSettings) : AbstractList<EditorVisualRow>() {
    var computedLines: Int = 0
        private set
    private val visualRoot = build(root, settings)
    override val size: Int get() = visualRoot.count

    private fun build(source: EditorLineNode, settings: EditorWrapSettings): EditorVisualNode {
        source.visualCache?.let { if (it.settings == settings) return it }
        val left = source.left?.let { build(it, settings) }
        val right = source.right?.let { build(it, settings) }
        val existing = source.line.wrapCache
        val wrapped = if (existing != null && existing.settings == settings) existing else {
            computedLines++
            wrap(source.line.text, settings).also { source.line.wrapCache = it }
        }
        return EditorVisualNode(source, settings, wrapped, left, right).also { source.visualCache = it }
    }

    override fun get(index: Int): EditorVisualRow {
        require(index in indices)
        var node = visualRoot
        var remaining = index
        var offset = 0
        var logicalLine = 0
        while (true) {
            val leftRows = node.left?.count ?: 0
            when {
                remaining < leftRows -> node = checkNotNull(node.left)
                remaining < leftRows + node.line.ranges.size -> {
                    val local = remaining - leftRows
                    val range = node.line.ranges[local]
                    val start = offset + (node.source.left?.span ?: 0)
                    return EditorVisualRow(start + range.first, start + range.last + 1,
                        logicalLine + (node.source.left?.count ?: 0), local == 0)
                }
                else -> {
                    remaining -= leftRows + node.line.ranges.size
                    offset += (node.source.left?.span ?: 0) + node.source.line.text.length + 1
                    logicalLine += (node.source.left?.count ?: 0) + 1
                    node = checkNotNull(node.right)
                }
            }
        }
    }

    private fun wrap(text: String, settings: EditorWrapSettings): EditorWrappedLine {
        val ranges = buildList {
            var start = 0
            var offset = 0
            var cells = 0
            var lastBreak = -1
            while (offset < text.length) {
                val advance = if (text[offset] == '\t') settings.tabWidth else editorCellWidth(text, offset)
                if (offset > start && cells.toLong() + advance > settings.columns.toLong()) {
                    val boundary = if (settings.words && lastBreak > start) lastBreak else offset
                    add(start until boundary)
                    start = boundary
                    offset = boundary
                    cells = 0
                    lastBreak = -1
                    continue
                }
                cells += advance
                val previous = offset
                offset = editorNextSymbolOffset(text, offset)
                if (text[previous].isWhitespace() || text[previous] in ",.;:()[]{}") lastBreak = offset
            }
            add(start until text.length)
        }
        return EditorWrappedLine(settings, ranges)
    }
}