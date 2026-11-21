package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** 编辑器偏好与浏览偏好一起持久化；软换行仍沿用既有字段，避免两处开关分离。 */
data class EditorSettings(
    val fontSize: Float = 14f,
    val lineSpacing: Float = 1.25f,
    val wordWrap: Boolean = true,
    val wrapWords: Boolean = true,
    val lineNumbers: Boolean = true,
    val fixedLineNumbers: Boolean = true,
    val wrapMarkers: Boolean = false,
    val whitespace: Boolean = false,
    val hideSingleSpaces: Boolean = true,
    val autoIndent: Boolean = true,
    val useTabs: Boolean = false,
    val indentWidth: Int = 4,
    val indentGuides: Boolean = true,
    val showSymbols: Boolean = true,
    val pinchZoom: Boolean = true,
    val syntaxHighlight: Boolean = true,
    val detectExternalChanges: Boolean = true,
    val symbols: String = "{ } ( ) [ ] = . , ; : \" ' + - * / _ < > & | ! ?",
) {
    val indent: String get() = if (useTabs) "\t" else " ".repeat(indentWidth)
    init {
        require(fontSize in 8f..40f && lineSpacing in 1f..2.5f && indentWidth in 1..8)
        require(symbols.length <= 512 && '\n' !in symbols && '\r' !in symbols)
    }
}

internal fun EditorSettings.toJson(): JSONObject = JSONObject().apply {
    put("wordWrap", wordWrap)
    put("fontSize", fontSize.toDouble()); put("lineSpacing", lineSpacing.toDouble())
    put("wrapWords", wrapWords); put("lineNumbers", lineNumbers); put("fixedLineNumbers", fixedLineNumbers)
    put("wrapMarkers", wrapMarkers); put("whitespace", whitespace); put("hideSingleSpaces", hideSingleSpaces)
    put("autoIndent", autoIndent); put("useTabs", useTabs); put("indentWidth", indentWidth)
    put("indentGuides", indentGuides); put("showSymbols", showSymbols); put("pinchZoom", pinchZoom)
    put("syntaxHighlight", syntaxHighlight); put("detectExternalChanges", detectExternalChanges); put("symbols", symbols)
}

internal fun editorSettingsFromJson(json: JSONObject): EditorSettings = EditorSettings(
    fontSize = json.getDouble("fontSize").toFloat(), lineSpacing = json.getDouble("lineSpacing").toFloat(), wordWrap = json.getBoolean("wordWrap"),
    wrapWords = json.getBoolean("wrapWords"), lineNumbers = json.getBoolean("lineNumbers"), fixedLineNumbers = json.getBoolean("fixedLineNumbers"),
    wrapMarkers = json.getBoolean("wrapMarkers"), whitespace = json.getBoolean("whitespace"), hideSingleSpaces = json.getBoolean("hideSingleSpaces"),
    autoIndent = json.getBoolean("autoIndent"), useTabs = json.getBoolean("useTabs"), indentWidth = json.getInt("indentWidth"),
    indentGuides = json.getBoolean("indentGuides"), showSymbols = json.getBoolean("showSymbols"), pinchZoom = json.getBoolean("pinchZoom"),
    syntaxHighlight = json.getBoolean("syntaxHighlight"), detectExternalChanges = json.getBoolean("detectExternalChanges"), symbols = json.getString("symbols"),
)

data class EditorStatus(val canUndo: Boolean = false, val canRedo: Boolean = false, val selectionStart: Int = 0,
    val selectionEnd: Int = 0, val lines: Int = 1, val characters: Int = 0, val revision: Int = 0) {
    val hasSelection: Boolean get() = selectionStart != selectionEnd
}

/** 会话保留文档、撤销栈及文件连接归属，切换文件后也不能在另一后端溯源。 */
class EditorSession(text: String, internal val sourceBackend: String? = null) {
    // 文件首次打开从开头显示，已有会话继续保留阅读位置。
    internal val document = EditorDocument(text).also { it.collapseSelection(0) }
    internal var scrollX = 0f
    internal var scrollY = 0f
    internal var scale = 1f
    internal var appliedFontSize: Float? = null
    internal var searchScope by mutableStateOf<EditorSearchScope?>(null)
    var readOnly by mutableStateOf(false)
    var status by mutableStateOf(EditorStatus())
        internal set
}

enum class EditorAction { COPY_LINE, CUT_LINE, DELETE_LINE, CLEAR_LINE, DUPLICATE_LINE, UPPERCASE, LOWERCASE, INDENT, UNINDENT, SELECT_ALL }
