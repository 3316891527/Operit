package com.ai.assistance.operit.ui.common.markdown.mermaid

import java.time.LocalDate
import java.time.format.DateTimeParseException

internal object MermaidChartParser {
    fun sequence(lines: List<MermaidStatement>): MermaidDiagram.Sequence {
        val actors = linkedMapOf<String, String>()
        val events = mutableListOf<SequenceEvent>()
        var title = ""
        var numbered = false
        var frames = 0
        val activations = mutableMapOf<String, Int>()
        fun actor(id: String): String {
            if (!id.matches(Regex("[\\p{L}\\p{N}_.-]+"))) throw MermaidParseException(1, "Invalid participant: $id")
            actors.putIfAbsent(id, id)
            return id
        }
        fun activation(id: String, active: Boolean, line: Int) {
            val next = activations.getOrDefault(id, 0) + if (active) 1 else -1
            if (next < 0) throw MermaidParseException(line, "Participant is not active: $id")
            activations[id] = next
        }
        for ((line, text) in lines.drop(1)) {
            val message = Regex("^([\\p{L}\\p{N}_.-]+?)(-->>|->>|-->|->)([+-]?)([\\p{L}\\p{N}_.-]+)\\s*:\\s*(.*)$").matchEntire(text)
            val note = Regex("^[Nn]ote\\s+(right of|left of|over)\\s+([^:]+):\\s*(.*)$").matchEntire(text)
            when {
                text.startsWith("participant ") || text.startsWith("actor ") -> {
                    val declaration = text.substringAfter(' ').split(Regex("\\s+as\\s+"), limit = 2)
                    val id = actor(declaration[0].trim())
                    if (declaration.size == 2) actors[id] = MermaidParser.label(declaration[1])
                }
                text.startsWith("title ") -> title = MermaidParser.label(text.removePrefix("title "))
                text == "autonumber" -> numbered = true
                text.startsWith("activate ") || text.startsWith("deactivate ") -> {
                    val id = actor(text.substringAfter(' ').trim())
                    val active = text.startsWith("activate ")
                    activation(id, active, line)
                    events += SequenceEvent.Activate(id, active)
                }
                message != null -> {
                    val (from, arrow, change, to, label) = message.destructured
                    actor(from); actor(to)
                    val delta = when (change) { "+" -> 1; "-" -> -1; else -> 0 }
                    if (delta != 0) activation(if (delta > 0) to else from, delta > 0, line)
                    events += SequenceEvent.Message(from, to, MermaidParser.label(label), arrow.startsWith("--"), !arrow.endsWith(">>"), delta)
                }
                note != null -> {
                    val (side, ids, label) = note.destructured
                    val participants = ids.split(',').map { actor(it.trim()) }
                    events += SequenceEvent.Note(participants, MermaidParser.label(label), side)
                }
                text.substringBefore(' ') in setOf("loop", "alt", "opt", "par", "critical", "break") -> {
                    frames++
                    events += SequenceEvent.Frame(text.substringBefore(' '), MermaidParser.label(text.substringAfter(' ', "")))
                }
                text == "else" || text.startsWith("else ") || text.startsWith("and ") || text.startsWith("option ") -> {
                    if (frames == 0) throw MermaidParseException(line, "Branch outside sequence frame")
                    events += SequenceEvent.Divider(MermaidParser.label(text))
                }
                text == "end" -> {
                    if (frames-- <= 0) throw MermaidParseException(line, "Unexpected end")
                    events += SequenceEvent.End
                }
                else -> throw MermaidParseException(line, "Unsupported sequence statement: $text")
            }
        }
        if (frames != 0) throw MermaidParseException(lines.last().number, "Unclosed sequence frame")
        return MermaidDiagram.Sequence(actors.toList(), events, title, numbered)
    }

