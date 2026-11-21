package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.text.SpannableStringBuilder
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val EDITOR_TAB_SPACES = 4
private const val ZERO_WIDTH_JOINER = 0x200D
private const val COMBINING_KEYCAP = 0x20E3
private const val MAX_SYMBOL_BACKTRACK_CODE_POINTS = 12

internal fun editorCellWidth(char: Char): Int {
    if (char == '\t') {
        return EDITOR_TAB_SPACES
    }
    return editorCellWidth(char.code)
}

internal fun editorCellWidth(codePoint: Int): Int {
    if (codePoint == '\t'.code) {
        return EDITOR_TAB_SPACES
    }
    return if (isWideEditorCodePoint(codePoint) || isEmojiLikeCodePoint(codePoint)) 2 else 1
}

internal fun editorCodeUnitLength(
    text: CharSequence,
    offset: Int,
    limit: Int = text.length
): Int {
    val safeLimit = limit.coerceIn(0, text.length)
    val safeOffset = offset.coerceIn(0, safeLimit)
    if (safeOffset >= safeLimit) {
        return 0
    }
    val current = text[safeOffset]
    return if (
        Character.isHighSurrogate(current) &&
            safeOffset + 1 < safeLimit &&
            Character.isLowSurrogate(text[safeOffset + 1])
    ) {
        2
    } else {
        1
    }
}

internal fun editorNextCharacterOffset(
    text: CharSequence,
    offset: Int,
    limit: Int = text.length
): Int {
    val safeLimit = limit.coerceIn(0, text.length)
    val safeOffset = offset.coerceIn(0, safeLimit)
    return (safeOffset + editorCodeUnitLength(text, safeOffset, safeLimit)).coerceAtMost(safeLimit)
}

internal fun editorPreviousCharacterOffset(text: CharSequence, offset: Int): Int {
    val safeOffset = offset.coerceIn(0, text.length)
    if (safeOffset <= 0) {
        return 0
    }
    val candidate = safeOffset - 1
    return if (
        candidate > 0 &&
            Character.isLowSurrogate(text[candidate]) &&
            Character.isHighSurrogate(text[candidate - 1])
    ) {
        candidate - 1
    } else {
        candidate
    }
}

internal fun editorNextSymbolOffset(
    text: CharSequence,
    offset: Int,
    limit: Int = text.length
): Int {
    val safeLimit = limit.coerceIn(0, text.length)
    val safeOffset = offset.coerceIn(0, safeLimit)
    if (safeOffset >= safeLimit) {
        return safeLimit
    }

    val firstChar = text[safeOffset]
    if (firstChar == '\n' || firstChar == '\t') {
        return (safeOffset + 1).coerceAtMost(safeLimit)
    }

    val firstCodePoint = Character.codePointAt(text, safeOffset)
    var end = editorNextCharacterOffset(text, safeOffset, safeLimit)

    if (isRegionalIndicator(firstCodePoint) && end < safeLimit) {
        val nextCodePoint = Character.codePointAt(text, end)
        if (isRegionalIndicator(nextCodePoint)) {
            end = (end + Character.charCount(nextCodePoint)).coerceAtMost(safeLimit)
        }
    }

    while (end < safeLimit) {
        val codePoint = Character.codePointAt(text, end)
        when {
            codePoint == ZERO_WIDTH_JOINER -> {
                end = (end + Character.charCount(codePoint)).coerceAtMost(safeLimit)
                if (end < safeLimit) {
                    end = editorNextCharacterOffset(text, end, safeLimit)
                }
            }

            isVariationSelector(codePoint) ||
                isEmojiModifier(codePoint) ||
                isCombiningMark(codePoint) ||
                codePoint == COMBINING_KEYCAP -> {
                end = (end + Character.charCount(codePoint)).coerceAtMost(safeLimit)
            }

            else -> return end
        }
    }

    return end
}

internal fun editorPreviousSymbolOffset(text: CharSequence, offset: Int): Int {
    val safeOffset = offset.coerceIn(0, text.length)
    if (safeOffset <= 0) {
        return 0
    }

    var probe = safeOffset
    repeat(MAX_SYMBOL_BACKTRACK_CODE_POINTS) {
        probe = editorPreviousCharacterOffset(text, probe)
        if (editorNextSymbolOffset(text, probe) >= safeOffset) {
            return probe
        }
        if (probe <= 0) {
            return 0
        }
    }

    return editorPreviousCharacterOffset(text, safeOffset)
}

