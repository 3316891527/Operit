package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R

/** 空动作表示当前位置不支持；完整菜单始终保留相同的顺序和位置。 */
internal data class WorkspaceFileActions(
    val search: (() -> Unit)? = null,
    val archive: (() -> Unit)? = null,
    val copy: (() -> Unit)? = null,
    val cut: (() -> Unit)? = null,
    val paste: (() -> Unit)? = null,
    val rename: (() -> Unit)? = null,
    val properties: (() -> Unit)? = null,
    val openWith: (() -> Unit)? = null,
    val bookmark: (() -> Unit)? = null,
    val hide: (() -> Unit)? = null,
    val share: (() -> Unit)? = null,
    val delete: (() -> Unit)? = null,
    val extract: Boolean = false,
    val hidden: Boolean = false,
)

private data class WorkspaceFileActionSpec(
    val icon: ImageVector,
    val label: Int,
    val action: (() -> Unit)?,
    val destructive: Boolean = false,
)

@Composable
internal fun WorkspaceFileActionMenu(title: String, onDismiss: () -> Unit, actions: WorkspaceFileActions) {
    val items = listOf(
        WorkspaceFileActionSpec(Icons.Default.Search, R.string.workspace_browser_search, actions.search),
        WorkspaceFileActionSpec(if (actions.extract) Icons.Default.FolderOpen else Icons.Default.Archive,
            if (actions.extract) R.string.workspace_archive_extract else R.string.workspace_archive_compress, actions.archive),
        WorkspaceFileActionSpec(Icons.Default.ContentCopy, R.string.file_manager_copy, actions.copy),
        WorkspaceFileActionSpec(Icons.Default.ContentCut, R.string.file_manager_cut, actions.cut),
        WorkspaceFileActionSpec(Icons.Default.ContentPaste, R.string.file_manager_paste, actions.paste),
        WorkspaceFileActionSpec(Icons.Default.DriveFileRenameOutline, R.string.file_menu_rename, actions.rename),
        WorkspaceFileActionSpec(Icons.Default.Info, R.string.workspace_properties_title, actions.properties),
        WorkspaceFileActionSpec(Icons.Default.OpenInNew, R.string.workspace_open_with, actions.openWith),
        WorkspaceFileActionSpec(Icons.Default.Bookmark, R.string.workspace_browser_add_bookmark, actions.bookmark),
        WorkspaceFileActionSpec(Icons.Default.VisibilityOff,
            if (actions.hidden) R.string.workspace_browser_unhide else R.string.workspace_browser_hide, actions.hide),
        WorkspaceFileActionSpec(Icons.Default.Share, R.string.file_menu_share, actions.share),
        WorkspaceFileActionSpec(Icons.Default.Delete, R.string.file_manager_delete, actions.delete, destructive = true),
    )
    FileBrowserActionPanel(title, onDismiss) {
        for (row in items.chunked(2)) Row {
            for (item in row) WorkspaceFileAction(Modifier.weight(1f), item.icon, item.label,
                enabled = item.action != null, destructive = item.destructive) { checkNotNull(item.action).invoke() }
        }
    }
}