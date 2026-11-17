package com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTokenKind

/** 保留已有语义分类查询；实际颜色和字体由动态样式解析。 */
internal object TextMateScopeMapper {
    fun kind(scopes: List<String>): EditorTokenKind {
        for (index in scopes.indices.reversed()) {
            val scope = scopes[index]
            when {
                matches(scope, "comment") -> return EditorTokenKind.COMMENT
                matches(scope, "constant.numeric") -> return EditorTokenKind.NUMBER
                matches(scope, "constant.language") || matches(scope, "keyword") ||
                    matches(scope, "storage") || matches(scope, "entity.name.tag") -> return EditorTokenKind.KEYWORD
                matches(scope, "entity.name.function") || matches(scope, "support.function") ||
                    matches(scope, "variable.function") -> return EditorTokenKind.FUNCTION
                matches(scope, "entity.name.type") || matches(scope, "entity.name.class") ||
                    matches(scope, "entity.name.namespace") || matches(scope, "support.type") ||
                    matches(scope, "support.class") -> return EditorTokenKind.TYPE
                matches(scope, "variable") || matches(scope, "entity.other.attribute-name") ||
                    matches(scope, "support.variable") -> return EditorTokenKind.VARIABLE
                matches(scope, "string") || matches(scope, "markup.inline.raw") -> return EditorTokenKind.STRING
                matches(scope, "markup.heading") || matches(scope, "markup.bold") -> return EditorTokenKind.KEYWORD
                matches(scope, "meta.embedded") || matches(scope, "meta.interpolation") ||
                    (index > 0 && (matches(scope, "source") || matches(scope, "text"))) -> return EditorTokenKind.DEFAULT
            }
        }
        return EditorTokenKind.DEFAULT
    }

    private fun matches(scope: String, prefix: String): Boolean = scope == prefix || scope.startsWith("$prefix.")
}