internal fun editorCellWidth(
    text: CharSequence,
    offset: Int,
    limit: Int = text.length
): Int {
    val safeLimit = limit.coerceIn(0, text.length)
    val safeOffset = offset.coerceIn(0, safeLimit)
    if (safeOffset >= safeLimit) {
        return 0
    }
    if (text[safeOffset] == '\t') {
        return EDITOR_TAB_SPACES
    }

    val firstCodePoint = Character.codePointAt(text, safeOffset)
    if (editorCellWidth(firstCodePoint) >= 2) {
        return 2
    }

    val end = editorNextSymbolOffset(text, safeOffset, safeLimit)
    var index = editorNextCharacterOffset(text, safeOffset, safeLimit)
    while (index < end) {
        val codePoint = Character.codePointAt(text, index)
        if (
            isEmojiLikeCodePoint(codePoint) ||
                isEmojiModifier(codePoint) ||
                isRegionalIndicator(codePoint) ||
                isVariationSelector(codePoint) ||
                codePoint == ZERO_WIDTH_JOINER ||
                codePoint == COMBINING_KEYCAP
        ) {
            return 2
        }
        index += Character.charCount(codePoint)
    }

    return 1
}

internal fun editorSnapOffsetToCharacterBoundary(text: CharSequence, offset: Int): Int {
    val safeOffset = offset.coerceIn(0, text.length)
    return if (
        safeOffset in 1 until text.length &&
            Character.isLowSurrogate(text[safeOffset]) &&
            Character.isHighSurrogate(text[safeOffset - 1])
    ) {
        safeOffset - 1
    } else {
        safeOffset
    }
}

internal fun editorExpandOffsetToCharacterEnd(text: CharSequence, offset: Int): Int {
    val safeOffset = offset.coerceIn(0, text.length)
    return if (
        safeOffset in 1 until text.length &&
            Character.isLowSurrogate(text[safeOffset]) &&
            Character.isHighSurrogate(text[safeOffset - 1])
    ) {
        (safeOffset + 1).coerceAtMost(text.length)
    } else {
        safeOffset
    }
}

private fun isWideEditorCodePoint(codePoint: Int): Boolean {
    return when {
        codePoint in 0x4E00..0x9FFF -> true
        codePoint in 0x3400..0x4DBF -> true
        codePoint in 0x20000..0x2A6DF -> true
        codePoint in 0x2A700..0x2B73F -> true
        codePoint in 0x2B740..0x2B81F -> true
        codePoint in 0x2B820..0x2CEAF -> true
        codePoint in 0x3040..0x309F -> true
        codePoint in 0x30A0..0x30FF -> true
        codePoint in 0xAC00..0xD7AF -> true
        codePoint in 0xFF00..0xFFEF -> true
        codePoint in 0x3000..0x303F -> true
        else -> false
    }
}

private fun isEmojiLikeCodePoint(codePoint: Int): Boolean {
    return when {
        codePoint in 0x1F000..0x1FAFF -> true
        codePoint in 0x2600..0x27BF -> true
        else -> false
    }
}

private fun isEmojiModifier(codePoint: Int): Boolean {
    return codePoint in 0x1F3FB..0x1F3FF
}

private fun isRegionalIndicator(codePoint: Int): Boolean {
    return codePoint in 0x1F1E6..0x1F1FF
}

private fun isVariationSelector(codePoint: Int): Boolean {
    return codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF
}

private fun isCombiningMark(codePoint: Int): Boolean {
    return when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }
}

internal data class EditorEditCommand(
    val start: Int,
    val beforeText: String,
    val afterText: String,
    val beforeSelectionStart: Int,
    val beforeSelectionEnd: Int,
    val afterSelectionStart: Int,
    val afterSelectionEnd: Int,
    val searchScopeRevision: Int = -1,
    val beforeSearchScope: EditorSearchScope? = null,
    val afterSearchScope: EditorSearchScope? = null,
)

