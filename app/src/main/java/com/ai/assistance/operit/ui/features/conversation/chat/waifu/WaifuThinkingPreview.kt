package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.core.chat.protocol.ThinkingMarkup

/** 只复制原始思考的短前缀，折叠时不为预览创建完整正文或 Markdown 节点。 */
internal fun waifuThinkingPreview(content: String): String {
    val block = ThinkingMarkup.blockAt(content) ?: return ""
    val preview = StringBuilder()
    var cursor = block.bodyStart
    var pendingSpace = false
    while (cursor < block.bodyEnd && preview.length < 240) {
        val codePoint = content.codePointAt(cursor)
        cursor += Character.charCount(codePoint)
        if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
            pendingSpace = preview.isNotEmpty()
        } else {
            if (pendingSpace) preview.append(' ')
            preview.appendCodePoint(codePoint)
            pendingSpace = false
        }
    }
    if (cursor < block.bodyEnd) preview.append('…')
    return preview.toString()
}