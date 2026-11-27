package com.ai.assistance.operit.core.tools.commands.waifu

import com.ai.assistance.operit.data.preferences.WaifuSettings
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** 版本同时绑定目标和完整配置，提示词中的换行不会造成编码歧义。 */
internal object WaifuCommandRevision {
    fun of(targetType: String, targetId: String, settings: WaifuSettings): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            fun text(value: String) {
                val encoded = value.toByteArray(Charsets.UTF_8)
                output.writeInt(encoded.size)
                output.write(encoded)
            }
            text(targetType)
            text(targetId)
            output.writeBoolean(settings.enabled)
            output.writeInt(settings.charDelay)
            output.writeBoolean(settings.removePunctuation)
            output.writeBoolean(settings.enableEmoticons)
            output.writeBoolean(settings.enableSelfie)
            output.writeBoolean(settings.enableMergeSend)
            output.writeInt(settings.mergeSendDelayMs)
            text(settings.customPrompt)
            text(settings.selfiePrompt)
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}