package com.ai.assistance.operit.ui.common.markdown.mermaid

/** 只接受已实现的语法；无法解析的语句保留行号并报错，不能默默丢失节点和关系。 */
internal object MermaidGraphParser {
    private class Builder {
        val nodes = linkedMapOf<String, MermaidNode>()
        val groups = linkedMapOf<String, MermaidGroup>()
        val edges = mutableListOf<MermaidEdge>()
        val stack = ArrayDeque<String>()
        var direction = MermaidDirection.TB
        fun node(id: String, label: String? = null, shape: MermaidShape? = null): String {
            if (id in groups) return id
            val previous = nodes[id]
            nodes[id] = MermaidNode(id, label ?: previous?.label ?: id, shape ?: previous?.shape ?: MermaidShape.ROUND, if (previous == null) stack.lastOrNull() else previous.group, previous?.members.orEmpty(), previous?.style ?: MermaidStyle())
            if (nodes.size > 400) throw MermaidParseException(1, "Diagram exceeds 400 nodes")
            return id
        }
        fun group(id: String, title: String, line: Int) {
            if (id in groups || id in nodes) throw MermaidParseException(line, "Duplicate group: $id")
            if (stack.size >= 24) throw MermaidParseException(line, "Groups exceed 24 levels")
            groups[id] = MermaidGroup(id, title, stack.lastOrNull())
            stack.addLast(id)
        }
        fun result(): MermaidDiagram.Graph = MermaidDiagram.Graph(nodes.values.toList(), edges.toList(), groups.values.toList(), direction)
    }
    private class FlowCursor(val text: String, val number: Int, val builder: Builder, val assignments: MutableMap<String, String>) {
        var at = 0
        fun skip() { while (at < text.length && text[at].isWhitespace()) at++ }
        fun remaining(): String { skip(); return text.substring(at) }
        fun fail(): Nothing = throw MermaidParseException(number, "Unsupported syntax near: ${text.substring(at).take(80)}")
        fun node(): String {
            skip()
            val start = at
            while (at < text.length && (text[at].isLetterOrDigit() || text[at] in "_.-")) {
                if (text.startsWith("--", at) || text.startsWith("-.", at)) break
                at++
            }
            if (start == at) fail()
            val id = text.substring(start, at)
            skip()
            val shapes = listOf(
                "(((" to (")))" to MermaidShape.DOUBLE_CIRCLE),
                "([" to ("])" to MermaidShape.STADIUM),
                "[(" to (")]" to MermaidShape.CYLINDER),
                "((" to ("))" to MermaidShape.CIRCLE),
                "{{" to ("}}" to MermaidShape.HEXAGON),
                "[/" to ("/]" to MermaidShape.PARALLELOGRAM),
                "[\\" to ("\\]" to MermaidShape.PARALLELOGRAM),
                "[" to ("]" to MermaidShape.RECT),
                "(" to (")" to MermaidShape.ROUND),
                "{" to ("}" to MermaidShape.DIAMOND),
                ">" to ("]" to MermaidShape.ASYMMETRIC),
            )
            val shape = shapes.firstOrNull { text.startsWith(it.first, at) }
            if (shape == null) builder.node(id) else {
                at += shape.first.length
                val labelStart = at
                var quoted = false
                var escaped = false
                while (at < text.length && (quoted || !text.startsWith(shape.second.first, at))) {
                    if (text[at] == '"' && !escaped) quoted = !quoted
                    escaped = text[at] == '\\' && !escaped
                    at++
                }
                if (at >= text.length) fail()
                builder.node(id, MermaidParser.label(text.substring(labelStart, at)), shape.second.second)
                at += shape.second.first.length
            }
            skip()
            if (text.startsWith(":::" , at)) {
                at += 3
                val classStart = at
                while (at < text.length && (text[at].isLetterOrDigit() || text[at] in "_-")) at++
                if (classStart == at) fail()
                assignments[id] = text.substring(classStart, at)
            }
            return id
        }
        fun nodeList(): List<String> {
            val result = mutableListOf(node())
            skip()
            while (at < text.length && text[at] == '&') { at++; result += node(); skip() }
            return result
        }
        fun edge(): MermaidEdge {
            skip()
            val match = Regex("^(<|[ox])?(-{2,}|={2,}|-\\.-)(>|[ox])?").find(text.substring(at)) ?: fail()
            at += match.value.length
            var label = ""
            var arrow = match.value
            skip()
            if (at < text.length && text[at] == '|') {
                at++
                val end = text.indexOf('|', at)
                if (end < 0) fail()
                label = MermaidParser.label(text.substring(at, end))
                at = end + 1
            } else if (arrow in listOf("--", "==", "-.") ) {
                val end = Regex("-->|---|==>|===|\\.->|\\.-").find(text, at) ?: fail()
                label = MermaidParser.label(text.substring(at, end.range.first))
                arrow += end.value
                at = end.range.last + 1
            }
            val line = when { '=' in arrow -> MermaidLine.THICK; '.' in arrow -> MermaidLine.DASHED; else -> MermaidLine.SOLID }
            val from = when (arrow.first()) { '<' -> MermaidMarker.ARROW; 'o' -> MermaidMarker.CIRCLE; 'x' -> MermaidMarker.CROSS; else -> MermaidMarker.NONE }
            val to = when (arrow.last()) { '>' -> MermaidMarker.ARROW; 'o' -> MermaidMarker.CIRCLE; 'x' -> MermaidMarker.CROSS; else -> MermaidMarker.NONE }
            return MermaidEdge("", "", label, line, from, to)
        }
    }

