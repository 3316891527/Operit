package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import kotlin.math.abs

private sealed interface FileRowGesture {
    data class Tap(val change: PointerInputChange) : FileRowGesture
    data class Horizontal(val change: PointerInputChange) : FileRowGesture
    object Cancelled : FileRowGesture
}

/** 同一手势会话仲裁点击、长按与滑选；只移动内容，触摸坐标不随位移改变。 */
@Composable
internal fun WorkspaceFileRow(
    name: String,
    icon: ImageVector,
    isDirectory: Boolean,
    onClick: () -> Unit,
    detail: String = "",
    onLongPress: (() -> Unit)? = null,
    selection: Boolean? = null,
    onSwipeSelect: (() -> Unit)? = null,
    onSwipeActiveChanged: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestOnSwipe by rememberUpdatedState(onSwipeSelect)
    val latestOnSwipeActiveChanged by rememberUpdatedState(onSwipeActiveChanged)
    val latestHaptic by rememberUpdatedState(LocalHapticFeedback.current)
    val minimumSwipe = with(LocalDensity.current) { 32.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val visualOffset by animateFloatAsState(
        targetValue = if (dragging) dragOffset else 0f,
        animationSpec = if (dragging) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "workspaceRowSwipe",
    )
    DisposableEffect(Unit) {
        onDispose { if (dragging) latestOnSwipeActiveChanged(false) }
    }
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            // 仅排除正在横滑的条目，释放和取消后移除，避免整页长期占用系统手势区。
            .then(if (dragging) Modifier.systemGestureExclusion() else Modifier)
            .semantics(mergeDescendants = true) {
                role = if (selection == null) Role.Button else Role.Checkbox
                if (selection != null) selected = selection
                if (!enabled) disabled()
                semanticsOnClick { if (enabled) latestOnClick(); enabled }
                if (onLongPress != null) onLongClick { if (enabled) latestOnLongPress?.invoke(); enabled }
            }
            .pointerInput(name, isDirectory, enabled, minimumSwipe) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!enabled) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    // 先判方向，纵向与斜向移动留给列表；收到边缘触摸时同样允许滑选。
                    val intent = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var result: FileRowGesture? = null
                        while (result == null) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            result = when {
                                event.changes.size != 1 || change == null || change.isConsumed -> FileRowGesture.Cancelled
                                else -> {
                                    val dx = abs(change.position.x - down.position.x)
                                    val dy = abs(change.position.y - down.position.y)
                                    when {
                                        dx > slop && dx > dy * 1.5f ->
                                            if (latestOnSwipe != null) FileRowGesture.Horizontal(change) else FileRowGesture.Cancelled
                                        dx > slop || dy > slop -> FileRowGesture.Cancelled
                                        !change.pressed -> FileRowGesture.Tap(change)
                                        else -> null
                                    }
                                }
                            }
                        }
                        result
                    }
                    when (intent) {
                        is FileRowGesture.Tap -> { intent.change.consume(); latestOnClick() }
                        is FileRowGesture.Horizontal -> {
                            var change = intent.change
                            var cancelled = false
                            val initialOffset = visualOffset
                            val maximumOffset = size.width * 0.4f
                            fun followPointer() {
                                dragOffset = (initialOffset + change.position.x - down.position.x)
                                    .coerceIn(-maximumOffset, maximumOffset)
                            }
                            dragging = true
                            latestOnSwipeActiveChanged(true)
                            try {
                                followPointer()
                                change.consume()
                                while (change.pressed) {
                                    val event = awaitPointerEvent()
                                    val next = event.changes.firstOrNull { it.id == down.id }
                                    if (event.changes.size != 1 || next == null || next.isConsumed) {
                                        cancelled = true
                                        break
                                    }
                                    change = next
                                    followPointer()
                                    change.consume()
                                }
                                if (!cancelled && abs(change.position.x - down.position.x) >= maxOf(minimumSwipe, slop * 2f)) {
                                    latestOnSwipe?.invoke()
                                    latestHaptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            } finally {
                                dragging = false
                                latestOnSwipeActiveChanged(false)
                            }
                        }
                        null -> {
                            latestOnLongPress?.invoke()
                            waitForUpOrCancellation()?.consume()
                        }
                        FileRowGesture.Cancelled -> Unit
                    }
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth()
                .graphicsLayer { translationX = if (dragging) dragOffset else visualOffset }
                .background(if (selection == true) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
                .heightIn(min = 64.dp).padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (leading != null) leading()
                else Icon(icon, null, Modifier.size(28.dp),
                    tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                if (detail.isNotBlank()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selection != null) Checkbox(checked = selection, onCheckedChange = null)
        }
    }
}
