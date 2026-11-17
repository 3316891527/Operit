package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** 搜索范围独立于当前光标，边界在真实文档编辑时按偏移同步移动。 */
data class EditorSearchScope(val start: Int, val end: Int) {
    init { require(start >= 0 && end >= start) }

    internal fun afterEdit(editStart: Int, editEnd: Int, insertedLength: Int): EditorSearchScope {
        require(editStart >= 0 && editEnd >= editStart && insertedLength >= 0)
        val delta = insertedLength - (editEnd - editStart)
        val nextStart = when {
            start < editStart -> start
            start > editEnd || (start == editEnd && editStart != editEnd) -> start + delta
            else -> editStart
        }
        val nextEnd = when {
            end < editStart || (end == editStart && editStart != editEnd) -> end
            end >= editEnd -> end + delta
            else -> editStart + insertedLength
        }
        return EditorSearchScope(nextStart, nextEnd.coerceAtLeast(nextStart))
    }
}

internal data class EditorSearchOptions(val caseSensitive: Boolean = false, val wholeWord: Boolean = false, val regex: Boolean = false)
internal data class EditorSearchMatch(val start: Int, val end: Int, val replacement: String) {
    val range: IntRange get() = start until end
}
internal data class EditorSearchResult(val matches: List<EditorSearchMatch> = emptyList(), val error: String? = null)

/** 搜索和替换共用一份匹配；零宽匹配由 Matcher 推进，普通替换中的美元与反斜杠按原文写入。 */
internal fun searchEditorText(text: String, query: String, options: EditorSearchOptions, replacement: String? = null, scope: EditorSearchScope? = null): EditorSearchResult {
    if (query.isEmpty()) return EditorSearchResult()
    return try {
        val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        val pattern = Pattern.compile(if (options.regex) query else Pattern.quote(query), flags)
        val matcher = pattern.matcher(text)
        if (scope != null) {
            require(scope.end <= text.length)
            matcher.region(scope.start, scope.end)
        }
        val buffer = StringBuffer()
        var appendedThrough = 0
        val matches = mutableListOf<EditorSearchMatch>()
        fun word(code: Int): Boolean = Character.isLetterOrDigit(code) || code == '_'.code ||
            Character.getType(code) in setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt())
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (options.wholeWord && ((start > 0 && word(Character.codePointBefore(text, start))) ||
                    (end < text.length && word(Character.codePointAt(text, end))))) continue
            val inserted = if (replacement == null) "" else {
                val previousSize = buffer.length
                matcher.appendReplacement(buffer, if (options.regex) replacement else java.util.regex.Matcher.quoteReplacement(replacement))
                val expanded = buffer.substring(previousSize + start - appendedThrough)
                appendedThrough = end
                expanded
            }
            matches += EditorSearchMatch(start, end, inserted)
        }
        EditorSearchResult(matches)
    } catch (error: PatternSyntaxException) {
        EditorSearchResult(error = error.description)
    } catch (error: IllegalArgumentException) {
        EditorSearchResult(error = error.message ?: error.javaClass.simpleName)
    } catch (error: IndexOutOfBoundsException) {
        EditorSearchResult(error = error.message ?: error.javaClass.simpleName)
    }
}

internal fun replaceEditorMatches(text: String, matches: List<EditorSearchMatch>): String = buildString {
    var offset = 0
    for (match in matches) {
        require(match.start >= offset && match.end in match.start..text.length)
        append(text, offset, match.start)
        append(match.replacement)
        offset = match.end
    }
    append(text, offset, text.length)
}
