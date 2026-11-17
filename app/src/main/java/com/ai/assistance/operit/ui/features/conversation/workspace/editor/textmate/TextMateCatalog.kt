package com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate

import java.util.Locale

internal data class TextMateGrammarDefinition(
    val id: String,
    val scopeName: String,
    val aliases: List<String>,
    val isLanguage: Boolean,
)

/** 文件识别只读生成目录，不在主线程打开语法文件。 */
internal object TextMateCatalog {
    val grammars: List<TextMateGrammarDefinition> = TextMateCatalogData.grammars.lineSequence().map { row ->
        val fields = row.split('|')
        TextMateGrammarDefinition(fields[0], fields[1], fields[2].split(',').filter { it.isNotEmpty() }, fields[3] == "1")
    }.toList()
    val languages: List<TextMateGrammarDefinition> = grammars.filter { it.isLanguage }
    private val byId = grammars.associateBy { it.id }
    private val byScope = grammars.associateBy { it.scopeName }
    private val aliases = buildMap {
        for (language in languages) for (alias in language.aliases) put(alias.lowercase(Locale.ROOT), language.id)
        putAll(pairs(TextMateCatalogData.aliases))
    }
    private val extensions = pairs(TextMateCatalogData.extensions)
    private val fileNames = pairs(TextMateCatalogData.fileNames)
    private val fileNamePrefixes = pairs(TextMateCatalogData.fileNamePrefixes)
    private val scopeAliases = pairs(TextMateCatalogData.scopeAliases)
    private val injections = pairs(TextMateCatalogData.injections).mapValues { it.value.split(',') }
    val optionalScopes: Set<String> = TextMateCatalogData.optionalScopes.lineSequence().toSet()

    fun canonicalId(language: String): String {
        val normalized = language.lowercase(Locale.ROOT)
        return aliases[normalized] ?: normalized
    }

    fun language(language: String): TextMateGrammarDefinition? {
        val id = canonicalId(language)
        if (id == "text") return null
        return requireNotNull(byId[id]?.takeIf { it.isLanguage }) { "Unknown TextMate language: $language" }
    }

    fun grammarForScope(scopeName: String): TextMateGrammarDefinition? {
        byScope[scopeName]?.let { return it }
        scopeAliases[scopeName]?.let { return checkNotNull(byId[it]) }
        check(scopeName in optionalScopes) { "Unregistered TextMate scope: $scopeName" }
        return null
    }

    fun injectionsFor(scopeName: String): List<String> = injections[scopeName].orEmpty()

    fun detectLanguage(fileName: String): String {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT)
        fileNames[name]?.let { return it }
        for ((prefix, language) in fileNamePrefixes) if (name.startsWith(prefix)) return language
        // 从最左侧的点开始尝试，复合后缀比最后一个后缀更具体。
        var dot = name.indexOf('.')
        while (dot >= 0) {
            extensions[name.substring(dot)]?.let { return it }
            dot = name.indexOf('.', dot + 1)
        }
        return "text"
    }

    private fun pairs(data: String): Map<String, String> = data.lineSequence().filter { it.isNotEmpty() }.associate { row ->
        val separator = row.indexOf('|')
        check(separator > 0) { "Invalid TextMate catalog row: $row" }
        row.substring(0, separator) to row.substring(separator + 1)
    }
}
