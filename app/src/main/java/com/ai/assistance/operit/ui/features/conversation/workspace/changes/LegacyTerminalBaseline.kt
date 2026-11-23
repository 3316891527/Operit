package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.IOException

private val terminalSnapshotHash = Regex("[0-9a-f]{64}")

/** 旧 JSON 逐项迁移，不能再通过反序列化整份映射将旧占用带回内存。 */
internal fun readLegacyTerminalBaseline(
    file: File,
    onFile: (String, TerminalObservedFile) -> Unit,
    onUnreadable: (String) -> Unit = {},
    checkCancelled: () -> Unit = {},
) {
    JsonReader(file.bufferedReader(Charsets.UTF_8)).use { reader ->
        reader.beginObject()
        while (reader.hasNext()) when (reader.nextName()) {
            "files" -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    checkCancelled()
                    val path = reader.nextName()
                    var stamp: String? = null
                    var snapshot: ChangeSnapshot? = null
                    reader.beginObject()
                    while (reader.hasNext()) when (reader.nextName()) {
                        "stamp" -> stamp = reader.nextString()
                        "snapshot" -> snapshot = reader.terminalSnapshot()
                        else -> reader.skipValue()
                    }
                    reader.endObject()
                    onFile(path, TerminalObservedFile(stamp ?: throw IOException("Missing terminal file stamp"),
                        snapshot ?: throw IOException("Missing terminal file snapshot")))
                }
                reader.endObject()
            }
            "unreadable" -> {
                reader.beginArray()
                while (reader.hasNext()) { checkCancelled(); onUnreadable(reader.nextString()) }
                reader.endArray()
            }
            else -> reader.skipValue()
        }
        reader.endObject()
        check(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected data after terminal baseline" }
    }
}

private fun JsonReader.terminalSnapshot(): ChangeSnapshot {
    var kind: SnapshotKind? = null
    var size = 0L
    var blob: String? = null
    var detail: String? = null
    beginObject()
    while (hasNext()) when (nextName()) {
        "kind" -> kind = SnapshotKind.valueOf(nextString())
        "size" -> size = nextLong()
        "blob" -> blob = nullableString()
        "detail" -> detail = nullableString()
        else -> skipValue()
    }
    endObject()
    if (blob != null) require(terminalSnapshotHash.matches(blob)) { "Invalid terminal snapshot hash" }
    return ChangeSnapshot(kind ?: throw IOException("Missing terminal snapshot kind"), size, blob, detail)
}

private fun JsonReader.nullableString(): String? = if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()