    fun flow(source: String): MermaidDiagram.Graph {
        val lines = MermaidParser.flowStatements(source)
        val b = Builder()
        val header = lines.first()
        val words = header.text.split(Regex("\\s+"))
        if (words.size != 2) throw MermaidParseException(header.number, "Expected flowchart direction, for example flowchart TD")
        b.direction = MermaidParser.direction(words[1], header.number)
        val classes = mutableMapOf<String, MermaidStyle>()
        val assignments = mutableMapOf<String, String>()
        val explicitStyles = mutableMapOf<String, MermaidStyle>()
        for ((line, text) in lines.drop(1)) {
            when {
                text.startsWith("subgraph ") -> {
                    val raw = text.removePrefix("subgraph ").trim()
                    val named = Regex("^([\\p{L}\\p{N}_.-]+)\\s*\\[(.*)]$", RegexOption.DOT_MATCHES_ALL).matchEntire(raw)
                    val id = named?.groupValues?.get(1) ?: if (raw.matches(Regex("[\\p{L}\\p{N}_.-]+"))) raw else "__group_${b.groups.size}"
                    b.group(id, MermaidParser.label(named?.groupValues?.get(2) ?: raw), line)
                }
                text == "end" -> if (b.stack.isEmpty()) throw MermaidParseException(line, "Unexpected end") else b.stack.removeLast()
                text.startsWith("direction ") -> {
                    val direction = MermaidParser.direction(text.removePrefix("direction "), line)
                    val group = b.stack.lastOrNull()
                    if (group == null) b.direction = direction else b.groups[group] = b.groups.getValue(group).copy(direction = direction)
                }
                text.startsWith("classDef ") -> {
                    val parts = text.removePrefix("classDef ").split(Regex("\\s+"), limit = 2)
                    if (parts.size != 2) throw MermaidParseException(line, "Expected classDef name style")
                    parts[0].split(',').forEach { classes[it] = style(parts[1], line) }
                }
                text.startsWith("class ") -> {
                    val parts = text.removePrefix("class ").split(Regex("\\s+"), limit = 2)
                    if (parts.size != 2) throw MermaidParseException(line, "Expected class node name")
                    parts[0].split(',').forEach { assignments[it] = parts[1] }
                }
                text.startsWith("style ") -> {
                    val parts = text.removePrefix("style ").split(Regex("\\s+"), limit = 2)
                    if (parts.size != 2) throw MermaidParseException(line, "Expected style node properties")
                    explicitStyles[parts[0]] = style(parts[1], line)
                }
                else -> {
                    if (text.startsWith("click ") || text.startsWith("linkStyle ")) throw MermaidParseException(line, "Unsupported statement: ${text.substringBefore(' ')}")
                    val cursor = FlowCursor(text, line, b, assignments)
                    var from = cursor.nodeList()
                    while (cursor.remaining().isNotEmpty()) {
                        val edge = cursor.edge()
                        val to = cursor.nodeList()
                        for (a in from) for (z in to) b.edges += edge.copy(from = a, to = z)
                        if (b.edges.size > 2000) throw MermaidParseException(line, "Diagram exceeds 2000 edges")
                        from = to
                    }
                }
            }
        }
        if (b.stack.isNotEmpty()) throw MermaidParseException(lines.last().number, "Unclosed subgraph")
        for ((id, node) in b.nodes.toMap()) {
            val className = assignments[id]
            val assigned = if (className == null) classes["default"] else classes[className] ?: throw MermaidParseException(1, "Unknown class: $className")
            b.nodes[id] = node.copy(style = explicitStyles[id] ?: assigned ?: node.style)
        }
        return b.result()
    }

