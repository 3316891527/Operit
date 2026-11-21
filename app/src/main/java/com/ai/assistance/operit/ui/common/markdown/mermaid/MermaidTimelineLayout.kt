package com.ai.assistance.operit.ui.common.markdown.mermaid

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal class MermaidTimelineLayout(private val layout: MermaidLayout) {
    private fun label(text: MermaidText, x: Float, y: Float, background: Boolean = false) = MermaidDraw.Label(MermaidBounds(x, y, x + text.width, y + text.height), text, background = background)

    fun sequence(diagram: MermaidDiagram.Sequence): List<MermaidDraw> {
        val actorText = diagram.actors.associate { it.first to layout.text(it.second, 14f, 150f, bold = true) }
        val widest = actorText.values.maxOf { it.width }
        var gap = max(200f, widest + 70f)
        for (event in diagram.events.filterIsInstance<SequenceEvent.Message>()) {
            val span = abs(diagram.actors.indexOfFirst { it.first == event.from } - diagram.actors.indexOfFirst { it.first == event.to }).coerceAtLeast(1)
            gap = max(gap, (layout.text(event.text, 13f, 320f).width + 70f) / span)
        }
        val xs = diagram.actors.mapIndexed { index, (id, _) -> id to index * gap }.toMap()
        val headerHeight = actorText.values.maxOf { it.height } + 24f
        val title = layout.text(diagram.title, 18f, 600f, bold = true)
        val top = if (diagram.title.isBlank()) 0f else title.height + 22f
        val body = mutableListOf<MermaidDraw>()
        val backgrounds = mutableListOf<MermaidDraw>()
        val frames = ArrayDeque<Pair<Float, Int>>()
        val active = mutableMapOf<String, ArrayDeque<Float>>()
        var y = top + headerHeight + 30f
        var counter = 0
        fun activate(id: String, enabled: Boolean) {
            val stack = active.getOrPut(id) { ArrayDeque() }
            if (enabled) stack.addLast(y) else {
                val start = stack.removeLast()
                val x = xs.getValue(id) + stack.size * 5f
                backgrounds += MermaidDraw.Box(MermaidBounds(x - 6f, start, x + 6f, max(y, start + 12f)), MermaidShape.RECT)
            }
        }
        for (event in diagram.events) {
            when (event) {
                is SequenceEvent.Message -> {
                    counter++
                    val value = layout.text((if (diagram.numbered) "$counter. " else "") + event.text, 13f, 320f)
                    val from = xs.getValue(event.from)
                    val to = xs.getValue(event.to)
                    val lineY = y + value.height + 8f
                    val self = from == to
                    body += label(value, if (self) from + 18f else (from + to - value.width) / 2f, y, background = true)
                    val points = if (self) listOf(MermaidPoint(from, lineY), MermaidPoint(from + max(56f, value.width + 30f), lineY), MermaidPoint(from + max(56f, value.width + 30f), lineY + 28f), MermaidPoint(from, lineY + 28f)) else listOf(MermaidPoint(from, lineY), MermaidPoint(to, lineY))
                    body += MermaidDraw.Line(points, if (event.dashed) MermaidLine.DASHED else MermaidLine.SOLID, end = if (event.open) MermaidMarker.OPEN_ARROW else MermaidMarker.ARROW)
                    if (event.activation > 0) activate(event.to, true)
                    if (event.activation < 0) activate(event.from, false)
                    y = lineY + if (self) 62f else 38f
                }
                is SequenceEvent.Note -> {
                    val value = layout.text(event.text, 13f, 260f)
                    val centers = event.actors.map(xs::getValue)
                    val x = when (event.side) {
                        "right of" -> centers.max() + 20f
                        "left of" -> centers.min() - value.width - 36f
                        else -> (centers.min() + centers.max() - value.width) / 2f - 8f
                    }
                    body += MermaidDraw.Box(MermaidBounds(x, y, x + value.width + 16f, y + value.height + 16f), MermaidShape.RECT, MermaidStyle(fill = "#3e3955"))
                    body += label(value, x + 8f, y + 8f)
                    y += value.height + 34f
                }
                is SequenceEvent.Frame -> {
                    frames.addLast(y to frames.size)
                    val value = layout.text("${event.kind}  ${event.label}", 13f, 420f, bold = true)
                    body += label(value, -70f + frames.size * 10f, y + 8f, background = true)
                    y += value.height + 28f
                }
                is SequenceEvent.Divider -> {
                    val value = layout.text(event.label, 13f, 420f)
                    body += MermaidDraw.Line(listOf(MermaidPoint(-70f, y), MermaidPoint(xs.values.max() + 70f, y)), MermaidLine.DASHED)
                    body += label(value, -54f, y + 8f, background = true)
                    y += value.height + 28f
                }
                SequenceEvent.End -> {
                    val (start, depth) = frames.removeLast()
                    backgrounds += MermaidDraw.Box(MermaidBounds(-80f + depth * 10f, start, xs.values.max() + 80f - depth * 10f, y), MermaidShape.RECT, MermaidStyle(fill = "transparent"), group = true)
                    y += 16f
                }
                is SequenceEvent.Activate -> activate(event.actor, event.active)
            }
        }
        for ((id, stack) in active) while (stack.isNotEmpty()) activate(id, false)
        val commands = mutableListOf<MermaidDraw>()
        if (diagram.title.isNotBlank()) commands += label(title, (xs.values.max() - title.width) / 2f, 0f)
        for ((id, _) in diagram.actors) {
            val x = xs.getValue(id)
            commands += MermaidDraw.Line(listOf(MermaidPoint(x, top + headerHeight), MermaidPoint(x, y)), MermaidLine.DASHED)
        }
        commands += backgrounds
        commands += body
        for ((id, _) in diagram.actors) {
            val x = xs.getValue(id)
            val value = actorText.getValue(id)
            for (actorY in listOf(top, y)) {
                commands += MermaidDraw.Box(MermaidBounds(x - widest / 2f - 16f, actorY, x + widest / 2f + 16f, actorY + headerHeight), MermaidShape.ROUND)
                commands += label(value, x - value.width / 2f, actorY + (headerHeight - value.height) / 2f)
            }
        }
        return commands
    }

    fun pie(diagram: MermaidDiagram.Pie): List<MermaidDraw> {
        val result = mutableListOf<MermaidDraw>()
        val title = layout.text(diagram.title, 18f, 600f, bold = true)
        val top = if (diagram.title.isBlank()) 0f else title.height + 24f
        if (diagram.title.isNotBlank()) result += label(title, 0f, 0f)
        val circle = MermaidBounds(0f, top, 260f, top + 260f)
        val total = diagram.slices.sumOf { it.second }
        var angle = -90f
        var legendY = top
        for ((index, entry) in diagram.slices.withIndex()) {
            val sweep = (entry.second / total * 360).toFloat()
            result += MermaidDraw.Slice(circle, angle, sweep, index)
            val percentage = String.format(Locale.ROOT, "%.1f%%", entry.second / total * 100)
            if (sweep >= 18f) {
                val radians = Math.toRadians((angle + sweep / 2).toDouble())
                val value = layout.text(percentage, 12f, 90f)
                result += label(value, 130f + cos(radians).toFloat() * 82f - value.width / 2f, top + 130f + sin(radians).toFloat() * 82f - value.height / 2f, background = true)
            }
            val value = layout.text("${entry.first}  ${entry.second}  ($percentage)", 14f, 300f)
            result += MermaidDraw.Swatch(MermaidBounds(294f, legendY + 3f, 308f, legendY + 17f), index)
            result += label(value, 320f, legendY)
            legendY += value.height + 14f
            angle += sweep
        }
        return result
    }

    fun gantt(diagram: MermaidDiagram.Gantt): List<MermaidDraw> {
        val result = mutableListOf<MermaidDraw>()
        val labels = diagram.tasks.map { layout.text(it.label, 14f, 200f) }
        val labelWidth = max(100f, labels.maxOf { it.width }) + 24f
        val min = diagram.tasks.minOf { it.start }
        val max = maxOf(min + 1, diagram.tasks.maxOf { it.end })
        val range = (max - min).toFloat()
        val chartWidth = 680f
        val title = layout.text(diagram.title, 18f, 800f, bold = true)
        var y = if (diagram.title.isBlank()) 0f else title.height + 20f
        if (diagram.title.isNotBlank()) result += label(title, 0f, 0f)
        val gridTop = y
        val dateY = y
        y += 34f
        var section: String? = null
        for ((index, task) in diagram.tasks.withIndex()) {
            if (section != task.section && task.section.isNotBlank()) {
                val name = layout.text(task.section, 14f, 600f, bold = true)
                result += label(name, 0f, y)
                y += name.height + 16f
            }
            section = task.section
            val value = labels[index]
            val rowHeight = max(38f, value.height + 16f)
            result += label(value, 0f, y + 7f)
            val x = labelWidth + (task.start - min) / range * chartWidth
            val right = labelWidth + (task.end - min) / range * chartWidth
            val color = when {
                "crit" in task.state -> "#8f3f51"
                "done" in task.state -> "#386854"
                "active" in task.state -> "#356e9f"
                else -> "#4c4382"
            }
            if ("milestone" in task.state) result += MermaidDraw.Box(MermaidBounds(x - 9f, y + 7f, x + 9f, y + 25f), MermaidShape.DIAMOND, MermaidStyle(fill = color))
            else result += MermaidDraw.Box(MermaidBounds(x, y + 7f, max(x + 4f, right), y + rowHeight - 7f), MermaidShape.ROUND, MermaidStyle(fill = color))
            y += rowHeight + 8f
        }
        val grid = mutableListOf<MermaidDraw>()
        for (step in 0..4) {
            val x = labelWidth + chartWidth * step / 4f
            grid += MermaidDraw.Line(listOf(MermaidPoint(x, gridTop + 26f), MermaidPoint(x, y)), MermaidLine.DASHED)
            val date = layout.text(LocalDate.ofEpochDay(min + (max - min) * step / 4).toString(), 12f, 120f)
            grid += label(date, x - date.width / 2f, dateY)
        }
        return grid + result
    }
}