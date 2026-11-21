package com.ai.assistance.operit.util.markdown

/** Markdown处理器类型枚举 */
enum class MarkdownProcessorType {
    // 块级处理器
    HEADER,
    BLOCK_QUOTE,
    CODE_BLOCK,
    ORDERED_LIST,
    UNORDERED_LIST,
    HORIZONTAL_RULE,
    BLOCK_LATEX, // LaTeX块级公式
    TABLE, // 表格支持
    XML_BLOCK, // XML块级元素

    // 内联处理器
    BOLD,
    ITALIC,
    INLINE_CODE,
    LINK,
    IMAGE,
    STRIKETHROUGH,
    UNDERLINE,
    INLINE_LATEX, // LaTeX行内公式

    // 纯文本
    PLAIN_TEXT,
    HTML_BREAK,
    ESCAPED_TEXT,
    HIGHLIGHT,
    SUBSCRIPT,
    SUPERSCRIPT,
    DEFINITION_LIST,
    FOOTNOTE,
    HTML_BLOCK
}
