package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceBrowserSettings
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.decodeWorkspaceBrowserSettings
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.encodeWorkspaceBrowserSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EditorSettingsLayoutTest {
    @Test fun wordWrappingMovesTheWholeNextWordAndPreservesOriginalOffsets() {
        val layout = EditorVisualLayout("alpha beta", 7, 1, wrapWords = true)
        assertEquals(listOf("alpha ", "beta"), layout.rows.map { layout.text.substring(it.start, it.end) })
        assertEquals(1, layout.rowForOffset(6))
        assertEquals(6, layout.offsetForColumn(1, 0f))
        assertEquals("alpha beta", layout.text)
    }
    @Test fun oversizedWordsStillWrapWithoutEmptyRowsOrLostCharacters() {
        val text = "abcdefghij"
        val layout = EditorVisualLayout(text, 3, 1, wrapWords = true)
        assertEquals(listOf("abc", "def", "ghi", "j"), layout.rows.map { text.substring(it.start, it.end) })
        assertTrue(layout.rows.all { it.start < it.end })
    }
    @Test fun tabWidthControlsBothLayoutAndCursorCoordinates() {
        val narrow = EditorVisualLayout("\tX", 4, 1, tabWidth = 2)
        val wide = EditorVisualLayout("\tX", 4, 1, tabWidth = 4)
        assertEquals(1, narrow.rows.size)
        assertEquals(2, wide.rows.size)
        assertEquals(2, narrow.columnForOffset(1))
        assertEquals(1, narrow.offsetForColumn(0, 2f))
        assertEquals(4, wide.columnForOffset(1, 0))
    }
    @Test fun editorPreferencesRoundTripWithoutLosingExistingBrowserPreferences() {
        val settings = WorkspaceBrowserSettings(wordWrap = false, showDotFiles = true,
            editor = EditorSettings(fontSize = 20f, lineSpacing = 1.8f, wordWrap = false, whitespace = true,
                useTabs = true, indentWidth = 2, showSymbols = false, symbols = "{ } →", syntaxHighlight = false))
        assertEquals(settings, decodeWorkspaceBrowserSettings(encodeWorkspaceBrowserSettings(settings)))
    }
    @Test fun preEditorPreferencesKeepTheirExistingWordWrapSetting() {
        val json = JSONObject(encodeWorkspaceBrowserSettings(WorkspaceBrowserSettings(wordWrap = false)))
        json.remove("editor")
        val settings = decodeWorkspaceBrowserSettings(json.toString())
        assertFalse(settings.wordWrap)
        assertEquals(14f, settings.editor.fontSize, 0f)
        assertEquals(4, settings.editor.indentWidth)
    }
    @Test fun invalidSettingsAreRejectedBeforeTheyReachTheCanvas() {
        assertThrows(IllegalArgumentException::class.java) { EditorSettings(fontSize = 0f) }
        assertThrows(IllegalArgumentException::class.java) { EditorSettings(indentWidth = 0) }
        assertThrows(IllegalArgumentException::class.java) { EditorSettings(symbols = "a\nb") }
    }
}
