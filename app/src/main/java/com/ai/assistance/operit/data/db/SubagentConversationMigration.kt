package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 在本分支 29 版上追加结构，既有分支不推断为子代理会话。 */
internal object SubagentConversationMigration : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chats ADD COLUMN chatKind TEXT NOT NULL DEFAULT 'NORMAL'")
        db.execSQL("CREATE INDEX index_chats_chatKind_parentChatId ON chats(chatKind, parentChatId)")
        db.execSQL(
            """
            CREATE TABLE subagent_runs (
                id TEXT NOT NULL PRIMARY KEY,
                parentChatId TEXT NOT NULL,
                childChatId TEXT NOT NULL,
                parentToolCallId TEXT NOT NULL,
                executionId TEXT NOT NULL,
                status TEXT NOT NULL,
                archived INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                snapshotJson TEXT NOT NULL,
                FOREIGN KEY(parentChatId) REFERENCES chats(id) ON DELETE RESTRICT,
                FOREIGN KEY(childChatId) REFERENCES chats(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX index_subagent_runs_parentChatId_parentToolCallId ON subagent_runs(parentChatId, parentToolCallId)")
        db.execSQL("CREATE UNIQUE INDEX index_subagent_runs_childChatId ON subagent_runs(childChatId)")
        db.execSQL("CREATE INDEX index_subagent_runs_parentChatId_archived_createdAt ON subagent_runs(parentChatId, archived, createdAt)")
    }
}
