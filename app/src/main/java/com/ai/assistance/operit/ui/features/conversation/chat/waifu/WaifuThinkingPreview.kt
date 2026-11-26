package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.core.chat.protocol.ThinkingMarkup

/** 只复制原始思考的短前缀，折叠时不为预览创建完整正文或 Markdown 节点。 */
internal fun waifuThinkingPreview(content: String): String {
    val block = ThinkingMarkup.blockAt(content) ?: return ""
    val preview = StringBuilder()
    var cursor = block.bodyStart
    var pendingSpace = false
    var previewCodePoints = 0
    while (cursor < block.bodyEnd) {
        val codePoint = content.codePointAt(cursor)
        cursor += Character.charCount(codePoint)
        if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
            pendingSpace = preview.isNotEmpty()
        } else {
            // 只有确认还有正文才截断，避免八字后的尾部空白产生多余省略号。
            if (previewCodePoints == 8) return preview.append('…').toString()
            if (pendingSpace) {
                preview.append(' ')
                previewCodePoints++
            }
            if (previewCodePoints == 8) return preview.append('…').toString()
            preview.appendCodePoint(codePoint)
            previewCodePoints++
            pendingSpace = false
        }
    }
    return preview.toString()
}
