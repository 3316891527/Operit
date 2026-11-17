package com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer

import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R

@Composable
internal fun WorkspaceDeleteProgressDialog(progress: WorkspaceDeleteProgress, cancelling: Boolean, onCancel: () -> Unit) {
    val target = progress.target
    WorkspaceFileOperationDialog(
        title = stringResource(R.string.workspace_transfer_deleting),
        status = stringResource(if (progress.scanning) R.string.workspace_transfer_scanning_delete else R.string.workspace_transfer_deleting),
        fraction = progress.fraction,
        cancelling = cancelling,
        onCancel = onCancel,
    ) {
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_name), target.path.trimEnd('/').substringAfterLast('/'))
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_source), "${target.environment ?: "android"}:${target.path}")
        Text(if (progress.scanning) stringResource(R.string.workspace_transfer_discovered_items, progress.totalItems)
            else stringResource(R.string.workspace_transfer_deleted_items, progress.completedItems, progress.totalItems),
            style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.workspace_transfer_delete_recursive_scope),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.workspace_transfer_delete_recursive_cancel_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
