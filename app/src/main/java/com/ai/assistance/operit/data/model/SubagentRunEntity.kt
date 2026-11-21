package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 运行元数据不保存已消费消息；完整对话统一存入 messages。 */
@Entity(
    tableName = "subagent_runs",
    foreignKeys = [
        ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["parentChatId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["childChatId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["parentChatId", "parentToolCallId"], unique = true),
        Index(value = ["childChatId"], unique = true),
        Index(value = ["parentChatId", "archived", "createdAt"]),
    ],
)
data class SubagentRunEntity(
    @PrimaryKey val id: String,
    val parentChatId: String,
    val childChatId: String,
    val parentToolCallId: String,
    val executionId: String,
    val status: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val snapshotJson: String,
)