internal class EditorDocument(initialText: String = "") {
    private var searchScopeRevision = 0
    private var trackedSearchScope: EditorSearchScope? = null
    var searchScope: EditorSearchScope?
        get() = trackedSearchScope
        set(value) { trackedSearchScope = value; searchScopeRevision++ }
    private var indexedText = EditorTextSnapshot(EditorLineIndex.fromText(initialText), 0, Any())
    var tabWidth: Int = 4
        set(value) {
            require(value in 1..8)
            if (field == value) return
            field = value
            indexedText = EditorTextSnapshot(indexedText.lines.withTabWidth(value), version, indexedText.owner)
        }
    private val buffer = SpannableStringBuilder(initialText)
    private val undoStack = ArrayDeque<EditorEditCommand>()
    private val redoStack = ArrayDeque<EditorEditCommand>()
    private var compositionBefore: EditorEditCommand? = null

    var selectionStart: Int = 0
        private set
    var selectionEnd: Int = 0
        private set
    var composingStart: Int = -1
        private set
    var composingEnd: Int = -1
        private set
    var version: Int = 0
        private set
    val maxLineCells: Int get() = indexedText.lines.maxCells

    private data class OffsetEdit(
        val position: Int,
        val removedLength: Int,
        val insertedLength: Int
    )

    init {
        collapseSelection(length())
    }

    fun text(): CharSequence = buffer

    fun textString(): String = indexedText.toString()

    fun snapshot(): EditorTextSnapshot = indexedText

    fun length(): Int = buffer.length

    fun lineCount(): Int = indexedText.lines.size

    fun hasSelection(): Boolean = selectionStart != selectionEnd

    fun hasComposingRegion(): Boolean = composingStart >= 0 && composingEnd >= composingStart

    fun clearHistory() {
        compositionBefore = null
        undoStack.clear()
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun setText(newText: String, clearHistory: Boolean = true) {
        if (newText.contentEquals(buffer)) {
            if (clearHistory) {
                clearHistory()
            }
            clearComposingRegion()
            collapseSelection(0)
            return
        }
        replaceRangeInternal(
            start = 0,
            end = buffer.length,
            replacement = newText,
            afterSelectionStart = 0,
            afterSelectionEnd = 0,
            recordHistory = false
        )
        if (clearHistory) {
            clearHistory()
        }
    }

    fun replaceAllText(newText: String) {
        finishComposingText()
        replaceRangeInternal(
            start = 0,
            end = buffer.length,
            replacement = newText,
            afterSelectionStart = newText.length,
            afterSelectionEnd = newText.length,
            recordHistory = true
        )
    }

    fun insertTextAtCursor(text: String) {
        val range = activeReplaceRange()
        val cursor = range.first + text.length
        replaceRangeInternal(
            start = range.first,
            end = range.last,
            replacement = text,
            afterSelectionStart = cursor,
            afterSelectionEnd = cursor,
            recordHistory = true
        )
    }

    fun insertTextProgrammatically(start: Int, text: String) {
        val safeStart = start.coerceIn(0, buffer.length)
        val cursor = safeStart + text.length
        replaceRangeInternal(
            start = safeStart,
            end = safeStart,
            replacement = text,
            afterSelectionStart = cursor,
            afterSelectionEnd = cursor,
            recordHistory = true
        )
    }

    fun applyCompletion(prefixLength: Int, replacement: String) {
        val cursor = selectionEnd
        val start = (cursor - prefixLength).coerceAtLeast(0)
        replaceRangeInternal(
            start = start,
            end = cursor,
            replacement = replacement,
            afterSelectionStart = start + replacement.length,
            afterSelectionEnd = start + replacement.length,
            recordHistory = true
        )
    }

    fun replaceSelection(text: String, recordHistory: Boolean = true) {
        val composing = compositionBefore
        if (recordHistory && composing != null && hasComposingRegion()) {
            val start = composingStart
            val end = composingEnd
            val caret = start + text.length
            replaceRangeInternal(start, end, text, caret, caret, false)
            if (composing.beforeText != text) {
                undoStack.addLast(composing.copy(afterText = text, afterSelectionStart = caret, afterSelectionEnd = caret, afterSearchScope = trackedSearchScope))
                redoStack.clear()
            }
            compositionBefore = null
            return
        }
        val range = activeReplaceRange()
        val cursor = range.first + text.length
        replaceRangeInternal(
            start = range.first,
            end = range.last,
            replacement = text,
            afterSelectionStart = cursor,
            afterSelectionEnd = cursor,
            recordHistory = recordHistory
        )
    }

    fun setComposingText(text: String) {
        val range = activeReplaceRange()
        if (compositionBefore == null) compositionBefore = EditorEditCommand(range.first, buffer.substring(range.first, range.last), "",
            selectionStart, selectionEnd, selectionStart, selectionEnd,
            searchScopeRevision, trackedSearchScope, trackedSearchScope)
        val end = range.first + text.length
        replaceRangeInternal(
            start = range.first,
            end = range.last,
            replacement = text,
            afterSelectionStart = end,
            afterSelectionEnd = end,
            recordHistory = false
        )
        composingStart = range.first
        composingEnd = end
    }

    fun finishComposingText() {
        val before = compositionBefore
        if (before != null && hasComposingRegion()) {
            val content = buffer.substring(composingStart, composingEnd)
            if (content != before.beforeText) {
                undoStack.addLast(before.copy(afterText = content, afterSelectionStart = selectionStart, afterSelectionEnd = selectionEnd, afterSearchScope = trackedSearchScope))
                redoStack.clear()
            }
        }
        compositionBefore = null
        clearComposingRegion()
    }

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        finishComposingText()
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        if (buffer.isEmpty()) return
        val start =
            editorSnapOffsetToCharacterBoundary(
                buffer,
                (selectionEnd - beforeLength).coerceAtLeast(0)
            )
        val end =
            editorExpandOffsetToCharacterEnd(
                buffer,
                (selectionEnd + afterLength).coerceAtMost(buffer.length)
            )
        if (start == end) return
        replaceRangeInternal(
            start = start,
            end = end,
            replacement = "",
            afterSelectionStart = start,
            afterSelectionEnd = start,
            recordHistory = true
        )
    }

