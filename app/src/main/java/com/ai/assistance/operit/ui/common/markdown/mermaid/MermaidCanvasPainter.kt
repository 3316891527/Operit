package com.ai.assistance.operit.ui.common.markdown.mermaid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.hypot

/** 原生绘图器由预览视图调用，所有绘制指令共用同一变换。 */
internal class MermaidCanvasPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val dash = DashPathEffect(floatArrayOf(6f, 5f), 0f)
    private val palette = intArrayOf(0xFF7765BA.toInt(), 0xFF418A9B.toInt(), 0xFFC18C56.toInt(), 0xFF629F78.toInt(), 0xFFB76585.toInt(), 0xFF6485C4.toInt())
    fun draw(canvas: Canvas, scene: MermaidScene, viewport: MermaidViewport, width: Int, height: Int) {
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(BACKGROUND)
        canvas.translate(viewport.offset.x, viewport.offset.y)
        canvas.scale(viewport.scale, viewport.scale)
        for (command in scene.commands) {
            paint.reset()
            paint.isAntiAlias = true
            paint.strokeWidth = 1.5f
            paint.color = INK
            when (command) {
                is MermaidDraw.Box -> box(canvas, command)
                is MermaidDraw.Label -> text(canvas, command)
                is MermaidDraw.Line -> line(canvas, command)
                is MermaidDraw.Slice -> {
                    paint.color = palette[command.index % palette.size]
                    canvas.drawArc(command.bounds.rect(), command.start, command.sweep, true, paint)
                    paint.color = BACKGROUND
                    paint.style = Paint.Style.STROKE
                    canvas.drawArc(command.bounds.rect(), command.start, command.sweep, true, paint)
                }
                is MermaidDraw.Swatch -> {
                    paint.color = palette[command.index % palette.size]
                    canvas.drawRect(command.bounds.rect(), paint)
                }
            }
        }
        canvas.restore()
    }

    private fun MermaidBounds.rect() = RectF(left, top, right, bottom)
    private fun color(value: String?, default: Int): Int {
        if (value == null) return default
        if (value == "transparent") return Color.TRANSPARENT
        val expanded = if (value.startsWith('#') && value.length == 4) "#" + value.drop(1).map { "$it$it" }.joinToString("") else value
        return Color.parseColor(expanded)
    }

    private fun box(canvas: Canvas, command: MermaidDraw.Box) {
        val b = command.bounds
        val rect = b.rect()
        val stroke = color(command.style.stroke, if (command.group) 0xFF666079.toInt() else 0xFFB9A6EF.toInt())
        val fill = color(command.style.fill, if (command.group) 0xFF26232E.toInt() else 0xFF39314F.toInt())
        path.reset()
        fun polygon(vararg points: MermaidPoint) {
            path.moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { path.lineTo(it.x, it.y) }
            path.close()
        }
        when (command.shape) {
            MermaidShape.DIAMOND -> polygon(MermaidPoint(b.center.x, b.top), MermaidPoint(b.right, b.center.y), MermaidPoint(b.center.x, b.bottom), MermaidPoint(b.left, b.center.y))
            MermaidShape.HEXAGON -> polygon(MermaidPoint(b.left + 18f, b.top), MermaidPoint(b.right - 18f, b.top), MermaidPoint(b.right, b.center.y), MermaidPoint(b.right - 18f, b.bottom), MermaidPoint(b.left + 18f, b.bottom), MermaidPoint(b.left, b.center.y))
            MermaidShape.PARALLELOGRAM -> polygon(MermaidPoint(b.left + 20f, b.top), MermaidPoint(b.right, b.top), MermaidPoint(b.right - 20f, b.bottom), MermaidPoint(b.left, b.bottom))
            MermaidShape.ASYMMETRIC -> polygon(MermaidPoint(b.left, b.top), MermaidPoint(b.right - 18f, b.top), MermaidPoint(b.right, b.center.y), MermaidPoint(b.right - 18f, b.bottom), MermaidPoint(b.left, b.bottom), MermaidPoint(b.left + 18f, b.center.y))
            MermaidShape.CIRCLE, MermaidShape.DOUBLE_CIRCLE, MermaidShape.INITIAL, MermaidShape.FINAL -> path.addOval(rect, Path.Direction.CW)
            MermaidShape.ROUND -> path.addRoundRect(rect, 8f, 8f, Path.Direction.CW)
            MermaidShape.STADIUM -> path.addRoundRect(rect, b.height / 2f, b.height / 2f, Path.Direction.CW)
            MermaidShape.CYLINDER -> {
                path.moveTo(b.left, b.top + 10f)
                path.cubicTo(b.left, b.top - 3f, b.right, b.top - 3f, b.right, b.top + 10f)
                path.lineTo(b.right, b.bottom - 10f)
                path.cubicTo(b.right, b.bottom + 3f, b.left, b.bottom + 3f, b.left, b.bottom - 10f)
                path.close()
            }
            MermaidShape.RECT -> path.addRect(rect, Path.Direction.CW)
        }
        paint.color = if (command.shape == MermaidShape.INITIAL) stroke else fill
        canvas.drawPath(path, paint)
        paint.color = stroke
        paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)
        if (command.shape == MermaidShape.DOUBLE_CIRCLE || command.shape == MermaidShape.FINAL) {
            val inner = b.expand(-5f).rect()
            if (command.shape == MermaidShape.FINAL) paint.style = Paint.Style.FILL
            canvas.drawOval(inner, paint)
        }
        if (command.shape == MermaidShape.CYLINDER) canvas.drawOval(RectF(b.left, b.top, b.right, b.top + 20f), paint)
    }

    private fun text(canvas: Canvas, command: MermaidDraw.Label) {
        val b = command.bounds
        if (command.background) {
            paint.color = BACKGROUND
            canvas.drawRoundRect(b.expand(4f).rect(), 3f, 3f, paint)
        }
        paint.color = color(command.color, INK)
        paint.textSize = command.text.size
        paint.typeface = if (command.text.bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        paint.textAlign = if (command.centered) Paint.Align.CENTER else Paint.Align.LEFT
        val metrics = paint.fontMetrics
        val lineHeight = command.text.size * 1.45f
        var baseline = b.top + (lineHeight - metrics.descent + metrics.ascent) / 2f - metrics.ascent
        for (line in command.text.lines) {
            canvas.drawText(line, if (command.centered) b.center.x else b.left, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun line(canvas: Canvas, command: MermaidDraw.Line) {
        path.reset()
        val points = command.points
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (command.type == MermaidLine.THICK) 3f else 1.5f
        paint.strokeJoin = Paint.Join.ROUND
        if (command.type == MermaidLine.DASHED) paint.pathEffect = dash
        canvas.drawPath(path, paint)
        paint.pathEffect = null
        val startDirection = points.drop(1).firstOrNull { it != points.first() }
        if (startDirection != null) marker(canvas, points.first(), startDirection, command.start)
        val endDirection = points.dropLast(1).lastOrNull { it != points.last() }
        if (endDirection != null) marker(canvas, points.last(), endDirection, command.end)
    }

    private fun marker(canvas: Canvas, tip: MermaidPoint, inside: MermaidPoint, marker: MermaidMarker) {
        if (marker == MermaidMarker.NONE) return
        val distance = hypot(tip.x - inside.x, tip.y - inside.y)
        if (distance < .001f) return
        val direction = (tip - inside) * (1f / distance)
        val normal = MermaidPoint(-direction.y, direction.x)
        fun point(back: Float, side: Float) = tip - direction * back + normal * side
        fun segment(a: MermaidPoint, b: MermaidPoint) = canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        path.reset()
        paint.style = Paint.Style.STROKE
        when (marker) {
            MermaidMarker.ARROW, MermaidMarker.OPEN_ARROW, MermaidMarker.TRIANGLE -> {
                val a = point(10f, 5f)
                val b = point(10f, -5f)
                path.moveTo(a.x, a.y); path.lineTo(tip.x, tip.y); path.lineTo(b.x, b.y)
                if (marker != MermaidMarker.OPEN_ARROW) {
                    path.close()
                    paint.style = Paint.Style.FILL
                    paint.color = if (marker == MermaidMarker.TRIANGLE) BACKGROUND else INK
                    canvas.drawPath(path, paint)
                    paint.color = INK
                    paint.style = Paint.Style.STROKE
                }
                canvas.drawPath(path, paint)
            }
            MermaidMarker.CIRCLE -> { val p = point(4f, 0f); canvas.drawCircle(p.x, p.y, 4f, paint) }
            MermaidMarker.CROSS -> { segment(point(8f, 4f), point(0f, -4f)); segment(point(8f, -4f), point(0f, 4f)) }
            MermaidMarker.DIAMOND, MermaidMarker.HOLLOW_DIAMOND -> {
                val a = point(6f, 4f); val b = point(12f, 0f); val c = point(6f, -4f)
                path.moveTo(tip.x, tip.y); path.lineTo(a.x, a.y); path.lineTo(b.x, b.y); path.lineTo(c.x, c.y); path.close()
                paint.style = Paint.Style.FILL
                paint.color = if (marker == MermaidMarker.DIAMOND) INK else BACKGROUND
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE; paint.color = INK
                canvas.drawPath(path, paint)
            }
            MermaidMarker.ONE, MermaidMarker.MANY, MermaidMarker.ZERO_ONE, MermaidMarker.ZERO_MANY -> {
                if (marker == MermaidMarker.MANY || marker == MermaidMarker.ZERO_MANY) {
                    segment(tip + normal * 6f, point(12f, 0f)); segment(tip - normal * 6f, point(12f, 0f))
                } else segment(point(3f, -6f), point(3f, 6f))
                if (marker == MermaidMarker.ZERO_ONE || marker == MermaidMarker.ZERO_MANY) {
                    val p = point(17f, 0f)
                    paint.style = Paint.Style.FILL; paint.color = BACKGROUND
                    canvas.drawCircle(p.x, p.y, 4f, paint)
                    paint.style = Paint.Style.STROKE; paint.color = INK
                    canvas.drawCircle(p.x, p.y, 4f, paint)
                } else segment(point(16f, -6f), point(16f, 6f))
            }
            MermaidMarker.NONE -> Unit
        }
    }

    companion object {
        internal val BACKGROUND = 0xFF1E1E1E.toInt()
        private val INK = 0xFFE6E0F0.toInt()
        fun buildScene(diagram: MermaidDiagram): MermaidScene {
            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF }
            return MermaidLayout { text, size -> measurePaint.textSize = size; measurePaint.measureText(text) }.layout(diagram)
        }
    }
}
