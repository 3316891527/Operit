package com.ai.assistance.operit.ui.features.conversation.workspace.changes

internal enum class ReviewDiffLoad { AUTOMATIC, MANUAL, BLOCKED }

/** 字符与行数计算旧、新两侧总量，单行另设上限以限制布局和分词开销。 */
internal object WorkspaceDiffPolicy {
    const val AUTO_CHARACTERS = 256 * 1024
    const val AUTO_LINES = 4_000
    const val AUTO_LINE_CHARACTERS = 16 * 1024
    const val MAX_CHARACTERS = 4 * 1024 * 1024
    const val MAX_LINES = 20_000
    const val MAX_LINE_CHARACTERS = 128 * 1024

    fun classify(content: ReviewContent): ReviewDiffLoad {
        if (!content.isText) return ReviewDiffLoad.BLOCKED
        val before = checkNotNull(content.before)
        val after = checkNotNull(content.after)
        if (before.length.toLong() + after.length > MAX_CHARACTERS) return ReviewDiffLoad.BLOCKED
        var lines = 0
        var longest = 0
        for (text in listOf(before, after)) {
            if (text.isEmpty()) continue
            var start = 0
            for (index in text.indices) if (text[index] == '\n') {
                longest = maxOf(longest, index - start)
                lines++
                if (lines > MAX_LINES || longest > MAX_LINE_CHARACTERS) return ReviewDiffLoad.BLOCKED
                start = index + 1
            }
            if (start < text.length) { lines++; longest = maxOf(longest, text.length - start) }
        }
        if (lines > MAX_LINES || longest > MAX_LINE_CHARACTERS) return ReviewDiffLoad.BLOCKED
        return if (before.length.toLong() + after.length > AUTO_CHARACTERS || lines > AUTO_LINES || longest > AUTO_LINE_CHARACTERS)
            ReviewDiffLoad.MANUAL else ReviewDiffLoad.AUTOMATIC
    }
}
