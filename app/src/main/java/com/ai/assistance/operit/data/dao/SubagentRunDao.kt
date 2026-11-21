package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ai.assistance.operit.data.model.SubagentRunEntity

private const val SNAPSHOT_CHUNK_BYTES = 65_536
private const val RUN_ROW_QUERY = """
    SELECT id, parentChatId, childChatId, parentToolCallId, executionId, status,
        archived, createdAt, updatedAt, '' AS snapshotJson,
        LENGTH(CAST(snapshotJson AS BLOB)) AS snapshotByteCount,
        COALESCE(CAST(SUBSTR(CAST(snapshotJson AS BLOB), 1, 65536) AS BLOB), X'') AS snapshotBytes
    FROM subagent_runs
"""

data class SubagentRunRow(
    @Embedded val run: SubagentRunEntity,
    val snapshotByteCount: Long,
    val snapshotBytes: ByteArray,
)

/** 长任务报告按字节分块读取，避免单条运行快照超出 Android CursorWindow。 */
@Dao
abstract class SubagentRunDao {
    @Query(RUN_ROW_QUERY + " ORDER BY createdAt ASC, id ASC")
    protected abstract suspend fun queryAll(): List<SubagentRunRow>

    @Query(RUN_ROW_QUERY + " WHERE id = :id")
    protected abstract suspend fun queryById(id: String): SubagentRunRow?

    @Query(RUN_ROW_QUERY + " WHERE parentChatId = :chatId ORDER BY createdAt ASC, id ASC")
    protected abstract suspend fun queryForParent(chatId: String): List<SubagentRunRow>

    @Query("SELECT CAST(SUBSTR(CAST(snapshotJson AS BLOB), :startByte, :byteCount) AS BLOB) FROM subagent_runs WHERE id = :id")
    protected abstract suspend fun querySnapshotChunk(id: String, startByte: Long, byteCount: Int): ByteArray?

    @Transaction
    open suspend fun getAll(): List<SubagentRunEntity> = queryAll().map { materialize(it) }

    @Transaction
    open suspend fun getById(id: String): SubagentRunEntity? = queryById(id)?.let { materialize(it) }

    @Transaction
    open suspend fun getForParent(chatId: String): List<SubagentRunEntity> = queryForParent(chatId).map { materialize(it) }

    @Insert
    abstract suspend fun insert(run: SubagentRunEntity)

    @Update
    abstract suspend fun update(run: SubagentRunEntity): Int

    private suspend fun materialize(row: SubagentRunRow): SubagentRunEntity {
        check(row.snapshotByteCount in 0..Int.MAX_VALUE.toLong()) { "Invalid subagent snapshot size" }
        val bytes = ByteArray(row.snapshotByteCount.toInt())
        check(row.snapshotBytes.size == minOf(bytes.size, SNAPSHOT_CHUNK_BYTES)) { "Incomplete subagent snapshot header" }
        row.snapshotBytes.copyInto(bytes)
        var offset = row.snapshotBytes.size
        while (offset < bytes.size) {
            val count = minOf(SNAPSHOT_CHUNK_BYTES, bytes.size - offset)
            val chunk = requireNotNull(querySnapshotChunk(row.run.id, offset.toLong() + 1, count)) { "Subagent snapshot disappeared" }
            check(chunk.size == count) { "Incomplete subagent snapshot chunk" }
            chunk.copyInto(bytes, offset)
            offset += count
        }
        return row.run.copy(snapshotJson = bytes.toString(Charsets.UTF_8))
    }
}
