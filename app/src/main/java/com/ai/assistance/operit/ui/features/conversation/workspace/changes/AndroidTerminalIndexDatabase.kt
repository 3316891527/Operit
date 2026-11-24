package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException
import android.system.Os
import android.system.OsConstants

/** 每个物理观察根独立落盘，SQLite 页缓存保持固定预算。 */
internal class AndroidTerminalIndexDatabase(file: File, durable: Boolean = false) : TerminalIndexDatabase {
    private val database: SQLiteDatabase

    init {
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.enableWriteAheadLogging()
            database.execSQL("PRAGMA cache_size=-2048")
            database.execSQL("PRAGMA temp_store=FILE")
            database.execSQL(if (durable) "PRAGMA synchronous=FULL" else "PRAGMA synchronous=NORMAL")
            database.rawQuery("PRAGMA wal_autocheckpoint=256", null).use { it.moveToFirst() }
            database.setMaxSqlCacheSize(16)
            if (durable) syncSnapshotDirectory(checkNotNull(file.parentFile))
        } catch (error: Exception) {
            // 持久化设置或目录同步失败时释放连接，下一次打开仍明确报告原始错误。
            database.close()
            throw error
        }
    }

    override fun execute(sql: String, arguments: List<Any?>) {
        if (arguments.isEmpty()) database.execSQL(sql) else database.execSQL(sql, arguments.toTypedArray())
    }

    override fun query(sql: String, arguments: List<String>): TerminalIndexRows = Rows(database.rawQuery(sql, arguments.toTypedArray()))

    override fun <T> transaction(block: () -> T): T {
        database.beginTransactionNonExclusive()
        try {
            val result = block()
            database.setTransactionSuccessful()
            return result
        } finally { database.endTransaction() }
    }

    override fun close() = database.close()

    private class Rows(private val cursor: Cursor) : TerminalIndexRows {
        override fun next(): Boolean = cursor.moveToNext()
        override fun string(column: Int): String? = if (cursor.isNull(column)) null else cursor.getString(column)
        override fun long(column: Int): Long = cursor.getLong(column)
        override fun close() = cursor.close()
    }
}

/** 新卷目录项也先同步，避免索引提交后断电丢失尚未持久化的文件名。 */
internal fun syncSnapshotDirectory(directory: File) {
    // 公开 SDK 不暴露 O_DIRECTORY，打开后通过同一句柄确认目录类型再同步。
    val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
    try {
        if (!OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) throw IOException("Snapshot sync target is not a directory: $directory")
        Os.fsync(descriptor)
    } finally { Os.close(descriptor) }
}
