package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import org.junit.Assert.*
import org.junit.Test

class EditorSearchTest {
    @Test fun defaultsSearchLiterallyWithoutCaseSensitivity() {
        val result = searchEditorText("Ab.c abxc AB.C", "ab.c", EditorSearchOptions())
        assertNull(result.error)
        assertEquals(listOf(0, 10), result.matches.map { it.start })
    }
    @Test fun caseSensitivityAndUnicodeWordBoundariesAreIndependent() {
        val text = "test testing _test test2 TEST café caféine"
        val whole = searchEditorText(text, "test", EditorSearchOptions(wholeWord = true))
        assertEquals(listOf("test", "TEST"), whole.matches.map { text.substring(it.start, it.end) })
        assertEquals(1, searchEditorText(text, "test", EditorSearchOptions(caseSensitive = true, wholeWord = true)).matches.size)
        assertEquals(1, searchEditorText(text, "café", EditorSearchOptions(wholeWord = true)).matches.size)
    }
    @Test fun literalReplacementKeepsDollarSignsAndBackslashes() {
        val text = "a a"
        val replacement = "${'$'}1\\path"
        val result = searchEditorText(text, "a", EditorSearchOptions(), replacement)
        assertEquals("$replacement $replacement", replaceEditorMatches(text, result.matches))
    }
    @Test fun regexGroupsExpandForEachAcceptedWholeWordMatch() {
        val text = "x12 12 34z 56"
        val result = searchEditorText(text, "(\\d)(\\d)", EditorSearchOptions(regex = true, wholeWord = true), "${'$'}2${'$'}1")
        assertNull(result.error)
        assertEquals("x12 21 34z 65", replaceEditorMatches(text, result.matches))
    }
    @Test fun invalidPatternAndReplacementReturnErrorsWithoutEdits() {
        assertNotNull(searchEditorText("abc", "[", EditorSearchOptions(regex = true)).error)
        val replacement = searchEditorText("a", "(a)", EditorSearchOptions(regex = true), "${'$'}9")
        assertNotNull(replacement.error)
        assertTrue(replacement.matches.isEmpty())
    }
    @Test fun zeroWidthMatchesAdvanceAndCanInsertAtTheEndOfAFile() {
        val result = searchEditorText("ab", "(?=b)|${'$'}", EditorSearchOptions(regex = true), "_")
        assertEquals(listOf(1, 2), result.matches.map { it.start })
        assertTrue(result.matches.all { it.start == it.end })
        assertEquals("a_b_", replaceEditorMatches("ab", result.matches))
    }
    @Test fun emptyQueriesAndNoMatchesPreserveTheText() {
        assertTrue(searchEditorText("text", "", EditorSearchOptions()).matches.isEmpty())
        assertEquals("text", replaceEditorMatches("text", searchEditorText("text", "missing", EditorSearchOptions()).matches))
    }
    @Test fun replacementsUseOriginalOffsetsAndDoNotSearchInsertedText() {
        val result = searchEditorText("aaa", "a", EditorSearchOptions(), "aa")
        assertEquals(3, result.matches.size)
        assertEquals("aaaaaa", replaceEditorMatches("aaa", result.matches))
    }
}
