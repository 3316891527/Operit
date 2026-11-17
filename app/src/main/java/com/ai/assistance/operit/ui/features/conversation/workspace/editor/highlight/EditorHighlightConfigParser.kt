package com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.util.Locale
import org.jcodings.specific.UTF8Encoding
import org.joni.Option
import org.joni.Regex as OnigRegex
import org.joni.Syntax

/** 严格 JSON 与规则校验在后台执行，错误保留文件名和字段位置。 */
internal class EditorHighlightConfigParser {
    private var ruleCount = 0
    private var nodeCount = 0

    fun parse(id: String, source: String): EditorLanguageHighlight {
        try {
            ruleCount = 0
            nodeCount = 0
            val root = JsonReader(StringReader(source)).use { reader ->
                reader.isLenient = false
                val value = readValue(reader, 0)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "trailing JSON content" }
                objectValue(value, "root")
            }
            val global = id == "_global"
            fields(root, if (global) setOf("version", "styles", "scopes") else setOf(
                "version", "mode", "styles", "scopes", "rules", "definitions", "extensions", "fileNames",
            ), "root")
            val version = root.get("version")
            require(version != null && version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asBigDecimal.compareTo(BigDecimal.ONE) == 0) { "version must be 1" }
            val styles = root.get("styles")?.let { parseStyles(objectValue(it, "styles")) }.orEmpty()
            val scopes = root.get("scopes")?.let { arrayValue(it, "scopes").mapIndexed { index, value ->
                val path = "scopes[$index]"
                val rule = objectValue(value, path)
                fields(rule, setOf("scope", "style"), path)
                val scope = requiredText(rule, "scope", path)
                require(scope.length <= 256 && scope.matches(kotlin.text.Regex("[A-Za-z0-9_.-]+"))) { "$path.scope must be a scope prefix" }
                EditorScopeStyle(scope, requiredText(rule, "style", path))
            } }.orEmpty()
            val mode = text(root, "mode", "root") ?: "extend"
            require(mode == "extend" || mode == "replace") { "mode must be extend or replace" }
            val rules = root.get("rules")?.let { parseRules(it, "rules", 0) }.orEmpty()
            val definitions = root.get("definitions")?.let { value ->
                objectValue(value, "definitions").entrySet().associate { (name, body) ->
                    require(name.matches(kotlin.text.Regex("[A-Za-z_][A-Za-z0-9_-]{0,79}"))) { "invalid definition name '$name'" }
                    name to parseRules(body, "definitions.$name", 0)
                }
            }.orEmpty()
            require(mode != "replace" || rules.isNotEmpty()) { "replace mode requires nonempty rules" }
            val extensions = strings(root, "extensions", "root").map { it.lowercase(Locale.ROOT) }
            require(extensions.all { it.startsWith('.') && it.length > 1 && '/' !in it && '\\' !in it && !it.any(Char::isWhitespace) }) { "extensions must contain dot-prefixed suffixes" }
            val fileNames = strings(root, "fileNames", "root").map { it.lowercase(Locale.ROOT) }
            require(fileNames.all { '/' !in it && '\\' !in it }) { "fileNames must be file names without directories" }
            return EditorLanguageHighlight(id, mode, styles, scopes, rules, definitions, extensions, fileNames)
        } catch (failure: Exception) {
            throw IllegalArgumentException("$id.json: ${failure.message}", failure)
        }
    }

    private fun readValue(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 64 && ++nodeCount <= 30_000) { "JSON structure exceeds limits" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(!has(name)) { "duplicate key '$name' at ${reader.path}" }
                    add(name, readValue(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray()
                while (reader.hasNext()) add(readValue(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
            else -> throw IllegalArgumentException("unexpected JSON value at ${reader.path}")
        }
    }

    private fun parseStyles(root: JsonObject): Map<String, EditorStyleDefinition> {
        require(root.size() <= 256) { "styles exceeds 256 entries" }
        return root.entrySet().associate { (name, element) ->
            require(name.isNotBlank() && name.length <= 80) { "invalid style name '$name'" }
            val path = "styles.$name"
            val style = objectValue(element, path)
            fields(style, setOf("extends", "light", "dark", "bold", "italic", "underline", "strike"), path)
            name to EditorStyleDefinition(parent = text(style, "extends", path), light = color(style, "light", path), dark = color(style, "dark", path),
                bold = boolean(style, "bold", path), italic = boolean(style, "italic", path),
                underline = boolean(style, "underline", path), strike = boolean(style, "strike", path))
        }
    }

    private fun parseRules(element: JsonElement, path: String, depth: Int): List<EditorHighlightRule> {
        require(depth <= 24) { "$path nesting exceeds 24 levels" }
        return arrayValue(element, path).mapIndexed { index, value ->
            require(++ruleCount <= 1024) { "rules exceeds 1024 entries" }
            val current = "$path[$index]"
            val rule = objectValue(value, current)
            val alternatives = listOf("match", "words", "literals", "begin", "include", "language").filter(rule::has)
            require(alternatives.size == 1) { "$current requires exactly one matcher" }
            val type = alternatives.single()
            val allowed = when (type) {
                "include", "language" -> setOf(type)
                "begin" -> setOf("begin", "end", "style", "contentStyle", "beginCaptures", "endCaptures", "rules", "endLast", "ignoreCase")
                else -> setOf(type, "style", "captures", "ignoreCase")
            }
            fields(rule, allowed, current)
            when (type) {
                "include" -> EditorHighlightRule(include = requiredText(rule, type, current))
                "language" -> EditorHighlightRule(language = TextMateCatalog.canonicalId(requiredText(rule, type, current)))
                "begin" -> {
                    val begin = expression(requiredText(rule, "begin", current, allowWhitespace = true), rule, current)
                    val end = expression(requiredText(rule, "end", current, allowWhitespace = true), rule, current)
                    val beginGroups = checkRegex(begin, "$current.begin", true)
                    checkEndReferences(end, beginGroups, current)
                    val endGroups = checkRegex("()".repeat(beginGroups) + end, "$current.end", false) - beginGroups
                    EditorHighlightRule(begin = begin, end = end, style = text(rule, "style", current), contentStyle = text(rule, "contentStyle", current),
                        beginCaptures = captures(rule, "beginCaptures", beginGroups, current), endCaptures = captures(rule, "endCaptures", endGroups, current),
                        rules = rule.get("rules")?.let { parseRules(it, "$current.rules", depth + 1) }.orEmpty(),
                        endLast = boolean(rule, "endLast", current) ?: false)
                }
                else -> {
                    val raw = when (type) {
                        "match" -> requiredText(rule, type, current, allowWhitespace = true)
                        else -> {
                            val values = strings(rule, type, current, allowWhitespace = true)
                            require(values.isNotEmpty()) { "$current.$type must not be empty" }
                            val choices = values.sortedByDescending { it.length }.joinToString("|") { quote(it) }
                            if (type == "words") "(?<![\\p{L}\\p{M}\\p{N}_$])(?:$choices)(?![\\p{L}\\p{M}\\p{N}_$])" else "(?:$choices)"
                        }
                    }
                    val regex = expression(raw, rule, current)
                    val groups = checkRegex(regex, "$current.$type", true)
                    EditorHighlightRule(match = regex, style = text(rule, "style", current), captures = captures(rule, "captures", groups, current))
                }
            }
        }
    }

    private fun expression(pattern: String, rule: JsonObject, path: String): String {
        require(pattern.length <= 8192) { "$path regex exceeds 8192 characters" }
        return if (boolean(rule, "ignoreCase", path) == true) "(?i:$pattern)" else pattern
    }

    private fun checkRegex(pattern: String, path: String, consuming: Boolean): Int {
        try {
            val bytes = pattern.toByteArray(Charsets.UTF_8)
            val regex = OnigRegex(bytes, 0, bytes.size, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE, Syntax.DEFAULT)
            if (consuming) require(regex.matcher(ByteArray(0)).search(0, 0, Option.DEFAULT) < 0) { "matcher must not accept empty text" }
            return regex.numberOfCaptures()
        } catch (failure: Exception) {
            throw IllegalArgumentException("$path: ${failure.message}", failure)
        }
    }

    private fun checkEndReferences(pattern: String, groups: Int, path: String) {
        var index = 0
        while (index < pattern.length) {
            if (pattern[index++] != '\\' || index == pattern.length) continue
            val start = index
            if (pattern[index] in '1'..'9') {
                while (index < pattern.length && pattern[index].isDigit()) index++
                require(pattern.substring(start, index).toInt() <= groups) { "$path.end references an absent begin capture" }
            } else index++
        }
    }

    private fun captures(root: JsonObject, key: String, count: Int, path: String): Map<String, String> = root.get(key)?.let {
        objectValue(it, "$path.$key").entrySet().associate { (group, value) ->
            require(group.matches(kotlin.text.Regex("0|[1-9][0-9]{0,2}")) && group.toInt() <= count) { "$path.$key invalid capture '$group'" }
            group to stringValue(value, "$path.$key.$group")
        }
    }.orEmpty()

    private fun quote(value: String): String = buildString {
        for (character in value) {
            if (character in "\\.^$|?*+()[]{}") append('\\')
            append(character)
        }
    }

    private fun fields(value: JsonObject, allowed: Set<String>, path: String) {
        val unknown = value.keySet() - allowed
        require(unknown.isEmpty()) { "$path unsupported fields: ${unknown.joinToString()}" }
    }
    private fun objectValue(value: JsonElement, path: String): JsonObject {
        require(value.isJsonObject) { "$path must be an object" }
        return value.asJsonObject
    }
    private fun arrayValue(value: JsonElement, path: String): JsonArray {
        require(value.isJsonArray && value.asJsonArray.size() <= 1024) { "$path must be an array of at most 1024 entries" }
        return value.asJsonArray
    }
    private fun stringValue(value: JsonElement, path: String, allowWhitespace: Boolean = false): String {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotEmpty() && (allowWhitespace || value.asString.isNotBlank())) { "$path must be a nonempty string" }
        return value.asString
    }
    private fun text(root: JsonObject, key: String, path: String, allowWhitespace: Boolean = false): String? = root.get(key)?.let { stringValue(it, "$path.$key", allowWhitespace) }
    private fun requiredText(root: JsonObject, key: String, path: String, allowWhitespace: Boolean = false): String = requireNotNull(text(root, key, path, allowWhitespace)) { "$path requires $key" }
    private fun boolean(root: JsonObject, key: String, path: String): Boolean? = root.get(key)?.let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "$path.$key must be boolean" }
        it.asBoolean
    }
    private fun strings(root: JsonObject, key: String, path: String, allowWhitespace: Boolean = false): List<String> = root.get(key)?.let {
        arrayValue(it, "$path.$key").mapIndexed { index, value -> stringValue(value, "$path.$key[$index]", allowWhitespace) }
    }.orEmpty()
    private fun color(root: JsonObject, key: String, path: String): Int? = text(root, key, path)?.let {
        require(it.matches(kotlin.text.Regex("#[0-9a-fA-F]{6}"))) { "$path.$key must be #RRGGBB" }
        (0xFF000000L or it.drop(1).toLong(16)).toInt()
    }
}
