package com.ai.assistance.operit.ui.features.conversation.workspace.markdown

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.MarkdownOutlineState
import com.ai.assistance.operit.ui.common.markdown.visibleMarkdownHeadings
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

@Composable
internal fun BoxScope.WorkspaceMarkdownOutline(
    outline: MarkdownOutlineState,
    visible: Boolean,
    active: Int?,
    onDismiss: () -> Unit,
    onNavigate: (Int) -> Unit,
) {
    var collapsed by remember(outline) { mutableStateOf(emptySet<Int>()) }
    val headings = outline.headings
    val displayed = remember(headings, collapsed) { visibleMarkdownHeadings(headings, collapsed) }
    val parents = remember(headings) { headings.mapNotNull { it.parentIndex }.toSet() }
    val listState = rememberLazyListState()
    BackHandler(visible, onDismiss)
    LaunchedEffect(visible) {
        if (visible) {
            val selected = displayed.indexOfFirst { it.nodeIndex == active }
            if (selected >= 0) listState.scrollToItem(selected)
        }
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)).clickable(onClick = onDismiss))
    }
    AnimatedVisibility(visible, Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
        Surface(Modifier.fillMaxHeight().widthIn(max = 360.dp).fillMaxWidth(0.82f), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.workspace_markdown_outline), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
                HorizontalDivider()
                if (headings.isEmpty()) Text(stringResource(R.string.workspace_markdown_outline_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(displayed, key = { it.nodeIndex }) { heading ->
                        Row(Modifier.fillMaxWidth().background(if (heading.nodeIndex == active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(start = 8.dp + ((heading.level - 1) * 12).dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (heading.nodeIndex in parents) {
                                val folded = heading.nodeIndex in collapsed
                                IconButton(onClick = { collapsed = if (folded) collapsed - heading.nodeIndex else collapsed + heading.nodeIndex }, modifier = Modifier.size(32.dp)) {
                                    Icon(if (folded) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                        stringResource(if (folded) R.string.workspace_markdown_outline_expand else R.string.workspace_markdown_outline_collapse), Modifier.size(18.dp))
                                }
                            } else Spacer(Modifier.width(32.dp))
                            Text(heading.title, Modifier.weight(1f).clickable(enabled = outline.offset(heading.nodeIndex) != null) { onNavigate(heading.nodeIndex) }
                                .padding(vertical = 14.dp), style = MaterialTheme.typography.bodyMedium,
                                color = if (heading.nodeIndex == active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
