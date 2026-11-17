package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceBrowserPreferences
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceBrowserSettings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.ai.assistance.operit.util.logging.AppLogger
import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion.CompletionItem
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion.CompletionPopup
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.EditorTheme
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.currentEditorTheme
import kotlin.math.roundToInt

@Composable
fun CodeEditor(
    code: String,
    language: String,
    fileKey: String,
    fileName: String,
    onCodeChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
    enableCompletion: Boolean = false,
    session: EditorSession? = null,
    editorRef: ((NativeCodeEditor?) -> Unit)? = null,
    onKeyCommand: ((EditorKeyCommand) -> Boolean)? = null,
    onFind: ((String?, EditorSearchScope?) -> Unit)? = null,
    searchMatches: List<IntRange> = emptyList(),
    activeSearchMatch: Int = -1,
    searchNavigationRequest: Int = 0,
    initialLine: Int? = null,
    initialLineRequest: Int = 0,
    onDocumentChange: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val preferences = remember(context) { WorkspaceBrowserPreferences(context) }
    val browserSettings by preferences.settings.collectAsState(initial = WorkspaceBrowserSettings())
    val settings = browserSettings.editor.copy(wordWrap = browserSettings.wordWrap)
    val theme = currentEditorTheme(settings.fontSize)
    val editorSession = session ?: remember(fileKey) { EditorSession(code) }
    val scope = rememberCoroutineScope()
    // 文件切换或显式链接请求重新定位，同一文件编辑不会重复消费行号。
    var pendingInitialLine by remember(fileKey, initialLine, initialLineRequest) { mutableStateOf(initialLine) }
    var revealedSearchRequest by remember { mutableStateOf(searchNavigationRequest) }
    val latestCode = rememberUpdatedState(code)
    val latestOnCodeChange = rememberUpdatedState(onCodeChange)
    val latestOnDocumentChange = rememberUpdatedState(onDocumentChange)
    val lastNotifiedRevision = remember(editorSession) { intArrayOf(editorSession.document.version) }
    val lastExternalCode = remember(editorSession) { arrayOf(code) }
    val latestEditorRef = rememberUpdatedState(editorRef)
    val latestOnKeyCommand = rememberUpdatedState(onKeyCommand)
    val latestOnFind = rememberUpdatedState(onFind)
    var selectionMenu by remember(fileKey) { mutableStateOf<EditorSelectionMenuState?>(null) }
    val density = LocalDensity.current
    val popupVerticalOffsetPx = with(density) { 6.dp.toPx().roundToInt() }
    val imeBottomInsetPx = WindowInsets.ime.getBottom(density)
    val keyboardAvoidancePaddingPx = with(density) {
        if (imeBottomInsetPx > 0) 24.dp.roundToPx() else 0
    }

    var completionItems by remember { mutableStateOf<List<CompletionItem>>(emptyList()) }
    var showCompletions by remember { mutableStateOf(false) }
    var popupOffset by remember { mutableStateOf(IntOffset.Zero) }
    var editorWindowOffset by remember { mutableStateOf(IntOffset.Zero) }
    val editorRefState = remember { mutableStateOf<NativeCodeEditor?>(null) }

    fun updatePopupAnchor(editor: NativeCodeEditor?) {
        val cursor = editor?.getCursorScreenPosition() ?: Point(0, 0)
        popupOffset =
            IntOffset(
                editorWindowOffset.x + cursor.x,
                editorWindowOffset.y + cursor.y + popupVerticalOffsetPx
            )
    }

    BackHandler(enabled = selectionMenu != null) { editorRefState.value?.dismissSelectionMenu() }
    Column(modifier = modifier.fillMaxSize().background(theme.background)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(theme.background)) {
                AndroidView(
                    modifier =
                        Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            editorWindowOffset =
                                IntOffset(position.x.roundToInt(), position.y.roundToInt())
                            if (showCompletions) {
                                updatePopupAnchor(editorRefState.value)
                            }
                        },
                    factory = { context ->
                        NativeCodeEditor(context).apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            editorRefState.value = this
                            latestEditorRef.value?.invoke(this)
                            setKeyCommandListener(latestOnKeyCommand.value)
                        }
                    },
                    update = { view ->
                        editorRefState.value = view
                        latestEditorRef.value?.invoke(view)
                        view.setKeyCommandListener(latestOnKeyCommand.value)
                        view.setSelectionMenuListener { selectionMenu = it }
                        view.bindSession(editorSession)
                        view.setEditorTheme(theme)
                        view.setEditorSettings(settings)
                        view.setFontSizeListener { size -> scope.launch {
                            try { preferences.update { it.copy(editor = it.editor.copy(fontSize = size)) } } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) { AppLogger.e("WorkspaceEditor", "保存缩放字号失败", failure) }
                        } }
                        view.setHighlightFileName(fileName)
                        view.setLanguage(language)
                        view.setReadOnly(readOnly)
                        view.setShowLineNumbers(showLineNumbers && settings.lineNumbers)
                        view.setCompletionEnabled(enableCompletion)
                        view.setViewportBottomPadding(keyboardAvoidancePaddingPx)
                        view.setOnTextChangedListener { snapshot ->
                            if (lastNotifiedRevision[0] != snapshot.version) {
                                lastNotifiedRevision[0] = snapshot.version
                                val notify = latestOnDocumentChange.value
                                if (notify != null) notify() else {
                                    val newText = snapshot.toString()
                                    if (newText != latestCode.value) latestOnCodeChange.value(newText)
                                }
                            }
                        }
                        view.setCompletionCallback(
                            if (enableCompletion) {
                                object : EditorCompletionCallback {
                                    override fun showCompletions(
                                        items: List<CompletionItem>,
                                        prefix: String
                                    ) {
                                        completionItems = items
                                        showCompletions = items.isNotEmpty()
                                        updatePopupAnchor(view)
                                    }

                                    override fun hideCompletions() {
                                        showCompletions = false
                                        completionItems = emptyList()
                                    }

                                    override fun isCompletionVisible(): Boolean = showCompletions
                                }
                            } else {
                                showCompletions = false
                                completionItems = emptyList()
                                null
                            }
                        )
                        // 会话模式只订阅文档版本；受控字符串模式只在外部内容确实变化时同步。
                        if (latestOnDocumentChange.value == null && lastExternalCode[0] != latestCode.value) {
                            lastExternalCode[0] = latestCode.value
                            if (view.getText() != latestCode.value) view.setText(latestCode.value, fromUpdate = true)
                        }
                        view.setSearchMatches(searchMatches, activeSearchMatch)
                        if (revealedSearchRequest != searchNavigationRequest) {
                            view.revealActiveSearchMatch()
                            revealedSearchRequest = searchNavigationRequest
                        }
                        pendingInitialLine?.let { line ->
                            view.goToLine(line)
                            pendingInitialLine = null
                        }
                    },
                    onRelease = { view ->
                        if (editorRefState.value === view) {
                            editorRefState.value = null
                        }
                        latestEditorRef.value?.invoke(null)
                        view.release()
                    }
                )

                selectionMenu?.let { selected ->
                    EditorSelectionToolbar(selected, readOnly) { action ->
                        val editor = editorRefState.value
                        if (editor != null) {
                            val find = action == EditorSelectionAction.FIND || action == EditorSelectionAction.FIND_IN_SELECTION
                            if (find) {
                                val text = editor.getText()
                                if (selected.start in 0..selected.end && selected.end <= text.length) {
                                    val scope = if (action == EditorSelectionAction.FIND_IN_SELECTION) EditorSearchScope(selected.start, selected.end) else null
                                    val query = if (scope == null) text.substring(selected.start, selected.end) else null
                                    editor.performSelectionAction(action)
                                    latestOnFind.value?.invoke(query, scope)
                                }
                            } else editor.performSelectionAction(action)
                        }
                    }
                }

                if (showCompletions && completionItems.isNotEmpty()) {
                    CompletionPopup(
                        completionItems = completionItems,
                        theme = theme,
                        onItemSelected = { item ->
                            editorRefState.value?.applyCompletion(item)
                            showCompletions = false
                        },
                        onDismissRequest = {
                            showCompletions = false
                        },
                        offset = popupOffset
                    )
                }
            }

            if (settings.showSymbols) Surface(
                modifier = Modifier.fillMaxWidth().height(40.dp), color = theme.gutterBackground, contentColor = theme.textColor,
            ) {
                LazyRow(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(settings.symbols.split(' ').filter(String::isNotEmpty)) { symbol ->
                        SymbolButton(symbol, theme, enabled = !readOnly) { editorRefState.value?.insertSymbol(symbol) }
                    }
                }
            }
    }
}

