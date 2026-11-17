package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorWordWrapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wrappingMovesTheCaretToAVisualRowWithoutChangingImeOffsetsOrUndo() {
        val original = "abcdefghij".repeat(7)
        val content = mutableStateOf(original)
        var editor: NativeCodeEditor? = null
        compose.setContent {
            MaterialTheme {
                CodeEditor(content.value, "text", "android:/wrap.txt", fileName = "wrap.txt", onCodeChange = { content.value = it },
                    modifier = Modifier.size(260.dp, 320.dp), enableCompletion = false, editorRef = { editor = it })
            }
        }
        compose.runOnIdle {
            val native = editor!!
            val connection = native.getChildAt(0).onCreateInputConnection(EditorInfo())!!
            native.setWordWrap(false)
            connection.setSelection(original.length, original.length)
            val unwrapped = native.getCursorScreenPosition()
            native.setWordWrap(true)
            val wrapped = native.getCursorScreenPosition()
            assertTrue(wrapped.y > unwrapped.y)
            assertEquals(original, native.getText())
            assertTrue(connection.commitText("尾", 1))
            assertEquals(original + "尾", content.value)
            native.undo()
            assertEquals(original, content.value)
        }
    }

    @Test fun logicalLineLinksAndCompositionRemainStableAfterLongWrappedLines() {
        val original = "中abc👋\t".repeat(25) + "\nTARGET"
        val content = mutableStateOf(original)
        var editor: NativeCodeEditor? = null
        compose.setContent {
            MaterialTheme {
                CodeEditor(content.value, "text", "android:/line.txt", fileName = "line.txt", onCodeChange = { content.value = it },
                    initialLine = 2, enableCompletion = false, editorRef = { editor = it })
            }
        }
        compose.runOnIdle {
            val connection = editor!!.getChildAt(0).onCreateInputConnection(EditorInfo())!!
            assertTrue(connection.setComposingText("输入", 1))
            assertTrue(connection.finishComposingText())
            assertEquals(original.replace("TARGET", "输入TARGET"), content.value)
        }
    }
}
