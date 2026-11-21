package com.ai.assistance.operit.ui.features.conversation.workspace.changes

/** 来源和快照版本共同确定缓存，文件切换不重新读取或分词。 */
internal data class ReviewCacheKey(val file: ReviewFile, val recordId: String?)

internal data class LoadedFileReview(
    val content: ReviewContent, val diff: PreparedReviewDiff?, val load: ReviewDiffLoad = WorkspaceDiffPolicy.classify(content),
    val snapshotKey: String? = if (content.isText) diff?.document?.snapshotKey
        ?: reviewSnapshotKey(checkNotNull(content.before), checkNotNull(content.after)) else null,
    val highlightComplete: Boolean = false,
) {
    init { require(diff == null || content.isText) }
    val characters: Int get() = (content.before?.length ?: 0) + (content.after?.length ?: 0)
}

/** 页面释放即释放缓存；限制条目数和文本总量，避免长对话无限持有高亮快照。 */
internal class WorkspaceReviewCache(private val maximumEntries: Int = 12, private val maximumCharacters: Int = 4 * 1024 * 1024) {
    private val entries = LinkedHashMap<ReviewCacheKey, LoadedFileReview>(16, 0.75f, true)
    private var characters = 0

    operator fun get(key: ReviewCacheKey): LoadedFileReview? = entries[key]

    fun put(key: ReviewCacheKey, value: LoadedFileReview) {
        characters -= entries.remove(key)?.characters ?: 0
        if (value.characters > maximumCharacters) return
        entries[key] = value
        characters += value.characters
        while (entries.size > maximumEntries || characters > maximumCharacters) {
            val oldest = entries.entries.iterator()
            val removed = oldest.next()
            characters -= removed.value.characters
            oldest.remove()
        }
    }
}

/** 只按真实用户消息划分轮次，选取最近一次包含文件操作的请求。 */
internal fun latestTurnRecords(records: List<FileChangeRecord>, userMessages: List<Long>): List<FileChangeRecord> {
    val latest = records.maxOfOrNull { it.timestamp } ?: return emptyList()
    val starts = userMessages.distinct().sorted()
    val start = starts.lastOrNull { it <= latest } ?: return emptyList()
    val end = starts.firstOrNull { it > start }
    return records.filter { it.timestamp >= start && (end == null || it.timestamp < end) }
}

internal fun reviewDiffSupported(content: ReviewContent): Boolean =
    WorkspaceDiffPolicy.classify(content) != ReviewDiffLoad.BLOCKED

/** 后台统计不提前计算需要用户主动加载的大型差异。 */
internal fun reviewDiffAutomatic(content: ReviewContent): Boolean =
    WorkspaceDiffPolicy.classify(content) == ReviewDiffLoad.AUTOMATIC

/** 行差异可直接显示，高亮使用同一个行模型，避免等候 Markdown 等语法的初始化。 */
internal fun prepareReviewLines(content: ReviewContent): PreparedReviewDiff {
    val document = WorkspaceDiff.compare(checkNotNull(content.before), checkNotNull(content.after))
    val columns = document.rows.maxOfOrNull { line ->
        line.text.sumOf { ch: Char -> if (ch == '\t') 4L else if (ch.code > 127) 2L else 1L }.toInt()
    } ?: 0
    return PreparedReviewDiff(document, null, null, columns)
}
