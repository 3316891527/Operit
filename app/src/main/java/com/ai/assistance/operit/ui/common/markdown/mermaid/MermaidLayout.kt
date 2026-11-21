package com.ai.assistance.operit.ui.common.markdown.mermaid

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** 布局与 Android 分离，所有绘制内容都有明确边界，可在 JVM 中验证完整适配。 */
internal class MermaidLayout(private val measurer: MermaidTextMeasurer) {
    internal fun text(value: String, size: Float = 14f, maxWidth: Float = 220f, bold: Boolean = false): MermaidText {
        val lines = mutableListOf<String>()
        for (paragraph in value.split('\n')) {
            var line = ""
            var at = 0
            while (at < paragraph.length) {
                val count = Character.charCount(paragraph.codePointAt(at))
                val next = paragraph.substring(at, at + count)
                if (line.isNotEmpty() && measurer.width(line + next, size) > maxWidth) { lines += line; line = "" }
                line += next
                at += count
            }
            lines += line
        }
        // 加粗文字留出额外宽度，防止字形靠近边框。
        val width = lines.maxOf { measurer.width(it, size) } * if (bold) 1.08f else 1f
        return MermaidText(lines, max(1f, width), max(size * 1.45f, lines.size * size * 1.45f), size, bold)
    }

    fun layout(diagram: MermaidDiagram): MermaidScene {
        val commands = when (diagram) {
            is MermaidDiagram.Graph -> graph(diagram)
            is MermaidDiagram.Sequence -> sequence(diagram)
            is MermaidDiagram.Pie -> pie(diagram)
            is MermaidDiagram.Gantt -> gantt(diagram)
        }
        require(commands.isNotEmpty()) { "Diagram has no drawable content" }
        val bounds = commands.map { it.bounds }.reduce(MermaidBounds::union).expand(20f)
        require(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite) && bounds.width > 0 && bounds.height > 0) { "Invalid diagram bounds" }
        val description = commands.filterIsInstance<MermaidDraw.Label>().joinToString("; ") { it.text.lines.joinToString(" ") }
        return MermaidScene(commands, bounds, description)
    }

    private fun label(value: MermaidText, x: Float, y: Float, color: String? = null, background: Boolean = false, centered: Boolean = true) = MermaidDraw.Label(MermaidBounds(x, y, x + value.width, y + value.height), value, color, background, centered)
    private data class UnitLayout(val id: String, val width: Float, val height: Float, val commands: List<MermaidDraw>, val boxes: Map<String, MermaidBounds>)
    private fun move(command: MermaidDraw, offset: MermaidPoint): MermaidDraw = when (command) {
        is MermaidDraw.Box -> command.copy(bounds = command.bounds.translate(offset))
        is MermaidDraw.Label -> command.copy(bounds = command.bounds.translate(offset))
        is MermaidDraw.Line -> command.copy(points = command.points.map { it + offset })
        is MermaidDraw.Slice -> command.copy(bounds = command.bounds.translate(offset))
        is MermaidDraw.Swatch -> command.copy(bounds = command.bounds.translate(offset))
    }

    private fun node(node: MermaidNode): UnitLayout {
        val title = text(node.label, bold = node.members.isNotEmpty())
        val members = node.members.map { text(it, 13f, 260f) }
        var width = max(76f, max(title.width, members.maxOfOrNull { it.width } ?: 0f) + 32f)
        var height = title.height + 26f + if (members.isEmpty()) 0f else members.sumOf { it.height.toDouble() }.toFloat() + 18f
        when (node.shape) {
            MermaidShape.DIAMOND -> { width *= 2f; height *= 2f }
            MermaidShape.CIRCLE, MermaidShape.DOUBLE_CIRCLE -> { val diameter = hypot(width, height) + 8f; width = diameter; height = diameter }
            MermaidShape.HEXAGON, MermaidShape.PARALLELOGRAM, MermaidShape.ASYMMETRIC -> width += 36f
            MermaidShape.CYLINDER -> height += 20f
            MermaidShape.INITIAL, MermaidShape.FINAL -> { width = 24f; height = 24f }
            else -> Unit
        }
        val bounds = MermaidBounds(0f, 0f, width, height)
        val commands = mutableListOf<MermaidDraw>(MermaidDraw.Box(bounds, node.shape, node.style))
        if (node.shape !in setOf(MermaidShape.INITIAL, MermaidShape.FINAL)) {
            var y = if (members.isEmpty()) (height - title.height) / 2f else 13f
            commands += label(title, (width - title.width) / 2f, y, node.style.color)
            if (members.isNotEmpty()) {
                y += title.height + 8f
                commands += MermaidDraw.Line(listOf(MermaidPoint(0f, y), MermaidPoint(width, y)))
                y += 8f
                for (member in members) {
                    commands += label(member, 16f, y, node.style.color, centered = false)
                    y += member.height
                }
            }
        }
        return UnitLayout(node.id, width, height, commands, mapOf(node.id to bounds))
    }

    private fun graph(graph: MermaidDiagram.Graph): List<MermaidDraw> {
        val groups = graph.groups.associateBy { it.id }
        val nodes = graph.nodes.associateBy { it.id }
        fun owner(id: String, parent: String?): String? {
            var current = id
            var group = nodes[id]?.group ?: groups[id]?.parent
            while (group != parent) {
                if (group == null) return null
                current = group
                group = groups.getValue(group).parent
            }
            return current
        }
        fun compound(parent: String?, inherited: MermaidDirection): UnitLayout {
            val group = parent?.let(groups::getValue)
            val direction = group?.direction ?: inherited
            val units = graph.nodes.filter { it.group == parent }.map(::node) + graph.groups.filter { it.parent == parent }.map { compound(it.id, direction) }
            if (units.isEmpty()) throw IllegalArgumentException("Empty subgraph: ${group?.title.orEmpty()}")
            val ids = units.map { it.id }
            val relations = graph.edges.mapNotNull { edge ->
                val from = owner(edge.from, parent)
                val to = owner(edge.to, parent)
                if (from != null && to != null && from != to && from in ids && to in ids) from to to else null
            }.distinct()
            val ranks = mutableMapOf<String, Int>()
            val remaining = ids.toMutableSet()
            // 无入边节点先布局；有向环以稳定声明顺序断开排序依赖，关系仍完整绘制。
            while (remaining.isNotEmpty()) {
                val id = remaining.firstOrNull { candidate -> relations.none { it.second == candidate && it.first in remaining } } ?: remaining.first()
                ranks[id] = relations.filter { it.second == id && it.first in ranks }.maxOfOrNull { ranks.getValue(it.first) + 1 } ?: 0
                remaining.remove(id)
            }
            val horizontal = direction == MermaidDirection.LR || direction == MermaidDirection.RL
            val layers = units.groupBy { ranks.getValue(it.id) }.toSortedMap().values.map { layer ->
                layer.sortedBy { unit ->
                    val incoming = relations.filter { it.second == unit.id }.map { ids.indexOf(it.first) }
                    if (incoming.isEmpty()) ids.indexOf(unit.id).toFloat() else incoming.average().toFloat()
                }
            }
            val crossSizes = layers.map { layer -> layer.sumOf { (if (horizontal) it.height else it.width).toDouble() }.toFloat() + (layer.size - 1) * 40f }
            val cross = crossSizes.max()
            val alongSizes = layers.map { layer -> layer.maxOf { if (horizontal) it.width else it.height } }
            val edgeTextHeight = graph.edges.maxOfOrNull { text(it.label, 12f, 160f).height } ?: 0f
            val gap = max(74f, edgeTextHeight + 40f)
            val along = alongSizes.sum() + (layers.size - 1) * gap
            val title = group?.let { text(it.title, 14f, maxWidth = max(cross, 180f), bold = true) }
            val insetX = if (group == null) 0f else 22f
            val insetY = if (group == null) 0f else title!!.height + 26f
            val width = max(if (horizontal) along else cross, (title?.width ?: 0f)) + insetX * 2f
            val height = (if (horizontal) cross else along) + insetY + if (group == null) 0f else 22f
            val commands = mutableListOf<MermaidDraw>()
            val boxes = mutableMapOf<String, MermaidBounds>()
            if (group != null) {
                val bounds = MermaidBounds(0f, 0f, width, height)
                boxes[group.id] = bounds
                commands += MermaidDraw.Box(bounds, MermaidShape.ROUND, group = true)
                commands += label(title!!, 16f, 10f)
            }
            var position = 0f
            layers.forEachIndexed { index, layer ->
                var across = (cross - crossSizes[index]) / 2f
                for (unit in layer) {
                    val unitAlong = if (horizontal) unit.width else unit.height
                    var main = position + (alongSizes[index] - unitAlong) / 2f
                    if (direction == MermaidDirection.RL || direction == MermaidDirection.BT) main = along - main - unitAlong
                    val offset = if (horizontal) MermaidPoint(main + insetX, across + insetY) else MermaidPoint(across + insetX, main + insetY)
                    commands += unit.commands.map { move(it, offset) }
                    unit.boxes.forEach { (id, box) -> boxes[id] = box.translate(offset) }
                    across += (if (horizontal) unit.height else unit.width) + 40f
                }
                position += alongSizes[index] + gap
            }
            return UnitLayout(parent.orEmpty(), width, height, commands, boxes)
        }
        val root = compound(null, graph.direction)
        val edges = mutableListOf<MermaidDraw>()
        val horizontal = graph.direction == MermaidDirection.LR || graph.direction == MermaidDirection.RL
        graph.edges.forEachIndexed { index, edge ->
            val from = root.boxes[edge.from] ?: throw IllegalArgumentException("Unknown node: ${edge.from}")
            val to = root.boxes[edge.to] ?: throw IllegalArgumentException("Unknown node: ${edge.to}")
            val points: List<MermaidPoint>
            val caption: MermaidPoint
            if (edge.from == edge.to) {
                val x = from.right + 32f
                points = listOf(MermaidPoint(from.right, from.center.y - 7f), MermaidPoint(x, from.center.y - 7f), MermaidPoint(x, from.top - 26f), MermaidPoint(from.center.x, from.top - 26f), MermaidPoint(from.center.x, from.top))
                caption = MermaidPoint(x + 6f, from.top - 28f)
            } else if (horizontal && abs(to.center.x - from.center.x) > 20f) {
                val forward = to.center.x > from.center.x
                val a = MermaidPoint(if (forward) from.right else from.left, from.center.y)
                val z = MermaidPoint(if (forward) to.left else to.right, to.center.y)
                val obstacles = root.boxes.filterKeys { it != edge.from && it != edge.to && it !in groups }.values
                val between = obstacles.any { it.left > minOf(a.x, z.x) && it.right < maxOf(a.x, z.x) }
                if (between || forward == (graph.direction == MermaidDirection.RL)) {
                    val y = root.height + 32f + index % 8 * 18f
                    val ax = a.x + if (forward) 22f else -22f
                    val zx = z.x + if (forward) -22f else 22f
                    points = listOf(a, MermaidPoint(ax, a.y), MermaidPoint(ax, y), MermaidPoint(zx, y), MermaidPoint(zx, z.y), z)
                    caption = MermaidPoint((ax + zx) / 2f, y - 8f)
                } else {
                    val x = (a.x + z.x) / 2f
                    points = listOf(a, MermaidPoint(x, a.y), MermaidPoint(x, z.y), z)
                    caption = MermaidPoint(x, (a.y + z.y) / 2f - 8f)
                }
            } else {
                val forward = to.center.y > from.center.y
                val a = MermaidPoint(from.center.x, if (forward) from.bottom else from.top)
                val z = MermaidPoint(to.center.x, if (forward) to.top else to.bottom)
                val obstacles = root.boxes.filterKeys { it != edge.from && it != edge.to && it !in groups }.values
                val between = obstacles.any { it.top > minOf(a.y, z.y) && it.bottom < maxOf(a.y, z.y) }
                if (between || (!horizontal && forward == (graph.direction == MermaidDirection.BT)) || abs(a.y - z.y) < 16f) {
                    val x = root.width + 32f + index % 8 * 18f
                    val ay = a.y + if (forward) 22f else -22f
                    val zy = z.y + if (forward) -22f else 22f
                    points = listOf(a, MermaidPoint(a.x, ay), MermaidPoint(x, ay), MermaidPoint(x, zy), MermaidPoint(z.x, zy), z)
                    caption = MermaidPoint(x + 8f, (ay + zy) / 2f)
                } else {
                    val y = (a.y + z.y) / 2f
                    points = listOf(a, MermaidPoint(a.x, y), MermaidPoint(z.x, y), z)
                    caption = MermaidPoint((a.x + z.x) / 2f, y - 8f)
                }
            }
            edges += MermaidDraw.Line(points, edge.line, edge.start, edge.end)
            if (edge.label.isNotBlank()) {
                val value = text(edge.label, 12f, 160f)
                edges += label(value, caption.x - value.width / 2f, caption.y - value.height, background = true)
            }
        }
        return root.commands.filter { it is MermaidDraw.Box && it.group } + edges + root.commands.filterNot { it is MermaidDraw.Box && it.group }
    }

    private fun sequence(diagram: MermaidDiagram.Sequence): List<MermaidDraw> = MermaidTimelineLayout(this).sequence(diagram)
    private fun pie(diagram: MermaidDiagram.Pie): List<MermaidDraw> = MermaidTimelineLayout(this).pie(diagram)
    private fun gantt(diagram: MermaidDiagram.Gantt): List<MermaidDraw> = MermaidTimelineLayout(this).gantt(diagram)
}