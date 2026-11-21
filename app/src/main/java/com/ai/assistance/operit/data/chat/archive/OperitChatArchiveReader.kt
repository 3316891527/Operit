package com.ai.assistance.operit.data.chat.archive

import com.ai.assistance.operit.data.chat.folders.ChatFolder
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.io.File
import java.io.Reader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/** 类型化归档入口与通用 token 读取共用一份暂存正文，不能重新构造整会话消息列表。 */
internal class StagedChatArchive(
    val metadata: OperitArchivedChat,
    private val source: StreamedChat,
    private val json: Json,
) {
    val messageCount: Int get() = source.messageCount

    /** 只有带子树的备份才物化根消息；普通大聊天继续逐条解码和有界写入。 */
    suspend fun readGraph(): OperitArchivedChat {
        require(metadata.subagentGraph != null) { "Missing subagent graph" }
        val messages = mutableListOf<OperitArchivedMessage>()
        var characters = 0L
        forEachMessage { _, row ->
            characters += row.baseMessage.content.length.toLong() + row.variants.sumOf {
                com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec.render(it.sections).length.toLong()
            }
            require(characters <= 32_000_000L) { "Subagent graph exceeds the 32000000 character limit" }
            messages += row
        }
        return metadata.copy(messages = messages).also(com.ai.assistance.operit.core.agent.SubagentGraphTransfer::validate)
    }

    suspend fun forEachMessage(consume: suspend (Int, OperitArchivedMessage) -> Unit) {
        source.forEachMessage { index, element ->
            val message = if (source.archived) json.decodeFromJsonElement<OperitArchivedMessage>(element)
                else OperitArchivedMessage(json.decodeFromJsonElement<ChatMessage>(element))
            consume(index, message)
        }
    }
}

/** 只负责模型格式转换；逐 token 暂存、取消与缓存清理由唯一的流式读取器完成。 */
internal class OperitChatArchiveReader(private val json: Json, private val directory: File) {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun read(source: Reader, onFolders: suspend (List<ChatFolder>) -> Unit, onChat: suspend (StagedChatArchive) -> Unit) {
        val descriptors = listOf(OperitArchivedChat.serializer().descriptor, ChatHistory.serializer().descriptor)
        val fields = descriptors.flatMap { descriptor -> (0 until descriptor.elementsCount).map(descriptor::getElementName) }.toSet() - "messages"
        OperitChatStreamReader(directory, fields).read(source,
            onFolders = { onFolders(json.decodeFromJsonElement<List<ChatFolder>>(it)) },
            onChat = { streamed ->
                val metadata = if (streamed.archived) json.decodeFromJsonElement<OperitArchivedChat>(streamed.metadata)
                    else OperitArchivedChat.fromChatHistory(json.decodeFromJsonElement<ChatHistory>(streamed.metadata), emptyList())
                onChat(StagedChatArchive(metadata, streamed, json))
            },
        )
    }
}
