package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EditorTextMateIncrementalTest {
    private lateinit var resources: TextMateTestResources
    @Before fun prepare() { resources = TextMateTestResources() }
    @After fun release() { resources.close() }

    @Test fun aLocalEditOnlyTokenizesOneLineAndReusesFollowingOffsets() {
        val text = (0 until 2000).joinToString("\n") { "val item$it = 1" }
        val index = EditorLineIndex.fromText(text)
        val owner = Any()
        assertEquals(2000, highlight(index, 0, owner).computedLines)
        val offset = index.start(500) + index.line(500).text.length - 1
        val updated = index.replace(offset, offset + 1, "123")
        val result = highlight(updated, 1, owner)
        assertEquals(1, result.computedLines)
        assertEquals(EditorTokenKind.KEYWORD, result.kindAt(updated.start(1500)))
        assertEquals(EditorTokenKind.NUMBER, result.kindAt(offset + 2))
    }

    @Test fun removingABlockTerminatorPropagatesUntilTheNextTerminator() {
        val text = "/* open\nbody */\nval a = 1\n*/\nval b = 2"
        val owner = Any()
        val index = EditorLineIndex.fromText(text)
        val before = highlight(index, 0, owner)
        assertEquals(EditorTokenKind.KEYWORD, before.kindAt(index.start(2)))
        val marker = text.indexOf("*/")
        val updated = index.replace(marker, marker + 2, "")
        val after = highlight(updated, 1, owner)
        assertEquals(EditorTokenKind.COMMENT, after.kindAt(updated.start(2)))
        assertEquals(EditorTokenKind.KEYWORD, after.kindAt(updated.start(4)))
        assertTrue(after.computedLines in 2..3)
        assertEquals(EditorTokenKind.KEYWORD, before.kindAt(index.start(2)))
    }

    @Test fun multilineStringsKeepCommentMarkersAndPropagateChangedState() {
        val quote = "\"\"\""
        val text = "val a = $quote\n/* 字符串 */\n$quote\nval b = 1"
        val index = EditorLineIndex.fromText(text)
        val owner = Any()
        val before = highlight(index, 0, owner)
        assertEquals(EditorTokenKind.STRING, before.kindAt(index.start(1)))
        assertEquals(EditorTokenKind.KEYWORD, before.kindAt(index.start(3)))
        val close = index.start(2)
        val updated = index.replace(close, close + 3, "")
        val after = highlight(updated, 1, owner)
        assertEquals(EditorTokenKind.STRING, after.kindAt(updated.start(3)))
    }

    @Test fun nestedCommentsAndEscapedQuotesUseTheRealKotlinGrammar() {
        val text = "/* outer /* inner */ still comment */\nval a = \"a\\\"/*b\"\nval n = 0xFF"
        val index = EditorLineIndex.fromText(text)
        val result = highlight(index, 0, Any())
        assertEquals(EditorTokenKind.COMMENT, result.kindAt(text.indexOf("still")))
        assertEquals(EditorTokenKind.DEFAULT, result.kindAt(text.indexOf('\n')))
        assertEquals(EditorTokenKind.STRING, result.kindAt(text.indexOf("/*b")))
        assertEquals(EditorTokenKind.NUMBER, result.kindAt(text.lastIndexOf("FF")))
    }

    @Test fun cancellationDoesNotPublishAPartialTreeAndLaterRequestsFinish() {
        val text = (0 until 200).joinToString("\n") { "val value$it = 42" }
        val index = EditorLineIndex.fromText(text)
        val snapshot = EditorTextSnapshot(index, 0, Any())
        val rules = resources.rules("kotlin")
        var probes = 0
        assertNull(EditorTextMateHighlighter.highlight(snapshot, rules) { ++probes < 6 })
        val complete = checkNotNull(EditorTextMateHighlighter.highlight(snapshot, rules))
        assertEquals(EditorTokenKind.NUMBER, complete.kindAt(text.length - 1))
    }

    @Test fun languageIdentityAndPlainTextModeCannotReuseUnrelatedColors() {
        val first = EditorLineIndex.fromText("val a = 1")
        val before = highlight(first, 0, Any())
        val snapshot = EditorTextSnapshot(first, 0, Any())
        val changed = checkNotNull(EditorTextMateHighlighter.highlight(snapshot, resources.rules("python")))
        assertEquals(EditorTokenKind.KEYWORD, before.kindAt(0))
        assertNotEquals(EditorTokenKind.KEYWORD, changed.kindAt(0))
        val plain = checkNotNull(EditorTextMateHighlighter.highlight(snapshot, null))
        assertEquals(EditorTokenKind.DEFAULT, plain.kindAt(0))
        assertEquals(EditorTokenKind.DEFAULT, plain.kindAt(snapshot.length - 1))
        assertEquals(0, plain.computedLines)
    }

    @Test fun splittingAndMergingLinesKeepAnOldSnapshotAndNewAbsoluteOffsetsCorrect() {
        val original = "val a = 1\nval b = 2\nval c = 3\n"
        val index = EditorLineIndex.fromText(original)
        val owner = Any()
        val before = highlight(index, 0, owner)
        val split = index.replace(4, 4, "new\nval ")
        val after = highlight(split, 1, owner)
        assertEquals(EditorTokenKind.KEYWORD, after.kindAt(split.start(3)))
        assertEquals(EditorTokenKind.NUMBER, after.kindAt(split.start(3) + 8))
        val merged = split.replace(4, 12, "")
        val restored = highlight(merged, 2, owner)
        assertEquals(EditorTokenKind.KEYWORD, restored.kindAt(merged.start(2)))
        assertEquals(EditorTokenKind.KEYWORD, before.kindAt(index.start(2)))
        assertEquals(original, before.text.toString())
    }

    private fun highlight(index: EditorLineIndex, version: Int, owner: Any): HighlightSnapshot =
        checkNotNull(EditorTextMateHighlighter.highlight(EditorTextSnapshot(index, version, owner), resources.rules("kotlin")))
}