    fun deleteBackward() {
        finishComposingText()
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        if (selectionEnd <= 0) return
        val deleteStart = previousCursorOffset(selectionEnd)
        replaceRangeInternal(
            start = deleteStart,
            end = selectionEnd,
            replacement = "",
            afterSelectionStart = deleteStart,
            afterSelectionEnd = deleteStart,
            recordHistory = true
        )
    }

    fun deleteForward() {
        finishComposingText()
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        if (selectionEnd >= buffer.length) return
        val deleteEnd = nextCursorOffset(selectionEnd)
        replaceRangeInternal(
            start = selectionEnd,
            end = deleteEnd,
            replacement = "",
            afterSelectionStart = selectionEnd,
            afterSelectionEnd = selectionEnd,
            recordHistory = true
        )
    }

    fun deleteSurroundingCodePoints(beforeLength: Int, afterLength: Int) {
        finishComposingText()
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        if (buffer.isEmpty()) return

        var start = selectionEnd
        repeat(beforeLength.coerceAtLeast(0)) {
            start = editorPreviousCharacterOffset(buffer, start)
        }

        var end = selectionEnd
        repeat(afterLength.coerceAtLeast(0)) {
            end = editorNextCharacterOffset(buffer, end)
        }

        if (start == end) return
        replaceRangeInternal(
            start = start,
            end = end,
            replacement = "",
            afterSelectionStart = start,
            afterSelectionEnd = start,
            recordHistory = true
        )
    }

    fun insertNewlineWithIndent(autoIndent: Boolean = true, indentUnit: String = "    ") {
        finishComposingText()
        val range = normalizedSelection()
        val cursor = range.first
        val currentLine = getLineForOffset(cursor)
        val prefix = buffer.substring(getLineStart(currentLine), cursor).takeWhile { it == ' ' || it == '\t' }
        val indent = if (autoIndent) prefix else ""
        val increase = autoIndent && cursor > 0 && buffer[cursor - 1] in "{[("
        val paired = increase && range.last < buffer.length && (buffer[cursor - 1].toString() + buffer[range.last]) in setOf("{}", "[]", "()")
        val leading = indent + if (increase) indentUnit else ""
        val insertion = if (paired) "\n$leading\n$indent" else "\n$leading"
        val caret = cursor + 1 + leading.length
        replaceRangeInternal(cursor, range.last, insertion, caret, caret, true)
    }

