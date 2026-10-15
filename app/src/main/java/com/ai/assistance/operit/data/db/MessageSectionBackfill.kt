package com.ai.assistance.operit.data.db

import com.ai.assistance.operit.data.model.MessageSectionCodec
import com.ai.assistance.operit.data.model.MessageSectionStorage
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** 升级后分批把旧正文解析成 sections，避免首次打开数据库时卡住。 */
internal object MessageSectionBackfill {
    private const val TAG = "MessageSectionBackfill"
    private const val BATCH_SIZE = 32

    suspend fun run(database: AppDatabase) = withContext(Dispatchers.IO) {
        backfillTable(database, "messages", "messageId")
        backfillTable(database, "message_variants", "variantId")
    }

    private suspend fun backfillTable(database: AppDatabase, table: String, key: String) {
        val db = database.openHelper.writableDatabase
        var lastId = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = ArrayList<Pair<Long, String>>(BATCH_SIZE)
            // 按主键继续扫描，已处理的历史不参与后续批次查找。
            db.query(
                "SELECT `$key`, searchText FROM `$table`" +
                    " WHERE `$key` > ? AND sections = '' ORDER BY `$key` LIMIT $BATCH_SIZE",
                arrayOf(lastId),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += cursor.getLong(0) to cursor.getString(1)
                }
            }
            if (rows.isEmpty()) return

            // 解析、编码和索引生成均在写事务外完成，避免长正文阻塞其他数据库事务。
            val updates = rows.map { (id, legacy) ->
                currentCoroutineContext().ensureActive()
                val encoded = try {
                    val sections = MessageSectionStorage.decodeLegacy(legacy)
                    MessageSectionStorage.encode(sections) to MessageSectionCodec.searchText(sections)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Backfill failed for $table row $id; preserving legacy text", e)
                    "[]" to legacy
                }
                Triple(id, encoded.first, encoded.second)
            }
            currentCoroutineContext().ensureActive()
            db.beginTransaction()
            try {
                updates.forEach { (id, sections, searchText) ->
                    // 准备数据期间可能发生编辑，只更新仍未回填的行。
                    db.execSQL(
                        "UPDATE `$table` SET sections = ?, searchText = ?" +
                            " WHERE `$key` = ? AND sections = ''",
                        arrayOf(sections, searchText, id),
                    )
                }
                db.setTransactionSuccessful()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Backfill failed for $table", e)
                return
            } finally {
                db.endTransaction()
            }
            lastId = rows.last().first
            yield()
        }
    }
}
