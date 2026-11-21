package com.ai.assistance.operit.data.chat.archive

import com.ai.assistance.operit.data.chat.folders.ChatFolder
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.Reader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

/** 一次只暂存一个会话的原始消息，元数据在 messages 后出现时也不必保留整段历史。 */
internal class StagedChatArchive(
    val metadata: OperitArchivedChat,
    val messageCount: Int,
    private val messages: File,
    private val legacy: Boolean,
    private val json: Json,
) {
    suspend fun forEachMessage(consume: suspend (Int, OperitArchivedMessage) -> Unit) {
        val coroutine = currentCoroutineContext()
        JsonReader(messages.bufferedReader(Charsets.UTF_8)).use { reader ->
            reader.beginArray()
            var index = 0
            while (reader.hasNext()) {
                coroutine.ensureActive()
                val element = readArchiveValue(reader, checkActive = { coroutine.ensureActive() })
                val message = if (legacy) OperitArchivedMessage(json.decodeFromJsonElement<ChatMessage>(element))
                    else json.decodeFromJsonElement<OperitArchivedMessage>(element)
                consume(index++, message)
            }
            reader.endArray()
            check(index == messageCount && reader.peek() == JsonToken.END_DOCUMENT) { "Incomplete staged chat" }
        }
    }
}

/** 只在内存保留元数据和当前消息；暂存文件无论成功、解析失败或取消都在 finally 中删除。 */
internal class OperitChatArchiveReader(private val json: Json, private val directory: File) {
    suspend fun read(
        source: Reader,
        onFolders: suspend (List<ChatFolder>) -> Unit,
        onChat: suspend (StagedChatArchive) -> Unit,
    ) {
        val coroutine = currentCoroutineContext()
        JsonReader(source).use { reader ->
            reader.isLenient = true
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        coroutine.ensureActive()
                        when (reader.nextName()) {
                            "folders" -> onFolders(json.decodeFromJsonElement(readArchiveValue(reader, checkActive = { coroutine.ensureActive() })))
                            "chats" -> readChats(reader, legacy = false, onChat)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                JsonToken.BEGIN_ARRAY -> readChats(reader, legacy = true, onChat)
                else -> error("Expected an Operit archive object or legacy chat array")
            }
            check(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected data after chat archive" }
        }
    }

    private suspend fun readChats(reader: JsonReader, legacy: Boolean, onChat: suspend (StagedChatArchive) -> Unit) {
        reader.beginArray()
        while (reader.hasNext()) readChat(reader, legacy, onChat)
        reader.endArray()
    }

    private suspend fun readChat(reader: JsonReader, legacy: Boolean, onChat: suspend (StagedChatArchive) -> Unit) {
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        val staged = File.createTempFile("operit-chat-import-", ".json", directory)
        try {
            val fields = linkedMapOf<String, JsonElement>()
            var count = 0
            var sawMessages = false
            JsonWriter(staged.bufferedWriter(Charsets.UTF_8)).use { writer ->
                writer.serializeNulls = true
                writer.beginArray()
                reader.beginObject()
                while (reader.hasNext()) {
                    coroutine.ensureActive()
                    val name = reader.nextName()
                    when {
                        name == "messages" -> {
                            check(!sawMessages) { "Duplicate messages field" }
                            sawMessages = true
                            reader.beginArray()
                            while (reader.hasNext()) {
                                coroutine.ensureActive()
                                check(reader.peek() == JsonToken.BEGIN_OBJECT) { "Expected a message object" }
                                copyArchiveValue(reader, writer, checkActive = { coroutine.ensureActive() })
                                count++
                            }
                            reader.endArray()
                        }
                        name in metadataFields -> fields[name] = readArchiveValue(reader, checkActive = { coroutine.ensureActive() })
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                writer.endArray()
            }
            check(sawMessages) { "Missing messages field" }
            fields["messages"] = JsonArray(emptyList())
            val metadata = if (legacy) OperitArchivedChat.fromChatHistory(
                json.decodeFromJsonElement<ChatHistory>(JsonObject(fields)), emptyList(),
            ) else json.decodeFromJsonElement<OperitArchivedChat>(JsonObject(fields))
            onChat(StagedChatArchive(metadata, count, staged, legacy, json))
        } finally {
            staged.delete()
        }
    }

    private companion object {
        val metadataFields = setOf("id", "title", "createdAt", "updatedAt", "inputTokens", "outputTokens",
            "currentWindowSize", "usageBreakdownJson", "group", "displayOrder", "workspace", "workspaceEnv",
            "parentChatId", "characterCardName", "characterGroupId", "locked", "pinned", "archived",
            "folderId", "lastUsedAt", "folderPath")
    }
}

/** 从流直接构造当前消息的 Kotlin JSON 元素，不经过 Gson 树和整段字符串中转。 */
private fun readArchiveValue(reader: JsonReader, depth: Int = 0, checkActive: () -> Unit): JsonElement {
    check(depth <= 256) { "Archive JSON nesting exceeds 256 levels" }
    checkActive()
    return when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val fields = linkedMapOf<String, JsonElement>()
            reader.beginObject()
            while (reader.hasNext()) fields[reader.nextName()] = readArchiveValue(reader, depth + 1, checkActive)
            reader.endObject()
            JsonObject(fields)
        }
        JsonToken.BEGIN_ARRAY -> {
            val items = mutableListOf<JsonElement>()
            reader.beginArray()
            while (reader.hasNext()) items += readArchiveValue(reader, depth + 1, checkActive)
            reader.endArray()
            JsonArray(items)
        }
        JsonToken.STRING -> JsonPrimitive(reader.nextString())
        JsonToken.NUMBER -> Json.parseToJsonElement(reader.nextString())
        JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
        JsonToken.NULL -> { reader.nextNull(); JsonNull }
        else -> error("Unexpected JSON token: ${reader.peek()}")
    }
}

/** 原始消息按 token 写入磁盘，暂存阶段不建立消息对象或整条消息字符串。 */
private fun copyArchiveValue(reader: JsonReader, writer: JsonWriter, depth: Int = 0, checkActive: () -> Unit) {
    check(depth <= 256) { "Archive JSON nesting exceeds 256 levels" }
    checkActive()
    when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            reader.beginObject(); writer.beginObject()
            while (reader.hasNext()) { writer.name(reader.nextName()); copyArchiveValue(reader, writer, depth + 1, checkActive) }
            reader.endObject(); writer.endObject()
        }
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray(); writer.beginArray()
            while (reader.hasNext()) copyArchiveValue(reader, writer, depth + 1, checkActive)
            reader.endArray(); writer.endArray()
        }
        JsonToken.STRING -> writer.value(reader.nextString())
        JsonToken.NUMBER -> writer.jsonValue(reader.nextString())
        JsonToken.BOOLEAN -> writer.value(reader.nextBoolean())
        JsonToken.NULL -> { reader.nextNull(); writer.nullValue() }
        else -> error("Unexpected JSON token: ${reader.peek()}")
    }
}
