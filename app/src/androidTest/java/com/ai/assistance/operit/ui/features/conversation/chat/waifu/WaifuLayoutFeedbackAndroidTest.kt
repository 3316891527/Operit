package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.ChatTimelineEntry
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.LocalChatMessageSlice
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.rememberChatTimelineEntries
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.waifuTimelineJoinsPrevious
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaifuLayoutFeedbackAndroidTest {
    @get:Rule val compose = createComposeRule()

    private val role = "布局回归角色"
    private val rendered = AtomicReference<List<ChatTimelineEntry>>(emptyList())
    private fun reply(content: String) = ChatMessage(
        sender = "ai", content = content, roleName = role, sentAt = 100L,
        provider = "provider", modelName = "model",
    )

    @Test
    fun segmentedThinkingHasTheOnlyRoleTitleAboveItAndReplyKeepsItsAvatar() {
        val messages = listOf(reply("<think>原始思考</think>"), reply("正文。"))
        val showThinking = mutableStateOf(true)
        compose.setContent { Timeline(messages, showThinking = showThinking.value) }
        awaitRows(2)

        val entries = rendered.get()
        assertTrue(entries[0].waifuProcess)
        assertTrue(requireNotNull(entries[0].slice).waifuHeaderStart)
        assertFalse(entries[1].waifuProcess)
        assertTrue(waifuTimelineJoinsPrevious(messages, entries[0], entries[1], true))
        assertAvatars(0, 0)
        assertAvatars(1, 1)
        compose.onAllNodes(hasText(role), useUnmergedTree = true).assertCountEquals(1)
        val title = compose.onNode(hasText(role), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val thought = compose.onNodeWithTag("body-0").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("body-1").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= thought.top)
        assertEquals(thought.bottom, body.top, 0.1f)
        assertEquals(thought.left, body.left, 0.1f)

        compose.runOnIdle { showThinking.value = false }
        compose.waitUntil(20_000) { rendered.get().singleOrNull()?.messageIndex == 1 }
        compose.onAllNodes(hasText(role), useUnmergedTree = true).assertCountEquals(1)
        assertAvatars(0, 1)
    }

    @Test
    fun hiddenRoleTitleKeepsThinkingAndReplyAdjacent() {
        val messages = listOf(reply("<think>思考</think>"), reply("正文。"))
        compose.setContent { Timeline(messages, showRoleName = false) }
        awaitRows(2)

        compose.onAllNodes(hasText(role), useUnmergedTree = true).assertCountEquals(0)
        val thought = compose.onNodeWithTag("body-0").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("body-1").fetchSemanticsNode().boundsInRoot
        assertEquals(thought.bottom, body.top, 0.1f)
        assertAvatars(1, 1)
    }

    @Test
    fun enablingWaifuClassifiesHistoricalMixedProcessAndStandaloneBlocksWithoutChangingTheMessage() {
        val enabled = mutableStateOf(false)
        val message = reply("<think>旧思考</think>前文。![图片](file:///layout-fixture.png)后文。")
        val original = message.content
        compose.setContent { Timeline(listOf(message), waifuEnabled = enabled.value) }
        compose.waitUntil(20_000) { rendered.get().isNotEmpty() }
        assertTrue(rendered.get().none { it.waifuProcess })

        compose.runOnIdle { enabled.value = true }
        awaitRows(4)
        val slices = rendered.get().map { requireNotNull(it.slice) }
        assertEquals(listOf(true, false, false, false), rendered.get().map { it.waifuProcess })
        assertEquals(listOf(false, false, true, false), slices.map { it.standaloneContent })
        assertTrue(slices[1].shellFirst)
        assertTrue(slices[3].waifuAvatarAfterStandalone)
        compose.onAllNodes(hasText(role), useUnmergedTree = true).assertCountEquals(1)
        val titleMatcher = hasText(role) and hasAnyAncestor(hasTestTag("row-0"))
        compose.onAllNodes(titleMatcher, useUnmergedTree = true).assertCountEquals(1)
        assertEquals(original, message.content)

        compose.runOnIdle { enabled.value = false }
        compose.waitUntil(20_000) { rendered.get().none { it.waifuProcess || it.slice?.standaloneContent == true } }
        assertEquals(original, message.content)
    }

    @Test
    fun processInTheMiddleOfShortHistoricalTextHasItsOwnUnframedRow() {
        val message = reply("前文。<think>穿插的思考</think>后文。")
        compose.setContent { Timeline(listOf(message)) }
        awaitRows(3)
        val entries = rendered.get()
        assertEquals(listOf(false, true, false), entries.map { it.waifuProcess })
        assertTrue(requireNotNull(entries.first().slice).shellLast)
        assertTrue(requireNotNull(entries.last().slice).shellFirst)
        compose.onAllNodes(hasText(role) and hasAnyAncestor(hasTestTag("row-0")), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun streamedMixedProcessKeepsItsHeaderWhenTheReplyArrives() {
        val source = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
        val message = reply("").copy(contentStream = source)
        compose.setContent { Timeline(listOf(message)) }
        try {
            compose.waitUntil(20_000) { source.subscriptionCount > 0 }
            runBlocking { source.emit("<think>流式思考</think>\n") }
            awaitRows(1)
            assertTrue(rendered.get()[0].waifuProcess)

            runBlocking { source.emit("正文。\n") }
            awaitRows(2)
            assertTrue(requireNotNull(rendered.get()[0].slice).waifuHeaderStart)
            assertFalse(requireNotNull(rendered.get()[1].slice).waifuHeaderStart)
            compose.onAllNodes(hasText(role), useUnmergedTree = true).assertCountEquals(1)
            assertAvatars(1, 1)
        } finally {
            source.close()
        }
    }

    @Composable
    private fun Timeline(
        messages: List<ChatMessage>,
        showRoleName: Boolean = true,
        waifuEnabled: Boolean = true,
        showThinking: Boolean = true,
    ) {
        val theme = ThemePreferenceSnapshot(
            source = "test",
            values = ThemePreferenceValues.defaultVisual().withBoolean("show_role_name", showRoleName),
        )
        MaterialTheme {
            CompositionLocalProvider(LocalThemePreferenceSnapshot provides theme) {
                val entries = rememberChatTimelineEntries(
                    messages, "waifu-layout-feedback", showThinkingProcess = showThinking, showStatusTags = true,
                    toolCollapseMode = ToolCollapseMode.ALL, waifuEnabled = waifuEnabled,
                )
                SideEffect { rendered.set(entries) }
                Column {
                    entries.forEachIndexed { index, entry ->
                        val message = messages[entry.messageIndex]
                        val joined = waifuTimelineJoinsPrevious(messages, entries.getOrNull(index - 1), entry, waifuEnabled)
                        key(entry.key) {
                            Box(Modifier.testTag("row-$index")) {
                                CompositionLocalProvider(LocalChatMessageSlice provides entry.slice) {
                                    WaifuReplyAvatarRow(
                                        enabled = waifuEnabled, message = message,
                                        processOnly = entry.waifuProcess, joinedToPrevious = joined,
                                        onAvatarLongPress = null,
                                    ) {
                                        // 分块与标题走生产入口，正文使用固定高度方便检查相邻布局。
                                        Box(Modifier.fillMaxWidth().height(18.dp).testTag("body-$index"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun awaitRows(count: Int) {
        compose.waitUntil(20_000) { rendered.get().size == count }
    }

    private fun assertAvatars(row: Int, count: Int) {
        val matcher = hasContentDescription("AI Avatar") and hasAnyAncestor(hasTestTag("row-$row"))
        compose.onAllNodes(matcher, useUnmergedTree = true).assertCountEquals(count)
    }
}
