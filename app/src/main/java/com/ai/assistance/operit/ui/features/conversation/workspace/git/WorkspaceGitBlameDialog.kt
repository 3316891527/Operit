package com.ai.assistance.operit.ui.features.conversation.workspace.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceDiffOptions
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceDiffView
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTextSnapshot
import com.ai.assistance.operit.util.logging.AppLogger
import java.text.DateFormat
import java.util.Date
import java.util.SimpleTimeZone
import kotlinx.coroutines.CancellationException
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

internal data class GitBlameRequest(val snapshot: EditorTextSnapshot, val line: Int)
private sealed interface BlameLoad<out T> {
    data object Loading : BlameLoad<Nothing>
    data class Ready<T>(val value: T) : BlameLoad<T>
    data class Failed(val message: String) : BlameLoad<Nothing>
}

/** 查询绑定打开时的快照，窗口离开组合时取消请求，返回差异页前保留来源信息。 */
@Composable
internal fun WorkspaceGitBlameDialog(
    path: String, request: GitBlameRequest,
    load: suspend (GitBlameRequest) -> GitLineProvenance,
    change: suspend (GitLineProvenance) -> GitBlameCommitDiff,
    onDismiss: () -> Unit,
) {
    var showDiff by remember(request) { mutableStateOf(false) }
    val latestLoad by rememberUpdatedState(load)
    Dialog(onDismissRequest = { if (showDiff) showDiff = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (showDiff) showDiff = false else onDismiss() }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Text(stringResource(if (showDiff) R.string.workspace_blame_commit_diff else R.string.workspace_blame_title),
                        Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
                Text(stringResource(R.string.workspace_blame_current_line, path, request.line),
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                HorizontalDivider()
                BlameQuery(request, { latestLoad(request) }) { provenance ->
                    if (showDiff) BlameHistory(provenance, change) else BlameDetails(request, provenance) { showDiff = true }
                }
            }
        }
    }
}

@Composable
private fun <T> ColumnScope.BlameQuery(key: Any, load: suspend () -> T, content: @Composable ColumnScope.(T) -> Unit) {
    var attempt by remember(key) { mutableIntStateOf(0) }
    var result by remember(key) { mutableStateOf<BlameLoad<T>>(BlameLoad.Loading) }
    val latestLoad by rememberUpdatedState(load)
    LaunchedEffect(key, attempt) {
        result = BlameLoad.Loading
        try {
            result = BlameLoad.Ready(latestLoad())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WorkspaceBlame", "读取代码来源失败", failure)
            result = BlameLoad.Failed(failure.message ?: failure.javaClass.simpleName)
        }
    }
    when (val loaded = result) {
        BlameLoad.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(R.string.loading), fontSize = 12.sp)
            }
        }
        is BlameLoad.Failed -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.workspace_blame_failed), color = MaterialTheme.colorScheme.error)
            SelectionContainer { Text(loaded.message, fontSize = 12.sp) }
            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.workspace_blame_retry)) }
        }
        is BlameLoad.Ready -> content(loaded.value)
    }
}

@Composable
private fun ColumnScope.BlameDetails(request: GitBlameRequest, provenance: GitLineProvenance, onChange: () -> Unit) {
    val line = provenance.line
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SelectionContainer {
            Text(request.snapshot.lines.line(request.line - 1).text, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        if (line == null) {
            Text(stringResource(R.string.workspace_blame_uncommitted), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.workspace_blame_uncommitted_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BlameField(R.string.author, line.author + "\n" + line.email)
                    BlameField(R.string.workspace_blame_time, blameAuthorDate(line))
                    BlameField(R.string.workspace_blame_summary, line.summary)
                    BlameField(R.string.workspace_blame_hash, line.commit)
                    BlameField(R.string.workspace_blame_source_path, line.path + ":" + line.originalLine)
                    BlameField(R.string.workspace_blame_repository, provenance.root)
                }
            }
            if (line.boundary) Text(stringResource(R.string.workspace_blame_boundary), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onChange) { Text(stringResource(R.string.workspace_blame_show_diff)) }
        }
    }
}

@Composable
private fun ColumnScope.BlameHistory(provenance: GitLineProvenance, load: suspend (GitLineProvenance) -> GitBlameCommitDiff) {
    val latestLoad by rememberUpdatedState(load)
    val options = rememberSaveable(saver = WorkspaceDiffOptions.Saver) { WorkspaceDiffOptions() }
    BlameQuery(provenance, { latestLoad(provenance) }) { history ->
        WorkspaceDiffView(history.diff, Modifier.weight(1f).fillMaxWidth(), options, fileHeader = {
            Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(history.message, fontSize = 12.sp)
                        Text(if (history.parent == null) stringResource(R.string.workspace_blame_root_commit)
                            else stringResource(R.string.workspace_blame_comparison, history.parent.take(12), checkNotNull(provenance.line).commit.take(12)),
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val source = checkNotNull(provenance.line).path
                        Text(if (history.beforePath != null && history.beforePath != source) "${history.beforePath} → $source" else source,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        })
    }
}

@Composable
private fun BlameField(label: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp)
    }
}

/** 使用提交作者声明的时区，界面语言只影响日期排版。 */
internal fun blameAuthorDate(line: GitBlameLine): String {
    val zone = line.timezone
    val seconds = (zone.substring(1, 3).toInt() * 60 + zone.substring(3).toInt()) * 60 * if (zone[0] == '-') -1 else 1
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
    format.timeZone = SimpleTimeZone(seconds * 1000, "GMT$zone")
    return format.format(Date(Math.multiplyExact(line.authorTime, 1000L))) + " (UTC$zone)"
}