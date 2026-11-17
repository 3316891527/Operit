package com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class EditorHighlightRule(
    val match: String? = null,
    val begin: String? = null,
    val end: String? = null,
    val include: String? = null,
    val language: String? = null,
    val style: String? = null,
    val contentStyle: String? = null,
    val captures: Map<String, String> = emptyMap(),
    val beginCaptures: Map<String, String> = emptyMap(),
    val endCaptures: Map<String, String> = emptyMap(),
    val rules: List<EditorHighlightRule> = emptyList(),
    val endLast: Boolean = false,
)

internal data class EditorLanguageHighlight(
    val id: String,
    val mode: String = "extend",
    val styles: Map<String, EditorStyleDefinition> = emptyMap(),
    val scopes: List<EditorScopeStyle> = emptyList(),
    val rules: List<EditorHighlightRule> = emptyList(),
    val definitions: Map<String, List<EditorHighlightRule>> = emptyMap(),
    val extensions: List<String> = emptyList(),
    val fileNames: List<String> = emptyList(),
)

/** 每次重载生成独立配置与注册表，使旧快照始终保留原有规则语义。 */
internal class EditorHighlightConfiguration(
    val global: EditorLanguageHighlight = EditorLanguageHighlight("_global"),
    val languages: Map<String, EditorLanguageHighlight> = emptyMap(),
) {
    companion object {
        val Empty = EditorHighlightConfiguration()
        const val PlainScope = "text.operit.plain"
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        fun styleScope(language: String, style: String): String = "operit.style." + digest("$language:$style")
        fun customScope(language: String): String = "source.operit.config." + digest(language)
    }

    private val resolvedStyles = ConcurrentHashMap<String, Map<String, EditorStyleDefinition>>()
    val namedStyleScopes: Map<String, EditorStyleDefinition> by lazy {
        buildMap {
            for (id in languages.keys) for ((name, definition) in stylesFor(id)) put(styleScope(id, name), definition)
        }
    }
    val grammarSources: Map<String, String> by lazy {
        buildMap {
            put(PlainScope, JsonObject().apply {
                addProperty("scopeName", PlainScope)
                add("patterns", JsonArray())
            }.toString())
            for ((id, language) in languages) if (needsGrammar(language)) put(customScope(id), compile(language))
        }
    }
    val originalScopes: Map<String, String> by lazy {
        buildMap {
            for ((id, language) in languages) if (needsGrammar(language)) {
                builtInScope(id)?.let { put(customScope(id), it) }
            }
        }
    }

    fun stylesFor(language: String): Map<String, EditorStyleDefinition> = resolvedStyles.getOrPut(language) {
        val definitions = EditorBuiltInStyles.definitions.toMutableMap()
        for (source in listOf(global.styles, languages[language]?.styles.orEmpty())) {
            for ((name, style) in source) definitions[name] = definitions[name]?.merge(style) ?: style
        }
        val resolved = HashMap<String, EditorStyleDefinition>()
        val visiting = HashSet<String>()
        fun resolve(name: String): EditorStyleDefinition {
            resolved[name]?.let { return it }
            val definition = requireNotNull(definitions[name]) { "$language: unknown style '$name'" }
            require(visiting.add(name)) { "$language: cyclic style inheritance at '$name'" }
            val value = definition.parent?.let { resolve(it).merge(definition.copy(parent = null)) } ?: definition
            visiting.remove(name)
            resolved[name] = value
            return value
        }
        for (name in definitions.keys) resolve(name)
        resolved.toMap()
    }

    fun languageFor(hint: String, fileName: String): String =
        associatedLanguage(fileName) ?: TextMateCatalog.canonicalId(hint)

    /** 打开路由与分词共用文件名优先、最长后缀匹配，未登记时返回空结果。 */
    fun associatedLanguage(fileName: String): String? {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT)
        for ((id, language) in languages) if (name in language.fileNames) return id
        var chosen: String? = null
        var longest = 0
        for ((id, language) in languages) for (extension in language.extensions) {
            if (extension.length > longest && name.endsWith(extension)) { chosen = id; longest = extension.length }
        }
        return chosen
    }

    fun scopeFor(language: String): String {
        val configured = languages[language]
        if (configured != null && needsGrammar(configured)) return customScope(language)
        return builtInScope(language) ?: PlainScope
    }

    private fun needsGrammar(language: EditorLanguageHighlight) = language.mode == "replace" || language.rules.isNotEmpty()

    private fun builtInScope(language: String): String? {
        if (language in languages && languages.getValue(language).mode == "replace" &&
            TextMateCatalog.languages.none { it.id == language }) return null
        return TextMateCatalog.language(language)?.scopeName
    }

    fun validate() {
        val globalStyles = stylesFor("_global")
        for (scope in global.scopes) require(scope.style in globalStyles) { "_global: unknown style '${scope.style}'" }
        val associations = HashMap<String, String>()
        for ((id, language) in languages) {
            val styles = stylesFor(id)
            for (scope in language.scopes) require(scope.style in styles) { "$id: unknown style '${scope.style}'" }
            for (association in language.extensions.map { "extension:$it" } + language.fileNames.map { "name:$it" }) {
                val previous = associations.put(association, id)
                require(previous == null || previous == id) { "Conflicting association '$association': $previous, $id" }
            }
            fun validateRules(rules: List<EditorHighlightRule>) {
                for (rule in rules) {
                    for (style in listOfNotNull(rule.style, rule.contentStyle) + rule.captures.values + rule.beginCaptures.values + rule.endCaptures.values) {
                        require(style in styles) { "$id: unknown style '$style'" }
                    }
                    rule.include?.let {
                        require(it == "\$self" || it == "\$base" || it in language.definitions) { "$id: undefined rule '$it'" }
                        if (it == "\$base") require(builtInScope(id) != null) { "$id: no built-in grammar for \$base" }
                    }
                    rule.language?.let { scopeFor(it) }
                    validateRules(rule.rules)
                }
            }
            validateRules(language.rules)
            for (rules in language.definitions.values) validateRules(rules)
            scopeFor(id)
        }
        // 区域内部的引用逐次进入；这里检查直接展开的引用链，限制循环、深度与重复展开。
        val graph = buildMap<Pair<String, String>, List<EditorHighlightRule>> {
            for ((id, language) in languages) {
                put(id to "\$self", language.rules)
                for ((name, rules) in language.definitions) put(id to name, rules)
            }
        }
        data class Expansion(val depth: Int, val patterns: Int)
        val visiting = HashSet<Pair<String, String>>()
        val complete = HashMap<Pair<String, String>, Expansion>()
        fun visit(node: Pair<String, String>): Expansion {
            complete[node]?.let { return it }
            require(visiting.add(node)) { "Non-consuming include cycle at ${node.first}:${node.second}" }
            require(visiting.size <= 64) { "Include chain exceeds 64 levels at ${node.first}:${node.second}" }
            var depth = 1
            var patterns = 0
            for (rule in graph.getValue(node)) {
                val target = rule.include?.takeIf { it != "\$base" }?.let { node.first to it }
                    ?: rule.language?.takeIf { languages[it]?.let(::needsGrammar) == true }?.let { it to "\$self" }
                val expansion = target?.let(::visit)
                depth = maxOf(depth, 1 + (expansion?.depth ?: 0))
                patterns += expansion?.patterns ?: 1
                require(depth <= 64) { "Include chain exceeds 64 levels at ${node.first}:${node.second}" }
                require(patterns <= 8192) { "Include expansion exceeds 8192 patterns at ${node.first}:${node.second}" }
            }
            visiting.remove(node)
            return Expansion(depth, patterns).also { complete[node] = it }
        }
        for (node in graph.keys) visit(node)
        fun checkRegionExpansion(id: String, rules: List<EditorHighlightRule>) {
            var patterns = 0
            for (rule in rules) {
                val target = rule.include?.takeIf { it != "\$base" }?.let { id to it }
                    ?: rule.language?.takeIf { languages[it]?.let(::needsGrammar) == true }?.let { it to "\$self" }
                patterns += target?.let { complete.getValue(it).patterns } ?: 1
                require(patterns <= 8192) { "$id: region include expansion exceeds 8192 patterns" }
                if (rule.begin != null) checkRegionExpansion(id, rule.rules)
            }
        }
        for ((id, language) in languages) {
            checkRegionExpansion(id, language.rules)
            for (rules in language.definitions.values) checkRegionExpansion(id, rules)
        }
        // 编译全部配置，尽早发现跨语言引用和语法源中的错误。
        grammarSources
        namedStyleScopes
    }

    private fun compile(language: EditorLanguageHighlight): String {
        fun captures(values: Map<String, String>) = JsonObject().apply {
            for ((group, name) in values) add(group, JsonObject().apply { addProperty("name", styleScope(language.id, name)) })
        }
        fun pattern(rule: EditorHighlightRule): JsonObject = JsonObject().apply {
            rule.include?.let { addProperty("include", when (it) {
                "\$self" -> "\$self"
                "\$base" -> checkNotNull(builtInScope(language.id))
                else -> "#$it"
            }) }
            rule.language?.let { addProperty("include", scopeFor(it)) }
            rule.match?.let { addProperty("match", it) }
            rule.begin?.let { addProperty("begin", it) }
            rule.end?.let { addProperty("end", it) }
            rule.style?.let { addProperty("name", styleScope(language.id, it)) }
            rule.contentStyle?.let { addProperty("contentName", styleScope(language.id, it)) }
            if (rule.captures.isNotEmpty()) add("captures", captures(rule.captures))
            if (rule.beginCaptures.isNotEmpty()) add("beginCaptures", captures(rule.beginCaptures))
            if (rule.endCaptures.isNotEmpty()) add("endCaptures", captures(rule.endCaptures))
            if (rule.begin != null) {
                addProperty("applyEndPatternLast", rule.endLast)
                add("patterns", JsonArray().apply { rule.rules.forEach { add(pattern(it)) } })
            }
        }
        val patterns = JsonArray().apply { language.rules.forEach { add(pattern(it)) } }
        if (language.mode == "extend") builtInScope(language.id)?.let { patterns.add(JsonObject().apply { addProperty("include", it) }) }
        return JsonObject().apply {
            addProperty("scopeName", customScope(language.id))
            add("patterns", patterns)
            add("repository", JsonObject().apply {
                for ((name, rules) in language.definitions) add(name, JsonObject().apply {
                    add("patterns", JsonArray().apply { rules.forEach { add(pattern(it)) } })
                })
            })
        }.toString()
    }
}
