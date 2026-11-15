package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorOperationsTest {
    @Test fun deletingTheLastLineDoesNotLeaveItsSeparatorAndCanUndo() {
        val document = EditorDocument("a\nb")
        document.editLines(EditorAction.DELETE_LINE)
        assertEquals("a", document.textString())
        assertTrue(document.undo())
        assertEquals("a\nb", document.textString())
    }
    @Test fun clearingSelectedLinesPreservesTheirPositionsAndExcludesTheNextLine() {
        val document = EditorDocument("a\nb\nc")
        document.setSelection(0, 4)
        assertEquals("a\nb\n", document.selectedLinesText())
        document.editLines(EditorAction.CLEAR_LINE)
        assertEquals("\n\nc", document.textString())
        assertEquals(3, document.lineCount())
        document.undo()
        assertEquals("a\nb\nc", document.textString())
    }
    @Test fun reversedMultiLineIndentSelectionKeepsItsDirectionAndUsesOneUndoStep() {
        val document = EditorDocument("a\nb\nc")
        document.setSelection(4, 0)
        document.editLines(EditorAction.INDENT, "  ")
        assertEquals("  a\n  b\nc", document.textString())
        assertTrue(document.selectionStart > document.selectionEnd)
        assertTrue(document.undo())
        assertEquals("a\nb\nc", document.textString())
        assertFalse(document.canUndo())
    }
    @Test fun duplicatingAnEmptyLastLineCreatesExactlyOneExtraLine() {
        val document = EditorDocument("a\n")
        document.editLines(EditorAction.DUPLICATE_LINE)
        assertEquals("a\n\n", document.textString())
        document.undo()
        assertEquals("a\n", document.textString())
    }
    @Test fun expandingUppercaseKeepsSelectionDirectionAndIsUndoable() {
        val document = EditorDocument("ß")
        document.setSelection(1, 0)
        assertTrue(document.changeSelectionCase(true))
        assertEquals("SS", document.textString())
        assertEquals(2, document.selectionStart)
        assertEquals(0, document.selectionEnd)
        document.undo()
        assertEquals("ß", document.textString())
        document.collapseSelection(0)
        assertFalse(document.changeSelectionCase(true))
    }
    @Test fun newlineIndentUsesTheConfiguredTabAndDisablingItReplacesSelection() {
        val document = EditorDocument("\t{}")
        document.collapseSelection(2)
        document.insertNewlineWithIndent(true, "\t")
        assertEquals("\t{\n\t\t\n\t}", document.textString())
        assertEquals(5, document.selectionEnd)
        val plain = EditorDocument("  abc")
        plain.setSelection(3, 4)
        plain.insertNewlineWithIndent(false, "  ")
        assertEquals("  a\nc", plain.textString())
    }
    @Test fun composingAndCommittingChineseInputFormsOneUndoStep() {
        val document = EditorDocument("前后")
        document.collapseSelection(1)
        document.setComposingText("拼")
        document.setComposingText("拼音")
        document.replaceSelection("输入")
        assertEquals("前输入后", document.textString())
        assertTrue(document.undo())
        assertEquals("前后", document.textString())
        assertFalse(document.canUndo())
        assertTrue(document.redo())
        assertEquals("前输入后", document.textString())
    }
    @Test fun finishingCompositionBeforeSwitchingFilesStillCreatesAnUndoRecord() {
        val document = EditorDocument("a")
        document.setComposingText("中文")
        document.finishComposingText()
        assertEquals("a中文", document.textString())
        assertTrue(document.undo())
        assertEquals("a", document.textString())
    }
    @Test fun readOnlyBlocksHistoryImeAndToolbarMutationsWhileAllowingSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val native = NativeCodeEditor(instrumentation.targetContext)
            val session = EditorSession("abc")
            native.bindSession(session)
            native.setCompletionEnabled(false)
            native.insertSymbol("x")
            native.setReadOnly(true)
            native.undo()
            native.performEditorAction(EditorAction.DELETE_LINE)
            native.replaceEditorRange(0, 1, "z")
            native.getChildAt(0).onCreateInputConnection(EditorInfo())!!.commitText("坏", 1)
            assertEquals("abcx", native.getText())
            native.performEditorAction(EditorAction.SELECT_ALL)
            assertTrue(session.status.hasSelection)
            native.setReadOnly(false)
            native.undo()
            assertEquals("abc", native.getText())
            native.release()
        }
    }
    @Test fun switchingSessionsKeepsIndependentSelectionsAndUndoHistories() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val native = NativeCodeEditor(instrumentation.targetContext)
            val first = EditorSession("A")
            val second = EditorSession("B")
            native.bindSession(first)
            native.setCompletionEnabled(false)
            native.insertSymbol("x")
            native.bindSession(second)
            native.insertSymbol("y")
            native.bindSession(first)
            assertEquals("Ax", native.getText())
            native.undo()
            assertEquals("A", native.getText())
            native.bindSession(second)
            assertEquals("By", native.getText())
            native.undo()
            assertEquals("B", native.getText())
            native.release()
        }
    }
}
