package com.ai.assistance.operit.ui.features.conversation.workspace.svg

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserActionPanel
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceFileAction

/** 单个 SVG 的长按入口与普通操作面板采用相同的圆角、间距和主题。 */
@Composable
internal fun WorkspaceSvgActionMenu(name: String, onDismiss: () -> Unit, onEdit: () -> Unit,
    onConvert: (WorkspaceSvgFormat) -> Unit) {
    FileBrowserActionPanel(name, onDismiss) {
        WorkspaceFileAction(Modifier.fillMaxWidth(), Icons.Default.Edit, R.string.workspace_svg_edit, onClick = onEdit)
        WorkspaceFileAction(Modifier.fillMaxWidth(), Icons.Default.Code, R.string.workspace_svg_to_xml) { onConvert(WorkspaceSvgFormat.XML) }
        WorkspaceFileAction(Modifier.fillMaxWidth(), Icons.Default.Image, R.string.workspace_svg_to_png) { onConvert(WorkspaceSvgFormat.PNG) }
    }
}