    /** 行操作只使用真实行，软换行不会扩大修改范围；一次操作只加入一条撤销记录。 */
    fun selectedLinesText(): String {
        val (first, last) = selectedLineBounds()
        val start = getLineStart(first)
        val end = if (last + 1 < lineCount()) getLineStart(last + 1) else length()
        return buffer.substring(start, end)
    }

    fun editLines(action: EditorAction, indent: String = "    "): Boolean {
        val (first, last) = selectedLineBounds()
        val start = getLineStart(first)
        val end = getLineEnd(last)
        val original = buffer.substring(start, end)
        when (action) {
            EditorAction.DELETE_LINE, EditorAction.CUT_LINE -> {
                val removeStart = if (end == length() && start > 0) start - 1 else start
                val removeEnd = if (end < length()) end + 1 else end
                replaceRangeInternal(removeStart, removeEnd, "", removeStart, removeStart, true)
            }
            EditorAction.CLEAR_LINE -> {
                val replacement = "\n".repeat(last - first)
                replaceRangeInternal(start, end, replacement, start, start, true)
            }
            EditorAction.DUPLICATE_LINE -> {
                val inserted = "\n$original"
                replaceRangeInternal(end, end, inserted, end + 1, end + inserted.length, true)
            }
            EditorAction.INDENT -> {
                val edits = (first..last).map { OffsetEdit(getLineStart(it), 0, indent.length) }
                val updated = original.split('\n').joinToString("\n") { indent + it }
                replaceRangeInternal(start, end, updated, mapOffsetAfterEdits(selectionStart, edits), mapOffsetAfterEdits(selectionEnd, edits), true)
            }
            else -> return false
        }
        return true
    }

    fun changeSelectionCase(uppercase: Boolean): Boolean {
        if (!hasSelection()) return false
        val range = normalizedSelection()
        val text = buffer.substring(range.first, range.last)
        val updated = if (uppercase) text.uppercase(Locale.ROOT) else text.lowercase(Locale.ROOT)
        if (updated == text) return false
        val reversed = selectionStart > selectionEnd
        val end = range.first + updated.length
        replaceRangeInternal(range.first, range.last, updated, if (reversed) end else range.first, if (reversed) range.first else end, true)
        return true
    }

    fun replaceRange(start: Int, end: Int, text: String) {
        finishComposingText()
        require(start in 0..length() && end in start..length())
        replaceRangeInternal(start, end, text, start + text.length, start + text.length, true)
    }

    fun unindentSelection(spaces: Int = EDITOR_TAB_SPACES): Boolean {
        val (firstLine, lastLine) = selectedLineBounds()
        val rangeStart = getLineStart(firstLine)
        val rangeEnd = getLineEnd(lastLine)
        val original = buffer.substring(rangeStart, rangeEnd)
        val edits = mutableListOf<OffsetEdit>()
        val removeSpaces = spaces.coerceAtLeast(1)
        val lines = original.split('\n')
        val updated = buildString {
            var oldLineStart = rangeStart
            lines.forEachIndexed { index, line ->
                val removeLength = when {
                    line.startsWith('\t') -> 1
                    else -> line.take(removeSpaces).takeWhile { it == ' ' }.length
                }
                if (removeLength > 0) {
                    edits += OffsetEdit(oldLineStart, removeLength, 0)
                    append(line.substring(removeLength))
                } else {
                    append(line)
                }
                if (index != lines.lastIndex) {
                    append('\n')
                    oldLineStart += line.length + 1
                }
            }
        }
        if (edits.isEmpty()) return false

        replaceRangeInternal(
            start = rangeStart,
            end = rangeEnd,
            replacement = updated,
            afterSelectionStart = mapOffsetAfterEdits(selectionStart, edits),
            afterSelectionEnd = mapOffsetAfterEdits(selectionEnd, edits),
            recordHistory = true
        )
        return true
    }

