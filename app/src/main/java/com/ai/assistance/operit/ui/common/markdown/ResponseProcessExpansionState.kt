package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalResponseProcessChatId = staticCompositionLocalOf<String?> { null }
internal val LocalResponseProcessId = staticCompositionLocalOf<String?> { null }

/** Conversation changes dispose the transcript; keep explicit expansion choices outside it. */
internal object ResponseProcessExpansionState {
    private val expandedByChat = mutableStateMapOf<String, Set<String>>()

    fun expandedIds(chatId: String?): Set<String> = expandedByChat[chatId.orEmpty()].orEmpty()

    fun isExpanded(chatId: String?, id: String): Boolean = id in expandedIds(chatId)

    fun clear(chatId: String) { expandedByChat.remove(chatId) }

    fun expand(chatId: String?, id: String) {
        expandedByChat[chatId.orEmpty()] = expandedIds(chatId) + id
    }

    fun toggle(chatId: String?, id: String) {
        val previous = expandedIds(chatId)
        expandedByChat[chatId.orEmpty()] = if (id in previous) previous - id else previous + id
    }
}
