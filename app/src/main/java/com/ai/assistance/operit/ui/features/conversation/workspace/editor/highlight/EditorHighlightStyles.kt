package com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTokenKind
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateScopeMapper

/** 分类仅保留已有语义查询；实际绘制使用可叠加的颜色和字体属性。 */
internal data class EditorHighlightStyle(
    val kind: EditorTokenKind = EditorTokenKind.DEFAULT,
    val colorRole: String = "default",
    val light: Int? = null,
    val dark: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
) {
    companion object {
        val Default = EditorHighlightStyle()
    }
}

internal data class EditorStyleDefinition(
    val parent: String? = null,
    val colorRole: String? = null,
    val light: Int? = null,
    val dark: Int? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
) {
    fun merge(overlay: EditorStyleDefinition) = EditorStyleDefinition(
        parent = overlay.parent ?: parent,
        colorRole = overlay.colorRole ?: colorRole,
        light = overlay.light ?: light,
        dark = overlay.dark ?: dark,
        bold = overlay.bold ?: bold,
        italic = overlay.italic ?: italic,
        underline = overlay.underline ?: underline,
        strike = overlay.strike ?: strike,
    )

    fun applyTo(style: EditorHighlightStyle) = style.copy(
        colorRole = colorRole ?: style.colorRole,
        // 显式切换基础颜色角色时结束外层颜色继承，字体属性仍可叠加。
        light = if (colorRole != null) light else light ?: style.light,
        dark = if (colorRole != null) dark else dark ?: style.dark,
        bold = bold ?: style.bold, italic = italic ?: style.italic,
        underline = underline ?: style.underline, strike = strike ?: style.strike,
    )
}

internal data class EditorScopeStyle(val scope: String, val style: String)

internal object EditorBuiltInStyles {
    val definitions: Map<String, EditorStyleDefinition> = buildMap {
        for (role in listOf("default", "keyword", "function", "string", "number", "comment", "type", "variable")) {
            put(role, EditorStyleDefinition(colorRole = role))
        }
        put("operator", EditorStyleDefinition(parent = "default"))
        put("punctuation", EditorStyleDefinition())
        put("heading", EditorStyleDefinition(colorRole = "heading", bold = true))
        put("headingMarker", EditorStyleDefinition(parent = "function"))
        put("bold", EditorStyleDefinition(bold = true))
        put("italic", EditorStyleDefinition(italic = true))
        put("underline", EditorStyleDefinition(underline = true))
        put("strikethrough", EditorStyleDefinition(strike = true))
        put("link", EditorStyleDefinition(colorRole = "processing", underline = true))
        put("quote", EditorStyleDefinition(colorRole = "quote"))
        put("list", EditorStyleDefinition(colorRole = "list"))
        put("inlineCode", EditorStyleDefinition(parent = "string"))
        put("tag", EditorStyleDefinition(parent = "variable"))
    }

    private fun String.isScope(prefix: String) = this == prefix || startsWith("$prefix.")

    fun forScope(scope: String): String? = when {
        scope.isScope("comment") -> "comment"
        scope.isScope("constant.numeric") -> "number"
        scope.isScope("keyword.operator") -> "operator"
        scope.isScope("constant.language") || scope.isScope("keyword") || scope.isScope("storage") -> "keyword"
        scope.isScope("entity.name.tag") -> "tag"
        scope.isScope("entity.name.function") || scope.isScope("support.function") || scope.isScope("variable.function") -> "function"
        scope.isScope("entity.name.type") || scope.isScope("entity.name.class") || scope.isScope("entity.name.namespace") ||
            scope.isScope("support.type") || scope.isScope("support.class") -> "type"
        scope.isScope("variable") || scope.isScope("entity.other.attribute-name") || scope.isScope("support.variable") -> "variable"
        scope.isScope("string.other.link") || scope.isScope("markup.underline.link") -> "link"
        scope.isScope("string") -> "string"
        scope.isScope("punctuation.definition.heading") -> "headingMarker"
        scope.isScope("punctuation.definition.list") -> "list"
        scope.isScope("markup.heading") -> "heading"
        scope.isScope("markup.bold") -> "bold"
        scope.isScope("markup.italic") -> "italic"
        scope.isScope("markup.strikethrough") -> "strikethrough"
        scope.isScope("markup.underline") -> "underline"
        scope.isScope("markup.quote") -> "quote"
        scope.isScope("markup.inline.raw") || scope.isScope("markup.raw.inline") -> "inlineCode"
        scope.isScope("punctuation") -> "punctuation"
        else -> null
    }

    fun isBoundary(scope: String, index: Int): Boolean = scope.isScope("meta.embedded") || scope.isScope("meta.interpolation") ||
        (index > 0 && (scope.isScope("source") || scope.isScope("text")))
}

/** 完整作用域按层叠加，任意配置样式名都能产生独立颜色与字体效果。 */
internal class EditorStyleResolver(
    configuration: EditorHighlightConfiguration = EditorHighlightConfiguration.Empty,
    language: String = "text",
) {
    private val styles = configuration.stylesFor(language)
    private val scopeStyles = (configuration.global.scopes + configuration.languages[language]?.scopes.orEmpty())
        .sortedBy { it.scope.length }
    private val namedScopes = configuration.namedStyleScopes
    private val defaultScopes = configuration.languages.keys.map { EditorHighlightConfiguration.styleScope(it, "default") }.toSet()
    private val defaultStyle = checkNotNull(styles["default"]).applyTo(EditorHighlightStyle.Default)
    private val cache = HashMap<List<String>, EditorHighlightStyle>()

    fun resolve(scopes: List<String>): EditorHighlightStyle = cache.getOrPut(scopes) {
        var style = defaultStyle
        for ((index, scope) in scopes.withIndex()) {
            if (EditorBuiltInStyles.isBoundary(scope, index)) {
                style = defaultStyle
            }
            EditorBuiltInStyles.forScope(scope)?.let { style = checkNotNull(styles[it]).applyTo(style) }
            for (rule in scopeStyles) {
                if (scope == rule.scope || scope.startsWith(rule.scope + ".")) {
                    style = if (rule.style == "default") defaultStyle else checkNotNull(styles[rule.style]).applyTo(style)
                }
            }
            // 显式引用 default 会结束外层字体继承，便于插值和嵌入区域恢复正文样式。
            if (scope in defaultScopes) style = EditorHighlightStyle.Default
            namedScopes[scope]?.let { style = it.applyTo(style) }
        }
        style.copy(kind = TextMateScopeMapper.kind(scopes))
    }.also {
        // 作用域种类异常丰富时限制缓存规模，已产生的不可变样式继续属于原快照。
        if (cache.size > 4096) cache.clear()
    }
}
