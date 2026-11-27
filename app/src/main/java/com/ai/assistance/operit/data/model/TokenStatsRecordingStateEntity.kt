package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 清空代次持久化，避免旧在途请求在清空后重新写回。 */
@Entity(tableName = "token_stats_recording_state")
data class TokenStatsRecordingStateEntity(
    @PrimaryKey val id: Int = 1,
    val epoch: Long = 0L,
)
