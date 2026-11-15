package com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightConfiguration
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorStyleResolver
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTextMateRules
import java.io.Reader
import java.io.StringReader
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IRegistryOptions
import org.eclipse.tm4e.core.registry.Registry

/** 每个后台分析器独占注册表和规则栈，配置重载使用全新实例。 */
internal class TextMateGrammarRegistry(
    private val configuration: EditorHighlightConfiguration = EditorHighlightConfiguration.Empty,
    private val openGrammar: (String) -> Reader,
) {
    private val registry = Registry(object : IRegistryOptions {
        override fun getGrammarSource(scopeName: String): IGrammarSource? {
            val configured = configuration.grammarSources[scopeName]
            val path: String
            val reader: () -> Reader
            if (configured != null) {
                path = "configured/$scopeName.json"
                reader = { StringReader(configured) }
            } else {
                val definition = TextMateCatalog.grammarForScope(scopeName) ?: return null
                path = "grammars/${definition.id}.json"
                reader = { openGrammar(path) }
            }
            return object : IGrammarSource {
                override fun getReader(): Reader = reader()
                override fun getFilePath(): String = "workspace-textmate/$path"
                override fun getContentType(): IGrammarSource.ContentType = IGrammarSource.ContentType.JSON
            }
        }

        override fun getInjections(scopeName: String): Collection<String> =
            TextMateCatalog.injectionsFor(configuration.originalScopes[scopeName] ?: scopeName)
    })
    private val rules = mutableMapOf<String, Result<EditorTextMateRules>>()

    fun rulesFor(language: String, fileName: String = ""): EditorTextMateRules {
        val id = configuration.languageFor(language, fileName)
        // 资源或配置加载失败保留原异常，同一修订不会反复加载损坏资源。
        return rules.getOrPut(id) {
            runCatching {
                val scope = configuration.scopeFor(id)
                EditorTextMateRules(id, checkNotNull(registry.loadGrammar(scope)) {
                    "TextMate grammar did not load: $id ($scope)"
                }, EditorStyleResolver(configuration, id))
            }
        }.getOrThrow()
    }
}
