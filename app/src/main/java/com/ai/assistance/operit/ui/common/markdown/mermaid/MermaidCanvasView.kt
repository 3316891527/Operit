package com.ai.assistance.operit.ui.common.markdown.mermaid

import android.content.Context
import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/** Mermaid 的原生绘制表面，不创建浏览器、HTML、SVG 或 JavaScript 上下文。 */
internal class MermaidCanvasView(context: Context) : View(context) {
    internal var scene: MermaidScene? = null
        private set
    internal val viewport = MermaidViewport()
    private val painter = MermaidCanvasPainter()
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewport.zoom(detector.scaleFactor, MermaidPoint(detector.focusX, detector.focusY))
            invalidate()
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleDetector.isInProgress && current.pointerCount == 1) {
                viewport.pan(MermaidPoint(-distanceX, -distanceY))
                invalidate()
            }
            return true
        }
        override fun onDoubleTap(event: MotionEvent): Boolean { fit(); return true }
        override fun onSingleTapUp(event: MotionEvent): Boolean { performClick(); return true }
    })

    init {
        setBackgroundColor(BACKGROUND)
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun render(next: MermaidScene) {
        if (scene === next) return
        scene = next
        contentDescription = next.description
        fit()
    }

    fun fit() {
        val current = scene ?: return
        if (width <= 0 || height <= 0) return
        viewport.fit(current.bounds, width.toFloat(), height.toFloat())
        invalidate()
    }

    fun zoom(factor: Float) {
        if (scene == null || width <= 0 || height <= 0) return
        viewport.zoom(factor, MermaidPoint(width / 2f, height / 2f))
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        fit()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scene == null) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = scene ?: return
        painter.draw(canvas, current, viewport, width, height)
    }

    companion object {
        internal val BACKGROUND get() = MermaidCanvasPainter.BACKGROUND
        fun buildScene(diagram: MermaidDiagram) = MermaidCanvasPainter.buildScene(diagram)
    }
}
