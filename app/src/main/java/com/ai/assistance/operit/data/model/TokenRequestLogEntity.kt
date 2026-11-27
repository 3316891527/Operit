package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 请求结束事实独立于成功用量账本；未知字段保持为空。 */
@Entity(
    tableName = "token_request_logs",
    indices = [Index(value = ["startedAtMs"]), Index(value = ["configId", "startedAtMs"])],
)
data class TokenRequestLogEntity(
    @PrimaryKey val requestId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationMs: Long,
    val firstTokenLatencyMs: Long?,
    val configId: String,
    val provider: String,
    val model: String,
    val functionType: String?,
    val executionModel: String?,
    val status: String,
    val successfulAttempt: Int?,
    val observedAttemptNumbers: String?,
    val usageAttempt: Int?,
    val uncachedInputTokens: Long?,
    val cachedInputTokens: Long?,
    val cacheWriteTokens: Long?,
    val totalInputTokens: Long?,
    val outputTokens: Long?,
    val reasoningTokens: Long?,
    val reasoningIncludedInOutput: Boolean?,
    val cacheWriteSeparateBilling: Boolean?,
    val usageSource: String?,
) {
    companion object {
        const val SUCCESS = "SUCCESS"
        const val FAILED = "FAILED"
        const val CANCELLED = "CANCELLED"
        // 流正常结束，但提供商未确认成功；不能将其并入成功用量事实。
        const val COMPLETED = "COMPLETED"
    }
}
