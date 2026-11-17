package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightFiles
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorLineIndex
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTextMateHighlighter
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTextSnapshot
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.HighlightSnapshot
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateGrammarRegistry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal data class PreparedReviewDiff(
    val document: ReviewDiffDocument,
    val oldHighlight: HighlightSnapshot?,
    val newHighlight: HighlightSnapshot?,
    val maxColumns: Int,
)

/** 在后台以完整的旧、新文本分别分词，保留多行语法状态及 UTF-16 坐标。 */
internal suspend fun prepareReviewDiff(context: Context, path: String, content: ReviewContent): PreparedReviewDiff {
    val before = checkNotNull(content.before)
    val after = checkNotNull(content.after)
    require(before.count { it == '\n' } + after.count { it == '\n' } <= 20_000) { "Diff exceeds 20,000 lines" }
    val coroutine = currentCoroutineContext()
    coroutine.ensureActive()
    val document = WorkspaceDiff.compare(before, after)
    require(document.rows.none { it.text.length > 8_192 }) { "Diff line exceeds 8,192 characters" }
    val language = TextMateCatalog.detectLanguage(path)
    val configuration = EditorHighlightFiles.load(EditorHighlightFiles.directory(context))
    val registry = TextMateGrammarRegistry(configuration) { context.assets.open("workspace-textmate/$it").bufferedReader(Charsets.UTF_8) }
    val rules = registry.rulesFor(language, path)
    fun highlight(text: String): HighlightSnapshot? {
        coroutine.ensureActive()
        val snapshot = EditorTextSnapshot(EditorLineIndex.fromText(text), 0, Any())
        return EditorTextMateHighlighter.highlight(snapshot, rules) { coroutine.isActive }
    }
    return PreparedReviewDiff(document, highlight(before), highlight(after), document.rows.maxOfOrNull { line -> line.text.sumOf { if (it == '\t') 4 else if (it.code > 127) 2 else 1 } } ?: 0)
}
