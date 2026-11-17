package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 点击区与可见圆角框分开，扩大触摸高度不会增加框内的上下留白。 */
@Composable
internal fun WorkspaceNavigationTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    unsaved: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(modifier.heightIn(min = 44.dp).clickable(interactionSource = interactions, indication = null, onClick = onClick).semantics { this.selected = selected }, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp),
            color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
            Row(Modifier.heightIn(min = 28.dp).padding(start = 8.dp, end = if (onClose == null) 8.dp else 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(16.dp))
                Text(title, Modifier.widthIn(max = if (icon == null) 156.dp else 96.dp), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onClose != null) {
                    Box(Modifier.size(24.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(if (unsaved) Icons.Default.FiberManualRecord else Icons.Default.Close,
                            stringResource(if (unsaved) R.string.workspace_unsaved else R.string.workspace_close), Modifier.size(if (unsaved) 8.dp else 16.dp))
                    }
                }
            }
        }
    }
}