    private fun style(raw: String, line: Int): MermaidStyle {
        val values = raw.split(',').associate { declaration ->
            val parts = declaration.trim().split(':', limit = 2)
            if (parts.size != 2 || parts[0] !in setOf("fill", "stroke", "color")) throw MermaidParseException(line, "Supported style properties: fill, stroke, color")
            val color = parts[1].trim()
            if (!color.matches(Regex("#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?|transparent|black|white|red|green|blue|gray|grey|yellow|cyan|magenta"))) throw MermaidParseException(line, "Unsupported color: $color")
            parts[0] to color
        }
        return MermaidStyle(values["fill"], values["stroke"], values["color"])
    }

    fun state(lines: List<MermaidStatement>): MermaidDiagram.Graph {
        val b = Builder()
        fun stateId(raw: String, initial: Boolean): String {
            val id = raw.trim()
            if (id == "[*]") return b.node("__${b.stack.lastOrNull().orEmpty()}_${if (initial) "start" else "end"}", "", if (initial) MermaidShape.INITIAL else MermaidShape.FINAL)
            if (!id.matches(Regex("[\\p{L}\\p{N}_.-]+"))) throw MermaidParseException(1, "Invalid state identifier: $id")
            return b.node(id)
        }
        for ((line, text) in lines.drop(1)) {
            when {
                text == "}" -> if (b.stack.isEmpty()) throw MermaidParseException(line, "Unexpected }") else b.stack.removeLast()
                text.startsWith("direction ") -> b.direction = MermaidParser.direction(text.removePrefix("direction "), line)
                text.startsWith("state ") -> {
                    val block = text.endsWith('{')
                    val body = text.removePrefix("state ").removeSuffix("{").trim()
                    val named = Regex("^\"(.*)\"\\s+as\\s+([\\p{L}\\p{N}_.-]+)$").matchEntire(body)
                    val id = named?.groupValues?.get(2) ?: body
                    val label = named?.groupValues?.get(1) ?: body
                    if (block) b.group(id, label, line) else b.node(id, MermaidParser.label(label))
                }
                "-->" in text -> {
                    val relation = text.substringBefore(':').split("-->")
                    if (relation.size != 2) throw MermaidParseException(line, "Invalid state transition")
                    b.edges += MermaidEdge(stateId(relation[0], true), stateId(relation[1], false), MermaidParser.label(text.substringAfter(':', "")))
                }
                ':' in text -> {
                    val id = stateId(text.substringBefore(':'), true)
                    b.nodes[id] = b.nodes.getValue(id).let { it.copy(label = it.label + "\n" + MermaidParser.label(text.substringAfter(':'))) }
                }
                else -> throw MermaidParseException(line, "Unsupported state statement: $text")
            }
        }
        if (b.stack.isNotEmpty()) throw MermaidParseException(lines.last().number, "Unclosed composite state")
        return b.result()
    }

    fun classes(lines: List<MermaidStatement>): MermaidDiagram.Graph {
        val b = Builder()
        b.direction = MermaidDirection.TB
        var current: String? = null
        val relation = Regex("^([\\p{L}\\p{N}_.-]+)\\s*(<\\|--|--\\|>|<\\|\\.\\.|\\.\\.\\|>|\\*--|--\\*|o--|--o|<--|-->|\\.\\.>|<\\.\\.|--|\\.\\.)\\s*([\\p{L}\\p{N}_.-]+)(?:\\s*:\\s*(.*))?$")
        for ((line, text) in lines.drop(1)) {
            val active = current
            val edge = relation.matchEntire(text)
            when {
                active != null -> {
                    if (text == "}") current = null else b.nodes[active] = b.nodes.getValue(active).let { it.copy(members = it.members + MermaidParser.label(text)) }
                }
                text.startsWith("direction ") -> b.direction = MermaidParser.direction(text.removePrefix("direction "), line)
                text.startsWith("class ") -> {
                    val raw = text.removePrefix("class ")
                    val id = raw.substringBefore('{').substringBefore('[').trim()
                    if (!id.matches(Regex("[\\p{L}\\p{N}_.-]+"))) throw MermaidParseException(line, "Unsupported class declaration")
                    val label = if ('[' in raw) MermaidParser.label(raw.substringAfter('[').substringBeforeLast(']')) else id
                    b.node(id, label, MermaidShape.RECT)
                    if ('{' in raw) {
                        if (raw.endsWith('}')) b.nodes[id] = b.nodes.getValue(id).copy(members = raw.substringAfter('{').substringBeforeLast('}').split(';').map(String::trim).filter(String::isNotBlank)) else current = id
                    }
                }
                edge != null -> {
                    val (from, arrow, to, label) = edge.destructured
                    fun marker(value: String) = when (value) { "<|", "|>" -> MermaidMarker.TRIANGLE; "*" -> MermaidMarker.DIAMOND; "o" -> MermaidMarker.HOLLOW_DIAMOND; "<", ">" -> MermaidMarker.OPEN_ARROW; else -> MermaidMarker.NONE }
                    b.edges += MermaidEdge(b.node(from, shape = MermaidShape.RECT), b.node(to, shape = MermaidShape.RECT), MermaidParser.label(label), if ('.' in arrow) MermaidLine.DASHED else MermaidLine.SOLID, marker(arrow.substringBefore(if ('.' in arrow) ".." else "--")), marker(arrow.substringAfter(if ('.' in arrow) ".." else "--")))
                }
                ':' in text -> {
                    val id = b.node(text.substringBefore(':').trim(), shape = MermaidShape.RECT)
                    b.nodes[id] = b.nodes.getValue(id).let { it.copy(members = it.members + MermaidParser.label(text.substringAfter(':'))) }
                }
                else -> throw MermaidParseException(line, "Unsupported class statement: $text")
            }
        }
        if (current != null) throw MermaidParseException(lines.last().number, "Unclosed class")
        return b.result()
    }

