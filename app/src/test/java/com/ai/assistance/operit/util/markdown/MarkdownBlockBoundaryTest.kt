package com.ai.assistance.operit.util.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlockBoundaryTest {
    @Test fun completedStructuralBlocksCanEndAStableSegment() {
        val blocks = listOf(
            MarkdownProcessorType.HEADER, MarkdownProcessorType.BLOCK_QUOTE,
            MarkdownProcessorType.CODE_BLOCK, MarkdownProcessorType.ORDERED_LIST,
            MarkdownProcessorType.UNORDERED_LIST, MarkdownProcessorType.BLOCK_LATEX,
            MarkdownProcessorType.TABLE, MarkdownProcessorType.IMAGE,
            MarkdownProcessorType.DEFINITION_LIST, MarkdownProcessorType.FOOTNOTE,
            MarkdownProcessorType.HTML_BLOCK,
        )
        blocks.forEach { assertTrue("结构块应形成稳定分段边界：$it", it.canCloseStableTextAtBlockBoundary()) }
    }

    @Test fun inlineStylesAndNonTerminatingNodesDoNotEndAStableSegment() {
        val inline = listOf(
            MarkdownProcessorType.HORIZONTAL_RULE, MarkdownProcessorType.XML_BLOCK,
            MarkdownProcessorType.BOLD, MarkdownProcessorType.ITALIC,
            MarkdownProcessorType.INLINE_CODE, MarkdownProcessorType.LINK,
            MarkdownProcessorType.STRIKETHROUGH, MarkdownProcessorType.UNDERLINE,
            MarkdownProcessorType.INLINE_LATEX, MarkdownProcessorType.PLAIN_TEXT,
            MarkdownProcessorType.HTML_BREAK, MarkdownProcessorType.ESCAPED_TEXT,
            MarkdownProcessorType.HIGHLIGHT, MarkdownProcessorType.SUBSCRIPT,
            MarkdownProcessorType.SUPERSCRIPT,
        )
        inline.forEach { assertFalse("行内与非终止节点不能提前结束分段：$it", it.canCloseStableTextAtBlockBoundary()) }
    }

    @Test fun existingNativeTagOrdinalsStayCompatible() {
        // JNI 使用固定序号传递节点，移动源码文件不能重排已有类型。
        val nativeTypes = listOf(
            "HEADER", "BLOCK_QUOTE", "CODE_BLOCK", "ORDERED_LIST", "UNORDERED_LIST",
            "HORIZONTAL_RULE", "BLOCK_LATEX", "TABLE", "XML_BLOCK", "BOLD", "ITALIC",
            "INLINE_CODE", "LINK", "IMAGE", "STRIKETHROUGH", "UNDERLINE", "INLINE_LATEX",
            "PLAIN_TEXT", "HTML_BREAK", "ESCAPED_TEXT", "HIGHLIGHT", "SUBSCRIPT", "SUPERSCRIPT",
        )
        assertEquals(nativeTypes, MarkdownProcessorType.entries.take(nativeTypes.size).map { it.name })
    }
}
