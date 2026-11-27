package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryDragSelectionAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val uris = listOf("0", "1", "2", "3")

    @Test
    fun quickRetreatRestoresTheRangeLastDrawnByTheGrid() {
        val selection = mutableStateOf(emptyList<String>())
        showGrid(selection)
        val grid = compose.onNodeWithTag("gallery-grid")

        grid.performTouchInput { down(cell(3)); moveTo(cell(2)) }
        compose.onNodeWithTag("image-2").assertIsSelected()
        compose.mainClock.autoAdvance = false
        try {
            // 越过两格后在同一帧返回，最终范围和上一帧相同，但途中确实修改过选择。
            grid.performTouchInput { moveTo(cell(0)); moveTo(cell(2)); up() }
            compose.runOnIdle { assertEquals(listOf("3", "2"), selection.value) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun nextGestureUsesTheLatestSelectionAndRetreatRestoresCancelledImagesInOrder() {
        val selection = mutableStateOf(listOf("outside-album"))
        showGrid(selection)
        compose.mainClock.autoAdvance = false
        try {
            // 两次手势之间不推进重组，第二次仍从刚刚选中的图片开始取消。
            compose.onNodeWithTag("gallery-grid").performTouchInput {
                down(cell(3)); moveTo(cell(0)); up()
                down(cell(3)); moveTo(cell(0)); moveTo(cell(2)); up()
            }
            compose.runOnIdle { assertEquals(listOf("outside-album", "1", "0"), selection.value) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun showGrid(selection: MutableState<List<String>>) {
        compose.setContent {
            val gridState = rememberLazyGridState()
            val selectedUris = selection.value
            LazyVerticalGrid(
                columns = GridCells.Fixed(uris.size),
                state = gridState,
                modifier = Modifier.fillMaxWidth().height(144.dp).testTag("gallery-grid")
                    .then(galleryDragSelectionModifier(gridState, uris, selection)),
            ) {
                items(uris.size) { index ->
                    val uri = uris[index]
                    Box(Modifier.height(144.dp).testTag("image-$uri").semantics { selected = uri in selectedUris })
                }
            }
        }
    }

    private fun TouchInjectionScope.cell(index: Int) = Offset(width * (index + 0.5f) / uris.size, centerY)
}
