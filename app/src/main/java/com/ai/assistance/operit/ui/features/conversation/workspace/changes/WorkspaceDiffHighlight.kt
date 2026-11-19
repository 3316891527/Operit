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

internal suspend fun highlightReviewLines(
    context: Context, path: String, content: ReviewContent, lines: PreparedReviewDiff,
): PreparedReviewDiff {
    val coroutine = currentCoroutineContext()
    coroutine.ensureActive()
    val language = TextMateCatalog.detectLanguage(path)
    val configuration = EditorHighlightFiles.load(EditorHighlightFiles.directory(context))
    val registry = TextMateGrammarRegistry(configuration) {
        context.assets.open("workspace-textmate/$it").bufferedReader(Charsets.UTF_8)
    }
    val rules = registry.rulesFor(language, path)
    fun highlight(text: String): HighlightSnapshot? {
        coroutine.ensureActive()
        val snapshot = EditorTextSnapshot(EditorLineIndex.fromText(text), 0, Any())
        return EditorTextMateHighlighter.highlight(snapshot, rules) { coroutine.isActive }
    }
    return lines.copy(oldHighlight = highlight(checkNotNull(content.before)), newHighlight = highlight(checkNotNull(content.after)))
}

internal suspend fun prepareReviewDiff(context: Context, path: String, content: ReviewContent): PreparedReviewDiff {
    val policy = WorkspaceDiffPolicy.classify(content)
    require(policy != ReviewDiffLoad.BLOCKED) { "Diff exceeds review limits" }
    val lines = prepareReviewLines(content)
    return if (policy == ReviewDiffLoad.AUTOMATIC) highlightReviewLines(context, path, content, lines) else lines
}