    fun toggleLineComment(prefix: String): Boolean {
        if (prefix.isEmpty()) return false
        val commentCurrentLine = !hasSelection()
        val (firstLine, lastLine) = selectedLineBounds()
        val rangeStart = getLineStart(firstLine)
        val rangeEnd = getLineEnd(lastLine)
        val original = buffer.substring(rangeStart, rangeEnd)
        val lines = original.split('\n')
        val nonBlankLines = lines.filter { it.any { character -> !character.isWhitespace() } }
        if (nonBlankLines.isEmpty() && !commentCurrentLine) return false

        val allCommented = nonBlankLines.isNotEmpty() && nonBlankLines.all { line ->
            val firstNonWhitespace = line.indexOfFirst { !it.isWhitespace() }
            firstNonWhitespace >= 0 && line.startsWith(prefix, firstNonWhitespace)
        }
        val edits = mutableListOf<OffsetEdit>()
        val updated = buildString {
            var oldLineStart = rangeStart
            lines.forEachIndexed { index, line ->
                val firstNonWhitespace = line.indexOfFirst { !it.isWhitespace() }
                if (firstNonWhitespace < 0) {
                    append(line)
                    if (commentCurrentLine) {
                        val inserted = "$prefix "
                        edits += OffsetEdit(oldLineStart + line.length, 0, inserted.length)
                        append(inserted)
                    }
                } else if (allCommented) {
                    val prefixEnd = firstNonWhitespace + prefix.length
                    val hasSpaceAfterPrefix =
                        line.startsWith(prefix, firstNonWhitespace) &&
                            prefixEnd < line.length &&
                            line[prefixEnd] == ' '
                    val removeLength = prefix.length + if (hasSpaceAfterPrefix) 1 else 0
                    if (line.startsWith(prefix, firstNonWhitespace)) {
                        edits += OffsetEdit(oldLineStart + firstNonWhitespace, removeLength, 0)
                        append(line.removeRange(firstNonWhitespace, firstNonWhitespace + removeLength))
                    } else {
                        append(line)
                    }
                } else {
                    val inserted = "$prefix "
                    edits += OffsetEdit(oldLineStart + firstNonWhitespace, 0, inserted.length)
                    append(line.substring(0, firstNonWhitespace))
                    append(inserted)
                    append(line.substring(firstNonWhitespace))
                }
                if (index != lines.lastIndex) {
                    append('\n')
                    oldLineStart += line.length + 1
                }
            }
        }
        if (edits.isEmpty()) return false

        replaceRangeInternal(
            start = rangeStart,
            end = rangeEnd,
            replacement = updated,
            afterSelectionStart = mapOffsetAfterEdits(selectionStart, edits),
            afterSelectionEnd = mapOffsetAfterEdits(selectionEnd, edits),
            recordHistory = true
        )
        return true
    }

    fun toggleBlockComment(startMarker: String, endMarker: String): Boolean {
        if (startMarker.isEmpty() || endMarker.isEmpty()) return false

        val hadSelection = hasSelection()
        val line = getLineForOffset(selectionEnd)
        val start = if (hadSelection) min(selectionStart, selectionEnd) else getLineStart(line)
        val end = if (hadSelection) max(selectionStart, selectionEnd) else getLineEnd(line)

        val selected = buffer.substring(start, end)
        val alreadyCommented =
            selected.startsWith(startMarker) && selected.endsWith(endMarker)
        val replacement = if (alreadyCommented) {
            selected.removePrefix(startMarker).removeSuffix(endMarker)
        } else {
            "$startMarker$selected$endMarker"
        }
        if (replacement == selected) return false

        val reversedSelection = selectionStart > selectionEnd
        val newStart = start
        val newEnd = start + replacement.length
        val newCursor = if (alreadyCommented) (selectionEnd - startMarker.length).coerceIn(newStart, newEnd)
            else selectionEnd + startMarker.length
        replaceRangeInternal(
            start = start,
            end = end,
            replacement = replacement,
            afterSelectionStart = if (!hadSelection) newCursor else if (reversedSelection) newEnd else newStart,
            afterSelectionEnd = if (!hadSelection) newCursor else if (reversedSelection) newStart else newEnd,
            recordHistory = true
        )
        return true
    }

    private fun selectedLineBounds(): Pair<Int, Int> {
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        val lastOffset = if (end > start) end - 1 else start
        return getLineForOffset(start) to getLineForOffset(lastOffset)
    }

