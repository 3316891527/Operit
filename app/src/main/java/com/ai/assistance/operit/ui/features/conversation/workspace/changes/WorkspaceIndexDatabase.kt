package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.Closeable

/** 存储接口只暴露逐行读取和事务，终端与快照分卷共用，Android 与 JVM 使用相同的索引 SQL。 */
internal interface TerminalIndexDatabase : Closeable {
    fun execute(sql: String, arguments: List<Any?> = emptyList())
    fun query(sql: String, arguments: List<String> = emptyList()): TerminalIndexRows
    fun <T> transaction(block: () -> T): T
}

internal interface TerminalIndexRows : Closeable {
    fun next(): Boolean
    fun string(column: Int): String?
    fun long(column: Int): Long
}
