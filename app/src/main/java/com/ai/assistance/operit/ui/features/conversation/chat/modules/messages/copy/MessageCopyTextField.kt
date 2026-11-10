package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.copy

import com.ai.assistance.operit.ui.theme.script.text.LocalThemeTextStroke
import com.ai.assistance.operit.ui.theme.script.text.ResolvedThemeTextStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import com.ai.assistance.operit.ui.theme.script.text.ThemeBasicTextField as BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun messageCopyEdgeScrollFactor(pointerY: Float, viewportHeight: Float, edgeSize: Float): Float {
    val edge = edgeSize.coerceAtMost(viewportHeight / 2f)
    if (viewportHeight <= 0f || edge <= 0f) return 0f
    return when {
        pointerY < edge -> -((edge - pointerY) / edge).coerceIn(0f, 1f)
        pointerY > viewportHeight - edge -> ((pointerY - (viewportHeight - edge)) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** 复制预览独立持有文本选区和滚动位置，焦点或布局更新不重新创建字符串版编辑状态。 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MessageCopyTextField(
    text: String,
    modifier: Modifier = Modifier,
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    // 正文由消息源重新提供，状态包只保存选区，避免长消息重复占用系统保存状态的容量。
    val selectionSaver = remember(text) {
        Saver<TextFieldState, List<Int>>(
            save = { listOf(it.selection.start, it.selection.end) },
            restore = { TextFieldState(initialText = text, initialSelection = TextRange(it[0], it[1])) },
        )
    }
    val editor = rememberSaveable(text, saver = selectionSaver) {
        TextFieldState(initialText = text)
    }
    val scroll = rememberSaveable(text, saver = ScrollState.Saver) { ScrollState(0) }
    // Observe without consuming so BasicTextField keeps ownership of selection handles.
    val selectionScrollModifier = Modifier.pointerInput(editor, scroll) {
        val edgeSize = 48.dp.toPx()
        val maxSpeed = 900.dp.toPx()
        val viewportHeight = size.height.toFloat()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val initialSelection = editor.selection
            var pointerY = down.position.y
            var selectionChanged = false
            val autoScrollJob = launch {
                var previousFrame = 0L
                while (isActive) {
                    val frame = withFrameNanos { it }
                    if (previousFrame != 0L && selectionChanged) {
                        val elapsed = ((frame - previousFrame).coerceAtMost(50_000_000L)) / 1_000_000_000f
                        val factor = messageCopyEdgeScrollFactor(pointerY, viewportHeight, edgeSize)
                        if (factor != 0f) scroll.scrollBy(factor * maxSpeed * elapsed)
                    }
                    previousFrame = frame
                }
            }
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    pointerY = change.position.y
                    selectionChanged = editor.selection != initialSelection
                }
            } finally {
                autoScrollJob.cancel()
            }
        }
    }
    BasicTextField(
        state = editor,
        textStroke = textStroke,
        scrollState = scroll,
        readOnly = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.then(selectionScrollModifier),
    )
}
