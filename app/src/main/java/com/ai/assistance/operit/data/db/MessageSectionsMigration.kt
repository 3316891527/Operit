package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.model.MessageSectionCodec
import com.ai.assistance.operit.data.model.MessageSectionStorage

/** 在 Room 升级事务内转换消息与变体，完成转换后才替换旧表。 */
internal object MessageSectionsMigration : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) = migrate(object : MigrationDatabase {
        override fun execute(sql: String, args: List<Any>) {
            db.execSQL(sql, args.toTypedArray())
        }
        override fun longValue(sql: String, args: List<Any>): Long? =
            db.query(sql, args.toTypedArray()).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        override fun textValue(sql: String, args: List<Any>): String =
            db.query(sql, args.toTypedArray()).use { cursor ->
                check(cursor.moveToFirst()) { "Message disappeared during sections migration" }
                cursor.getString(0)
            }
    })

    override fun migrate(connection: SQLiteConnection) = migrate(object : MigrationDatabase {
        private fun prepare(sql: String, args: List<Any>): SQLiteStatement {
            val statement = connection.prepare(sql)
            args.forEachIndexed { index, value ->
                when (value) {
                    is Long -> statement.bindLong(index + 1, value)
                    is String -> statement.bindText(index + 1, value)
                    else -> error("Unsupported migration parameter")
                }
            }
            return statement
        }
        override fun execute(sql: String, args: List<Any>) {
            val statement = prepare(sql, args)
            try { statement.step() } finally { statement.close() }
        }
        override fun longValue(sql: String, args: List<Any>): Long? {
            val statement = prepare(sql, args)
            return try {
                if (statement.step() && !statement.isNull(0)) statement.getLong(0) else null
            } finally { statement.close() }
        }
        override fun textValue(sql: String, args: List<Any>): String {
            val statement = prepare(sql, args)
            return try {
                check(statement.step()) { "Message disappeared during sections migration" }
                statement.getText(0)
            } finally { statement.close() }
        }
    })

    internal interface MigrationDatabase {
        fun execute(sql: String, args: List<Any> = emptyList())
        fun longValue(sql: String, args: List<Any> = emptyList()): Long?
        fun textValue(sql: String, args: List<Any> = emptyList()): String
    }

    internal fun migrate(db: MigrationDatabase) {
        migrateTable(db,
            table = "messages", key = "messageId",
            createSql = """
            CREATE TABLE `messages_sections_v22` (
                `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `chatId` TEXT NOT NULL,
                `sender` TEXT NOT NULL,
                `sections` TEXT NOT NULL,
                `searchText` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `roleName` TEXT NOT NULL,
                `selectedVariantIndex` INTEGER NOT NULL,
                `provider` TEXT NOT NULL,
                `modelName` TEXT NOT NULL,
                `inputTokens` INTEGER NOT NULL,
                `outputTokens` INTEGER NOT NULL,
                `cachedInputTokens` INTEGER NOT NULL,
                `sentAt` INTEGER NOT NULL,
                `outputDurationMs` INTEGER NOT NULL,
                `waitDurationMs` INTEGER NOT NULL,
                `completedAt` INTEGER NOT NULL,
                `displayMode` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
            insertSql = "INSERT INTO `messages_sections_v22` (`messageId`, `chatId`, `sender`, `sections`, `searchText`, `timestamp`, `orderIndex`, `roleName`, `selectedVariantIndex`, `provider`, `modelName`, `inputTokens`, `outputTokens`, `cachedInputTokens`, `sentAt`, `outputDurationMs`, `waitDurationMs`, `completedAt`, `displayMode`, `isFavorite`)" +
                " SELECT `messageId`, `chatId`, `sender`, ?, ?, `timestamp`, `orderIndex`, `roleName`, `selectedVariantIndex`, `provider`, `modelName`, `inputTokens`, `outputTokens`, `cachedInputTokens`, `sentAt`, `outputDurationMs`, `waitDurationMs`, `completedAt`, `displayMode`, `isFavorite` FROM `messages` WHERE `messageId` = ?",
        )
        migrateTable(db,
            table = "message_variants", key = "variantId",
            createSql = """
            CREATE TABLE `message_variants_sections_v22` (
                `variantId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `chatId` TEXT NOT NULL,
                `messageTimestamp` INTEGER NOT NULL,
                `variantIndex` INTEGER NOT NULL,
                `sections` TEXT NOT NULL,
                `searchText` TEXT NOT NULL,
                `roleName` TEXT NOT NULL,
                `provider` TEXT NOT NULL,
                `modelName` TEXT NOT NULL,
                `inputTokens` INTEGER NOT NULL,
                `outputTokens` INTEGER NOT NULL,
                `cachedInputTokens` INTEGER NOT NULL,
                `sentAt` INTEGER NOT NULL,
                `outputDurationMs` INTEGER NOT NULL,
                `waitDurationMs` INTEGER NOT NULL,
                `completedAt` INTEGER NOT NULL,
                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
            insertSql = "INSERT INTO `message_variants_sections_v22` (`variantId`, `chatId`, `messageTimestamp`, `variantIndex`, `sections`, `searchText`, `roleName`, `provider`, `modelName`, `inputTokens`, `outputTokens`, `cachedInputTokens`, `sentAt`, `outputDurationMs`, `waitDurationMs`, `completedAt`)" +
                " SELECT `variantId`, `chatId`, `messageTimestamp`, `variantIndex`, ?, ?, `roleName`, `provider`, `modelName`, `inputTokens`, `outputTokens`, `cachedInputTokens`, `sentAt`, `outputDurationMs`, `waitDurationMs`, `completedAt` FROM `message_variants` WHERE `variantId` = ?",
        )
    }

    private fun migrateTable(
        db: MigrationDatabase, table: String, key: String, createSql: String, insertSql: String,
    ) {
        val sequence = db.longValue("SELECT seq FROM sqlite_sequence WHERE name = ?", listOf(table))
        db.execute(createSql)
        var id = db.longValue("SELECT MIN(`$key`) FROM `$table`")
        while (id != null) {
            val currentId = id
            val length = checkNotNull(db.longValue(
                "SELECT LENGTH(content) FROM `$table` WHERE `$key` = ?", listOf(currentId),
            ))
            // SQLite 的 SUBSTR 按字符分块，不使用 CursorWindow 读取整条大消息。
            val content = StringBuilder()
            var start = 1L
            while (start <= length) {
                val chunk = db.textValue(
                    "SELECT SUBSTR(content, ?, 65536) FROM `$table` WHERE `$key` = ?",
                    listOf(start, currentId),
                )
                check(chunk.isNotEmpty()) { "Legacy message ended before its recorded length" }
                content.append(chunk)
                start += 65_536L
            }
            val sections = MessageSectionStorage.decodeLegacy(content.toString())
            db.execute(insertSql, listOf(
                MessageSectionStorage.encode(sections),
                MessageSectionCodec.searchText(sections), currentId,
            ))
            id = db.longValue("SELECT MIN(`$key`) FROM `$table` WHERE `$key` > ?", listOf(currentId))
        }
        db.execute("DROP TABLE `$table`")
        db.execute("ALTER TABLE `${table}_sections_v22` RENAME TO `$table`")
        if (table == "messages") {
            db.execute("CREATE INDEX `index_messages_chatId` ON `messages` (`chatId`)")
            db.execute("CREATE INDEX `index_messages_chatId_timestamp` ON `messages` (`chatId`, `timestamp`)")
        } else {
            db.execute("CREATE INDEX `index_message_variants_chatId_messageTimestamp` ON `message_variants` (`chatId`, `messageTimestamp`)")
            db.execute("CREATE UNIQUE INDEX `index_message_variants_chatId_messageTimestamp_variantIndex` ON `message_variants` (`chatId`, `messageTimestamp`, `variantIndex`)")
        }
        // 保留已删除记录之后的自增高水位，避免升级后重新使用旧主键。
        if (sequence != null) {
            db.execute("DELETE FROM sqlite_sequence WHERE name = ?", listOf(table))
            db.execute("INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)", listOf(table, sequence))
        }
    }
}
