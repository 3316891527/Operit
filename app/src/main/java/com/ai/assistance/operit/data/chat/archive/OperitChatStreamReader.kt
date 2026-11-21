package com.ai.assistance.operit.data.chat.archive

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.Reader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 仅在回调期间持有当前会话的暂存文件，正文不进入会话元数据。 */
internal class StreamedChat internal constructor(
    val archived: Boolean,
    val metadata: JsonObject,
    val messageCount: Int,
    private val messages: File,
) {
    suspend fun forEachMessage(consume: suspend (index: Int, message: JsonElement) -> Unit) {
        JsonReader(messages.reader(Charsets.UTF_8).buffered()).use { reader ->
            reader.beginArray()
            var index = 0
            while (reader.hasNext()) {
                currentCoroutineContext().ensureActive()
                consume(index++, readImportJsonElement(reader))
            }
            reader.endArray()
            check(index == messageCount && reader.peek() == JsonToken.END_DOCUMENT) { "暂存消息数量不一致" }
        }
    }
}

/**
 * Operit JSON 始终逐消息处理，不按文件大小切换。
 * 先暂存当前会话的数组，避免字段顺序影响解析，也避免长时间解析网络文档占住数据库事务。
 */
internal class OperitChatStreamReader(
    private val cacheDirectory: File,
    private val metadataFields: Set<String>,
) {
    suspend fun read(
        source: Reader,
        onFolders: suspend (JsonElement) -> Unit,
        onChat: suspend (StreamedChat) -> Unit,
    ) {
        check(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) { "无法创建聊天导入缓存目录" }
        JsonReader(source).use { reader ->
            reader.isLenient = true
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        currentCoroutineContext().ensureActive()
                        when (reader.nextName()) {
                            "folders" -> onFolders(readImportJsonElement(reader))
                            "chats" -> readChats(reader, archived = true, onChat)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                JsonToken.BEGIN_ARRAY -> readChats(reader, archived = false, onChat)
                else -> error("聊天备份必须是归档对象或聊天数组")
            }
            check(reader.peek() == JsonToken.END_DOCUMENT) { "聊天备份包含多余的 JSON 内容" }
        }
    }

    private suspend fun readChats(
        reader: JsonReader,
        archived: Boolean,
        consume: suspend (StreamedChat) -> Unit,
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            currentCoroutineContext().ensureActive()
            val messages = File.createTempFile("messages-", ".json", cacheDirectory)
            try {
                val metadata = linkedMapOf<String, JsonElement>()
                var messageCount = 0
                reader.beginObject()
                while (reader.hasNext()) {
                    currentCoroutineContext().ensureActive()
                    when (val name = reader.nextName()) {
                        "messages" -> {
                            // 直接复制 token 到文件，不构造 Gson 树或整会话字符串。
                            JsonWriter(messages.writer(Charsets.UTF_8).buffered()).use { writer ->
                                reader.beginArray()
                                writer.beginArray()
                                messageCount = 0
                                while (reader.hasNext()) {
                                    copyImportJsonValue(reader, writer)
                                    messageCount++
                                }
                                reader.endArray()
                                writer.endArray()
                            }
                            metadata[name] = JsonArray(emptyList())
                        }
                        in metadataFields -> metadata[name] = readImportJsonElement(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                // messages 缺失时保留序列化器的必填字段校验，不能静默当成空会话。
                consume(StreamedChat(archived, JsonObject(metadata), messageCount, messages))
            } finally {
                check(!messages.exists() || messages.delete()) { "无法清理聊天导入缓存" }
            }
        }
        reader.endArray()
    }
}

/** 只建立当前消息的 kotlinx JSON 树，避免 Gson 树转字符串再解码的第二份正文。 */
private suspend fun readImportJsonElement(reader: JsonReader, depth: Int = 0): JsonElement {
    check(depth <= 256) { "Archive JSON nesting exceeds 256 levels" }
    currentCoroutineContext().ensureActive()
    return when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val fields = linkedMapOf<String, JsonElement>()
            reader.beginObject()
            while (reader.hasNext()) fields[reader.nextName()] = readImportJsonElement(reader, depth + 1)
            reader.endObject()
            JsonObject(fields)
        }
        JsonToken.BEGIN_ARRAY -> {
            val items = mutableListOf<JsonElement>()
            reader.beginArray()
            while (reader.hasNext()) items.add(readImportJsonElement(reader, depth + 1))
            reader.endArray()
            JsonArray(items)
        }
        JsonToken.STRING -> JsonPrimitive(reader.nextString())
        // 直接解析数字字面量，避免经 Double 丢失时间戳和 Long 精度。
        JsonToken.NUMBER -> Json.parseToJsonElement(reader.nextString())
        JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
        JsonToken.NULL -> { reader.nextNull(); JsonNull }
        else -> error("聊天备份包含无效 JSON 值")
    }
}

/** 同一时刻只保留一个标量字段，未知的大字段可由上层直接 skipValue。 */
private suspend fun copyImportJsonValue(reader: JsonReader, writer: JsonWriter, depth: Int = 0) {
    check(depth <= 256) { "Archive JSON nesting exceeds 256 levels" }
    currentCoroutineContext().ensureActive()
    when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            reader.beginObject()
            writer.beginObject()
            while (reader.hasNext()) {
                writer.name(reader.nextName())
                copyImportJsonValue(reader, writer, depth + 1)
            }
            reader.endObject()
            writer.endObject()
        }
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            writer.beginArray()
            while (reader.hasNext()) copyImportJsonValue(reader, writer, depth + 1)
            reader.endArray()
            writer.endArray()
        }
        JsonToken.STRING -> writer.value(reader.nextString())
        JsonToken.NUMBER -> writer.jsonValue(reader.nextString())
        JsonToken.BOOLEAN -> writer.value(reader.nextBoolean())
        JsonToken.NULL -> { reader.nextNull(); writer.nullValue() }
        else -> error("聊天备份包含无效 JSON 值")
    }
}
