package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightStyle
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorStyleResolver

import java.time.Duration
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack

internal enum class EditorTokenKind { DEFAULT, KEYWORD, FUNCTION, STRING, NUMBER, COMMENT, TYPE, VARIABLE }
internal data class EditorHighlightSpan(val start: Int, val end: Int, val style: EditorHighlightStyle)
internal class EditorTextMateRules(val language: String, val grammar: IGrammar, val styles: EditorStyleResolver = EditorStyleResolver())
internal class EditorTokenLine(
    val rules: EditorTextMateRules,
    val entering: IStateStack?,
    val exiting: IStateStack,
    val spans: List<EditorHighlightSpan>,
)
internal class EditorHighlightNode(
    val source: EditorLineNode,
    val rules: EditorTextMateRules,
    val entering: IStateStack?,
    val exiting: IStateStack,
    val line: EditorTokenLine,
    val left: EditorHighlightNode?,
    val right: EditorHighlightNode?,
)

internal class HighlightSnapshot(
    val text: EditorTextSnapshot,
    private val root: EditorHighlightNode?,
    val computedLines: Int = 0,
) {
    val version: Int get() = text.version

    fun styleAt(offset: Int): EditorHighlightStyle {
        if (offset !in 0 until text.length) return EditorHighlightStyle.Default
        var node = root ?: return EditorHighlightStyle.Default
        var local = offset
        while (true) {
            val leftSpan = node.source.left?.span ?: 0
            when {
                local < leftSpan -> node = checkNotNull(node.left)
                local < leftSpan + node.source.line.text.length -> {
                    val position = local - leftSpan
                    val spans = node.line.spans
                    var low = 0
                    var high = spans.lastIndex
                    while (low <= high) {
                        val middle = (low + high) ushr 1
                        val span = spans[middle]
                        when {
                            position < span.start -> high = middle - 1
                            position >= span.end -> low = middle + 1
                            else -> return span.style
                        }
                    }
                    return EditorHighlightStyle.Default
                }
                local == leftSpan + node.source.line.text.length -> return EditorHighlightStyle.Default
                else -> {
                    local -= leftSpan + node.source.line.text.length + 1
                    node = checkNotNull(node.right)
                }
            }
        }
    }

    fun kindAt(offset: Int): EditorTokenKind = styleAt(offset).kind
}

/** TextMate 规则栈相等时复用整棵未变化子树，着色结果始终对应完整文本版本。 */
internal object EditorTextMateHighlighter {
    private val lineTimeLimit = Duration.ofSeconds(1)

    fun highlight(text: EditorTextSnapshot, rules: EditorTextMateRules?, current: () -> Boolean = { true }): HighlightSnapshot? {
        if (!current()) return null
        if (rules == null) return HighlightSnapshot(text, null)
        var computed = 0
        fun build(source: EditorLineNode, entering: IStateStack?, firstLine: Int): EditorHighlightNode? {
            if (!current()) return null
            source.highlightCache?.let { if (it.rules === rules && it.entering == entering) return it }
            val left = source.left?.let { build(it, entering, firstLine) ?: return null }
            val lineState = left?.exiting ?: entering
            val previous = source.line.highlightCache
            val lineNumber = firstLine + (source.left?.count ?: 0)
            val line = if (previous != null && previous.rules === rules && previous.entering == lineState) previous else {
                computed++
                tokenize(source.line.text, rules, lineState, lineNumber, current)?.also { source.line.highlightCache = it }
                    ?: return null
            }
            val right = source.right?.let { build(it, line.exiting, lineNumber + 1) ?: return null }
            val exiting = right?.exiting ?: line.exiting
            return EditorHighlightNode(source, rules, entering, exiting, line, left, right).also { source.highlightCache = it }
        }
        val root = build(text.lines.root, null, 0) ?: return null
        return if (current()) HighlightSnapshot(text, root, computed) else null
    }

    private fun tokenize(text: String, rules: EditorTextMateRules, entering: IStateStack?, lineNumber: Int, current: () -> Boolean): EditorTokenLine? {
        val result = rules.grammar.tokenizeLine(text, entering, lineTimeLimit)
        if (!current()) return null
        check(!result.isStoppedEarly) { "TextMate time limit: ${rules.language}, line ${lineNumber + 1}" }
        val spans = mutableListOf<EditorHighlightSpan>()
        for (token in result.tokens) {
            // 引擎兼容类直接输出 UTF-16 坐标，禁止再次按码点转换。
            val start = token.startIndex.coerceIn(0, text.length)
            val end = token.endIndex.coerceIn(start, text.length)
            val style = rules.styles.resolve(token.scopes)
            if (start == end || style == EditorHighlightStyle.Default) continue
            val previous = spans.lastOrNull()
            if (previous != null && previous.end == start && previous.style == style) spans[spans.lastIndex] = previous.copy(end = end)
            else spans += EditorHighlightSpan(start, end, style)
        }
        return EditorTokenLine(rules, entering, result.ruleStack, spans)
    }
}
