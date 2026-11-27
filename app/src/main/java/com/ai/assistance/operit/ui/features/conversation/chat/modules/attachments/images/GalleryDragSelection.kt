package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max

/** 起点决定整次手势选中或取消；回滑恢复区间外的原状态，保留仍选中图片的加入顺序。 */
internal class GalleryDragSelection(
    private val visibleUris: List<String>,
    private val existing: List<String>,
    private val anchor: Int,
) {
    init { require(anchor in visibleUris.indices) }
    private val existingSet = existing.toSet()
    private val selecting = visibleUris[anchor] !in existingSet
    private val added = linkedSetOf<String>()

    fun through(index: Int): List<String> {
        require(index in visibleUris.indices)
        val dragged = if (index >= anchor) anchor..index else anchor downTo index
        val rangeUris = dragged.map { visibleUris[it] }
        val rangeSet = rangeUris.toSet()
        if (!selecting) return existing.filterNot { it in rangeSet }
        // 只撤销本次手势离开区间的新增项，已有项不移动，新项按指尖经过的方向追加。
        added.retainAll(rangeSet)
        rangeUris.filterNot { it in existingSet }.forEach { added.add(it) }
        return existing + added
    }
}

/** 仅拦截占主导的横向拖动，保留纵向图库滚动、单击复选与长按预览。 */
@Composable
internal fun galleryDragSelectionModifier(
    gridState: LazyGridState,
    visibleUris: List<String>,
    selection: MutableState<List<String>>,
): Modifier {
    var drag by remember(visibleUris) { mutableStateOf<GalleryDragSelection?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val maxStep = with(density) { 18.dp.toPx() }

    fun selectAt(session: GalleryDragSelection, position: Offset) {
        val items = gridState.layoutInfo.visibleItemsInfo
        val index = nearestGalleryIndex(position, items) ?: return
        // 指针事件可能连续到达，读写同一份选择状态，回滑无需等待下一次重组。
        selection.value = session.through(index)
    }

    LaunchedEffect(drag, gridState, selection) {
        val session = drag ?: return@LaunchedEffect
        while (isActive && drag === session) {
            val height = gridState.layoutInfo.viewportSize.height.toFloat()
            val delta = when {
                height <= 0f -> 0f
                pointer.y < edge -> -maxStep * ((edge - pointer.y) / edge).coerceIn(0f, 1f)
                pointer.y > height - edge -> maxStep * ((pointer.y - height + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (delta != 0f) {
                gridState.scrollBy(delta)
                if (drag === session) selectAt(session, pointer)
            }
            delay(16)
        }
    }

    return Modifier.pointerInput(gridState, visibleUris, selection) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val anchor = exactGalleryIndex(down.position, gridState.layoutInfo.visibleItemsInfo)
                ?: return@awaitEachGesture
            val change = awaitTouchSlopOrCancellation(down.id) { moved, slop ->
                if (abs(slop.x) > abs(slop.y)) moved.consume()
            }
            if (change == null || !change.isConsumed) return@awaitEachGesture
            val session = GalleryDragSelection(visibleUris, selection.value, anchor)
            drag = session
            try {
                pointer = change.position
                selectAt(session, pointer)
                while (true) {
                    val event = awaitPointerEvent()
                    val moved = event.changes.firstOrNull { it.id == down.id } ?: break
                    pointer = moved.position
                    selectAt(session, pointer)
                    moved.consume()
                    if (!moved.pressed) break
                }
            } finally {
                drag = null
            }
        }
    }
}

private fun exactGalleryIndex(position: Offset, items: List<LazyGridItemInfo>): Int? =
    items.firstOrNull { item ->
        position.x >= item.offset.x && position.x < item.offset.x + item.size.width &&
            position.y >= item.offset.y && position.y < item.offset.y + item.size.height
    }?.index

private fun nearestGalleryIndex(position: Offset, items: List<LazyGridItemInfo>): Int? =
    items.minByOrNull { item ->
        val dx = max(item.offset.x - position.x, 0f).coerceAtLeast(
            position.x - item.offset.x - item.size.width,
        )
        val dy = max(item.offset.y - position.y, 0f).coerceAtLeast(
            position.y - item.offset.y - item.size.height,
        )
        dx * dx + dy * dy
    }?.index
