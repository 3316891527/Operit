package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceFileRowGestureTest {
    @get:Rule val compose = createComposeRule()
    private val names = (1..20).map { "file$it" }
    private val selected = mutableStateOf(emptySet<String>())
    private var taps = 0
    private var longPresses = 0
    private var scrolled = false
    private var swipeActive = false
    private lateinit var composeView: View
    private val feedback = mutableListOf<HapticFeedbackType>()
    private val rowsEnabled = mutableStateOf(true)
    private val haptic = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback += hapticFeedbackType }
    }

    private fun showRows() {
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                MaterialTheme {
                    val view = LocalView.current
                    SideEffect { composeView = view }
                    var anchor by remember { mutableStateOf<String?>(null) }
                    val list = rememberLazyListState()
                    val hasScrolled = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0
                    SideEffect { scrolled = hasScrolled }
                    LazyColumn(Modifier.fillMaxWidth().height(420.dp), state = list) {
                        items(names, key = { it }) { name ->
                            WorkspaceFileRow(name, Icons.Default.Folder, true,
                                onClick = { taps++; selected.value = if (name in selected.value) selected.value - name else selected.value + name },
                                onLongPress = { longPresses++ }, selection = name in selected.value,
                                enabled = rowsEnabled.value, onSwipeActiveChanged = { swipeActive = it },
                                onSwipeSelect = {
                                    val next = selectWorkspaceRange(names, selected.value, anchor, name)
                                    selected.value = next.selected
                                    anchor = next.anchor
                                })
                        }
                    }
                }
            }
        }
    }
    private fun swipe(name: String, right: Boolean) {
        compose.onNodeWithText(name).performTouchInput {
            val from = Offset(width * if (right) 0.3f else 0.7f, centerY)
            val to = Offset(width * if (right) 0.7f else 0.3f, centerY)
            swipe(from, to, durationMillis = 180)
        }
    }
    @Test fun swipingFirstThenFourthSelectsAllFourWithoutExtraClicks() {
        showRows()
        swipe("file1", true)
        compose.onNodeWithText("file1").assertIsSelected()
        compose.onNodeWithText("file4").performClick()
        swipe("file4", false)
        compose.runOnIdle {
            assertEquals(setOf("file1", "file2", "file3", "file4"), selected.value)
            assertEquals(1, taps)
            assertEquals(0, longPresses)
        }
    }
    @Test fun reverseSwipesSelectTheSameClosedInterval() {
        showRows()
        swipe("file4", false)
        swipe("file1", true)
        compose.runOnIdle {
            assertEquals(setOf("file1", "file2", "file3", "file4"), selected.value)
            assertEquals(0, taps)
        }
    }
    @Test fun verticalSwipesScrollTheListWithoutSelectingOrOpening() {
        showRows()
        compose.onNodeWithText("file2").performTouchInput { swipeUp(durationMillis = 180) }
        compose.runOnIdle {
            assertTrue(scrolled)
            assertTrue(selected.value.isEmpty())
            assertEquals(0, taps)
        }
    }
    @Test fun longPressShortMovementAndCancellationDoNotBecomeSwipes() {
        showRows()
        compose.onNodeWithText("file1").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, longPresses); assertTrue(selected.value.isEmpty()) }
        compose.onNodeWithText("file2").performTouchInput { down(center); moveBy(Offset(1f, 0f)); up() }
        compose.runOnIdle { assertEquals(setOf("file2"), selected.value); assertEquals(1, taps) }
        compose.onNodeWithText("file3").performTouchInput { down(center); moveBy(Offset(width * 0.3f, 0f)); cancel() }
        compose.runOnIdle { assertEquals(setOf("file2"), selected.value); assertEquals(1, taps) }
    }
    @Test fun deliveredEdgeTouchesCanSelectInsteadOfBeingDiscarded() {
        showRows()
        compose.onNodeWithText("file1").performTouchInput {
            swipe(Offset(1f, centerY), center, durationMillis = 180)
        }
        compose.runOnIdle {
            assertEquals(setOf("file1"), selected.value)
            assertEquals(0, taps)
            assertFalse(swipeActive)
        }
    }
    @Test fun onlyCommittedSwipesProduceOneLightHaptic() {
        showRows()
        compose.onNodeWithText("file1").performTouchInput { click() }
        compose.runOnIdle { assertTrue(feedback.isEmpty()) }
        swipe("file2", true)
        compose.runOnIdle { assertEquals(listOf(HapticFeedbackType.SegmentTick), feedback) }
        compose.onNodeWithText("file3").performTouchInput {
            down(center); moveBy(Offset(width * 0.25f, 0f)); cancel()
        }
        compose.runOnIdle {
            assertEquals(1, feedback.size)
            assertFalse(swipeActive)
            assertEquals(setOf("file1", "file2"), selected.value)
        }
    }
    @Test fun rowFollowsTheFingerAndReturnsAfterRelease() {
        showRows()
        val text = compose.onNodeWithText("file1", useUnmergedTree = true)
        val initialLeft = text.fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("file1").performTouchInput {
            down(center); moveBy(Offset(width * 0.25f, 0f))
        }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(text.fetchSemanticsNode().boundsInRoot.left > initialLeft + 10f)
        compose.runOnIdle { assertTrue(swipeActive); assertTrue(selected.value.isEmpty()) }
        compose.onNodeWithText("file1").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(initialLeft, text.fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.runOnIdle { assertFalse(swipeActive); assertEquals(setOf("file1"), selected.value) }
        compose.mainClock.autoAdvance = true
    }
    @Test fun cancellationReturnsTheRowWithoutSelectionOrHaptic() {
        showRows()
        val text = compose.onNodeWithText("file1", useUnmergedTree = true)
        val initialLeft = text.fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("file1").performTouchInput {
            down(center); moveBy(Offset(width * 0.25f, 0f))
        }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(text.fetchSemanticsNode().boundsInRoot.left > initialLeft + 10f)
        compose.onNodeWithText("file1").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(initialLeft, text.fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.runOnIdle { assertFalse(swipeActive); assertTrue(selected.value.isEmpty()); assertTrue(feedback.isEmpty()) }
        compose.mainClock.autoAdvance = true
    }
    @Test fun disablingARowDuringDragReleasesTheGestureWithoutSelection() {
        showRows()
        compose.onNodeWithText("file1").performTouchInput {
            down(center); moveBy(Offset(width * 0.25f, 0f))
        }
        compose.runOnIdle { assertTrue(swipeActive); rowsEnabled.value = false }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(swipeActive); assertTrue(selected.value.isEmpty()); assertTrue(feedback.isEmpty()) }
        compose.onNodeWithText("file1").performTouchInput { cancel() }
    }
    @Test fun aSecondPointerCancelsTheActiveSwipe() {
        showRows()
        compose.onNodeWithText("file1").performTouchInput {
            down(0, center)
            moveBy(0, Offset(width * 0.25f, 0f))
            down(1, Offset(width * 0.25f, centerY))
            up(1)
            up(0)
        }
        compose.runOnIdle {
            assertFalse(swipeActive)
            assertTrue(selected.value.isEmpty())
            assertTrue(feedback.isEmpty())
            assertEquals(0, taps)
        }
    }

    @Test fun gestureExclusionIsReleasedWhenTheDragIsCancelled() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        showRows()
        compose.runOnIdle { assertTrue(composeView.systemGestureExclusionRects.isEmpty()) }
        compose.onNodeWithText("file1").performTouchInput {
            down(center); moveBy(Offset(width * 0.25f, 0f))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(swipeActive)
            assertTrue(composeView.systemGestureExclusionRects.isNotEmpty())
        }
        compose.onNodeWithText("file1").performTouchInput { cancel() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(swipeActive)
            assertTrue(composeView.systemGestureExclusionRects.isEmpty())
        }
    }

}