    fun pie(lines: List<MermaidStatement>): MermaidDiagram.Pie {
        val header = lines.first().text.removePrefix("pie").trim().removePrefix("showData").trim()
        var title = if (header.startsWith("title ")) MermaidParser.label(header.removePrefix("title ")) else ""
        if (header.isNotEmpty() && !header.startsWith("title ")) throw MermaidParseException(lines.first().number, "Unsupported pie header")
        val values = mutableListOf<Pair<String, Double>>()
        for ((line, text) in lines.drop(1)) {
            if (text.startsWith("title ")) { title = MermaidParser.label(text.removePrefix("title ")); continue }
            val match = Regex("^(\"(?:[^\"\\\\]|\\\\.)*\")\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)$").matchEntire(text) ?: throw MermaidParseException(line, "Expected quoted pie label and numeric value")
            val number = match.groupValues[2].toDoubleOrNull()
            if (number == null || !number.isFinite()) throw MermaidParseException(line, "Invalid pie value")
            values += MermaidParser.label(match.groupValues[1]) to number
        }
        return MermaidDiagram.Pie(values, title)
    }

    fun gantt(lines: List<MermaidStatement>): MermaidDiagram.Gantt {
        val tasks = mutableListOf<GanttTask>()
        val taskIds = mutableMapOf<String, GanttTask>()
        var title = ""
        var section = ""
        fun date(value: String, line: Int): Long = try {
            LocalDate.parse(value).toEpochDay()
        } catch (error: DateTimeParseException) {
            throw MermaidParseException(line, "Expected YYYY-MM-DD date: $value")
        }
        for ((line, text) in lines.drop(1)) {
            when {
                text.startsWith("title ") -> title = MermaidParser.label(text.removePrefix("title "))
                text.startsWith("section ") -> section = MermaidParser.label(text.removePrefix("section "))
                text.startsWith("dateFormat ") -> if (text.removePrefix("dateFormat ").trim() != "YYYY-MM-DD") throw MermaidParseException(line, "Native Gantt uses YYYY-MM-DD")
                else -> {
                    if (':' !in text) throw MermaidParseException(line, "Expected Gantt task")
                    val label = MermaidParser.label(text.substringBefore(':'))
                    val fields = text.substringAfter(':').split(',').map(String::trim).toMutableList()
                    val states = mutableListOf<String>()
                    while (fields.firstOrNull() in setOf("done", "active", "crit", "milestone")) states += fields.removeAt(0)
                    if (fields.isEmpty()) throw MermaidParseException(line, "Gantt task has no schedule")
                    val id = if (fields[0].matches(Regex("[A-Za-z_][A-Za-z0-9_-]*")) && !fields[0].startsWith("after ")) fields.removeAt(0) else "__task_${tasks.size}"
                    if (id in taskIds) throw MermaidParseException(line, "Duplicate task: $id")
                    if (fields.isEmpty()) throw MermaidParseException(line, "Gantt task has no dates")
                    val first = fields.removeAt(0)
                    val durationOnly = first.matches(Regex("[0-9]+d"))
                    val start = when {
                        durationOnly -> tasks.lastOrNull()?.end ?: throw MermaidParseException(line, "First task needs an explicit start date")
                        first.startsWith("after ") -> first.removePrefix("after ").trim().split(Regex("\\s+")).maxOf { dependency -> taskIds[dependency]?.end ?: throw MermaidParseException(line, "Unknown task dependency: $dependency") }
                        else -> date(first, line)
                    }
                    val endText = if (durationOnly) first else fields.removeFirstOrNull() ?: throw MermaidParseException(line, "Gantt task needs an end date or duration")
                    val end = if (endText.matches(Regex("[0-9]+d"))) start + (endText.removeSuffix("d").toLongOrNull() ?: throw MermaidParseException(line, "Invalid duration")) else date(endText, line)
                    if (fields.isNotEmpty() || end < start || end - start > 365_000 || start !in -365_000L..365_000L) throw MermaidParseException(line, "Unsupported or invalid Gantt schedule")
                    val task = GanttTask(id, label, section, start, end, states.joinToString(","))
                    tasks += task
                    taskIds[id] = task
                }
            }
        }
        return MermaidDiagram.Gantt(tasks, title)
    }
}