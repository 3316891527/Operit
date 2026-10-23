package com.ai.assistance.operit.core.chat.voice

import com.ai.assistance.operit.data.model.AttachmentInfo

/** 录音附件与转写共同保存，模型请求只使用转写文字。 */
data class VoiceMessageData(
    val attachment: AttachmentInfo,
    val durationMs: Long,
    val waveform: List<Float>,
    val transcript: String,
)

object VoiceMessageMarkup {
    private val block = Regex("<voice_bar\\b[^>]*>[\\s\\S]*?</voice_bar>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-z_]+)=\"([^\"]*)\"")
    private val attachment = Regex("<attachment\\b[^>]*?/>")
    private val transcript = Regex("<transcript>([\\s\\S]*?)</transcript>")

    fun encode(message: VoiceMessageData): String {
        require(message.durationMs > 0 && message.transcript.isNotBlank())
        require(message.attachment.mimeType == "audio/wav")
        require(message.attachment.filePath.isNotBlank() && message.attachment.fileName.isNotBlank())
        require(message.attachment.fileSize >= 44)
        require(message.waveform.size <= 64 && message.waveform.all { it.isFinite() && it in 0f..1f })
        val waves = message.waveform.joinToString(",") { ((it.coerceIn(0f, 1f) * 100).toInt() / 100f).toString() }
        return "<voice_bar version=\"1\" duration_ms=\"${message.durationMs}\" waveform=\"$waves\">" +
            "<attachment id=\"${escape(message.attachment.filePath)}\" filename=\"${escape(message.attachment.fileName)}\" type=\"audio/wav\" size=\"${message.attachment.fileSize}\" />" +
            "<transcript>${escape(message.transcript)}</transcript></voice_bar>"
    }

    fun parse(value: String): VoiceMessageData? {
        val normalized = value.trim()
        val matches = block.findAll(normalized).toList()
        val match = matches.singleOrNull()?.takeIf { it.value == normalized } ?: return null
        val header = match.value.substringBefore('>')
        val attrs = attribute.findAll(header).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        if (attrs["version"] != "1") return null
        val duration = attrs["duration_ms"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val waveText = attrs["waveform"] ?: return null
        val waves = if (waveText.isEmpty()) emptyList() else waveText.split(',').map { part ->
            val level = part.toFloatOrNull() ?: return null
            if (!level.isFinite() || level !in 0f..1f) return null
            level
        }
        if (waves.size > 64) return null
        val audio = attachment.findAll(match.value).singleOrNull()?.value ?: return null
        val audioAttrs = attribute.findAll(audio).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        if (audioAttrs["type"] != "audio/wav") return null
        val path = audioAttrs["id"]?.takeIf { it.isNotBlank() } ?: return null
        val name = audioAttrs["filename"]?.takeIf { it.isNotBlank() } ?: return null
        val size = audioAttrs["size"]?.toLongOrNull()?.takeIf { it >= 44 } ?: return null
        val textMatch = transcript.findAll(match.value).singleOrNull() ?: return null
        val text = unescape(textMatch.groupValues[1]).takeIf { it.isNotBlank() } ?: return null
        val body = match.value.substringAfter('>').substringBeforeLast("</voice_bar>")
        if (body.replace(audio, "").replace(textMatch.value, "").isNotBlank()) return null
        return VoiceMessageData(AttachmentInfo(path, name, "audio/wav", size), duration, waves, text)
    }

    /** 聊天消息可以在语音正文之外附带系统生成的工作区等元数据。 */
    fun parseMessage(value: String): VoiceMessageData? {
        val body = value
            .replace(Regex("<workspace_attachment>[\\s\\S]*?</workspace_attachment>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<reply_to\\b[^>]*>[\\s\\S]*?</reply_to>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<proxy_sender\\b[^>]*/>", RegexOption.IGNORE_CASE), "")
        return parse(body)
    }

    fun toModelText(value: String): String = block.replace(value) { match ->
        val message = parse(match.value)
        if (message != null) message.transcript else match.value
    }

    private fun escape(value: String): String = value.replace("&", "&" + "amp;").replace("\"", "&" + "quot;")
        .replace("<", "&" + "lt;").replace(">", "&" + "gt;").replace("'", "&" + "apos;")
    private fun unescape(value: String): String = value.replace("&" + "quot;", "\"").replace("&" + "apos;", "'")
        .replace("&" + "lt;", "<").replace("&" + "gt;", ">").replace("&" + "amp;", "&")

}

/** 每次检测到人声重新计时，开始录音后的纯静音同样会结束。 */
class VoiceSilenceGate(startedAtMillis: Long, private val silenceMillis: Long = 3000L) {
    private var lastSpeechAt = startedAtMillis
    var heardSpeech: Boolean = false
        private set
    fun observe(isSpeech: Boolean, nowMillis: Long) {
        if (isSpeech) {
            heardSpeech = true
            lastSpeechAt = nowMillis
        }
    }
    fun shouldFinish(nowMillis: Long): Boolean = nowMillis - lastSpeechAt >= silenceMillis
}

fun canStartVoiceMessage(text: String, attachmentCount: Int, processing: Boolean, hasReply: Boolean): Boolean =
    text.isBlank() && attachmentCount == 0 && !processing && !hasReply
