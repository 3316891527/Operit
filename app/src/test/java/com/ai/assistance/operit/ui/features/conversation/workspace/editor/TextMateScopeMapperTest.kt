package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateScopeMapper
import org.junit.Assert.*
import org.junit.Test

class TextMateScopeMapperTest {
    @Test fun standardScopesMapToTheExistingEightColorCategories() {
        val expected = mapOf("comment.block.kotlin" to EditorTokenKind.COMMENT,
            "constant.numeric.integer" to EditorTokenKind.NUMBER, "storage.type" to EditorTokenKind.KEYWORD,
            "entity.name.function" to EditorTokenKind.FUNCTION, "support.class" to EditorTokenKind.TYPE,
            "variable.other.readwrite" to EditorTokenKind.VARIABLE, "string.quoted.double" to EditorTokenKind.STRING,
            "punctuation.separator" to EditorTokenKind.DEFAULT)
        for ((scope, kind) in expected) assertEquals(scope, kind, TextMateScopeMapper.kind(listOf("source.kotlin", scope)))
    }

    @Test fun interpolationUsesInnerVariablesAndEmbeddedCodeDoesNotInheritStringColor() {
        assertEquals(EditorTokenKind.VARIABLE, TextMateScopeMapper.kind(listOf("source.kotlin", "string.quoted.double.kotlin", "variable.interpolation.kotlin")))
        assertEquals(EditorTokenKind.DEFAULT, TextMateScopeMapper.kind(listOf("source.js", "string.template.js", "meta.embedded.expression.js", "punctuation.section.embedded.begin.js")))
        assertEquals(EditorTokenKind.KEYWORD, TextMateScopeMapper.kind(listOf("text.html.basic", "source.js.embedded.html", "storage.type.js")))
    }

    @Test fun quotedAttributesAndUnmappedChildrenRetainTheirOuterCategory() {
        assertEquals(EditorTokenKind.STRING, TextMateScopeMapper.kind(listOf("text.html.basic", "string.quoted.double.html", "punctuation.definition.string.begin.html")))
        assertEquals(EditorTokenKind.COMMENT, TextMateScopeMapper.kind(listOf("source.kotlin", "comment.block.kotlin", "punctuation.definition.comment")))
        assertEquals(EditorTokenKind.VARIABLE, TextMateScopeMapper.kind(listOf("text.html.basic", "entity.other.attribute-name.html")))
        assertEquals(EditorTokenKind.KEYWORD, TextMateScopeMapper.kind(listOf("text.html.basic", "entity.name.tag.html")))
    }
}
