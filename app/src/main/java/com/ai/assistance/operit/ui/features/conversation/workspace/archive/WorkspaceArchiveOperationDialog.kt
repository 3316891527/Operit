package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceFileOperationDialog
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceOperationDetail
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 归档操作和复制共用进度外观，阶段、条目数量与字节数均来自实际工作。 */
@Composable
internal fun WorkspaceArchiveOperationDialog(progress: WorkspaceArchiveProgress, onCancel: () -> Unit) {
    val context = LocalContext.current
    var cancelling by remember { mutableStateOf(false) }
    val title = stringResource(when (progress.stage) {
        WorkspaceArchiveStage.PREPARING -> R.string.workspace_archive_preparing
        WorkspaceArchiveStage.READING -> R.string.workspace_archive_reading_index
        WorkspaceArchiveStage.EXTRACTING -> R.string.workspace_archive_extracting
        WorkspaceArchiveStage.WRITING -> R.string.workspace_archive_stage_write
        WorkspaceArchiveStage.VERIFYING -> R.string.workspace_archive_stage_verify
        WorkspaceArchiveStage.TRANSFERRING -> R.string.workspace_archive_transferring
        WorkspaceArchiveStage.PUBLISHING -> R.string.workspace_archive_publishing
    })
    WorkspaceFileOperationDialog(title = title, status = title, fraction = progress.fraction,
        cancelling = cancelling, onCancel = { cancelling = true; onCancel() }) {
        if (progress.entry.isNotEmpty()) WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_name), progress.entry)
        val count = progress.totalEntries
        if (count != null) Text(stringResource(R.string.workspace_archive_entry_progress, progress.completedEntries, count),
            style = MaterialTheme.typography.bodyMedium)
        else if (progress.completedEntries > 0) Text(stringResource(R.string.workspace_archive_entries_done, progress.completedEntries),
            style = MaterialTheme.typography.bodyMedium)
        val total = progress.total
        if (total != null) Text(stringResource(R.string.workspace_archive_byte_progress,
            Formatter.formatShortFileSize(context, progress.bytes), Formatter.formatShortFileSize(context, total)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (progress.bytes > 0) Text(stringResource(R.string.workspace_archive_processed,
            Formatter.formatShortFileSize(context, progress.bytes)), style = MaterialTheme.typography.bodySmall)
        val current = progress.entryFraction
        if (current != null) {
            Text(stringResource(R.string.workspace_archive_current_file), style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { current }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }
}