package com.ai.assistance.operit.util.markdown

/** 块级内容可结束稳定片段；行内样式继续等待句子边界。新增类型必须显式分类。 */
internal fun MarkdownProcessorType.canCloseStableTextAtBlockBoundary(): Boolean =
    when (this) {
        MarkdownProcessorType.HEADER,
        MarkdownProcessorType.BLOCK_QUOTE,
        MarkdownProcessorType.CODE_BLOCK,
        MarkdownProcessorType.ORDERED_LIST,
        MarkdownProcessorType.UNORDERED_LIST,
        MarkdownProcessorType.BLOCK_LATEX,
        MarkdownProcessorType.TABLE,
        MarkdownProcessorType.IMAGE,
        MarkdownProcessorType.DEFINITION_LIST,
        MarkdownProcessorType.FOOTNOTE,
        MarkdownProcessorType.HTML_BLOCK -> true

        MarkdownProcessorType.HORIZONTAL_RULE,
        MarkdownProcessorType.XML_BLOCK,
        MarkdownProcessorType.BOLD,
        MarkdownProcessorType.ITALIC,
        MarkdownProcessorType.INLINE_CODE,
        MarkdownProcessorType.LINK,
        MarkdownProcessorType.STRIKETHROUGH,
        MarkdownProcessorType.UNDERLINE,
        MarkdownProcessorType.INLINE_LATEX,
        MarkdownProcessorType.PLAIN_TEXT,
        MarkdownProcessorType.HTML_BREAK,
        MarkdownProcessorType.ESCAPED_TEXT,
        MarkdownProcessorType.HIGHLIGHT,
        MarkdownProcessorType.SUBSCRIPT,
        MarkdownProcessorType.SUPERSCRIPT -> false
    }
