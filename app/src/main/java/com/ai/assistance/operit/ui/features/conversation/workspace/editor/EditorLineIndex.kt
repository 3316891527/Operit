package com.ai.assistance.operit.ui.features.conversation.workspace.editor

/** 行内容独立保存，布局和高亮的缓存随未修改行一起复用。 */
internal class EditorTextLine(val text: String) {
    private val baseCells: Int
    private val tabs: Int
    @Volatile internal var wrapCache: EditorWrappedLine? = null
    @Volatile internal var highlightCache: EditorTokenLine? = null

    init {
        var cells = 0
        var tabCount = 0
        var offset = 0
        while (offset < text.length) {
            if (text[offset] == '\t') tabCount++ else cells += editorCellWidth(text, offset)
            offset = editorNextSymbolOffset(text, offset)
        }
        baseCells = cells
        tabs = tabCount
    }

    fun cells(tabWidth: Int): Int = baseCells + tabs * tabWidth
}

/** 每个节点汇总长度、行数和最长行；编辑只复制 AVL 路径，不移动后续行的绝对偏移。 */
internal class EditorLineNode(
    val line: EditorTextLine,
    val left: EditorLineNode?,
    val right: EditorLineNode?,
    val tabWidth: Int,
) {
    val height: Int = 1 + maxOf(left?.height ?: 0, right?.height ?: 0)
    val count: Int = 1 + (left?.count ?: 0) + (right?.count ?: 0)
    val span: Int = line.text.length + 1 + (left?.span ?: 0) + (right?.span ?: 0)
    val maxCells: Int = maxOf(line.cells(tabWidth), left?.maxCells ?: 0, right?.maxCells ?: 0)
    @Volatile internal var visualCache: EditorVisualNode? = null
    @Volatile internal var highlightCache: EditorHighlightNode? = null
}

internal class EditorLineIndex private constructor(val root: EditorLineNode, val tabWidth: Int) {
    val length: Int get() = root.span - 1
    val size: Int get() = root.count
    val maxCells: Int get() = root.maxCells

    fun line(index: Int): EditorTextLine {
        require(index in 0 until size)
        var node = root
        var remaining = index
        while (true) {
            val leftCount = node.left?.count ?: 0
            when {
                remaining < leftCount -> node = checkNotNull(node.left)
                remaining == leftCount -> return node.line
                else -> { remaining -= leftCount + 1; node = checkNotNull(node.right) }
            }
        }
    }

    fun start(index: Int): Int {
        require(index in 0 until size)
        var node = root
        var remaining = index
        var offset = 0
        while (true) {
            val leftCount = node.left?.count ?: 0
            when {
                remaining < leftCount -> node = checkNotNull(node.left)
                remaining == leftCount -> return offset + (node.left?.span ?: 0)
                else -> {
                    offset += (node.left?.span ?: 0) + node.line.text.length + 1
                    remaining -= leftCount + 1
                    node = checkNotNull(node.right)
                }
            }
        }
    }

    fun lineForOffset(offset: Int): Int {
        var remaining = offset.coerceIn(0, length)
        var preceding = 0
        var node = root
        while (true) {
            val leftSpan = node.left?.span ?: 0
            when {
                remaining < leftSpan -> node = checkNotNull(node.left)
                remaining <= leftSpan + node.line.text.length -> return preceding + (node.left?.count ?: 0)
                else -> {
                    remaining -= leftSpan + node.line.text.length + 1
                    preceding += (node.left?.count ?: 0) + 1
                    node = checkNotNull(node.right)
                }
            }
        }
    }

    fun replace(start: Int, end: Int, inserted: String): EditorLineIndex {
        require(start in 0..length && end in start..length)
        val first = lineForOffset(start)
        val last = lineForOffset(end)
        val prefix = line(first).text.substring(0, start - this.start(first))
        val suffix = line(last).text.substring(end - this.start(last))
        val replacement = (prefix + inserted + suffix).split('\n').map(::EditorTextLine)
        val (before, rest) = split(root, first)
        val (_, after) = split(rest, last - first + 1)
        return EditorLineIndex(checkNotNull(concat(concat(before, build(replacement, 0, replacement.size, tabWidth)), after)), tabWidth)
    }

    fun withTabWidth(width: Int): EditorLineIndex {
        require(width in 1..8)
        if (width == tabWidth) return this
        fun update(node: EditorLineNode?): EditorLineNode? = node?.let {
            EditorLineNode(it.line, update(it.left), update(it.right), width)
        }
        return EditorLineIndex(checkNotNull(update(root)), width)
    }

    fun appendRange(output: StringBuilder, start: Int, end: Int) {
        require(start in 0..length && end in start..length)
        fun append(node: EditorLineNode?, from: Int, to: Int) {
            if (node == null || from >= to) return
            val leftSpan = node.left?.span ?: 0
            if (from < leftSpan) append(node.left, from, minOf(to, leftSpan))
            val localStart = (from - leftSpan).coerceAtLeast(0)
            val localEnd = (to - leftSpan).coerceAtMost(node.line.text.length)
            if (localStart < localEnd) output.append(node.line.text, localStart, localEnd)
            val newline = leftSpan + node.line.text.length
            if (from <= newline && to > newline) output.append('\n')
            if (to > newline + 1) append(node.right, (from - newline - 1).coerceAtLeast(0), to - newline - 1)
        }
        append(root, start, end)
    }

