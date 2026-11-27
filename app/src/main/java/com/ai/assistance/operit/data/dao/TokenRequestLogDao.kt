package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.assistance.operit.data.model.TokenRequestLogEntity
import com.ai.assistance.operit.data.model.TokenStatsRecordingStateEntity

@Dao
abstract class TokenRequestLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLog(log: TokenRequestLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun initializeState(state: TokenStatsRecordingStateEntity)

    @Query("SELECT epoch FROM token_stats_recording_state WHERE id = 1")
    abstract suspend fun recordingEpoch(): Long

    @Query("UPDATE token_stats_recording_state SET epoch = :epoch WHERE id = 1")
    abstract suspend fun setRecordingEpoch(epoch: Long)

    @Query("DELETE FROM token_request_logs")
    abstract suspend fun clearLogs()

    @Query("UPDATE token_request_logs SET configId = :detachedConfigId WHERE configId = :configId")
    abstract suspend fun detachFromConfig(configId: String, detachedConfigId: String)
}
