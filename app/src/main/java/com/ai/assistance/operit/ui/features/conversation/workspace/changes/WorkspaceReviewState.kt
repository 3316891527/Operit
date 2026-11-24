package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.security.MessageDigest

/** 摘要只关联两侧内容，重新读取和分词不会改变比较身份。 */
internal fun reviewSnapshotKey(before: String, after: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (text in listOf(before, after)) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        digest.update((bytes.size.toString() + ":").toByteArray(Charsets.US_ASCII))
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal data class ReviewSnapshotChoices(val loadRequested: Boolean = false, val expanded: Set<Int> = emptySet())

/** 每份比较分别保存选择，切换记录后返回也能继续展开；不保存正文或高亮对象。 */
internal data class ReviewViewChoices(val snapshots: Map<String, ReviewSnapshotChoices> = emptyMap()) {
    fun forSnapshot(key: String): ReviewSnapshotChoices = snapshots[key] ?: ReviewSnapshotChoices()

    fun requestLoad(key: String): ReviewViewChoices =
        updated(key, forSnapshot(key).copy(loadRequested = true))

    fun expand(key: String, start: Int): ReviewViewChoices =
        updated(key, forSnapshot(key).let { it.copy(expanded = it.expanded + start) })

    private fun updated(key: String, choices: ReviewSnapshotChoices): ReviewViewChoices =
        copy(snapshots = ((snapshots - key) + (key to choices)).entries.toList().takeLast(12).associate { it.toPair() })

    fun saved(): List<String> = snapshots.toSortedMap().map { (key, choices) ->
        "$key:${choices.loadRequested}:${choices.expanded.sorted().joinToString(",")}"
    }

    companion object {
        fun restore(saved: List<String>): ReviewViewChoices = ReviewViewChoices(saved.takeLast(12).associate { entry ->
            val parts = entry.split(':', limit = 3)
            val starts = if (parts[2].isEmpty()) emptySet() else parts[2].split(',').map(String::toInt).toSet()
            parts[0] to ReviewSnapshotChoices(parts[1].toBooleanStrict(), starts)
        })
    }
}
