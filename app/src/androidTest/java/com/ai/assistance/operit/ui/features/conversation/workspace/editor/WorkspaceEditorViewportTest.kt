package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorViewportTest {
    @Test
    fun resizingAndKeyboardPaddingPreserveTheBrowsedPositionWithAnOffscreenCaret() = withEditor { view, session ->
        assertTrue(view.hasFocus())
        assertTrue(view.getCursorScreenPosition().y < 0)
        val originalPoint = view.getCursorScreenPosition()
        // 模拟搜索面板展开、收起以及键盘避让变化，不回到留在文件开头的光标。
        for (height in listOf(720, 640, 480, 320, 480, 640, 800)) {
            resize(view, height)
            assertEquals(originalPoint, view.getCursorScreenPosition())
        }
        view.setViewportBottomPadding(96)
        assertEquals(originalPoint, view.getCursorScreenPosition())
        view.setViewportBottomPadding(0)
        assertEquals(originalPoint, view.getCursorScreenPosition())
        val beforeInput = session.document.textString()
        assertEquals(EditorSearchScope(0, beforeInput.length), session.document.searchScope)
        // 继续输入仍应主动定位到真实插入点，搜索范围随插入正常扩展。
        assertTrue(view.onCreateInputConnection(EditorInfo()).commitText("输入", 1))
        assertTrue(view.getCursorScreenPosition().y > 0)
        assertEquals("输入$beforeInput", session.document.textString())
    }

    @Test
    fun resizingDuringADragAndItsFlingDoesNotPullBackToTheCaret() = withEditor { view, session ->
        placeCaret(view, session, 320)
        val beforeDrag = view.getCursorScreenPosition().y
        val downTime = SystemClock.uptimeMillis() - 60L
        touch(view, downTime, downTime, MotionEvent.ACTION_DOWN, 120f)
        touch(view, downTime, downTime + 20L, MotionEvent.ACTION_MOVE, 300f)
        val duringDrag = view.getCursorScreenPosition()
        assertTrue(duringDrag.y > beforeDrag)
        resize(view, 400)
        assertEquals(duringDrag, view.getCursorScreenPosition())
        resize(view, 800)
        touch(view, downTime, downTime + 40L, MotionEvent.ACTION_MOVE, 380f)
        touch(view, downTime, downTime + 60L, MotionEvent.ACTION_UP, 400f)
        val duringFling = view.getCursorScreenPosition()
        assertTrue(duringFling.y > 400)
        assertTrue(duringFling.y < 800)
        // 在同一主线程回调中检查每次布局，避免等待惯性结束后漏掉帧间跳动。
        for (height in listOf(700, 560, 420, 300, 800)) {
            resize(view, height)
            assertEquals(duringFling, view.getCursorScreenPosition())
        }
        view.setViewportBottomPadding(96)
        assertEquals(duringFling, view.getCursorScreenPosition())
    }

    @Test
    fun keyboardChangesForAnotherFocusedFieldDoNotMoveTheEditor() = withEditor { view, session ->
        placeCaret(view, session, 620)
        val originalPoint = view.getCursorScreenPosition()
        // 查找框获得焦点后，编辑器的底部避让仍会随 IME 更新。
        view.isFocusable = false
        assertFalse(view.hasFocus())
        view.setViewportBottomPadding(240)
        assertEquals(originalPoint, view.getCursorScreenPosition())
        resize(view, 360)
        assertEquals(originalPoint, view.getCursorScreenPosition())
        view.setViewportBottomPadding(0)
        resize(view, 800)
        assertEquals(originalPoint, view.getCursorScreenPosition())
    }

    @Test
    fun shrinkingTheViewportAndKeyboardPaddingKeepAVisibleEditingCaretInView() = withEditor { view, session ->
        placeCaret(view, session, 620)
        assertTrue(view.hasFocus())
        assertTrue(view.getCursorScreenPosition().y > 560)
        for (height in listOf(700, 600, 520, 460)) {
            resize(view, height)
            val cursorY = view.getCursorScreenPosition().y
            assertTrue(cursorY > 0)
            assertTrue(cursorY < height)
        }
        val afterResize = view.getCursorScreenPosition().y
        view.setViewportBottomPadding(96)
        val afterPadding = view.getCursorScreenPosition().y
        assertTrue(afterPadding > 0)
        assertTrue(afterPadding <= 460 - 96)
        assertTrue(afterPadding < afterResize)
    }

    private fun withEditor(assertions: (CanvasCodeEditorView, EditorSession) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val view = CanvasCodeEditorView(context)
            val original = (1..1_000).joinToString("\n") { "line-$it" }
            val session = EditorSession(original).apply { scrollY = 4_000f }
            val scope = EditorSearchScope(0, original.length)
            session.document.searchScope = scope
            try {
                view.setEditorSettings(EditorSettings(wordWrap = false, syntaxHighlight = false))
                resize(view, 800)
                view.bindSession(session)
                assertTrue(view.requestFocus())
                assertions(view, session)
                assertEquals(EditorSearchScope(0, session.document.length()), session.document.searchScope)
            } finally {
                view.release()
            }
        }
    }

    private fun resize(view: CanvasCodeEditorView, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 800, height)
    }

    private fun placeCaret(view: CanvasCodeEditorView, session: EditorSession, targetY: Int) {
        val document = session.document
        document.collapseSelection(0)
        val firstBottom = view.getCursorScreenPosition().y
        document.collapseSelection(document.getLineStart(100))
        val lineHeight = (view.getCursorScreenPosition().y - firstBottom) / 100f
        val line = ((targetY - firstBottom) / lineHeight).roundToInt().coerceIn(0, document.lineCount() - 1)
        document.collapseSelection(document.getLineStart(line))
    }

    private fun touch(view: CanvasCodeEditorView, downTime: Long, time: Long, action: Int, y: Float) {
        val event = MotionEvent.obtain(downTime, time, action, 400f, y, 0)
        try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
    }
}
