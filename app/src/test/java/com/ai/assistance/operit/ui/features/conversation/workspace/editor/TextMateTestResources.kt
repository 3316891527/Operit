package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightConfiguration

import android.util.Log
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateGrammarRegistry
import org.mockito.Mockito

/** 只隔离 Android 日志桩，语法加载、正则匹配和分词均使用真实实现。 */
internal class TextMateTestResources(configuration: EditorHighlightConfiguration = EditorHighlightConfiguration.Empty) : AutoCloseable {
    private val logger = Mockito.mockStatic(Log::class.java)
    val registry = TextMateGrammarRegistry(configuration) { path ->
        checkNotNull(javaClass.getResourceAsStream("/$path")) { "Missing grammar resource: $path" }.bufferedReader(Charsets.UTF_8)
    }
    fun rules(language: String): EditorTextMateRules = registry.rulesFor(language)
    override fun close() = logger.close()
}