    private fun mapOffsetAfterEdits(offset: Int, edits: List<OffsetEdit>): Int {
        var delta = 0
        for (edit in edits.sortedBy { it.position }) {
            if (offset < edit.position) break
            if (edit.removedLength > 0 && offset < edit.position + edit.removedLength) {
                return edit.position + delta
            }
            delta += edit.insertedLength - edit.removedLength
        }
        return offset + delta
    }

    fun setSelection(start: Int, end: Int) {
        finishComposingText()
        selectionStart = editorSnapOffsetToCharacterBoundary(buffer, start)
        selectionEnd = editorSnapOffsetToCharacterBoundary(buffer, end)
        clearComposingRegion()
    }

    fun collapseSelection(offset: Int) {
        setSelection(offset, offset)
    }

    fun selectWordAt(offset: Int) {
        if (buffer.isEmpty()) {
            collapseSelection(0)
            return
        }
        val safeOffset = offset.coerceIn(0, buffer.length)
        val anchor = when {
            safeOffset < buffer.length && isWordChar(buffer[safeOffset]) -> safeOffset
            safeOffset > 0 && isWordChar(buffer[safeOffset - 1]) -> safeOffset - 1
            safeOffset < buffer.length -> safeOffset
            else -> buffer.length - 1
        }

        if (!isWordChar(buffer[anchor])) {
            setSelection(anchor, min(anchor + 1, buffer.length))
            return
        }

        var start = anchor
        var end = anchor + 1
        while (start > 0 && isWordChar(buffer[start - 1])) {
            start--
        }
        while (end < buffer.length && isWordChar(buffer[end])) {
            end++
        }
        setSelection(start, end)
    }

    fun selectAll() {
        setSelection(0, buffer.length)
    }

    fun undo(): Boolean {
        finishComposingText()
        val command = undoStack.removeLastOrNull() ?: return false
        applyCommand(command, reverse = true, targetStack = redoStack)
        return true
    }

    fun redo(): Boolean {
        finishComposingText()
        val command = redoStack.removeLastOrNull() ?: return false
        applyCommand(command, reverse = false, targetStack = undoStack)
        return true
    }

    fun getLineForOffset(offset: Int): Int = indexedText.lines.lineForOffset(offset)

    fun getLineStart(line: Int): Int = indexedText.lines.start(line.coerceIn(0, lineCount() - 1))

    fun getLineEnd(line: Int): Int {
        val safe = line.coerceIn(0, lineCount() - 1)
        return indexedText.lines.start(safe) + indexedText.lines.line(safe).text.length
    }

    fun getLineText(line: Int): CharSequence {
        val start = getLineStart(line)
        val end = getLineEnd(line)
        return buffer.subSequence(start, end)
    }

    fun getCellColumnForOffset(offset: Int): Int {
        val safeOffset = editorSnapOffsetToCharacterBoundary(buffer, offset)
        val line = getLineForOffset(safeOffset)
        val lineStart = getLineStart(line)
        val lineEnd = getLineEnd(line)
        var cells = 0
        var i = lineStart
        while (i < safeOffset && i < lineEnd) {
            val next = editorNextSymbolOffset(buffer, i, lineEnd)
            if (next > safeOffset) {
                break
            }
            cells += if (buffer[i] == '\t') tabWidth else editorCellWidth(buffer, i, lineEnd)
            i = next
        }
        return cells
    }

    fun getOffsetForLineAndCellColumn(line: Int, targetCells: Int): Int {
        val safeLine = line.coerceIn(0, lineCount() - 1)
        val lineStart = getLineStart(safeLine)
        val lineEnd = getLineEnd(safeLine)
        if (targetCells <= 0) return lineStart

        var cells = 0
        var offset = lineStart
        while (offset < lineEnd) {
            val nextOffset = editorNextSymbolOffset(buffer, offset, lineEnd)
            val cellWidth = if (buffer[offset] == '\t') tabWidth else editorCellWidth(buffer, offset, lineEnd)
            val nextCells = cells + cellWidth
            if (targetCells < nextCells) {
                return if (targetCells - cells >= cellWidth / 2f) nextOffset else offset
            }
            cells = nextCells
            offset = nextOffset
        }
        return lineEnd
    }

    fun previousCursorOffset(offset: Int): Int {
        return editorPreviousSymbolOffset(buffer, offset)
    }

