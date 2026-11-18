// SPDX-License-Identifier: AGPL-3.0-only
package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import com.github.difflib.DiffUtils
import com.github.difflib.algorithm.myers.MeyersDiffWithLinearSpace

internal enum class DiffLineKind { CONTEXT, ADD, REMOVE }
internal data class DiffRange(val start: Int, val end: Int)
internal data class ReviewDiffLine(
    val oldLine: Int?, val newLine: Int?, val text: String, val kind: DiffLineKind,
    val emphasis: DiffRange? = null,
)
internal data class ReviewDiffDocument(val rows: List<ReviewDiffLine>, val additions: Int, val deletions: Int, val newlineChanged: Boolean, val snapshotKey: String)
internal sealed interface ReviewDiffItem {
    data class Line(val left: ReviewDiffLine?, val right: ReviewDiffLine?, val offset: Int = 0, val end: Int = Int.MAX_VALUE) : ReviewDiffItem
    data class Gap(val start: Int, val count: Int) : ReviewDiffItem
    data class Hunk(val oldStart: Int, val oldCount: Int, val newStart: Int, val newCount: Int) : ReviewDiffItem
}

/** 完整历史快照生成差异，折叠和展开共享同一份行模型。 */
internal object WorkspaceDiff {
    fun compare(before: String, after: String): ReviewDiffDocument {
        val old = lines(before)
        val next = lines(after)
        // 线性空间算法避免大量行全部变化时持有随编辑距离膨胀的搜索路径。
        val patch = DiffUtils.diff(old, next, MeyersDiffWithLinearSpace<String>())
        val rows = mutableListOf<ReviewDiffLine>()
        var oldIndex = 0
        var newIndex = 0
        for (delta in patch.deltas.sortedBy { it.source.position }) {
            while (oldIndex < delta.source.position) {
                rows += ReviewDiffLine(++oldIndex, ++newIndex, old[oldIndex - 1], DiffLineKind.CONTEXT)
            }
            val removed = delta.source.lines
            val added = delta.target.lines
            removed.forEach { text -> rows += ReviewDiffLine(++oldIndex, null, text, DiffLineKind.REMOVE) }
            added.forEach { text -> rows += ReviewDiffLine(null, ++newIndex, text, DiffLineKind.ADD) }
        }
        while (oldIndex < old.size) {
            rows += ReviewDiffLine(++oldIndex, ++newIndex, old[oldIndex - 1], DiffLineKind.CONTEXT)
        }
        return ReviewDiffDocument(pairChanges(rows), rows.count { it.kind == DiffLineKind.ADD }, rows.count { it.kind == DiffLineKind.REMOVE },
            before.endsWith('\n') != after.endsWith('\n'), reviewSnapshotKey(before, after))
    }

    /** 算法可分别返回相邻的新增与删除，按连续改动块配对后再计算行内强调。 */
    private fun pairChanges(rows: List<ReviewDiffLine>): List<ReviewDiffLine> = buildList {
        var index = 0
        while (index < rows.size) {
            if (rows[index].kind == DiffLineKind.CONTEXT) { add(rows[index++]); continue }
            val removed = mutableListOf<ReviewDiffLine>()
            val added = mutableListOf<ReviewDiffLine>()
            while (index < rows.size && rows[index].kind != DiffLineKind.CONTEXT) {
                val row = rows[index++]
                if (row.kind == DiffLineKind.REMOVE) removed += row else added += row
            }
            val emphasis = (0 until maxOf(removed.size, added.size)).map { pair ->
                val old = removed.getOrNull(pair)
                val next = added.getOrNull(pair)
                if (old != null && next != null) changedRanges(old.text, next.text) else null to null
            }
            removed.forEachIndexed { pair, row -> add(row.copy(emphasis = emphasis[pair].first)) }
            added.forEachIndexed { pair, row -> add(row.copy(emphasis = emphasis[pair].second)) }
        }
    }

    fun items(document: ReviewDiffDocument, split: Boolean, full: Boolean, expanded: Set<Int>): List<ReviewDiffItem> {
        val visible = BooleanArray(document.rows.size) { full }
        if (!full) document.rows.forEachIndexed { index, line ->
            if (line.kind != DiffLineKind.CONTEXT) for (near in (index - 3).coerceAtLeast(0)..(index + 3).coerceAtMost(visible.lastIndex)) visible[near] = true
        }
        val output = mutableListOf<ReviewDiffItem>()
        var index = 0
        while (index < document.rows.size) {
            if (!visible[index]) {
                val start = index
                while (index < visible.size && !visible[index]) index++
                if (start in expanded) (start until index).forEach { row -> output += single(document.rows[row], split) }
                else output += ReviewDiffItem.Gap(start, index - start)
                continue
            }
            val row = document.rows[index]
            if (!split || row.kind == DiffLineKind.CONTEXT) {
                output += single(row, split)
                index++
                continue
            }
            val removed = mutableListOf<ReviewDiffLine>()
            val added = mutableListOf<ReviewDiffLine>()
            while (index < document.rows.size && document.rows[index].kind == DiffLineKind.REMOVE) removed += document.rows[index++]
            while (index < document.rows.size && document.rows[index].kind == DiffLineKind.ADD) added += document.rows[index++]
            repeat(maxOf(removed.size, added.size)) { pair -> output += ReviewDiffItem.Line(removed.getOrNull(pair), added.getOrNull(pair)) }
        }
        return withHeaders(output)
    }

