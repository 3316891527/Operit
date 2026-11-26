package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** 将目标和全部主题偏好编码为稳定版本，避免并发修改时覆盖后来保存的设置。 */
internal object ThemeCommandRevision {
    fun of(targetType: String, targetId: String, values: ThemePreferenceValues): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            fun text(value: String) {
                val encoded = value.toByteArray(Charsets.UTF_8)
                output.writeInt(encoded.size)
                output.write(encoded)
            }
            text(targetType)
            text(targetId)
            output.writeInt(values.strings.size)
            values.strings.toSortedMap().forEach { (key, value) -> text(key); text(value) }
            output.writeInt(values.booleans.size)
            values.booleans.toSortedMap().forEach { (key, value) -> text(key); output.writeBoolean(value) }
            output.writeInt(values.ints.size)
            values.ints.toSortedMap().forEach { (key, value) -> text(key); output.writeInt(value) }
            output.writeInt(values.floats.size)
            values.floats.toSortedMap().forEach { (key, value) -> text(key); output.writeInt(value.toRawBits()) }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}
