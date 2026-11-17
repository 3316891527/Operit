package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class EditorLineIndexTest {
    @Test fun boundariesIncludeEmptyFilesAndTrailingEmptyLines() {
        for (text in listOf("", "\n", "a\n", "\n\n", "中\t👋\n尾")) {
            verify(EditorLineIndex.fromText(text), text)
        }
    }

    @Test fun localEditsReuseDistantLinesAndOldSnapshotsStayImmutable() {
        val original = (0 until 2000).joinToString("\n") { "line $it" }
        val before = EditorLineIndex.fromText(original)
        val position = before.start(1000) + 2
        val after = before.replace(position, position + 1, "中文\n新行")
        assertSame(before.line(0), after.line(0))
        assertSame(before.line(1999), after.line(2000))
        assertEquals(2001, after.size)
        assertEquals(original, EditorTextSnapshot(before, 0, Any()).toString())
        verify(after, original.replaceRange(position, position + 1, "中文\n新行"))
    }

    @Test fun removingTheWidestLineUpdatesTheMaximumAndTabWidthKeepsLineContent() {
        val before = EditorLineIndex.fromText("123456789\n\t中\nend")
        assertEquals(9, before.maxCells)
        val shortened = before.replace(0, 9, "a")
        assertEquals(6, shortened.maxCells)
        val wideTabs = shortened.withTabWidth(8)
        assertEquals(10, wideTabs.maxCells)
        assertSame(shortened.line(1), wideTabs.line(1))
        assertEquals("a\n\t中\nend", EditorTextSnapshot(wideTabs, 2, Any()).toString())
    }

    @Test fun snapshotsShareOneMaterializedStringAndReturnExactSlices() {
        val text = "first\n中👨‍👩‍👧‍👦\t尾\nlast\n"
        val snapshot = EditorTextSnapshot(EditorLineIndex.fromText(text), 1, Any())
        for (start in 0..text.length) {
            val end = (start + 7).coerceAtMost(text.length)
            assertEquals(text.substring(start, end), snapshot.subSequence(start, end).toString())
        }
        val materialized = snapshot.toString()
        assertSame(materialized, snapshot.toString())
        assertEquals(text, materialized)
        for (index in text.indices) assertEquals(text[index], snapshot[index])
    }

    @Test fun repeatedInsertDeleteAndMergeKeepOffsetsAndAvlBalance() {
        val random = Random(614L)
        val insertions = listOf("", "\n", "abc", "中", "\t", "x\ny", "\n\n", "👋")
        var text = "head\nbody\n"
        var index = EditorLineIndex.fromText(text)
        repeat(500) {
            val start = random.nextInt(text.length + 1)
            val end = start + random.nextInt(text.length - start + 1)
            val insertion = insertions[random.nextInt(insertions.size)]
            index = index.replace(start, end, insertion)
            text = text.replaceRange(start, end, insertion)
            verify(index, text)
        }
    }

    private fun verify(index: EditorLineIndex, text: String) {
        val lines = text.split('\n')
        assertEquals(text.length, index.length)
        assertEquals(lines.size, index.size)
        assertEquals(text, EditorTextSnapshot(index, 0, Any()).toString())
        var start = 0
        lines.forEachIndexed { number, line ->
            assertEquals(line, index.line(number).text)
            assertEquals(start, index.start(number))
            start += line.length + 1
        }
        var expectedLine = 0
        for (offset in 0..text.length) {
            if (offset > 0 && text[offset - 1] == '\n') expectedLine++
            assertEquals(expectedLine, index.lineForOffset(offset))
        }
        fun balanced(node: EditorLineNode?): Int {
            if (node == null) return 0
            val left = balanced(node.left)
            val right = balanced(node.right)
            assertTrue(kotlin.math.abs(left - right) <= 1)
            assertEquals(1 + maxOf(left, right), node.height)
            assertEquals(1 + (node.left?.count ?: 0) + (node.right?.count ?: 0), node.count)
            return node.height
        }
        balanced(index.root)
    }
}