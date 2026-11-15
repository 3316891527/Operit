package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import org.junit.Assert.*
import org.junit.Test

class EditorSearchScopeTest {
    @Test fun literalMatchesAndReplacementStayInsideTheCapturedSelection() {
        val text = "one one one"
        val result = searchEditorText(text, "one", EditorSearchOptions(), "TWO", EditorSearchScope(4, 7))
        assertNull(result.error)
        assertEquals(listOf(4), result.matches.map { it.start })
        assertEquals("one TWO one", replaceEditorMatches(text, result.matches))
    }

    @Test fun aMatchCannotCrossEitherScopeBoundary() {
        assertTrue(searchEditorText("abcabc", "abc", EditorSearchOptions(), scope = EditorSearchScope(1, 5)).matches.isEmpty())
        assertTrue(searchEditorText("abcabc", "a.*c", EditorSearchOptions(regex = true), scope = EditorSearchScope(1, 5)).matches.isEmpty())
    }

    @Test fun wholeWordsUseTheOriginalNeighbouringCharacters() {
        val result = searchEditorText("testing test", "test", EditorSearchOptions(wholeWord = true), scope = EditorSearchScope(0, 4))
        assertTrue(result.matches.isEmpty())
    }

    @Test fun scopedRegexGroupsKeepAbsoluteOffsetsAndTheOutsideText() {
        val text = "12 34 56"
        val result = searchEditorText(text, "(\\d)(\\d)", EditorSearchOptions(regex = true), "${'$'}2${'$'}1", EditorSearchScope(3, 5))
        assertNull(result.error)
        assertEquals(listOf(3), result.matches.map { it.start })
        assertEquals("12 43 56", replaceEditorMatches(text, result.matches))
    }

    @Test fun zeroWidthScopeBoundariesInsertOnlyAtTheTwoCapturedEdges() {
        val result = searchEditorText("abcd", "^|${'$'}", EditorSearchOptions(regex = true), "_", EditorSearchScope(1, 3))
        assertEquals(listOf(1, 3), result.matches.map { it.start })
        assertEquals("a_bc_d", replaceEditorMatches("abcd", result.matches))
    }

    @Test fun insertingBeforeTheScopeMovesBothBoundariesByTheExactLength() {
        assertEquals(EditorSearchScope(6, 10), EditorSearchScope(4, 8).afterEdit(1, 1, 2))
    }

    @Test fun insertingAtEachEdgeIncludesTheNewTextWithoutMovingTheStart() {
        assertEquals(EditorSearchScope(4, 10), EditorSearchScope(4, 8).afterEdit(4, 4, 2))
        assertEquals(EditorSearchScope(4, 10), EditorSearchScope(4, 8).afterEdit(8, 8, 2))
    }

    @Test fun editsAfterTheScopeLeaveBothBoundariesUnchanged() {
        assertEquals(EditorSearchScope(4, 8), EditorSearchScope(4, 8).afterEdit(9, 9, 3))
        assertEquals(EditorSearchScope(4, 8), EditorSearchScope(4, 8).afterEdit(8, 10, 3))
    }

    @Test fun deletingTheEntireScopeKeepsAnEmptyScopeAtItsOriginalStart() {
        assertEquals(EditorSearchScope(4, 4), EditorSearchScope(4, 8).afterEdit(4, 8, 0))
        assertEquals(EditorSearchScope(4, 7), EditorSearchScope(4, 4).afterEdit(4, 4, 3))
    }

    @Test fun replacementsInsideAndBeforeTheScopeUpdateTheAppropriateBoundaries() {
        assertEquals(EditorSearchScope(4, 7), EditorSearchScope(4, 8).afterEdit(5, 7, 1))
        assertEquals(EditorSearchScope(2, 6), EditorSearchScope(4, 8).afterEdit(0, 3, 1))
    }
}
