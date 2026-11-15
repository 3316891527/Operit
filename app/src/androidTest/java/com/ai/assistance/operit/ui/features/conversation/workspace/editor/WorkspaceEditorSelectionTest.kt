package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorSelectionTest {
    @Test fun undoRestoresExactScopeAfterDeletingRepeatedTextBeforeItsStart() {
        val document = EditorDocument("aaaa")
        document.searchScope = EditorSearchScope(1, 3)
        document.replaceRange(0, 1, "")
        assertEquals(EditorSearchScope(0, 2), document.searchScope)
        assertTrue(document.undo())
        assertEquals(EditorSearchScope(1, 3), document.searchScope)
        assertTrue(document.redo())
        assertEquals(EditorSearchScope(0, 2), document.searchScope)
    }

    @Test fun replacingTheScopeIsOneUndoStepAndKeepsOutsideText() {
        val document = EditorDocument("a a a")
        document.searchScope = EditorSearchScope(2, 3)
        document.replaceRange(2, 3, "long")
        assertEquals("a long a", document.textString())
        assertEquals(EditorSearchScope(2, 6), document.searchScope)
        assertTrue(document.undo())
        assertEquals("a a a", document.textString())
        assertEquals(EditorSearchScope(2, 3), document.searchScope)
        assertFalse(document.canUndo())
    }

    @Test fun composingInputUpdatesTheScopeAndUndoRestoresTheOriginalCapture() {
        val document = EditorDocument("abcd")
        document.searchScope = EditorSearchScope(1, 3)
        document.collapseSelection(2)
        document.setComposingText("中")
        document.setComposingText("中文")
        document.replaceSelection("汉字")
        assertEquals(EditorSearchScope(1, 5), document.searchScope)
        assertTrue(document.undo())
        assertEquals("abcd", document.textString())
        assertEquals(EditorSearchScope(1, 3), document.searchScope)
    }

    @Test fun recapturingAScopeDoesNotRestoreAnUnrelatedOldRangeOnUndo() {
        val document = EditorDocument("abc")
        document.searchScope = EditorSearchScope(0, 1)
        document.replaceRange(0, 1, "AA")
        document.searchScope = EditorSearchScope(2, 4)
        document.undo()
        assertEquals(EditorSearchScope(1, 3), document.searchScope)
        document.searchScope = null
        document.redo()
        assertNull(document.searchScope)
    }

    @Test fun searchNavigationMovesTheRealCursorWithoutReplacingTheSearchScope() = withEditor("one two three") { editor, session ->
        session.document.searchScope = EditorSearchScope(0, 7)
        editor.setSearchMatches(listOf(4..6), 0)
        editor.revealActiveSearchMatch()
        assertEquals(4, session.status.selectionStart)
        assertEquals(4, session.status.selectionEnd)
        assertEquals(EditorSearchScope(0, 7), session.searchScope)
    }

    @Test fun customSelectionEventsStayOpenForSelectAllAndDismissWithoutCollapsingTheRange() = withEditor("abc") { editor, session ->
        var shown: EditorSelectionMenuState? = null
        editor.setSelectionMenuListener { shown = it }
        editor.performEditorAction(EditorAction.SELECT_ALL)
        assertEquals(0, shown?.start)
        assertEquals(3, shown?.end)
        editor.performSelectionAction(EditorSelectionAction.SELECT_ALL)
        assertNotNull(shown)
        editor.dismissSelectionMenu()
        assertNull(shown)
        assertTrue(session.status.hasSelection)
    }

    @Test fun readOnlySelectionActionsCannotCutOrPasteTheDocument() = withEditor("abc") { editor, session ->
        editor.setReadOnly(true)
        editor.performEditorAction(EditorAction.SELECT_ALL)
        editor.performSelectionAction(EditorSelectionAction.CUT)
        editor.performSelectionAction(EditorSelectionAction.PASTE)
        assertEquals("abc", editor.getText())
        assertTrue(session.status.hasSelection)
    }

    private fun withEditor(text: String, action: (NativeCodeEditor, EditorSession) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val editor = NativeCodeEditor(instrumentation.targetContext)
            try {
                val session = EditorSession(text)
                editor.bindSession(session)
                editor.setCompletionEnabled(false)
                editor.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
                editor.layout(0, 0, 640, 480)
                action(editor, session)
            } finally { editor.release() }
        }
    }
}
