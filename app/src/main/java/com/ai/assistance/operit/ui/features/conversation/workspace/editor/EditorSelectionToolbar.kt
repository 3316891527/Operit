package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

internal enum class EditorSelectionAction { FIND, SELECT_ALL, CUT, COPY, PASTE, FIND_IN_SELECTION }
internal data class EditorSelectionMenuState(val start: Int, val end: Int, val bounds: IntRect)

/** 非焦点弹层保留编辑器和手柄触摸，不启动系统 ActionMode。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorSelectionToolbar(
    state: EditorSelectionMenuState,
    readOnly: Boolean,
    onAction: (EditorSelectionAction) -> Unit,
) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(state.bounds, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val selected = state.bounds.translate(anchorBounds.topLeft)
                val maxX = (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)
                val x = (selected.center.x - popupContentSize.width / 2).coerceIn(gap, maxX)
                val above = selected.top - popupContentSize.height - gap
                val y = if (above >= anchorBounds.top + gap) above else selected.bottom + gap * 2
                val bottom = minOf(windowSize.height, anchorBounds.bottom) - popupContentSize.height - gap
                return IntOffset(x, y.coerceIn(anchorBounds.top, bottom.coerceAtLeast(anchorBounds.top)))
            }
        }
    }
    Popup(popupPositionProvider = position,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
            Row(Modifier.widthIn(max = 320.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // 插入点只提供全选和粘贴，已有选区继续使用完整的六项菜单。
                val actions = if (state.start == state.end) listOf(EditorSelectionAction.SELECT_ALL, EditorSelectionAction.PASTE)
                    else EditorSelectionAction.entries
                actions.forEach { action ->
                    val label = stringResource(when (action) {
                        EditorSelectionAction.FIND -> R.string.workspace_editor_find
                        EditorSelectionAction.SELECT_ALL -> android.R.string.selectAll
                        EditorSelectionAction.CUT -> android.R.string.cut
                        EditorSelectionAction.COPY -> android.R.string.copy
                        EditorSelectionAction.PASTE -> android.R.string.paste
                        EditorSelectionAction.FIND_IN_SELECTION -> R.string.workspace_editor_find_in_selection
                    })
                    val icon = when (action) {
                        EditorSelectionAction.FIND, EditorSelectionAction.FIND_IN_SELECTION -> Icons.Default.Search
                        EditorSelectionAction.SELECT_ALL -> Icons.Default.SelectAll
                        EditorSelectionAction.CUT -> Icons.Default.ContentCut
                        EditorSelectionAction.COPY -> Icons.Default.ContentCopy
                        EditorSelectionAction.PASTE -> Icons.Default.ContentPaste
                    }
                    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
                        IconButton(onClick = { onAction(action) }, modifier = Modifier.size(44.dp),
                            enabled = !readOnly || action !in setOf(EditorSelectionAction.CUT, EditorSelectionAction.PASTE)) {
                            Icon(icon, label, if (action == EditorSelectionAction.FIND_IN_SELECTION)
                                Modifier.size(24.dp).border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(3.dp)).padding(2.dp)
                                else Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
