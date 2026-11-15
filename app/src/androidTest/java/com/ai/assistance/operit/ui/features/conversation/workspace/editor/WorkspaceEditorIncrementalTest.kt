package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorIncrementalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multilineEditsUndoAndRedoKeepTheSameContentAndLineOffsets() {
        val document = EditorDocument("first\n中👋\nlast\n")
        val original = document.snapshot()
        val start = document.getLineStart(1)
        document.replaceRange(start, document.getLineStart(2), "新\n内容\n")
        assertEquals("first\n新\n内容\nlast\n", document.textString())
        assertEquals(5, document.lineCount())
        assertEquals(11, document.getLineStart(3))
        assertEquals("first\n中👋\nlast\n", original.toString())
        assertTrue(document.undo())
        assertEquals(original.toString(), document.textString())
        assertTrue(document.redo())
        assertEquals("first\n新\n内容\nlast\n", document.textString())
    }

    @Test fun composingInputUpdatesSnapshotsAndStillUndoesAsOneEdit() {
        val document = EditorDocument("前\n后")
        document.collapseSelection(2)
        val before = document.snapshot()
        document.setComposingText("拼")
        document.setComposingText("输入")
        document.replaceSelection("中文")
        assertEquals("前\n中文后", document.snapshot().toString())
        assertEquals("前\n后", before.toString())
        assertTrue(document.undo())
        assertEquals("前\n后", document.textString())
        assertFalse(document.canUndo())
    }

    @Test fun changingTabWidthUpdatesWidthWithoutEditingTheDocument() {
        val document = EditorDocument("\t中\nabc")
        val before = document.snapshot()
        val version = document.version
        document.tabWidth = 8
        assertEquals(10, document.maxLineCells)
        assertEquals(version, document.version)
        assertSame(before.lines.line(0), document.snapshot().lines.line(0))
        assertFalse(document.canUndo())
    }

    @Test fun aCapturedSaveVersionStaysUnchangedWhileEditingContinues() {
        val document = EditorDocument("saved")
        val saving = document.snapshot()
        document.insertTextAtCursor(" newer")
        assertEquals("saved", saving.toString())
        assertEquals("saved newer", document.snapshot().toString())
        assertTrue(saving.version != document.version)
        assertSame(saving.owner, document.snapshot().owner)
    }

    @Test fun sessionNotificationsDoNotRoundTripTheWholeStringOrResetTypedContent() {
        val session = EditorSession("seed")
        val revision = mutableIntStateOf(0)
        var strings = 0
        var editor: NativeCodeEditor? = null
        compose.setContent {
            MaterialTheme {
                CodeEditor("seed", "kotlin", "android:/session.kt", fileName = "session.kt", onCodeChange = { strings++ }, session = session,
                    onDocumentChange = { revision.intValue++ }, searchNavigationRequest = revision.intValue,
                    enableCompletion = false, editorRef = { editor = it })
            }
        }
        compose.runOnIdle {
            editor!!.insertSymbol("A")
            editor!!.insertSymbol("B")
        }
        compose.runOnIdle {
            assertEquals("seedAB", editor!!.getText())
            assertEquals(2, revision.intValue)
            assertEquals(0, strings)
            assertEquals(session.document.version, session.status.revision)
        }
    }

    @Test fun reloadingASessionKeepsTheNewDocumentAndCompositionNotifications() {
        val session = mutableStateOf(EditorSession("original"))
        val revision = mutableIntStateOf(0)
        var editor: NativeCodeEditor? = null
        compose.setContent {
            MaterialTheme {
                CodeEditor("original", "text", "android:/reload.txt", fileName = "reload.txt", session = session.value,
                    onDocumentChange = { revision.intValue++ }, searchNavigationRequest = revision.intValue,
                    enableCompletion = false, editorRef = { editor = it })
            }
        }
        compose.runOnIdle {
            editor!!.insertSymbol(" old")
            session.value = EditorSession("disk")
        }
        compose.runOnIdle {
            assertEquals("disk", editor!!.getText())
            val connection = editor!!.getChildAt(0).onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("中文", 1)
            connection.finishComposingText()
        }
        compose.runOnIdle {
            assertEquals("disk中文", editor!!.getText())
            assertEquals(2, revision.intValue)
            editor!!.undo()
            assertEquals("disk", editor!!.getText())
        }
    }
}