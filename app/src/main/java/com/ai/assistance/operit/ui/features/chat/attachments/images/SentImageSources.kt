package com.ai.assistance.operit.ui.features.chat.attachments.images

import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.util.ChatMarkupRegex

/** 同一图片的池链接和附件描述合为一项；不修改已保存消息的 XML。 */
internal fun sentImageSources(content: String): List<SentMessageImage> {
    val matches = (ChatMarkupRegex.attachmentDataTag.findAll(content) +
        ChatMarkupRegex.attachmentDataSelfClosingTag.findAll(content)).sortedBy { it.range.first }
    var previousEnd = -1
    val attachments = matches.filter { match ->
        if (match.range.first <= previousEnd) false else {
            previousEnd = match.range.last
            match.groupValues[3].startsWith("image/", ignoreCase = true)
        }
    }.associate { it.groupValues[1] to it.groupValues[2] }
    val bindings = MediaLinkParser.extractImageAttachmentBindings(content)
    val images = MediaLinkParser.extractImageLinkIds(content).map { poolId ->
        val attachmentId = bindings.entries.firstOrNull { it.value == poolId }?.key
        SentMessageImage(id = poolId, title = attachments[attachmentId], poolId = poolId)
    }
    return images + attachments.filterKeys { it !in bindings }.map { (id, name) -> SentMessageImage(id, name) }
}