    fun entities(lines: List<MermaidStatement>): MermaidDiagram.Graph {
        val b = Builder()
        b.direction = MermaidDirection.LR
        var current: String? = null
        val relation = Regex("^([\\p{L}\\p{N}_-]+)\\s+([|o}{]{1,2})(--|\\.\\.)([|o}{]{1,2})\\s+([\\p{L}\\p{N}_-]+)\\s*:\\s*(.*)$")
        fun marker(value: String): MermaidMarker = when {
            ('{' in value || '}' in value) && 'o' in value -> MermaidMarker.ZERO_MANY
            '{' in value || '}' in value -> MermaidMarker.MANY
            'o' in value -> MermaidMarker.ZERO_ONE
            else -> MermaidMarker.ONE
        }
        for ((line, text) in lines.drop(1)) {
            val active = current
            val edge = relation.matchEntire(text)
            when {
                active != null -> if (text == "}") current = null else b.nodes[active] = b.nodes.getValue(active).let { it.copy(members = it.members + text) }
                edge != null -> {
                    val (from, start, type, end, to, label) = edge.destructured
                    b.edges += MermaidEdge(b.node(from, shape = MermaidShape.RECT), b.node(to, shape = MermaidShape.RECT), MermaidParser.label(label), if (type == "..") MermaidLine.DASHED else MermaidLine.SOLID, marker(start), marker(end))
                }
                text.endsWith('{') -> current = b.node(text.removeSuffix("{").trim(), shape = MermaidShape.RECT)
                text.matches(Regex("[\\p{L}\\p{N}_-]+")) -> b.node(text, shape = MermaidShape.RECT)
                else -> throw MermaidParseException(line, "Unsupported ER statement: $text")
            }
        }
        if (current != null) throw MermaidParseException(lines.last().number, "Unclosed entity")
        return b.result()
    }

    fun mindmap(source: String): MermaidDiagram.Graph {
        val b = Builder()
        b.direction = MermaidDirection.LR
        val parents = ArrayDeque<Pair<Int, String>>()
        var started = false
        for ((index, raw) in source.lines().withIndex()) {
            val clean = MermaidParser.stripComment(raw)
            val text = clean.trim()
            if (text.isBlank()) continue
            if (!started) { started = true; continue }
            if (text.startsWith("::")) throw MermaidParseException(index + 1, "Mindmap icons and classes are not supported")
            val indent = clean.takeWhile(Char::isWhitespace).fold(0) { total, character -> total + if (character == '\t') 4 else 1 }
            val id = "__mind_${b.nodes.size}"
            val opening = text.indexOfFirst { it in "([{" }
            val shaped = if (opening >= 0 && text.take(opening).all { it.isLetterOrDigit() || it in "_-" }) text.substring(opening) else null
            val spec = if (shaped == null) null else listOf("((" to "))", "(" to ")", "[" to "]", "{" to "}").first { shaped.startsWith(it.first) }
            if (spec != null && !shaped!!.endsWith(spec.second)) throw MermaidParseException(index + 1, "Unclosed mindmap node")
            val label = MermaidParser.label(if (spec == null) text else shaped!!.substring(spec.first.length, shaped.length - spec.second.length))
            val shape = when (spec?.first) { "((" -> MermaidShape.CIRCLE; "[" -> MermaidShape.RECT; "{" -> MermaidShape.HEXAGON; else -> MermaidShape.ROUND }
            b.node(id, label, shape)
            while (parents.isNotEmpty() && parents.last().first >= indent) parents.removeLast()
            if (parents.isEmpty() && b.nodes.size > 1) throw MermaidParseException(index + 1, "Mindmap requires one indented root")
            parents.lastOrNull()?.let { b.edges += MermaidEdge(it.second, id, end = MermaidMarker.NONE) }
            parents.addLast(indent to id)
        }
        return b.result()
    }
}