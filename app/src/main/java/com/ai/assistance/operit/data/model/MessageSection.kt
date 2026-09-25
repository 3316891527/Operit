package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/** 一条消息里按顺序排列的完整片段。 */
@Serializable
sealed class MessageSection {
    abstract val type: String

    @Serializable
    data class Text(
        val content: String,
    ) : MessageSection() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "text"
        }
    }

    @Serializable
    data class Thinking(
        val content: String,
    ) : MessageSection() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "thinking"
        }
    }

    @Serializable
    data class ToolCall(
        val name: String,
        val params: Map<String, String> = emptyMap(),
        val raw: String = "",
    ) : MessageSection() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "tool_call"
        }
    }

    @Serializable
    data class ToolResult(
        val name: String,
        val status: String = "",
        val content: String,
        val raw: String = "",
    ) : MessageSection() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "tool_result"
        }
    }

    /** Responses 下一轮需要原样带回的数据，只保存，不展示。 */
    @Serializable
    data class Protocol(
        val provider: String,
        val payload: String,
        val raw: String = "",
    ) : MessageSection() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "protocol"
        }
    }

    @Serializable
    data class Search(
        val raw: String,
    ) : MessageSection() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "search"
        }
    }

    @Serializable
    data class Status(
        val raw: String,
    ) : MessageSection() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "status"
        }
    }
}
