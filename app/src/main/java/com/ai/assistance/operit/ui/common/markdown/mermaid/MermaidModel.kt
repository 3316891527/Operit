package com.ai.assistance.operit.ui.common.markdown.mermaid

internal enum class MermaidDirection { TB, BT, LR, RL }
internal enum class MermaidShape { RECT, ROUND, STADIUM, DIAMOND, CIRCLE, DOUBLE_CIRCLE, HEXAGON, PARALLELOGRAM, CYLINDER, ASYMMETRIC, INITIAL, FINAL }
internal enum class MermaidMarker { NONE, ARROW, OPEN_ARROW, CIRCLE, CROSS, DIAMOND, HOLLOW_DIAMOND, TRIANGLE, ONE, MANY, ZERO_ONE, ZERO_MANY }
internal enum class MermaidLine { SOLID, DASHED, THICK }
internal data class MermaidStyle(val fill: String? = null, val stroke: String? = null, val color: String? = null)
internal data class MermaidNode(
    val id: String,
    val label: String,
    val shape: MermaidShape = MermaidShape.ROUND,
    val group: String? = null,
    val members: List<String> = emptyList(),
    val style: MermaidStyle = MermaidStyle(),
)
internal data class MermaidEdge(
    val from: String,
    val to: String,
    val label: String = "",
    val line: MermaidLine = MermaidLine.SOLID,
    val start: MermaidMarker = MermaidMarker.NONE,
    val end: MermaidMarker = MermaidMarker.ARROW,
)
internal data class MermaidGroup(val id: String, val title: String, val parent: String?, val direction: MermaidDirection? = null)
internal sealed interface MermaidDiagram {
    data class Graph(val nodes: List<MermaidNode>, val edges: List<MermaidEdge>, val groups: List<MermaidGroup> = emptyList(), val direction: MermaidDirection = MermaidDirection.TB) : MermaidDiagram
    data class Sequence(val actors: List<Pair<String, String>>, val events: List<SequenceEvent>, val title: String = "", val numbered: Boolean = false) : MermaidDiagram
    data class Pie(val slices: List<Pair<String, Double>>, val title: String) : MermaidDiagram
    data class Gantt(val tasks: List<GanttTask>, val title: String) : MermaidDiagram
}
internal sealed interface SequenceEvent {
    data class Message(val from: String, val to: String, val text: String, val dashed: Boolean, val open: Boolean, val activation: Int = 0) : SequenceEvent
    data class Note(val actors: List<String>, val text: String, val side: String) : SequenceEvent
    data class Frame(val kind: String, val label: String) : SequenceEvent
    data class Divider(val label: String) : SequenceEvent
    data object End : SequenceEvent
    data class Activate(val actor: String, val active: Boolean) : SequenceEvent
}
internal data class GanttTask(val id: String, val label: String, val section: String, val start: Long, val end: Long, val state: String)
internal class MermaidParseException(val lineNumber: Int, detail: String) : IllegalArgumentException("Line $lineNumber: $detail")

internal data class MermaidPoint(val x: Float, val y: Float) {
    operator fun plus(other: MermaidPoint) = MermaidPoint(x + other.x, y + other.y)
    operator fun minus(other: MermaidPoint) = MermaidPoint(x - other.x, y - other.y)
    operator fun times(value: Float) = MermaidPoint(x * value, y * value)
}
internal data class MermaidBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val center get() = MermaidPoint((left + right) / 2f, (top + bottom) / 2f)
    fun translate(offset: MermaidPoint) = MermaidBounds(left + offset.x, top + offset.y, right + offset.x, bottom + offset.y)
    fun expand(amount: Float) = MermaidBounds(left - amount, top - amount, right + amount, bottom + amount)
    fun union(other: MermaidBounds) = MermaidBounds(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
    fun contains(other: MermaidBounds, tolerance: Float = 0f) = other.left >= left - tolerance && other.top >= top - tolerance && other.right <= right + tolerance && other.bottom <= bottom + tolerance
}
internal data class MermaidText(val lines: List<String>, val width: Float, val height: Float, val size: Float, val bold: Boolean = false)
internal sealed interface MermaidDraw {
    val bounds: MermaidBounds
    data class Box(override val bounds: MermaidBounds, val shape: MermaidShape, val style: MermaidStyle = MermaidStyle(), val group: Boolean = false) : MermaidDraw
    data class Label(override val bounds: MermaidBounds, val text: MermaidText, val color: String? = null, val background: Boolean = false, val centered: Boolean = true) : MermaidDraw
    data class Line(val points: List<MermaidPoint>, val type: MermaidLine = MermaidLine.SOLID, val start: MermaidMarker = MermaidMarker.NONE, val end: MermaidMarker = MermaidMarker.NONE) : MermaidDraw {
        override val bounds get() = MermaidBounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }).expand(24f)
    }
    data class Slice(override val bounds: MermaidBounds, val start: Float, val sweep: Float, val index: Int) : MermaidDraw
    data class Swatch(override val bounds: MermaidBounds, val index: Int) : MermaidDraw
}
internal data class MermaidScene(val commands: List<MermaidDraw>, val bounds: MermaidBounds, val description: String)
internal fun interface MermaidTextMeasurer { fun width(text: String, size: Float): Float }

/** 初始适配、按钮和手势共享同一变换，范围直接来自全部原生绘制指令。 */
internal class MermaidViewport {
    var scale = 1f
        private set
    var offset = MermaidPoint(0f, 0f)
        private set
    private var fitScale = 1f
    fun fit(bounds: MermaidBounds, width: Float, height: Float) {
        require(bounds.width > 0 && bounds.height > 0 && width > 0 && height > 0)
        fitScale = minOf(width / bounds.width, height / bounds.height)
        scale = fitScale
        offset = MermaidPoint(width / 2f, height / 2f) - bounds.center * scale
    }
    fun zoom(factor: Float, focus: MermaidPoint) {
        if (!factor.isFinite() || factor <= 0f) return
        val next = (scale * factor).coerceIn(fitScale * .2f, maxOf(fitScale * 16f, 4f))
        offset = focus - (focus - offset) * (next / scale)
        scale = next
    }
    fun pan(delta: MermaidPoint) { offset += delta }
    fun screen(bounds: MermaidBounds) = MermaidBounds(bounds.left * scale + offset.x, bounds.top * scale + offset.y, bounds.right * scale + offset.x, bounds.bottom * scale + offset.y)
}
