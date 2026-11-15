package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorEmptySelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyDocumentLongPressShowsOnlySelectAllAndPasteAndCanInsertClipboardText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = EditorSession("")
        var editor: NativeCodeEditor? = null
        compose.setContent {
            MaterialTheme {
                CodeEditor("", "text", "empty", "empty.txt", session = session, editorRef = { editor = it })
            }
        }
        compose.runOnIdle {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("test", "粘贴内容"))
        }
        longPress { checkNotNull(editor) }
        compose.onNodeWithContentDescription(context.getString(android.R.string.selectAll)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(android.R.string.paste)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(context.getString(android.R.string.copy)).assertCountEquals(0)
        compose.onNodeWithContentDescription(context.getString(android.R.string.paste)).performClick()
        compose.runOnIdle { assertEquals("粘贴内容", session.document.textString()) }
    }

    @Test fun blankMiddleLineCanSelectTheWholeDocumentWithoutSelectingTheNewlineFirst() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = EditorSession("first\n\nlast")
        var editor: NativeCodeEditor? = null
        compose.setContent {
            MaterialTheme {
                CodeEditor("first\n\nlast", "text", "blank-line", "blank.txt", session = session, editorRef = { editor = it })
            }
        }
        compose.runOnIdle { editor!!.goToLine(2) }
        longPress { checkNotNull(editor) }
        compose.runOnIdle {
            assertFalse(session.document.hasSelection())
            assertEquals(6, session.document.selectionEnd)
        }
        compose.onNodeWithContentDescription(context.getString(android.R.string.selectAll)).performClick()
        compose.runOnIdle {
            assertEquals(0, session.document.selectionStart)
            assertEquals(11, session.document.selectionEnd)
        }
        compose.onNodeWithContentDescription(context.getString(android.R.string.copy)).assertIsDisplayed()
    }

    private fun longPress(editor: () -> NativeCodeEditor) {
        val downTime = SystemClock.uptimeMillis()
        var x = 0f
        var y = 0f
        compose.runOnIdle {
            val view = editor()
            val point = view.getCursorScreenPosition()
            x = point.x.toFloat() + 2f
            y = (point.y - 2).coerceAtLeast(1).toFloat()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            try { view.dispatchTouchEvent(down) } finally { down.recycle() }
        }
        // 等待真实 Android GestureDetector 超时，不能仅推进 Compose 动画时钟。
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 120L)
        compose.runOnIdle {
            val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            try { editor().dispatchTouchEvent(up) } finally { up.recycle() }
        }
    }
}