    /** 长逻辑行拆成有限长度的可见单元，完整原文仍保存在行模型中。 */
    fun slices(items: List<ReviewDiffItem>, maximumCharacters: Int = 2048): List<ReviewDiffItem> = buildList {
        require(maximumCharacters >= 4)
        for (item in items) {
            if (item !is ReviewDiffItem.Line) { add(item); continue }
            val length = maxOf(item.left?.text?.length ?: 0, item.right?.text?.length ?: 0)
            if (length <= maximumCharacters) { add(item); continue }
            var offset = 0
            while (offset < length) {
                val end = minOf(offset + maximumCharacters - 1, length)
                add(item.copy(offset = offset, end = end))
                offset = end
            }
        }
    }

    /** 两侧代理字符的位置可能不同，分别调整切片边界才能保留每侧完整原文。 */
    fun sliceRange(text: String, offset: Int, end: Int): DiffRange {
        var stop = minOf(end, text.length)
        var start = minOf(offset, stop)
        if (start < text.length && text[start].isLowSurrogate()) start--
        if (stop < text.length && text[stop].isLowSurrogate()) stop--
        return DiffRange(start.coerceAtLeast(0), stop.coerceAtLeast(0))
    }

    /** 导航按连续改动块定位，统一视图中的增删配对只算同一处改动。 */
    fun changeStarts(items: List<ReviewDiffItem>): List<Int> = buildList {
        var previousChanged = false
        items.forEachIndexed { index, item ->
            val changed = item is ReviewDiffItem.Line && listOfNotNull(item.left, item.right).any { it.kind != DiffLineKind.CONTEXT }
            if (changed && !previousChanged) add(index)
            previousChanged = changed
        }
    }

    private fun withHeaders(items: List<ReviewDiffItem>): List<ReviewDiffItem> = buildList {
        var index = 0
        while (index < items.size) {
            val item = items[index]
            if (item !is ReviewDiffItem.Line) { add(item); index++; continue }
            val start = index
            while (index < items.size && items[index] is ReviewDiffItem.Line) index++
            val lines = items.subList(start, index).filterIsInstance<ReviewDiffItem.Line>()
            val old = lines.mapNotNull { it.left?.oldLine ?: it.right?.oldLine }
            val next = lines.mapNotNull { it.right?.newLine ?: it.left?.newLine }
            add(ReviewDiffItem.Hunk(old.firstOrNull() ?: 0, old.size, next.firstOrNull() ?: 0, next.size))
            addAll(lines)
        }
    }

    private fun single(row: ReviewDiffLine, split: Boolean): ReviewDiffItem.Line = if (!split) ReviewDiffItem.Line(null, row)
        else when (row.kind) {
            DiffLineKind.CONTEXT -> ReviewDiffItem.Line(row, row)
            DiffLineKind.ADD -> ReviewDiffItem.Line(null, row)
            DiffLineKind.REMOVE -> ReviewDiffItem.Line(row, null)
        }

    private fun lines(text: String): List<String> = if (text.isEmpty()) emptyList()
        else text.split('\n').let { if (text.endsWith('\n')) it.dropLast(1) else it }

    /** 对应二代的 UTF-16 公共前后缀裁剪，同时保留完整代理字符。 */
    internal fun changedRanges(old: String, next: String): Pair<DiffRange?, DiffRange?> {
        val shared = minOf(old.length, next.length)
        var prefix = 0
        while (prefix < shared && old[prefix] == next[prefix]) prefix++
        if (prefix > 0 && prefix < old.length && old[prefix].isLowSurrogate()) prefix--
        var suffix = 0
        while (suffix < shared - prefix && old[old.lastIndex - suffix] == next[next.lastIndex - suffix]) suffix++
        if (suffix > 0 && old.length - suffix > prefix && old[old.length - suffix].isLowSurrogate()) suffix--
        val oldEnd = old.length - suffix
        val newEnd = next.length - suffix
        return (if (oldEnd > prefix) DiffRange(prefix, oldEnd) else null) to
            (if (newEnd > prefix) DiffRange(prefix, newEnd) else null)
    }
}
