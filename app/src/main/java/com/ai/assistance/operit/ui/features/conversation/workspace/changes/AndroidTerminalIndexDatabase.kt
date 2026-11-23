package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** 每个物理观察根独立落盘，SQLite 页缓存保持固定预算。 */
internal class AndroidTerminalIndexDatabase(file: File) : TerminalIndexDatabase {
    private val database: SQLiteDatabase

    init {
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.enableWriteAheadLogging()
        database.execSQL("PRAGMA cache_size=-2048")
        database.execSQL("PRAGMA synchronous=NORMAL")
        database.rawQuery("PRAGMA wal_autocheckpoint=256", null).use { it.moveToFirst() }
        database.setMaxSqlCacheSize(16)
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
