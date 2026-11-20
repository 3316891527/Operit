package com.ai.assistance.operit.ui.common.markdown.mermaid

internal data class MermaidStatement(val number: Int, val text: String)

internal object MermaidParser {
    fun parse(source: String): MermaidDiagram {
        if (source.length > 200_000) throw MermaidParseException(1, "Diagram exceeds 200000 characters")
        source.lineSequence().forEachIndexed { index, line ->
            if (line.trimStart().startsWith("%%{")) throw MermaidParseException(index + 1, "Configuration directives are not supported")
        }
        val lines = source.lineSequence().mapIndexed { index, text -> MermaidStatement(index + 1, stripComment(text).trim()) }.filter { it.text.isNotBlank() }.toList()
        if (lines.isEmpty()) throw MermaidParseException(1, "Empty diagram")
        val header = lines.first().text.substringBefore(';').trim()
        val kind = header.takeWhile { !it.isWhitespace() }
        val diagram = when (kind) {
            "flowchart", "graph" -> MermaidGraphParser.flow(source)
            "stateDiagram", "stateDiagram-v2" -> MermaidGraphParser.state(lines)
            "classDiagram" -> MermaidGraphParser.classes(lines)
            "erDiagram" -> MermaidGraphParser.entities(lines)
            "mindmap" -> MermaidGraphParser.mindmap(source)
            "sequenceDiagram" -> MermaidChartParser.sequence(lines)
            "pie" -> MermaidChartParser.pie(lines)
            "gantt" -> MermaidChartParser.gantt(lines)
            else -> throw MermaidParseException(lines.first().number, "Unsupported diagram: $kind")
        }
        when (diagram) {
            is MermaidDiagram.Graph -> {
                if (diagram.nodes.isEmpty()) throw MermaidParseException(1, "Diagram has no nodes")
                if (diagram.nodes.size + diagram.groups.size > 400 || diagram.edges.size > 2000) throw MermaidParseException(1, "Diagram exceeds 400 nodes or 2000 edges")
            }
            is MermaidDiagram.Sequence -> if (diagram.actors.isEmpty() || diagram.actors.size > 100 || diagram.events.size > 2000) throw MermaidParseException(1, "Sequence requires 1–100 participants and at most 2000 events")
            is MermaidDiagram.Pie -> if (diagram.slices.isEmpty() || diagram.slices.size > 200 || !diagram.slices.sumOf { it.second }.isFinite() || diagram.slices.sumOf { it.second } <= 0) throw MermaidParseException(1, "Pie requires positive values and at most 200 slices")
            is MermaidDiagram.Gantt -> if (diagram.tasks.isEmpty() || diagram.tasks.size > 400) throw MermaidParseException(1, "Gantt requires 1–400 tasks")
        }
        return diagram
    }

    internal fun stripComment(line: String): String {
        var quoted = false
        var escaped = false
        for (index in line.indices) {
            val c = line[index]
            if (c == '"' && !escaped) quoted = !quoted
            if (!quoted && line.startsWith("%%", index)) return line.take(index)
            escaped = c == '\\' && !escaped
        }
        return line
    }

    internal fun flowStatements(source: String): List<MermaidStatement> {
        val result = mutableListOf<MermaidStatement>()
        val buffer = StringBuilder()
        val stack = ArrayDeque<Char>()
        var quoted = false
        var escaped = false
        var line = 1
        var start = 1
        val clean = source.lineSequence().joinToString("\n", transform = ::stripComment)
        fun flush() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) result += MermaidStatement(start, text)
            buffer.clear()
        }
        for (c in clean) {
            if (buffer.isBlank() && !c.isWhitespace()) start = line
            if (c == '"' && !escaped) quoted = !quoted
            if (!quoted) {
                if (c in "[({") stack.addLast(c)
                if (c in "])}") {
                    val expected = when (c) { ']' -> '['; ')' -> '('; else -> '{' }
                    if (stack.lastOrNull() != expected) throw MermaidParseException(line, "Unmatched $c")
                    stack.removeLast()
                }
            }
            if (!quoted && stack.isEmpty() && (c == ';' || c == '\n')) flush() else buffer.append(c)
            if (c == '\n') line++
            escaped = c == '\\' && !escaped
        }
        if (quoted || stack.isNotEmpty()) throw MermaidParseException(start, "Unclosed node label")
        flush()
        return result
    }

    internal fun label(raw: String): String {
        var text = raw.trim()
        if (text.startsWith('"') && text.endsWith('"') && text.length >= 2) text = text.substring(1, text.length - 1)
        if (text.startsWith('`') && text.endsWith('`') && text.length >= 2) text = text.substring(1, text.length - 1)
        return text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace("\\n", "\n").replace("\\\"", "\"")
            .replace(Regex("</?(?:b|i|strong|em|p|span)(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE), "")
            .replace("&" + "quot;", "\"").replace("#quot;", "\"").replace("&" + "lt;", "<").replace("&" + "gt;", ">").replace("&" + "amp;", "&")
            .replace(Regex("(?:&#|#)([0-9]{1,7});")) { match ->
                val point = match.groupValues[1].toInt()
                if (Character.isValidCodePoint(point) && point !in 0xD800..0xDFFF) String(Character.toChars(point)) else throw IllegalArgumentException("Invalid character entity")
            }
    }

    internal fun direction(raw: String, line: Int): MermaidDirection = when (raw.trim()) {
        "TB", "TD" -> MermaidDirection.TB
        "BT" -> MermaidDirection.BT
        "LR" -> MermaidDirection.LR
        "RL" -> MermaidDirection.RL
        else -> throw MermaidParseException(line, "Unsupported direction: $raw")
    }
}