    fun nextCursorOffset(offset: Int): Int {
        val safeOffset = editorSnapOffsetToCharacterBoundary(buffer, offset)
        return editorNextSymbolOffset(buffer, safeOffset)
    }

    fun lineDigits(): Int = max(1, lineCount().toString().length)

    private fun activeReplaceRange(): IntRange {
        return if (hasComposingRegion()) {
            composingStart..composingEnd
        } else {
            normalizedSelection()
        }
    }

    private fun normalizedSelection(): IntRange {
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        return start..end
    }

    private fun replaceRangeInternal(
        start: Int,
        end: Int,
        replacement: String,
        afterSelectionStart: Int,
        afterSelectionEnd: Int,
        recordHistory: Boolean
    ) {
        val safeStart = start.coerceIn(0, buffer.length)
        val safeEnd = end.coerceIn(safeStart, buffer.length)
        val beforeText = buffer.subSequence(safeStart, safeEnd).toString()

        if (!recordHistory && beforeText == replacement) {
            selectionStart = afterSelectionStart.coerceIn(0, buffer.length)
            selectionEnd = afterSelectionEnd.coerceIn(0, buffer.length)
            clearComposingRegion()
            return
        }

        val nextSearchScope = trackedSearchScope?.afterEdit(safeStart, safeEnd, replacement.length)
        val command = if (recordHistory) {
            EditorEditCommand(
                start = safeStart,
                beforeText = beforeText,
                afterText = replacement,
                beforeSelectionStart = selectionStart,
                beforeSelectionEnd = selectionEnd,
                afterSelectionStart = afterSelectionStart,
                afterSelectionEnd = afterSelectionEnd,
                searchScopeRevision = searchScopeRevision,
                beforeSearchScope = trackedSearchScope,
                afterSearchScope = nextSearchScope,
            )
        } else {
            null
        }

        trackedSearchScope = nextSearchScope
        buffer.replace(safeStart, safeEnd, replacement)
        selectionStart = afterSelectionStart.coerceIn(0, buffer.length)
        selectionEnd = afterSelectionEnd.coerceIn(0, buffer.length)
        clearComposingRegion()
        publishEdit(safeStart, safeEnd, replacement)

        if (recordHistory && command != null) {
            undoStack.addLast(command)
            redoStack.clear()
        }
    }

    private fun applyCommand(
        command: EditorEditCommand,
        reverse: Boolean,
        targetStack: ArrayDeque<EditorEditCommand>
    ) {
        val replaceStart = command.start.coerceIn(0, buffer.length)
        val currentEnd = if (reverse) {
            (replaceStart + command.afterText.length).coerceIn(replaceStart, buffer.length)
        } else {
            (replaceStart + command.beforeText.length).coerceIn(replaceStart, buffer.length)
        }

        val replacement = if (reverse) command.beforeText else command.afterText
        // 同一轮圈选恢复真实边界；重新圈选后只映射偏移，不恢复旧搜索范围。
        trackedSearchScope = if (command.searchScopeRevision == searchScopeRevision) {
            if (reverse) command.beforeSearchScope else command.afterSearchScope
        } else trackedSearchScope?.afterEdit(replaceStart, currentEnd, replacement.length)
        buffer.replace(replaceStart, currentEnd, replacement)

        if (reverse) {
            selectionStart = command.beforeSelectionStart.coerceIn(0, buffer.length)
            selectionEnd = command.beforeSelectionEnd.coerceIn(0, buffer.length)
        } else {
            selectionStart = command.afterSelectionStart.coerceIn(0, buffer.length)
            selectionEnd = command.afterSelectionEnd.coerceIn(0, buffer.length)
        }

        clearComposingRegion()
        publishEdit(replaceStart, currentEnd, replacement)
        targetStack.addLast(command)
    }

    private fun clearComposingRegion() {
        composingStart = -1
        composingEnd = -1
    }

    /** 索引和快照沿用旧版本的未修改行，输入路径不再扫描全文。 */
    private fun publishEdit(start: Int, end: Int, replacement: String) {
        val lines = indexedText.lines.replace(start, end, replacement)
        version++
        indexedText = EditorTextSnapshot(lines, version, indexedText.owner)
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }
}
