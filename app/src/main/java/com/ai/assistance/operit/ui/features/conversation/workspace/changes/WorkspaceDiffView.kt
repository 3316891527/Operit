// SPDX-License-Identifier: AGPL-3.0-only
package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightRendering
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.HighlightSnapshot
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.EditorTheme
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.currentEditorTheme
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 查看偏好属于整个审阅页，切换文件不重置布局、换行和完整文件选项。 */
@Stable
internal class WorkspaceDiffOptions(split: Boolean = false, wrap: Boolean = false, full: Boolean = false) {
    var split by mutableStateOf(split)
    var wrap by mutableStateOf(wrap)
    var full by mutableStateOf(full)
    companion object {
        val Saver = listSaver<WorkspaceDiffOptions, Boolean>(
            save = { listOf(it.split, it.wrap, it.full) },
            restore = { WorkspaceDiffOptions(it[0], it[1], it[2]) },
        )
    }
}

@Composable
internal fun WorkspaceDiffView(
    diff: PreparedReviewDiff, modifier: Modifier, options: WorkspaceDiffOptions,
    toolbarPrefix: @Composable () -> Unit = {}, fileHeader: @Composable () -> Unit = {},
) {
    var expanded by remember(diff) { mutableStateOf(emptySet<Int>()) }
    var splitRatio by rememberSaveable { mutableFloatStateOf(0.5f) }
    // 整个版本为空时，不为不存在的一侧占用半幅画布；用户的双栏偏好仍保留。
    val hasBefore = diff.document.rows.any { it.oldLine != null }
    val hasAfter = diff.document.rows.any { it.newLine != null }
    val split = options.split && hasBefore && hasAfter
    val rows = remember(diff, split, options.full, expanded) { WorkspaceDiff.items(diff.document, split, options.full, expanded) }
    val changes = remember(rows) { WorkspaceDiff.changeStarts(rows) }
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState()
    val scope = rememberCoroutineScope()
    val theme = currentEditorTheme(11f)
    val rendering = remember(theme) { EditorHighlightRendering(theme) }
    val add = changeAdditionColor()
    val remove = changeDeletionColor()
    val density = LocalDensity.current
    val gutterWidth = maxOf(36.dp, ((diff.document.rows.maxOfOrNull { maxOf(it.oldLine ?: 0, it.newLine ?: 0) } ?: 0).toString().length * 7 + 12).dp)
    val previous = changes.lastOrNull { it < listState.firstVisibleItemIndex }
    val next = changes.firstOrNull { it > listState.firstVisibleItemIndex }
    val resizeLabel = stringResource(R.string.workspace_changes_resize_split)
    LaunchedEffect(options.wrap) { if (options.wrap) horizontal.scrollTo(0) }
    Column(modifier.background(theme.background)) {
        Row(Modifier.fillMaxWidth().height(32.dp).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.35f))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            toolbarPrefix()
            WorkspaceDiffControls(options)
            VerticalDivider(Modifier.height(14.dp).padding(horizontal = 3.dp))
            ChangeIconButton(Icons.Default.KeyboardArrowUp, stringResource(R.string.workspace_changes_previous_hunk), { previous?.let { target -> scope.launch { listState.animateScrollToItem(target) } } }, enabled = previous != null)
            ChangeIconButton(Icons.Default.KeyboardArrowDown, stringResource(R.string.workspace_changes_next_hunk), { next?.let { target -> scope.launch { listState.animateScrollToItem(target) } } }, enabled = next != null)
        }
        HorizontalDivider(color = theme.gutterBorder.copy(alpha = 0.4f))
        fileHeader()
        if (options.split && hasBefore != hasAfter) Text(stringResource(if (hasAfter) R.string.workspace_changes_added_only else R.string.workspace_changes_deleted_only),
            Modifier.fillMaxWidth().background(theme.gutterBackground).padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Normal, color = if (hasAfter) add else remove)
        if (diff.document.newlineChanged) Text(stringResource(R.string.workspace_changes_newline), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = theme.lineNumberColor, fontSize = 11.sp)
        HorizontalDivider(color = theme.gutterBorder.copy(alpha = 0.5f))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val textWidth = with(density) { (diff.maxColumns * 6.7.sp.toPx()).toDp() } + gutterWidth + 32.dp
            val canvasWidth = if (options.wrap) maxWidth else maxOf(maxWidth, if (split) textWidth * 2 + 1.dp else textWidth)
            val minRatio = minOf(0.5f, 96f / canvasWidth.value.coerceAtLeast(1f))
            val ratio = splitRatio.coerceIn(minRatio, 1f - minRatio)
            val leftWidth = (canvasWidth - 1.dp) * ratio
            val rightWidth = canvasWidth - 1.dp - leftWidth
            SelectionContainer {
                Box(Modifier.fillMaxSize().horizontalScroll(horizontal, enabled = !options.wrap)) {
                    Column(Modifier.width(canvasWidth).fillMaxHeight()) {
                        if (split) {
                            Row(Modifier.fillMaxWidth().background(theme.gutterBackground)) {
                                Text(stringResource(R.string.workspace_changes_before), Modifier.width(leftWidth).padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 10.sp, color = remove)
                                Text(stringResource(R.string.workspace_changes_after), Modifier.width(rightWidth).padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 10.sp, color = add)
                            }
                            HorizontalDivider(color = theme.gutterBorder.copy(alpha = 0.5f))
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                                itemsIndexed(rows, contentType = { _, item -> when (item) {
                                    is ReviewDiffItem.Line -> "line"
                                    is ReviewDiffItem.Gap -> "gap"
                                    is ReviewDiffItem.Hunk -> "hunk"
                                } }) { _, item ->
                                    when (item) {
                                        is ReviewDiffItem.Hunk -> Text("@@ −${item.oldStart},${item.oldCount} +${item.newStart},${item.newCount} @@",
                                            Modifier.fillMaxWidth().background(theme.gutterBackground).padding(start = gutterWidth + 19.dp, top = 4.dp, bottom = 4.dp),
                                            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = theme.lineNumberColor, maxLines = 1)
                                        is ReviewDiffItem.Gap -> DiffGap(item.count, theme) { expanded = expanded + item.start }
                                        is ReviewDiffItem.Line -> if (split) {
                                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                                DiffCell(item.left, true, true, diff.oldHighlight, theme, rendering, add, remove, options.wrap, gutterWidth, Modifier.width(leftWidth).fillMaxHeight())
                                                VerticalDivider(Modifier.fillMaxHeight(), color = theme.gutterBorder.copy(alpha = 0.5f))
                                                DiffCell(item.right, true, false, diff.newHighlight, theme, rendering, add, remove, options.wrap, gutterWidth, Modifier.width(rightWidth).fillMaxHeight())
                                            }
                                        } else {
                                            val line = checkNotNull(item.right)
                                            DiffCell(line, false, false, if (line.kind == DiffLineKind.REMOVE) diff.oldHighlight else diff.newHighlight,
                                                theme, rendering, add, remove, options.wrap, gutterWidth, Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                            if (split) Box(Modifier.offset(x = leftWidth - 5.dp).width(11.dp).fillMaxHeight().semantics { contentDescription = resizeLabel }
                                .draggable(rememberDraggableState { delta ->
                                    splitRatio = (splitRatio.coerceIn(minRatio, 1f - minRatio) + with(density) { delta.toDp().value } / canvasWidth.value).coerceIn(minRatio, 1f - minRatio)
                                }, Orientation.Horizontal))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkspaceDiffControls(options: WorkspaceDiffOptions, enabled: Boolean = true) {
    ChangeIconButton(Icons.Default.TableRows, stringResource(R.string.workspace_changes_unified), { options.split = false }, selected = !options.split, enabled = enabled)
    ChangeIconButton(Icons.Default.VerticalSplit, stringResource(R.string.workspace_changes_split), { options.split = true }, selected = options.split, enabled = enabled)
    Spacer(Modifier.width(6.dp))
    ChangeIconButton(Icons.Default.UnfoldMore, stringResource(R.string.workspace_changes_full), { options.full = !options.full }, selected = options.full, enabled = enabled)
    ChangeIconButton(Icons.AutoMirrored.Filled.WrapText, stringResource(R.string.workspace_changes_wrap), { options.wrap = !options.wrap }, selected = options.wrap, enabled = enabled)
}

@Composable
private fun DiffGap(count: Int, theme: EditorTheme, onExpand: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = theme.gutterBorder.copy(alpha = 0.5f))
        Surface(onClick = onExpand, shape = RoundedCornerShape(16.dp), color = theme.gutterBackground) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.UnfoldMore, contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.workspace_changes_expand, count), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(Modifier.weight(1f), color = theme.gutterBorder.copy(alpha = 0.5f))
    }
}

@Composable
private fun DiffCell(
    line: ReviewDiffLine?, split: Boolean, oldSide: Boolean, highlight: HighlightSnapshot?,
    theme: EditorTheme, rendering: EditorHighlightRendering, add: Color, remove: Color, wrap: Boolean, gutterWidth: Dp, modifier: Modifier,
) {
    val changed = when (line?.kind) { DiffLineKind.ADD -> add; DiffLineKind.REMOVE -> remove; else -> Color.Transparent }
    val background = if (line == null) theme.gutterBackground.copy(alpha = 0.5f) else changed.copy(alpha = if (changed == Color.Transparent) 0f else 0.12f)
    Row(modifier.height(IntrinsicSize.Min).background(background).heightIn(min = 22.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(changed))
        val number = (if (split) (if (oldSide) line?.oldLine else line?.newLine) else if (line?.kind == DiffLineKind.REMOVE) line.oldLine else line?.newLine)?.toString().orEmpty()
        Text(number, Modifier.width(gutterWidth).fillMaxHeight().background(if (changed == Color.Transparent) theme.gutterBackground else changed.copy(alpha = 0.09f)).padding(end = 7.dp, top = 3.dp),
            color = if (changed == Color.Transparent) theme.lineNumberColor.copy(alpha = 0.75f) else changed, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, maxLines = 1, textAlign = TextAlign.End)
        val marker = when (line?.kind) { DiffLineKind.ADD -> "+"; DiffLineKind.REMOVE -> "−"; else -> " " }
        Text(marker, Modifier.width(16.dp).padding(top = 3.dp), color = changed, fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        if (line != null) {
            val styled = remember(line, highlight, rendering, changed, oldSide) { diffText(line, highlight, rendering, changed, oldSide) }
            Text(styled, Modifier.weight(1f).padding(end = 10.dp, top = 3.dp, bottom = 3.dp), color = theme.textColor, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace, softWrap = wrap, maxLines = if (wrap) Int.MAX_VALUE else 1, overflow = TextOverflow.Clip)
        }
    }
}

private fun diffText(line: ReviewDiffLine, highlight: HighlightSnapshot?, rendering: EditorHighlightRendering, changed: Color, oldSide: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder(line.text)
    val lineNumber = if (oldSide || line.kind == DiffLineKind.REMOVE) line.oldLine else line.newLine
    if (highlight != null && lineNumber != null && lineNumber <= highlight.text.lines.size) {
        val offset = highlight.text.lines.start(lineNumber - 1)
        var start = 0
        while (start < line.text.length) {
            val style = highlight.styleAt(offset + start)
            var end = start + 1
            while (end < line.text.length && highlight.styleAt(offset + end) == style) end++
            val resolved = rendering.resolve(style)
            val decorations = buildList {
                if (resolved.underline) add(TextDecoration.Underline)
                if (resolved.strike) add(TextDecoration.LineThrough)
            }
            builder.addStyle(SpanStyle(color = Color(resolved.color),
                fontWeight = if (resolved.bold) FontWeight.Medium else FontWeight.Normal,
                fontStyle = if (resolved.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = TextDecoration.combine(decorations)), start, end)
            start = end
        }
    }
    line.emphasis?.let { builder.addStyle(SpanStyle(background = changed.copy(alpha = 0.28f)), it.start, it.end) }
    return builder.toAnnotatedString()
}
