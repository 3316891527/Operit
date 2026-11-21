package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightStyle
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightFiles
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorResolvedHighlightStyle
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightRendering

import android.animation.ValueAnimator
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Typeface
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion.CompletionItem
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion.CompletionProvider
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion.CompletionProviderFactory
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.EditorTheme
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.DarkTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

interface EditorCompletionCallback {
    fun showCompletions(items: List<CompletionItem>, prefix: String)
    fun hideCompletions()
    fun isCompletionVisible(): Boolean
}

enum class EditorKeyCommand {
    SAVE,
    SAVE_AS,
    CLOSE,
    FIND,
    ESCAPE
}

class CanvasCodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 3.0f
        private const val DEFAULT_TEXT_SIZE_SP = 14f
        private const val TAB_SPACES = 4
    }

    private enum class DragHandle {
        NONE,
        START,
        END
    }

    private data class HandleCenters(
        val start: PointF,
        val end: PointF
    )

    private val density = resources.displayMetrics.density
    private val renderSignal = Object()
    private val scroller = OverScroller(context)
    private val viewConfig = ViewConfiguration.get(context)
    private val clipboardManager: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val inputMethodManager: InputMethodManager? =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    private val backgroundPaint = Paint().apply { style = Paint.Style.FILL }
    private val gutterPaint = Paint().apply { style = Paint.Style.FILL }
    private val gutterBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val indentGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        textAlign = Paint.Align.RIGHT
    }
    private val activeLineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        textAlign = Paint.Align.RIGHT
    }
    private val activeGutterLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gutterAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        color = Color.WHITE
    }
    private val systemGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        color = Color.WHITE
        typeface = Typeface.DEFAULT
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val composingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.4f
    }

    private val horizontalPaddingPx = density * 12f
    private val verticalPaddingPx = density * 10f
    private val gutterAccentWidthPx = max(2f, density * 2f)
    private val cursorWidthPx = max(2f, density * 2f)
    private val handleRadiusPx = density * 10f
    private val touchSlop = viewConfig.scaledTouchSlop.toFloat()
    private val minFlingVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity = viewConfig.scaledMaximumFlingVelocity.toFloat()

    private var theme: EditorTheme = DarkTheme
    private var metrics = EditorMetrics(textPaint, spToPx(DEFAULT_TEXT_SIZE_SP))
    private var document = EditorDocument()
    private var boundSession: EditorSession? = null
    private var options = EditorSettings()
    private var fontSizeListener: ((Float) -> Unit)? = null
    private var highlighter = EditorSyntaxHighlighter(context.applicationContext.assets, "text", configurationDirectory = EditorHighlightFiles.directory(context.applicationContext), onFailure = { text, language, failure ->
        if (text.version == document.version && text.owner === document.snapshot().owner) {
            highlightSnapshot = HighlightSnapshot(text, null)
            AppLogger.e("WorkspaceEditor", "语法高亮失败: $language", failure)
            val reason = (failure.localizedMessage ?: failure.javaClass.simpleName).take(300)
            OKToast.error(context, context.getString(R.string.workspace_editor_highlight_failed, language, reason), OKToast.LENGTH_LONG).show()
            requestRender()
        }
    }) { snapshot ->
        if (snapshot.version == document.version && snapshot.text.owner === document.snapshot().owner) {
            highlightSnapshot = snapshot
            requestRender()
        }
    }
    @Volatile private var highlightSnapshot = HighlightSnapshot(document.snapshot(), null)
    private var completionProvider: CompletionProvider = CompletionProviderFactory.getProvider("text")
    private var completionCallback: EditorCompletionCallback? = null
    private var keyCommandListener: ((EditorKeyCommand) -> Boolean)? = null
    private var currentLanguage = "text"
    private var currentScale = 1f
    private var wordWrap = true
    @Volatile private var visualSnapshot = EditorVisualLayout("", Int.MAX_VALUE, -1)
    private var showLineNumbers = true
    private var completionEnabled = true
    private var readOnly = false
    @Volatile private var searchRanges: List<IntRange> = emptyList()
    @Volatile private var activeSearchMatch = -1
    private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scrollOffsetX = 0f
    private var scrollOffsetY = 0f
    private var viewportBottomPaddingPx = 0f
    private var isReleased = false
    @Volatile private var isDirty = true
    private var renderThread: RenderThread? = null
    private var completionPrefix = ""
    private var preferredColumnCells: Int? = null
    private var editorTypeface: Typeface = Typeface.MONOSPACE

    private var selectionMenuVisible = false
    private var selectionMenuListener: ((EditorSelectionMenuState?) -> Unit)? = null
    private var activeDragHandle = DragHandle.NONE
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var hasMovedBeyondTouchSlop = false
    private var hadMultiTouch = false
    private var longPressSelectionStarted = false
    private var doubleTapSelectionStarted = false
    private var isScaling = false

    private var textChangedListener: ((EditorTextSnapshot) -> Unit)? = null
    @Volatile private var highlightRendering = EditorHighlightRendering(DarkTheme)
    @Volatile private var editorStyleTypefaces = Array(4) { Typeface.create(Typeface.MONOSPACE, it) }
    @Volatile private var systemStyleTypefaces = Array(4) { Typeface.create(Typeface.DEFAULT, it) }
    private var cachedGutterWidth = 0f
    private var cachedGutterDigits = -1
    private var cachedGutterTextSize = -1f

    private val scaleGestureDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    dismissSelectionMenu()
                    isScaling = true
                    if (!scroller.isFinished) {
                        scroller.forceFinished(true)
                    }
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    applyScale(detector.scaleFactor, detector.focusX, detector.focusY)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    if (options.pinchZoom) fontSizeListener?.invoke((options.fontSize * currentScale).coerceIn(8f, 40f))
                    isScaling = false
                }
            }
        )

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    if (isScaling || activeDragHandle != DragHandle.NONE) {
                        return false
                    }
                    dismissSelectionMenu()
                    applyScroll(distanceX, distanceY)
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (isScaling || activeDragHandle != DragHandle.NONE) {
                        return false
                    }
                    startFling(velocityX, velocityY)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    if (isScaling) {
                        return
                    }
                    longPressSelectionStarted = true
                    val offset = screenToOffset(e.x, e.y)
                    // 空白行没有可见选区，仍在触点定位并提供插入菜单。
                    val line = document.getLineForOffset(offset)
                    if (document.getLineText(line).isBlank() || (offset == document.getLineEnd(line) && e.x > offsetToPoint(offset).x)) document.collapseSelection(offset)
                    else document.selectWordAt(offset)
                    ensureCursorVisible()
                    notifySelectionChanged()
                    showSelectionMenu()
                    requestRender()
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    doubleTapSelectionStarted = true
                    if (isScaling) {
                        return false
                    }
                    val offset = screenToOffset(e.x, e.y)
                    document.selectWordAt(offset)
                    ensureCursorVisible()
                    notifySelectionChanged()
                    showSelectionMenu()
                    requestRender()
                    return true
                }
            }
        )

    // Steps the fling on the main thread, one Choreographer frame at a time. OverScroller is a
    // UI-thread helper: it is not thread-safe, and some OEM frameworks (e.g. HONOR) call
    // Choreographer.getInstance() inside computeScrollOffset(), which throws
    // "The current thread must have a looper!" when it runs on the render thread (#798).
    // The render thread only draws whatever scroll offsets this runnable publishes.
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) {
                return
            }
            setScrollOffsets(scroller.currX.toFloat(), scroller.currY.toFloat())
            postOnAnimation(this)
        }
    }

    init {
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
        holder.setFormat(PixelFormat.OPAQUE)
        holder.addCallback(this)
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        editorTypeface = loadEditorTypeface()
        refreshPaints()
        requestHighlight()
    }

    fun release() {
        if (isReleased) {
            return
        }
        document.finishComposingText()
        textChangedListener?.invoke(document.snapshot())
        saveViewport()
        isReleased = true
        fontSizeListener = null
        removeCallbacks(flingRunnable)
        stopRenderThread()
        dismissSelectionMenu()
        selectionMenuListener = null
        textChangedListener = null
        completionCallback = null
        highlighter.release()
    }

    fun bindSession(session: EditorSession) {
        if (boundSession === session) return
        dismissSelectionMenu()
        scroller.forceFinished(true)
        removeCallbacks(flingRunnable)
        saveViewport()
        boundSession = session
        document = session.document
        highlightSnapshot = HighlightSnapshot(document.snapshot(), null)
        scrollOffsetX = session.scrollX
        scrollOffsetY = session.scrollY
        currentScale = session.scale
        visualSnapshot = EditorVisualLayout("", Int.MAX_VALUE, -1)
        refreshPaints()
        requestHighlight()
        notifySelectionChanged()
        requestRender()
    }

    private fun saveViewport() {
        boundSession?.let { it.scrollX = scrollOffsetX; it.scrollY = scrollOffsetY; it.scale = currentScale }
    }

    fun setEditorSettings(settings: EditorSettings) {
        if (options == settings) { boundSession?.appliedFontSize = settings.fontSize; return }
        if (boundSession?.appliedFontSize != settings.fontSize) currentScale = 1f
        boundSession?.appliedFontSize = settings.fontSize
        options = settings
        preferredColumnCells = null
        scroller.forceFinished(true)
        document.tabWidth = settings.indentWidth
        showLineNumbers = settings.lineNumbers
        wordWrap = settings.wordWrap
        refreshPaints()
        clampScrollOffsets()
        requestRender()
    }

    fun setFontSizeListener(listener: ((Float) -> Unit)?) { fontSizeListener = listener }

    fun performEditorAction(action: EditorAction) {
        document.finishComposingText()
        if (action == EditorAction.SELECT_ALL) {
            document.selectAll(); notifySelectionChanged(); showSelectionMenu(); requestRender(); return
        }
        if (action == EditorAction.COPY_LINE) {
            clipboardManager?.setPrimaryClip(ClipData.newPlainText("", document.selectedLinesText())); return
        }
        if (readOnly) return
        val changed = when (action) {
            EditorAction.CUT_LINE -> {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("", document.selectedLinesText()))
                document.editLines(action, options.indent)
            }
            EditorAction.UPPERCASE -> document.changeSelectionCase(true)
            EditorAction.LOWERCASE -> document.changeSelectionCase(false)
            EditorAction.UNINDENT -> document.unindentSelection(options.indentWidth)
            else -> document.editLines(action, options.indent)
        }
        if (changed) onDocumentMutated()
    }

    fun replaceEditorRange(start: Int, end: Int, text: String) {
        if (!readOnly) { document.replaceRange(start, end, text); onDocumentMutated() }
    }

    fun setEditorTheme(theme: EditorTheme) {
        if (this.theme == theme) return
        this.theme = theme
        refreshPaints()
        requestRender()
    }

    fun setHighlightFileName(fileName: String) { highlighter.setFileName(fileName) }

    fun setLanguage(language: String) {
        if (currentLanguage.equals(language, ignoreCase = true)) {
            return
        }
        currentLanguage = language
        completionProvider = CompletionProviderFactory.getProvider(language)
        highlighter.setLanguage(language)
        highlightSnapshot = HighlightSnapshot(document.snapshot(), null)
        requestHighlight()
        requestRender()
        updateCompletion()
    }

    fun setReadOnly(readOnly: Boolean) {
        if (this.readOnly == readOnly) return
        document.finishComposingText()
        this.readOnly = readOnly
        notifySelectionChanged()
        isFocusable = !readOnly
        isFocusableInTouchMode = !readOnly
        if (readOnly) {
            hideCompletions()
            hideSoftKeyboard()
        }
    }

    fun setShowLineNumbers(showLineNumbers: Boolean) {
        this.showLineNumbers = showLineNumbers
        requestRender()
    }

    fun setWordWrap(enabled: Boolean) {
        if (wordWrap == enabled) return
        wordWrap = enabled
        preferredColumnCells = null
        scroller.forceFinished(true)
        clampScrollOffsets()
        ensureCursorVisible()
        requestRender()
    }

    /** 主线程发布不可变布局，绘制线程不会遍历正在编辑的 StringBuilder。 */
    private fun visualLayout(): EditorVisualLayout {
        if (Looper.myLooper() != Looper.getMainLooper()) return visualSnapshot
        val columns = if (wordWrap && width > 0 && metrics.charWidth > 0f) {
            ((textViewportWidth() - cursorWidthPx).coerceAtLeast(metrics.charWidth) / metrics.charWidth).toInt().coerceAtLeast(1)
        } else Int.MAX_VALUE
        val previous = visualSnapshot
        if (previous.version != document.version || previous.columns != columns || previous.wrapWords != options.wrapWords || previous.tabWidth != options.indentWidth) {
            visualSnapshot = EditorVisualLayout(document.snapshot(), columns, document.version, options.wrapWords, options.indentWidth)
        }
        return visualSnapshot
    }

    fun setCompletionEnabled(enabled: Boolean) {
        completionEnabled = enabled
        if (!enabled) {
            hideCompletions()
        } else {
            updateCompletion()
        }
    }

    fun setViewportBottomPadding(bottomPaddingPx: Int) {
        val resolvedPadding = bottomPaddingPx.coerceAtLeast(0).toFloat()
        if (abs(viewportBottomPaddingPx - resolvedPadding) < 0.5f) {
            return
        }
        val followCursor = shouldFollowCursorOnViewportChange(width, height)
        viewportBottomPaddingPx = resolvedPadding
        if (followCursor) ensureCursorVisible()
        requestRender()
    }

    internal fun setOnTextChangedListener(listener: (EditorTextSnapshot) -> Unit) {
        textChangedListener = listener
    }

    fun setCompletionCallback(callback: EditorCompletionCallback?) {
        completionCallback = callback
        if (callback == null) {
            hideCompletions()
        } else {
            updateCompletion()
        }
    }

    fun setKeyCommandListener(listener: ((EditorKeyCommand) -> Boolean)?) {
        keyCommandListener = listener
    }

    fun setTextContent(text: String) {
        document.setText(text, clearHistory = true)
        highlightSnapshot = HighlightSnapshot(document.snapshot(), null)
        completionPrefix = ""
        preferredColumnCells = null
        scrollOffsetX = 0f
        scrollOffsetY = 0f
        dismissSelectionMenu()
        hideCompletions()
        requestHighlight()
        notifySelectionChanged()
        inputMethodManager?.restartInput(this)
        requestRender()
    }

    fun getTextContent(): String = document.textString()

    /** 行号链接按一基行号定位，等待布局完成后滚动到目标光标。 */
    fun goToLine(line: Int) {
        val targetLine = (line - 1).coerceIn(0, document.lineCount() - 1)
        document.collapseSelection(document.getLineStart(targetLine))
        preferredColumnCells = null
        notifySelectionChanged()
        post {
            ensureCursorVisible()
            requestRender()
        }
    }

    /** 高亮更新与光标定位分开，输入时不反复抢回选区。 */
    fun setSearchMatches(matches: List<IntRange>, active: Int) {
        val valid = matches.filter { it.first in 0..document.length() && it.last < document.length() }
        if (valid == searchRanges && active == activeSearchMatch) return
        searchRanges = valid
        activeSearchMatch = active
        requestRender()
    }

    /** 初次查找与上下项导航都同步真实光标，保持搜索输入框的焦点。 */
    fun revealActiveSearchMatch() {
        searchRanges.getOrNull(activeSearchMatch)?.let { match ->
            dismissSelectionMenu()
            document.collapseSelection(match.first)
            preferredColumnCells = null
            notifySelectionChanged()
            val line = visualLayout().rowForOffset(match.first)
            val targetX = (xForOffsetInLine(match.first) - textViewportWidth() / 3f).coerceIn(0f, maxScrollX())
            val targetY = (verticalPaddingPx + line * metrics.lineHeight - height / 3f).coerceIn(0f, maxScrollY())
            // 复用主线程帧调度；再次查找、手动触摸或切换会话都能中断定位。
            scroller.forceFinished(true)
            removeCallbacks(flingRunnable)
            if (ValueAnimator.areAnimatorsEnabled()) {
                val startX = scrollOffsetX.roundToInt()
                val startY = scrollOffsetY.roundToInt()
                scroller.startScroll(startX, startY, targetX.roundToInt() - startX, targetY.roundToInt() - startY, 220)
                postOnAnimation(flingRunnable)
            } else {
                setScrollOffsets(targetX, targetY)
            }
            requestRender()
        }
    }

    fun undo() {
        if (readOnly) return
        if (document.undo()) {
            onDocumentMutated()
        }
    }

    fun redo() {
        if (readOnly) return
        if (document.redo()) {
            onDocumentMutated()
        }
    }

    fun replaceAllText(newText: String) {
        if (readOnly) {
            return
        }
        document.replaceAllText(newText)
        onDocumentMutated()
    }

    fun insertSymbol(symbol: String) {
        if (readOnly) {
            return
        }
        document.insertTextAtCursor(symbol)
        onDocumentMutated()
    }

    fun applyCompletion(item: CompletionItem) {
        if (readOnly) {
            return
        }
        document.applyCompletion(completionPrefix.length, item.insertText)
        completionPrefix = ""
        hideCompletions()
        onDocumentMutated()
    }

    fun getCursorScreenPosition(): Point = offsetToPoint(document.selectionEnd)

    override fun surfaceCreated(holder: SurfaceHolder) {
        startRenderThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        clampScrollOffsets()
        requestRender()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRenderThread()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !isReleased) highlighter.reloadConfiguration()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isReleased && holder.surface.isValid) {
            startRenderThread()
        }
    }

    override fun onDetachedFromWindow() {
        stopRenderThread()
        dismissSelectionMenu()
        hideCompletions()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val followCursor = shouldFollowCursorOnViewportChange(oldw, oldh)
        super.onSizeChanged(w, h, oldw, oldh)
        clampScrollOffsets()
        if (followCursor) ensureCursorVisible()
        requestRender()
    }

    override fun onCheckIsTextEditor(): Boolean = !readOnly

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isReleased) {
            return false
        }
        return if (handleKeyEvent(event)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection {
        outAttrs?.apply {
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
            initialSelStart = document.selectionStart
            initialSelEnd = document.selectionEnd
        }
        return EditorInputConnection()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        scaleGestureDetector.onTouchEvent(event)
        if (!isScaling) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                hasMovedBeyondTouchSlop = false
                hadMultiTouch = false
                longPressSelectionStarted = false
                if (!hasFocus() && !readOnly) {
                    requestFocus()
                }
                activeDragHandle = detectHandleHit(event.x, event.y)
                if (activeDragHandle != DragHandle.NONE) dismissSelectionMenu()
                if (activeDragHandle != DragHandle.NONE && !scroller.isFinished) {
                    scroller.forceFinished(true)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                hadMultiTouch = true
                hasMovedBeyondTouchSlop = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!hasMovedBeyondTouchSlop) {
                    val dx = abs(event.x - touchDownX)
                    val dy = abs(event.y - touchDownY)
                    if (dx > touchSlop || dy > touchSlop) {
                        hasMovedBeyondTouchSlop = true
                    }
                }
                if (activeDragHandle != DragHandle.NONE) {
                    updateSelectionFromHandle(activeDragHandle, event.x, event.y)
                    notifySelectionChanged()
                    dismissSelectionMenu()
                    requestRender()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (activeDragHandle != DragHandle.NONE) {
                    activeDragHandle = DragHandle.NONE
                    doubleTapSelectionStarted = false
                    showSelectionMenu()
                    notifySelectionChanged()
                    requestRender()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }

                val isTap = !hasMovedBeyondTouchSlop && !hadMultiTouch && !isScaling
                if (isTap && !longPressSelectionStarted && !doubleTapSelectionStarted) {
                    performClick()
                    dismissSelectionMenu()
                    val offset = screenToOffset(event.x, event.y)
                    document.collapseSelection(offset)
                    preferredColumnCells = null
                    ensureCursorVisible()
                    notifySelectionChanged()
                    hideCompletions()
                    if (!readOnly) {
                        showSoftKeyboard()
                    }
                    requestRender()
                } else if (document.hasSelection()) {
                    showSelectionMenu()
                }
                activeDragHandle = DragHandle.NONE
                hasMovedBeyondTouchSlop = false
                hadMultiTouch = false
                doubleTapSelectionStarted = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                activeDragHandle = DragHandle.NONE
                hasMovedBeyondTouchSlop = false
                hadMultiTouch = false
                doubleTapSelectionStarted = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val ctrlPressed = event.isCtrlPressed
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && !ctrlPressed) {
            if (selectionMenuVisible) { dismissSelectionMenu(); return true }
            if (keyCommandListener?.invoke(EditorKeyCommand.ESCAPE) == true) {
                return true
            }
            hideCompletions()
            dismissSelectionMenu()
            if (document.hasSelection()) {
                document.collapseSelection(document.selectionEnd)
                notifySelectionChanged()
                requestRender()
            }
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (readOnly) {
                    return true
                }
                document.deleteBackward()
                onDocumentMutated()
                return true
            }

            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (readOnly) {
                    return true
                }
                document.deleteForward()
                onDocumentMutated()
                return true
            }

            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (readOnly) {
                    return true
                }
                document.insertNewlineWithIndent(options.autoIndent, options.indent)
                onDocumentMutated()
                return true
            }

            KeyEvent.KEYCODE_TAB -> {
                if (readOnly) {
                    return true
                }
                if (event.isShiftPressed) {
                    if (document.unindentSelection(options.indentWidth)) {
                        onDocumentMutated()
                    }
                } else {
                    if (document.hasSelection()) document.editLines(EditorAction.INDENT, options.indent) else document.replaceSelection(options.indent, recordHistory = true)
                    onDocumentMutated()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveCursorHorizontal(-1, event.isShiftPressed)
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveCursorHorizontal(1, event.isShiftPressed)
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursorVertical(-1, event.isShiftPressed)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursorVertical(1, event.isShiftPressed)
                return true
            }

            KeyEvent.KEYCODE_MOVE_HOME -> {
                val layout = visualLayout()
                val line = layout.rowForOffset(document.selectionEnd)
                val target = if (ctrlPressed) 0 else layout.rows[line].start
                if (event.isShiftPressed) {
                    document.setSelection(document.selectionStart, target)
                } else {
                    document.collapseSelection(target)
                }
                preferredColumnCells = 0
                ensureCursorVisible()
                notifySelectionChanged()
                updateCompletion()
                requestRender()
                return true
            }

            KeyEvent.KEYCODE_MOVE_END -> {
                val layout = visualLayout()
                val line = layout.rowForOffset(document.selectionEnd)
                val target = if (ctrlPressed) document.length() else layout.rows[line].end
                if (event.isShiftPressed) {
                    document.setSelection(document.selectionStart, target)
                } else {
                    document.collapseSelection(target)
                }
                preferredColumnCells = layout.columnForOffset(target, line)
                ensureCursorVisible()
                notifySelectionChanged()
                updateCompletion()
                requestRender()
                return true
            }
        }

        if (ctrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    keyCommandListener?.invoke(
                        if (event.isShiftPressed) EditorKeyCommand.SAVE_AS else EditorKeyCommand.SAVE
                    )
                    return true
                }

                KeyEvent.KEYCODE_W -> {
                    keyCommandListener?.invoke(EditorKeyCommand.CLOSE)
                    return true
                }

                KeyEvent.KEYCODE_F -> {
                    keyCommandListener?.invoke(EditorKeyCommand.FIND)
                    return true
                }

                KeyEvent.KEYCODE_SLASH,
                KeyEvent.KEYCODE_NUMPAD_DIVIDE -> {
                    if (!readOnly) {
                        val syntax = CommentSyntaxRegistry.forLanguage(currentLanguage)
                        val changed = when {
                            syntax == null -> false
                            !document.hasSelection() && syntax.preferredLinePrefix != null ->
                                document.toggleLineComment(syntax.preferredLinePrefix.orEmpty())
                            syntax.hasBlockComment -> document.toggleBlockComment(
                                syntax.blockStart.orEmpty(),
                                syntax.blockEnd.orEmpty()
                            )
                            else -> commentPrefixForLanguage()?.let(document::toggleLineComment) == true
                        }
                        if (changed) {
                            onDocumentMutated()
                        }
                    }
                    return true
                }

                KeyEvent.KEYCODE_A -> {
                    document.selectAll()
                    showSelectionMenu()
                    notifySelectionChanged()
                    requestRender()
                    return true
                }

                KeyEvent.KEYCODE_C -> {
                    copySelectionToClipboard()
                    return true
                }

                KeyEvent.KEYCODE_X -> {
                    cutSelectionToClipboard()
                    return true
                }

                KeyEvent.KEYCODE_V -> {
                    pasteFromClipboard()
                    return true
                }

                KeyEvent.KEYCODE_Z -> {
                    undo()
                    return true
                }

                KeyEvent.KEYCODE_Y -> {
                    redo()
                    return true
                }
            }
        }

        if (!readOnly) {
            val unicodeChar = event.unicodeChar
            if (unicodeChar != 0 && !Character.isISOControl(unicodeChar)) {
                document.replaceSelection(unicodeChar.toChar().toString(), recordHistory = true)
                onDocumentMutated()
                return true
            }
        }

        return false
    }

    private fun commentPrefixForLanguage(): String? {
        return CommentSyntaxRegistry.forLanguage(currentLanguage)?.preferredLinePrefix
    }

    private fun moveCursorHorizontal(delta: Int, extendSelection: Boolean) {
        val currentCaret =
            if (!extendSelection && document.hasSelection()) {
                if (delta < 0) {
                    min(document.selectionStart, document.selectionEnd)
                } else {
                    max(document.selectionStart, document.selectionEnd)
                }
            } else {
                document.selectionEnd
            }
        var target = currentCaret
        repeat(abs(delta)) {
            target =
                if (delta < 0) {
                    document.previousCursorOffset(target)
                } else {
                    document.nextCursorOffset(target)
                }
        }
        if (extendSelection) {
            document.setSelection(document.selectionStart, target)
        } else {
            document.collapseSelection(target)
        }
        preferredColumnCells = null
        ensureCursorVisible()
        notifySelectionChanged()
        updateCompletion()
        requestRender()
    }

    private fun moveCursorVertical(deltaLines: Int, extendSelection: Boolean) {
        val caret = document.selectionEnd
        val layout = visualLayout()
        val currentLine = layout.rowForOffset(caret)
        val targetLine = (currentLine + deltaLines).coerceIn(0, layout.rows.lastIndex)
        val targetColumn = preferredColumnCells ?: layout.columnForOffset(caret)
        val targetOffset = layout.offsetForColumn(targetLine, targetColumn.toFloat())
        if (extendSelection) {
            document.setSelection(document.selectionStart, targetOffset)
        } else {
            document.collapseSelection(targetOffset)
        }
        preferredColumnCells = targetColumn
        ensureCursorVisible()
        notifySelectionChanged()
        updateCompletion()
        requestRender()
    }

    private fun applyScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        if (!options.pinchZoom) return
        val oldScale = currentScale
        val newScale = (currentScale * scaleFactor).coerceIn(8f / options.fontSize, 40f / options.fontSize)
        if (abs(newScale - oldScale) < 0.001f) {
            return
        }

        if (wordWrap) {
            val anchor = screenToOffset(focusX, focusY)
            currentScale = newScale
            refreshPaints()
            val point = offsetToPoint(anchor)
            setScrollOffsets(0f, scrollOffsetY + point.y - metrics.lineHeight - focusY)
            return
        }
        val oldCharWidth = metrics.charWidth
        val oldLineHeight = metrics.lineHeight
        val oldTextLeft = textRegionLeft()
        val logicalX =
            if (oldCharWidth > 0f) {
                (scrollOffsetX + (focusX - oldTextLeft).coerceAtLeast(0f)) / oldCharWidth
            } else {
                0f
            }
        val logicalY =
            if (oldLineHeight > 0f) {
                (scrollOffsetY + (focusY - verticalPaddingPx).coerceAtLeast(0f)) / oldLineHeight
            } else {
                0f
            }

        currentScale = newScale
        refreshPaints()

        val newScrollX = logicalX * metrics.charWidth - (focusX - textRegionLeft())
        val newScrollY = logicalY * metrics.lineHeight - (focusY - verticalPaddingPx)
        setScrollOffsets(newScrollX, newScrollY)
    }

    private fun applyScroll(distanceX: Float, distanceY: Float) {
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        setScrollOffsets(scrollOffsetX + distanceX, scrollOffsetY + distanceY)
    }

    private fun startFling(velocityX: Float, velocityY: Float) {
        if (hypot(velocityX.toDouble(), velocityY.toDouble()) < minFlingVelocity) {
            return
        }
        scroller.fling(
            scrollOffsetX.roundToInt(),
            scrollOffsetY.roundToInt(),
            (-velocityX).coerceIn(-maxFlingVelocity, maxFlingVelocity).roundToInt(),
            (-velocityY).coerceIn(-maxFlingVelocity, maxFlingVelocity).roundToInt(),
            0,
            maxScrollX().roundToInt(),
            0,
            maxScrollY().roundToInt()
        )
        // A MOVE that stopped the previous fling and the UP that starts this one can land in
        // the same frame, leaving the previous step still posted; drop it so only one runs.
        removeCallbacks(flingRunnable)
        postOnAnimation(flingRunnable)
    }

    private fun setScrollOffsets(x: Float, y: Float, request: Boolean = true) {
        val clampedX = x.coerceIn(0f, maxScrollX())
        val clampedY = y.coerceIn(0f, maxScrollY())
        if (abs(clampedX - scrollOffsetX) < 0.5f && abs(clampedY - scrollOffsetY) < 0.5f) {
            if (request) {
                requestRender()
            }
            return
        }
        scrollOffsetX = clampedX
        scrollOffsetY = clampedY
        updateSelectionMenu()
        if (request) {
            requestRender()
        }
    }

    private fun clampScrollOffsets() {
        setScrollOffsets(scrollOffsetX, scrollOffsetY)
    }

    private fun requestRender() {
        visualLayout()
        isDirty = true
        synchronized(renderSignal) {
            renderSignal.notifyAll()
        }
    }

    private fun startRenderThread() {
        if (renderThread?.isAlive == true) {
            requestRender()
            return
        }
        renderThread = RenderThread().also {
            it.start()
        }
        requestRender()
    }

    private fun stopRenderThread() {
        val activeThread = renderThread ?: return
        renderThread = null
        activeThread.finish()
        synchronized(renderSignal) {
            renderSignal.notifyAll()
        }
        runCatching {
            activeThread.join(500)
        }
    }

    private fun onDocumentMutated() {
        if (isReleased) {
            return
        }
        dismissSelectionMenu()
        preferredColumnCells = null
        ensureCursorVisible()
        notifySelectionChanged()
        requestHighlight()
        requestRender()
        updateCompletion()
        textChangedListener?.invoke(document.snapshot())
    }

    private fun requestHighlight() {
        if (isReleased) {
            return
        }
        highlighter.requestHighlight(document.snapshot())
    }

    private fun notifySelectionChanged() {
        boundSession?.searchScope = document.searchScope
        updateSelectionMenu()
        boundSession?.status = EditorStatus(document.canUndo(), document.canRedo(), document.selectionStart, document.selectionEnd, document.lineCount(), document.length(), document.version)
        inputMethodManager?.updateSelection(
            this,
            document.selectionStart,
            document.selectionEnd,
            document.composingStart,
            document.composingEnd
        )
    }

    private fun updateCompletion() {
        if (!completionEnabled || readOnly || document.hasSelection()) {
            hideCompletions()
            return
        }

        val cursor = document.selectionEnd
        val text = document.text()
        if (!completionProvider.shouldShowCompletion(text, cursor)) {
            hideCompletions()
            return
        }

        val items = completionProvider.getCompletionItems(text, cursor)
        if (items.isEmpty()) {
            hideCompletions()
            return
        }

        completionPrefix = completionProvider.getPrefix(text, cursor)
        completionCallback?.showCompletions(items, completionPrefix)
    }

    private fun hideCompletions() {
        completionPrefix = ""
        completionCallback?.hideCompletions()
    }

    private fun refreshPaints() {
        val textSizePx =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                theme.fontSize.value.takeIf { it > 0f } ?: DEFAULT_TEXT_SIZE_SP,
                resources.displayMetrics
            ) * currentScale

        textPaint.typeface = editorTypeface
        metrics.update(textSizePx, options.lineSpacing)

        val textColor = theme.textColor.toArgb()
        val lineNumberColor = theme.lineNumberColor.toArgb()
        val backgroundColor = theme.background.toArgb()
        val gutterBackgroundColor = theme.gutterBackground.toArgb()
        val gutterBorderColor = theme.gutterBorder.toArgb()
        val selectionColor = theme.selectionColor.toArgb()
        val cursorColor = theme.cursorColor.toArgb()


        lineNumberPaint.apply {
            textSize = textSizePx * 0.82f
            color = lineNumberColor
            typeface = editorTypeface
        }
        activeLineNumberPaint.apply {
            textSize = textSizePx * 0.82f
            color = textColor
            typeface = Typeface.create(editorTypeface, Typeface.BOLD)
        }

        textPaint.apply {
            color = textColor
            typeface = editorTypeface
        }
        systemGlyphPaint.apply {
            textSize = textSizePx
            color = textColor
            typeface = Typeface.DEFAULT
        }

        backgroundPaint.color = backgroundColor
        gutterPaint.color = gutterBackgroundColor
        gutterBorderPaint.color = gutterBorderColor
        indentGuidePaint.color = blendColors(backgroundColor, gutterBorderColor, 0.68f)
        activeGutterLinePaint.color = blendColors(gutterBackgroundColor, textColor, 0.08f)
        gutterAccentPaint.color = blendColors(gutterBackgroundColor, gutterBorderColor, 0.82f)
        selectionPaint.color = selectionColor
        cursorPaint.color = cursorColor
        currentLinePaint.color = blendColors(backgroundColor, selectionColor, 0.16f)
        composingPaint.color = cursorColor
        handlePaint.color = cursorColor
        handleStrokePaint.color = backgroundColor

        highlightRendering = EditorHighlightRendering(theme)
        editorStyleTypefaces = Array(4) { Typeface.create(editorTypeface, it) }
        systemStyleTypefaces = Array(4) { Typeface.create(Typeface.DEFAULT, it) }
        cachedGutterDigits = -1
        cachedGutterTextSize = -1f
        cachedGutterWidth = 0f
    }

    private fun blendColors(baseColor: Int, overlayColor: Int, overlayRatio: Float): Int {
        val ratio = overlayRatio.coerceIn(0f, 1f)
        val red =
            (Color.red(baseColor) + (Color.red(overlayColor) - Color.red(baseColor)) * ratio)
                .roundToInt()
                .coerceIn(0, 255)
        val green =
            (Color.green(baseColor) + (Color.green(overlayColor) - Color.green(baseColor)) * ratio)
                .roundToInt()
                .coerceIn(0, 255)
        val blue =
            (Color.blue(baseColor) + (Color.blue(overlayColor) - Color.blue(baseColor)) * ratio)
                .roundToInt()
                .coerceIn(0, 255)
        return Color.argb(255, red, green, blue)
    }

    private fun drawEditor(canvas: Canvas) {
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), backgroundPaint)

        val layout = visualSnapshot
        val lineCount = layout.rows.size
        if (lineCount <= 0) {
            return
        }

        val gutterWidth = gutterWidth()
        val textRegionLeft = if (showLineNumbers) gutterWidth else horizontalPaddingPx
        if (showLineNumbers && gutterWidth > 0f) {
            canvas.drawRect(0f, 0f, textClipLeft(), canvas.height.toFloat(), gutterPaint)
        }

        val firstVisibleLine =
            max(
                0,
                ((scrollOffsetY - verticalPaddingPx) / metrics.lineHeight).toInt()
            )
        val lastVisibleLine =
            min(
                lineCount - 1,
                ceil(
                    ((scrollOffsetY + canvas.height - verticalPaddingPx) / metrics.lineHeight)
                        .toDouble()
                ).toInt()
            )

        val cursorLine = layout.rowForOffset(document.selectionEnd)
        if (!document.hasSelection() && cursorLine in firstVisibleLine..lastVisibleLine) {
            val lineTop = verticalPaddingPx + cursorLine * metrics.lineHeight - scrollOffsetY
            if (showLineNumbers && gutterWidth > 0f) {
                canvas.drawRect(
                    0f,
                    lineTop,
                    gutterWidth,
                    lineTop + metrics.lineHeight,
                    activeGutterLinePaint
                )
            }
            canvas.drawRect(
                textRegionLeft,
                lineTop,
                canvas.width.toFloat(),
                lineTop + metrics.lineHeight,
                currentLinePaint
            )
        }

        for (line in firstVisibleLine..lastVisibleLine) {
            val lineTop = verticalPaddingPx + line * metrics.lineHeight - scrollOffsetY
            if (showLineNumbers) {
                drawLineNumber(canvas, layout, line, lineTop, cursorLine, gutterWidth)
            }
        }

        canvas.save()
        canvas.clipRect(textClipLeft(), 0f, canvas.width.toFloat(), canvas.height.toFloat())
        for (line in firstVisibleLine..lastVisibleLine) {
            val lineTop = verticalPaddingPx + line * metrics.lineHeight - scrollOffsetY
            drawIndentGuides(canvas, layout, line, lineTop, textRegionLeft)
            drawSearchForLine(canvas, layout, line, lineTop, textRegionLeft)
            drawTextForLine(canvas, layout, line, lineTop, textRegionLeft)
            drawWhitespace(canvas, layout, line, lineTop, textRegionLeft)
            drawSelectionForLine(canvas, layout, line, lineTop, textRegionLeft)
            drawComposingUnderline(canvas, layout, line, lineTop, textRegionLeft)
            drawCursorForLine(canvas, layout, line, lineTop, cursorLine, textRegionLeft)
        }
        canvas.restore()

        if (document.hasSelection()) {
            drawSelectionHandles(canvas, layout)
        }
    }

    private fun drawLineNumber(
        canvas: Canvas,
        layout: EditorVisualLayout,
        line: Int,
        lineTop: Float,
        currentLine: Int,
        gutterWidth: Float
    ) {
        val baseline = lineTop + metrics.baseline
        if (baseline < 0f || baseline > canvas.height + metrics.lineHeight) {
            return
        }
        if (!layout.rows[line].first) {
            if (options.wrapMarkers) canvas.drawText("↳", gutterWidth - gutterTrailingPaddingPx() - if (options.fixedLineNumbers) 0f else scrollOffsetX, baseline, lineNumberPaint)
            return
        }
        val label = (layout.rows[line].logicalLine + 1).toString()
        val x = gutterWidth - gutterTrailingPaddingPx() - if (options.fixedLineNumbers) 0f else scrollOffsetX
        if (layout.rows[line].logicalLine == layout.rows[currentLine].logicalLine) {
            canvas.drawText(label, x, baseline, activeLineNumberPaint)
            return
        }
        canvas.drawText(label, x, baseline, lineNumberPaint)
    }

    private fun drawIndentGuides(canvas: Canvas, layout: EditorVisualLayout, line: Int, lineTop: Float, textRegionLeft: Float) {
        if (!options.indentGuides || !layout.rows[line].first) return
        val lineStart = layout.rows[line].start
        val lineEnd = layout.rows[line].end
        val text = layout.text
        var cells = 0
        var indentLevels = 0
        for (offset in lineStart until lineEnd) {
            val char = text[offset]
            if (char == ' ') {
                cells += 1
            } else if (char == '\t') {
                cells += options.indentWidth
            } else {
                indentLevels = cells / options.indentWidth
                break
            }
        }
        if (indentLevels == 0 && cells > 0) {
            indentLevels = cells / options.indentWidth
        }

        for (level in 1..indentLevels) {
            val x =
                textRegionLeft +
                    level * options.indentWidth * metrics.charWidth -
                    scrollOffsetX -
                    metrics.charWidth * 0.5f
            if (x < textClipLeft() || x > width.toFloat()) {
                continue
            }
            canvas.drawLine(
                x,
                lineTop + density * 2f,
                x,
                lineTop + metrics.lineHeight - density * 2f,
                indentGuidePaint
            )
        }
    }

    private fun drawSearchForLine(canvas: Canvas, layout: EditorVisualLayout, line: Int, top: Float, textLeft: Float) {
        val ranges = searchRanges
        if (ranges.isEmpty()) return
        val start = layout.rows[line].start
        val end = layout.rows[line].end
        val found = ranges.binarySearchBy(start) { it.first }
        var index = if (found >= 0) found else max(0, -found - 2)
        while (index < ranges.size && ranges[index].first <= end) {
            val match = ranges[index]
            val from = max(start, match.first)
            val to = min(end, match.last + 1)
            if (match.isEmpty() && layout.rowForOffset(match.first) == line) {
                searchPaint.color = (if (index == activeSearchMatch) theme.activeSearchMatchColor else theme.searchMatchColor).toArgb()
                val x = textLeft + layout.columnForOffset(match.first, line) * metrics.charWidth - scrollOffsetX
                canvas.drawRect(x, top, x + cursorWidthPx, top + metrics.lineHeight, searchPaint)
            }
            if (to > from) {
                searchPaint.color = (if (index == activeSearchMatch) theme.activeSearchMatchColor else theme.searchMatchColor).toArgb()
                canvas.drawRect(textLeft + (layout.columnForOffset(from, line) * metrics.charWidth) - scrollOffsetX, top,
                    textLeft + (layout.columnForOffset(to, line) * metrics.charWidth) - scrollOffsetX, top + metrics.lineHeight, searchPaint)
            }
            index++
        }
    }

    private fun drawSelectionForLine(
        canvas: Canvas,
        layout: EditorVisualLayout,
        line: Int,
        lineTop: Float,
        textRegionLeft: Float
    ) {
        if (!document.hasSelection()) {
            return
        }

        val selectionStart = min(document.selectionStart, document.selectionEnd)
        val selectionEnd = max(document.selectionStart, document.selectionEnd)
        val lineStart = layout.rows[line].start
        val lineEnd = layout.rows[line].end

        val start = max(selectionStart, lineStart)
        val end = min(selectionEnd, lineEnd)

        if (selectionEnd <= lineStart || selectionStart >= lineEnd) {
            return
        }

        if (start == end && !(selectionStart < lineStart && selectionEnd > lineStart)) {
            return
        }

        val left =
            if (selectionStart < lineStart) {
                textRegionLeft
            } else {
                textRegionLeft + (layout.columnForOffset(start, line) * metrics.charWidth) - scrollOffsetX
            }
        val right =
            if (selectionEnd > lineEnd) {
                textRegionLeft + (layout.columnForOffset(lineEnd, line) * metrics.charWidth) - scrollOffsetX
            } else {
                textRegionLeft + (layout.columnForOffset(end, line) * metrics.charWidth) - scrollOffsetX
            }
        if (right <= left) {
            return
        }

        canvas.drawRect(
            left.coerceAtLeast(textClipLeft()),
            lineTop,
            right.coerceAtMost(width.toFloat()),
            lineTop + metrics.lineHeight,
            selectionPaint
        )
    }

    private fun drawTextForLine(
        canvas: Canvas,
        layout: EditorVisualLayout,
        line: Int,
        lineTop: Float,
        textRegionLeft: Float
    ) {
        val lineStart = layout.rows[line].start
        val lineEnd = layout.rows[line].end
        val text = layout.text
        var x = textRegionLeft - scrollOffsetX
        val baseline = lineTop + metrics.baseline
        val leftClip = textClipLeft() - metrics.charWidth * options.indentWidth
        val rightClip = width.toFloat() + metrics.charWidth * TAB_SPACES

        var offset = lineStart
        var runStart = -1
        var runEnd = -1
        var runX = 0f
        var runStyle = highlightRendering.resolve(EditorHighlightStyle.Default)

        fun flushRun() {
            if (runStart < 0 || runEnd <= runStart) {
                return
            }
            applyHighlightStyle(textPaint, runStyle, false)
            canvas.drawText(text, runStart, runEnd, runX, baseline, textPaint)
            runStart = -1
            runEnd = -1
        }

        while (offset < lineEnd) {
            val nextOffset = editorNextSymbolOffset(text, offset, lineEnd)
            val cellWidth = layout.cellWidth(offset, lineEnd)
            val advance = metrics.charWidth * cellWidth
            if (x + advance < leftClip) {
                x += advance
                offset = nextOffset
                continue
            }
            if (x > rightClip) {
                flushRun()
                break
            }

            if (text[offset] != '\t') {
                val usesSystemGlyphPaint =
                    if (nextOffset == offset + 1) {
                        shouldRenderWithSystemGlyphs(text[offset].code)
                    } else {
                        shouldRenderWithSystemGlyphs(text, offset, nextOffset)
                    }
                val style = highlightRendering.resolve(styleForOffset(offset))
                val canBatch =
                    !usesSystemGlyphPaint &&
                        cellWidth == 1 &&
                        nextOffset == offset + 1

                if (canBatch) {
                    if (runStart >= 0 && runStyle == style && runEnd == offset) {
                        runEnd = nextOffset
                    } else {
                        flushRun()
                        runStart = offset
                        runEnd = nextOffset
                        runX = x
                        runStyle = style
                    }
                } else {
                    flushRun()
                    val paint = if (usesSystemGlyphPaint) systemGlyphPaint else textPaint
                    applyHighlightStyle(paint, style, usesSystemGlyphPaint)
                    canvas.drawText(text, offset, nextOffset, x, baseline, paint)
                }
            } else {
                flushRun()
            }
            x += advance
            offset = nextOffset
        }
        flushRun()
    }

    private fun drawWhitespace(canvas: Canvas, layout: EditorVisualLayout, rowIndex: Int, top: Float, left: Float) {
        if (!options.whitespace) return
        systemGlyphPaint.typeface = Typeface.DEFAULT
        systemGlyphPaint.isUnderlineText = false
        systemGlyphPaint.isStrikeThruText = false
        val row = layout.rows[rowIndex]
        var offset = row.start
        var x = left - scrollOffsetX
        while (offset < row.end) {
            val char = layout.text[offset]
            val advance = layout.cellWidth(offset, row.end) * metrics.charWidth
            val isolated = char == ' ' && (offset == 0 || layout.text[offset - 1] != ' ') &&
                (offset + 1 == layout.text.length || layout.text[offset + 1] != ' ')
            if (x + advance >= textClipLeft() && x <= width && (char == '\t' || (char == ' ' && (!options.hideSingleSpaces || !isolated)))) {
                systemGlyphPaint.color = theme.lineNumberColor.toArgb()
                canvas.drawText(if (char == '\t') "→" else "·", x, top + metrics.baseline, systemGlyphPaint)
            }
            x += advance
            offset = editorNextSymbolOffset(layout.text, offset, row.end)
        }
    }

    private fun drawComposingUnderline(
        canvas: Canvas,
        layout: EditorVisualLayout,
        line: Int,
        lineTop: Float,
        textRegionLeft: Float
    ) {
        if (!document.hasComposingRegion()) {
            return
        }
        val composingStart = document.composingStart
        val composingEnd = document.composingEnd
        val lineStart = layout.rows[line].start
        val lineEnd = layout.rows[line].end
        val start = max(composingStart, lineStart)
        val end = min(composingEnd, lineEnd)
        if (start >= end) {
            return
        }
        val left = textRegionLeft + (layout.columnForOffset(start, line) * metrics.charWidth) - scrollOffsetX
        val right = textRegionLeft + (layout.columnForOffset(end, line) * metrics.charWidth) - scrollOffsetX
        val y = lineTop + metrics.lineHeight - density * 3f
        canvas.drawLine(left, y, right, y, composingPaint)
    }

    private fun drawCursorForLine(
        canvas: Canvas,
        layout: EditorVisualLayout,
        line: Int,
        lineTop: Float,
        cursorLine: Int,
        textRegionLeft: Float
    ) {
        if (document.hasSelection() || line != cursorLine) {
            return
        }
        val x = textRegionLeft + (layout.columnForOffset(document.selectionEnd, line) * metrics.charWidth) - scrollOffsetX
        canvas.drawRect(
            x,
            lineTop + density,
            x + cursorWidthPx,
            lineTop + metrics.lineHeight - density,
            cursorPaint
        )
    }

    private fun drawSelectionHandles(canvas: Canvas, layout: EditorVisualLayout) {
        val handles = selectionHandleCenters(layout) ?: return
        drawHandle(canvas, handles.start)
        drawHandle(canvas, handles.end)
    }

    private fun drawHandle(canvas: Canvas, center: PointF) {
        canvas.drawLine(
            center.x,
            center.y - metrics.lineHeight * 0.45f,
            center.x,
            center.y,
            handlePaint
        )
        canvas.drawCircle(center.x, center.y, handleRadiusPx, handlePaint)
        canvas.drawCircle(center.x, center.y, handleRadiusPx, handleStrokePaint)
    }

    private fun styleForOffset(offset: Int): EditorHighlightStyle {
        val snapshot = highlightSnapshot
        if (!options.syntaxHighlight || snapshot.version != document.version) return EditorHighlightStyle.Default
        return snapshot.styleAt(offset)
    }

    private fun applyHighlightStyle(paint: Paint, style: EditorResolvedHighlightStyle, systemGlyph: Boolean) {
        val typefaceStyle = (if (style.bold) Typeface.BOLD else Typeface.NORMAL) or
            (if (style.italic) Typeface.ITALIC else Typeface.NORMAL)
        paint.typeface = if (systemGlyph) systemStyleTypefaces[typefaceStyle] else editorStyleTypefaces[typefaceStyle]
        paint.color = style.color
        paint.isUnderlineText = style.underline
        paint.isStrikeThruText = style.strike
    }

    private fun contentHeightPx(): Float {
        return verticalPaddingPx * 2f + visualLayout().rows.size * metrics.lineHeight
    }

    private fun contentWidthPx(): Float {
        return horizontalPaddingPx + document.maxLineCells * metrics.charWidth + horizontalPaddingPx
    }

    private fun gutterWidth(): Float {
        if (!showLineNumbers) {
            return 0f
        }
        val digits = document.lineDigits()
        val textSize = lineNumberPaint.textSize
        if (digits != cachedGutterDigits || abs(textSize - cachedGutterTextSize) >= 0.5f) {
            val digitsText = "9".repeat(digits)
            val lineNumberWidth = lineNumberPaint.measureText(digitsText)
            val padding = gutterLeadingPaddingPx() + gutterTrailingPaddingPx()
            cachedGutterWidth = max(lineNumberWidth + padding, density * 24f)
            cachedGutterDigits = digits
            cachedGutterTextSize = textSize
        }
        return cachedGutterWidth
    }

    private fun textClipLeft(): Float = if (showLineNumbers) {
        if (options.fixedLineNumbers) gutterWidth() else (gutterWidth() - scrollOffsetX).coerceAtLeast(0f)
    } else horizontalPaddingPx

    private fun textRegionLeft(): Float {
        return if (showLineNumbers) gutterWidth() else horizontalPaddingPx
    }

    private fun gutterLeadingPaddingPx(): Float {
        return when (document.lineDigits()) {
            1 -> density * 5f
            2 -> density * 6f
            3 -> density * 7f
            else -> density * 8f
        }
    }

    private fun gutterTrailingPaddingPx(): Float {
        return when (document.lineDigits()) {
            1 -> density * 5f
            2 -> density * 6f
            3 -> density * 7f
            else -> density * 8f
        }
    }

    private fun textViewportWidth(): Float {
        return max(0f, width.toFloat() - textRegionLeft() - horizontalPaddingPx)
    }

    private fun maxScrollX(): Float {
        if (wordWrap) return 0f
        return max(0f, contentWidthPx() - textViewportWidth())
    }

    private fun maxScrollY(): Float {
        return max(0f, contentHeightPx() - height.toFloat())
    }

    private fun xForOffsetInLine(offset: Int): Float = visualLayout().columnForOffset(offset) * metrics.charWidth

    private fun screenToOffset(x: Float, y: Float): Int {
        val layout = visualLayout()
        val row = ((y + scrollOffsetY - verticalPaddingPx) / metrics.lineHeight).toInt().coerceIn(0, layout.rows.lastIndex)
        val column = (scrollOffsetX + x - textRegionLeft()).coerceAtLeast(0f) / metrics.charWidth
        return layout.offsetForColumn(row, column)
    }

    private fun offsetToPoint(offset: Int, layout: EditorVisualLayout = visualLayout()): Point {
        val row = layout.rowForOffset(offset)
        val x = textRegionLeft() + layout.columnForOffset(offset, row) * metrics.charWidth - scrollOffsetX
        val y = verticalPaddingPx + row * metrics.lineHeight - scrollOffsetY + metrics.lineHeight
        return Point(x.roundToInt(), y.roundToInt())
    }

    /** 面板和键盘动画不能抢走滚动位置，仅保持变化前已可见的编辑光标。 */
    private fun shouldFollowCursorOnViewportChange(viewportWidth: Int, viewportHeight: Int): Boolean {
        if (!hasFocus() || !scroller.isFinished || hasMovedBeyondTouchSlop || isScaling ||
            viewportWidth <= 0 || viewportHeight <= 0) return false
        // 尺寸回调时 View 已使用新宽高，旧快照仍能准确判断换行前的光标位置。
        val layout = visualSnapshot
        val point = offsetToPoint(document.selectionEnd, layout)
        val lineTop = verticalPaddingPx + layout.rowForOffset(document.selectionEnd) * metrics.lineHeight - scrollOffsetY
        val bottomLimit = viewportHeight - verticalPaddingPx - viewportBottomPaddingPx
        // 滚动偏移会忽略不足半像素的变化，边缘可见性使用相同容差。
        val tolerance = 0.5f
        return lineTop >= verticalPaddingPx - tolerance && lineTop + metrics.lineHeight <= bottomLimit + tolerance &&
            point.x >= textRegionLeft() - tolerance && point.x <= viewportWidth - horizontalPaddingPx + tolerance
    }

    private fun ensureCursorVisible() {
        val line = visualLayout().rowForOffset(document.selectionEnd)
        val point = offsetToPoint(document.selectionEnd)
        val lineTop = verticalPaddingPx + line * metrics.lineHeight - scrollOffsetY
        val lineBottom = lineTop + metrics.lineHeight
        val leftLimit = textRegionLeft() + metrics.charWidth * 2f
        val rightLimit = width - horizontalPaddingPx - metrics.charWidth * 2f
        val topLimit = verticalPaddingPx
        val bottomLimit =
            max(
                topLimit + metrics.lineHeight,
                height - verticalPaddingPx - viewportBottomPaddingPx
            )

        var newScrollX = scrollOffsetX
        var newScrollY = scrollOffsetY

        if (point.x < leftLimit) {
            newScrollX -= (leftLimit - point.x)
        } else if (point.x > rightLimit) {
            newScrollX += (point.x - rightLimit)
        }

        if (lineTop < topLimit) {
            newScrollY -= (topLimit - lineTop)
        } else if (lineBottom > bottomLimit) {
            newScrollY += (lineBottom - bottomLimit)
        }

        setScrollOffsets(newScrollX, newScrollY, request = false)
    }

    private fun detectHandleHit(x: Float, y: Float): DragHandle {
        val centers = selectionHandleCenters() ?: return DragHandle.NONE
        val startDistance = hypot((x - centers.start.x).toDouble(), (y - centers.start.y).toDouble())
        val endDistance = hypot((x - centers.end.x).toDouble(), (y - centers.end.y).toDouble())
        val threshold = handleRadiusPx * 2.25f
        return when {
            startDistance <= threshold && endDistance <= threshold -> {
                if (startDistance <= endDistance) DragHandle.START else DragHandle.END
            }
            startDistance <= threshold -> DragHandle.START
            endDistance <= threshold -> DragHandle.END
            else -> DragHandle.NONE
        }
    }

    private fun updateSelectionFromHandle(handle: DragHandle, x: Float, y: Float) {
        if (!document.hasSelection()) {
            return
        }
        val otherStart = min(document.selectionStart, document.selectionEnd)
        val otherEnd = max(document.selectionStart, document.selectionEnd)
        val offset = screenToOffset(x, y)
        when (handle) {
            DragHandle.START -> document.setSelection(offset, otherEnd)
            DragHandle.END -> document.setSelection(otherStart, offset)
            DragHandle.NONE -> Unit
        }
        ensureCursorVisible()
    }

    private fun selectionHandleCenters(layout: EditorVisualLayout = visualLayout()): HandleCenters? {
        if (!document.hasSelection()) {
            return null
        }
        val start = min(document.selectionStart, document.selectionEnd)
        val end = max(document.selectionStart, document.selectionEnd)
        val startPoint = offsetToPoint(start, layout)
        val endPoint = offsetToPoint(end, layout)
        return HandleCenters(
            start = PointF(startPoint.x.toFloat(), startPoint.y.toFloat()),
            end = PointF(endPoint.x.toFloat(), endPoint.y.toFloat())
        )
    }

    internal fun setSelectionMenuListener(listener: ((EditorSelectionMenuState?) -> Unit)?) {
        selectionMenuListener = listener
    }

    internal fun dismissSelectionMenu() {
        selectionMenuVisible = false
        selectionMenuListener?.invoke(null)
    }

    private fun showSelectionMenu() {
        selectionMenuVisible = true
        updateSelectionMenu()
    }

    private fun updateSelectionMenu() {
        if (!selectionMenuVisible || width <= 0 || height <= 0) {
            selectionMenuListener?.invoke(null)
            return
        }
        val start = min(document.selectionStart, document.selectionEnd)
        val end = max(document.selectionStart, document.selectionEnd)
        val first = offsetToPoint(start)
        val last = offsetToPoint(end)
        val sameRow = visualLayout().rowForOffset(start) == visualLayout().rowForOffset(end)
        val left = if (sameRow) min(first.x, last.x) else textRegionLeft().roundToInt()
        val right = if (sameRow) max(first.x, last.x) else width
        val top = (first.y - metrics.lineHeight).roundToInt().coerceIn(0, height)
        val bottom = last.y.coerceIn(top, height)
        selectionMenuListener?.invoke(EditorSelectionMenuState(start, end,
            IntRect(left.coerceIn(0, width), top, right.coerceIn(0, width), bottom)))
    }

    internal fun performSelectionAction(action: EditorSelectionAction) {
        when (action) {
            EditorSelectionAction.SELECT_ALL -> {
                document.selectAll()
                notifySelectionChanged()
                showSelectionMenu()
                requestRender()
            }
            EditorSelectionAction.COPY -> {
                copySelectionToClipboard()
                dismissSelectionMenu()
            }
            EditorSelectionAction.CUT -> { cutSelectionToClipboard(); dismissSelectionMenu() }
            EditorSelectionAction.PASTE -> { pasteFromClipboard(); dismissSelectionMenu() }
            EditorSelectionAction.FIND, EditorSelectionAction.FIND_IN_SELECTION -> dismissSelectionMenu()
        }
    }

    private fun copySelectionToClipboard(showFeedback: Boolean = true): Boolean {
        if (!document.hasSelection()) return false
        val clipboard = clipboardManager ?: return false
        val start = min(document.selectionStart, document.selectionEnd)
        val end = max(document.selectionStart, document.selectionEnd)
        val selectedText = document.text().subSequence(start, end).toString()
        clipboard.setPrimaryClip(ClipData.newPlainText("Code", selectedText))
        if (showFeedback) OKToast.success(context, R.string.file_op_copy_success, OKToast.LENGTH_SHORT).show()
        return true
    }

    private fun cutSelectionToClipboard() {
        if (readOnly || !document.hasSelection()) {
            return
        }
        // 剪贴板写入成功后再删除选区，只提示最终的剪切操作。
        if (!copySelectionToClipboard(showFeedback = false)) return
        document.replaceSelection("", recordHistory = true)
        onDocumentMutated()
        OKToast.success(context, R.string.file_op_cut_success, OKToast.LENGTH_SHORT).show()
    }

    private fun pasteFromClipboard() {
        if (readOnly) {
            return
        }
        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount <= 0) {
            return
        }
        val pasted =
            clip.getItemAt(0).coerceToText(context)?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n')
                ?: return
        if (pasted.isEmpty()) {
            return
        }
        document.replaceSelection(pasted, recordHistory = true)
        onDocumentMutated()
    }

    private fun showSoftKeyboard() {
        if (readOnly) {
            return
        }
        if (!hasFocus()) {
            requestFocus()
        }
        inputMethodManager?.restartInput(this)
        inputMethodManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSoftKeyboard() {
        inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun spToPx(valueSp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            valueSp,
            resources.displayMetrics
        )
    }

    private fun loadEditorTypeface(): Typeface {
        return try {
            resources.getFont(com.ai.assistance.operit.terminal.R.font.jetbrains_mono_nerd_font_regular)
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
    }

    private fun shouldRenderWithSystemGlyphs(codePoint: Int): Boolean {
        return codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x1F3FB..0x1F3FF ||
            codePoint in 0x1F1E6..0x1F1FF ||
            codePoint in 0xFE00..0xFE0F ||
            codePoint == 0x200D ||
            codePoint == 0x20E3
    }

    private fun shouldRenderWithSystemGlyphs(
        text: CharSequence,
        start: Int,
        end: Int
    ): Boolean {
        var index = start.coerceAtLeast(0)
        val safeEnd = end.coerceAtMost(text.length)
        while (index < safeEnd) {
            val codePoint = Character.codePointAt(text, index)
            if (shouldRenderWithSystemGlyphs(codePoint)) {
                return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }

    private inner class EditorInputConnection : BaseInputConnection(this@CanvasCodeEditorView, true) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (readOnly) {
                return true
            }
            val normalized = text?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n') ?: ""
            if (normalized == "\n") document.insertNewlineWithIndent(options.autoIndent, options.indent) else document.replaceSelection(normalized, recordHistory = true)
            document.finishComposingText()
            onDocumentMutated()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (readOnly) {
                return true
            }
            val normalized = text?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n') ?: ""
            document.setComposingText(normalized)
            preferredColumnCells = null
            ensureCursorVisible()
            notifySelectionChanged()
            requestRender()
            updateCompletion()
            textChangedListener?.invoke(document.snapshot())
            return true
        }

        override fun finishComposingText(): Boolean {
            document.finishComposingText()
            notifySelectionChanged()
            requestRender()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (readOnly) {
                return true
            }
            document.deleteSurroundingText(beforeLength, afterLength)
            onDocumentMutated()
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            if (readOnly) {
                return true
            }
            document.deleteSurroundingCodePoints(beforeLength, afterLength)
            onDocumentMutated()
            return true
        }

        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event == null || event.action != KeyEvent.ACTION_DOWN) {
                return true
            }
            if (handleKeyEvent(event)) {
                return true
            }
            return super.sendKeyEvent(event)
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            document.setSelection(start, end)
            preferredColumnCells = null
            ensureCursorVisible()
            notifySelectionChanged()
            requestRender()
            updateCompletion()
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            val end = document.selectionEnd
            val start = (end - n).coerceAtLeast(0)
            return document.text().subSequence(start, end)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
            val start = document.selectionEnd
            val end = (start + n).coerceAtMost(document.length())
            return document.text().subSequence(start, end)
        }

        override fun getSelectedText(flags: Int): CharSequence? {
            if (!document.hasSelection()) {
                return ""
            }
            val start = min(document.selectionStart, document.selectionEnd)
            val end = max(document.selectionStart, document.selectionEnd)
            return document.text().subSequence(start, end)
        }

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
            return ExtractedText().apply {
                text = document.text()
                startOffset = 0
                selectionStart = document.selectionStart
                selectionEnd = document.selectionEnd
                partialStartOffset = -1
                partialEndOffset = -1
            }
        }

        override fun performContextMenuAction(id: Int): Boolean {
            return when (id) {
                android.R.id.copy -> {
                    copySelectionToClipboard()
                    true
                }

                android.R.id.cut -> {
                    cutSelectionToClipboard()
                    true
                }

                android.R.id.paste -> {
                    pasteFromClipboard()
                    true
                }

                android.R.id.selectAll -> {
                    document.selectAll()
                    notifySelectionChanged()
                    showSelectionMenu()
                    requestRender()
                    true
                }

                else -> super.performContextMenuAction(id)
            }
        }
    }

    private inner class RenderThread : Thread("CanvasCodeEditorRenderer") {
        @Volatile
        private var running = true

        fun finish() {
            running = false
            interrupt()
        }

        override fun run() {
            while (running) {
                if (!isDirty) {
                    synchronized(renderSignal) {
                        if (!running && !isDirty) {
                            return
                        }
                        try {
                            renderSignal.wait(24L)
                        } catch (_: InterruptedException) {
                            if (!running) {
                                return
                            }
                        }
                    }
                    continue
                }

                if (!holder.surface.isValid) {
                    synchronized(renderSignal) {
                        try {
                            renderSignal.wait(24L)
                        } catch (_: InterruptedException) {
                            if (!running) {
                                return
                            }
                        }
                    }
                    continue
                }

                val canvas =
                    runCatching {
                        holder.lockCanvas()
                    }.getOrNull()

                if (canvas != null) {
                    // Clear the flag before drawing: fling steps now arrive from the main thread
                    // while a frame may be in flight, and a request that lands mid-draw must
                    // survive to trigger the next frame instead of being wiped after the draw.
                    isDirty = false
                    try {
                        drawEditor(canvas)
                    } finally {
                        runCatching {
                            holder.unlockCanvasAndPost(canvas)
                        }
                    }
                }
            }
        }
    }
}