    private fun node(line: EditorTextLine, left: EditorLineNode?, right: EditorLineNode?): EditorLineNode =
        EditorLineNode(line, left, right, tabWidth)

    private fun join(left: EditorLineNode?, line: EditorTextLine, right: EditorLineNode?): EditorLineNode {
        val leftHeight = left?.height ?: 0
        val rightHeight = right?.height ?: 0
        return when {
            leftHeight > rightHeight + 1 -> {
                val branch = checkNotNull(left)
                balance(branch.line, branch.left, join(branch.right, line, right))
            }
            rightHeight > leftHeight + 1 -> {
                val branch = checkNotNull(right)
                balance(branch.line, join(left, line, branch.left), branch.right)
            }
            else -> node(line, left, right)
        }
    }

    private fun balance(line: EditorTextLine, left: EditorLineNode?, right: EditorLineNode?): EditorLineNode {
        val difference = (left?.height ?: 0) - (right?.height ?: 0)
        if (difference > 1) {
            val branch = checkNotNull(left)
            if ((branch.left?.height ?: 0) >= (branch.right?.height ?: 0)) {
                return node(branch.line, branch.left, node(line, branch.right, right))
            }
            val middle = checkNotNull(branch.right)
            return node(middle.line, node(branch.line, branch.left, middle.left), node(line, middle.right, right))
        }
        if (difference < -1) {
            val branch = checkNotNull(right)
            if ((branch.right?.height ?: 0) >= (branch.left?.height ?: 0)) {
                return node(branch.line, node(line, left, branch.left), branch.right)
            }
            val middle = checkNotNull(branch.left)
            return node(middle.line, node(line, left, middle.left), node(branch.line, middle.right, branch.right))
        }
        return node(line, left, right)
    }

    private fun split(root: EditorLineNode?, count: Int): Pair<EditorLineNode?, EditorLineNode?> {
        if (root == null) return null to null
        val leftCount = root.left?.count ?: 0
        return if (count <= leftCount) {
            val (before, after) = split(root.left, count)
            before to join(after, root.line, root.right)
        } else {
            val (before, after) = split(root.right, count - leftCount - 1)
            join(root.left, root.line, before) to after
        }
    }

    private fun concat(left: EditorLineNode?, right: EditorLineNode?): EditorLineNode? {
        if (left == null) return right
        if (right == null) return left
        val (first, rest) = removeFirst(right)
        return join(left, first, rest)
    }

    private fun removeFirst(root: EditorLineNode): Pair<EditorTextLine, EditorLineNode?> {
        val left = root.left ?: return root.line to root.right
        val (first, rest) = removeFirst(left)
        return first to join(rest, root.line, root.right)
    }

    companion object {
        fun fromText(text: String, tabWidth: Int = 4): EditorLineIndex {
            require(tabWidth in 1..8)
            val lines = text.split('\n').map(::EditorTextLine)
            return EditorLineIndex(checkNotNull(build(lines, 0, lines.size, tabWidth)), tabWidth)
        }

        private fun build(lines: List<EditorTextLine>, start: Int, end: Int, tabWidth: Int): EditorLineNode? {
            if (start >= end) return null
            val middle = (start + end) ushr 1
            return EditorLineNode(lines[middle], build(lines, start, middle, tabWidth), build(lines, middle + 1, end, tabWidth), tabWidth)
        }
    }
}

/** 快照共享未修改行，只有明确需要完整字符串时才拼接，并在本版本内复用结果。 */
internal class EditorTextSnapshot(val lines: EditorLineIndex, val version: Int, val owner: Any) : CharSequence {
    @Volatile private var materialized: String? = null
    private data class ReadLine(val start: Int, val text: String)
    @Volatile private var readLine: ReadLine? = null
    override val length: Int get() = lines.length
    override fun get(index: Int): Char {
        require(index in 0 until length)
        materialized?.let { return it[index] }
        val previous = readLine
        val window = if (previous != null && index in previous.start..previous.start + previous.text.length) previous else {
            val line = lines.lineForOffset(index)
            ReadLine(lines.start(line), lines.line(line).text).also { readLine = it }
        }
        val local = index - window.start
        return if (local == window.text.length) '\n' else window.text[local]
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex in 0..length && endIndex in startIndex..length)
        if (startIndex == 0 && endIndex == length) return this
        return buildString(endIndex - startIndex) { lines.appendRange(this, startIndex, endIndex) }
    }
    override fun toString(): String {
        materialized?.let { return it }
        return synchronized(this) {
            // buildString 内的 length 属于新建缓冲区，必须显式捕获源快照长度。
            val snapshotLength = length
            materialized ?: buildString(snapshotLength) {
                lines.appendRange(this, 0, snapshotLength)
            }.also { materialized = it }
        }
    }
}