@Composable
private fun SymbolButton(symbol: String, theme: EditorTheme, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = theme.gutterBorder, shape = RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = theme.textColor
        )
    }
}

class NativeCodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val canvasEditorView = CanvasCodeEditorView(context, attrs)
    private var isReleased = false

    init {
        addView(canvasEditorView)
    }

    internal fun setOnTextChangedListener(listener: (EditorTextSnapshot) -> Unit) {
        if (isReleased) {
            return
        }
        canvasEditorView.setOnTextChangedListener(listener)
    }

    fun bindSession(session: EditorSession) { if (!isReleased) canvasEditorView.bindSession(session) }
    fun setEditorSettings(settings: EditorSettings) { if (!isReleased) canvasEditorView.setEditorSettings(settings) }
    fun setFontSizeListener(listener: ((Float) -> Unit)?) { if (!isReleased) canvasEditorView.setFontSizeListener(listener) }
    internal fun setSelectionMenuListener(listener: ((EditorSelectionMenuState?) -> Unit)?) { if (!isReleased) canvasEditorView.setSelectionMenuListener(listener) }
    internal fun dismissSelectionMenu() { if (!isReleased) canvasEditorView.dismissSelectionMenu() }
    internal fun performSelectionAction(action: EditorSelectionAction) { if (!isReleased) canvasEditorView.performSelectionAction(action) }
    fun performEditorAction(action: EditorAction) { if (!isReleased) canvasEditorView.performEditorAction(action) }
    fun replaceEditorRange(start: Int, end: Int, text: String) { if (!isReleased) canvasEditorView.replaceEditorRange(start, end, text) }

    fun setEditorTheme(theme: EditorTheme) {
        if (isReleased) {
            return
        }
        canvasEditorView.setEditorTheme(theme)
    }

    fun setHighlightFileName(fileName: String) { canvasEditorView.setHighlightFileName(fileName) }

    fun setLanguage(language: String) {
        if (isReleased) {
            return
        }
        canvasEditorView.setLanguage(language)
    }

    fun setReadOnly(readOnly: Boolean) {
        if (isReleased) {
            return
        }
        canvasEditorView.setReadOnly(readOnly)
        isEnabled = true
    }

    fun setShowLineNumbers(showLineNumbers: Boolean) {
        if (isReleased) {
            return
        }
        canvasEditorView.setShowLineNumbers(showLineNumbers)
    }

    fun setWordWrap(enabled: Boolean) {
        if (!isReleased) canvasEditorView.setWordWrap(enabled)
    }
    fun setCompletionEnabled(enableCompletion: Boolean) {
        if (isReleased) {
            return
        }
        canvasEditorView.setCompletionEnabled(enableCompletion)
    }

    fun setViewportBottomPadding(bottomPaddingPx: Int) {
        if (isReleased) {
            return
        }
        canvasEditorView.setViewportBottomPadding(bottomPaddingPx)
    }

    fun setText(text: String, fromUpdate: Boolean = false) {
        if (isReleased) {
            return
        }
        if (canvasEditorView.getTextContent() != text || !fromUpdate) {
            canvasEditorView.setTextContent(text)
        }
    }

    fun getText(): String = if (isReleased) "" else canvasEditorView.getTextContent()

    fun goToLine(line: Int) {
        if (!isReleased) canvasEditorView.goToLine(line)
    }

    fun revealActiveSearchMatch() {
        if (!isReleased) canvasEditorView.revealActiveSearchMatch()
    }

    fun setSearchMatches(matches: List<IntRange>, active: Int) {
        if (!isReleased) canvasEditorView.setSearchMatches(matches, active)
    }


    fun setCompletionCallback(callback: EditorCompletionCallback?) {
        if (isReleased) {
            return
        }
        canvasEditorView.setCompletionCallback(callback)
    }

    fun setKeyCommandListener(listener: ((EditorKeyCommand) -> Boolean)?) {
        if (isReleased) {
            return
        }
        canvasEditorView.setKeyCommandListener(listener)
    }

    fun applyCompletion(item: CompletionItem) {
        if (isReleased) {
            return
        }
        canvasEditorView.applyCompletion(item)
    }

    fun undo() {
        if (isReleased) {
            return
        }
        canvasEditorView.undo()
    }

    fun redo() {
        if (isReleased) {
            return
        }
        canvasEditorView.redo()
    }

    fun insertSymbol(symbol: String) {
        if (isReleased) {
            return
        }
        canvasEditorView.insertSymbol(symbol)
    }

    fun replaceAllText(newText: String) {
        if (isReleased) {
            return
        }
        canvasEditorView.replaceAllText(newText)
    }

    fun getCursorScreenPosition(): Point =
        if (isReleased) Point(0, 0) else canvasEditorView.getCursorScreenPosition()

    fun release() {
        if (isReleased) {
            return
        }
        isReleased = true
        canvasEditorView.release()
        removeAllViews()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        canvasEditorView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        canvasEditorView.layout(0, 0, r - l, b - t)
    }
}
