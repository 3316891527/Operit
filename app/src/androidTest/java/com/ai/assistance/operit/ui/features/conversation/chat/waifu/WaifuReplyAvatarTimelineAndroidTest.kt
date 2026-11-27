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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.ChatMessageSlice
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.LocalChatMessageSlice
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.rememberChatTimelineEntries
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaifuReplyAvatarTimelineAndroidTest {
    @get:Rule val compose = createComposeRule()

    private val image = "![表情](file:///waifu-avatar-fixture.png)"
    private val role = "头像回归角色"
    private val slices = AtomicReference<List<ChatMessageSlice>>(emptyList())
    private val theme = ThemePreferenceSnapshot(source = "test", values = ThemePreferenceValues.defaultVisual())

    @Test
    fun imageAndFollowingTextEachHaveAnAvatarWithoutRepeatingTheTitle() {
        val message = ChatMessage(sender = "ai", content = image + "正文。", roleName = role)
        compose.setContent { Timeline(message) }

        awaitRows(2)
        assertTrue(slices.get()[0].standaloneContent)
        assertFalse(slices.get()[1].standaloneContent)
        assertTrue(slices.get()[1].shellFirst)
        assertAvatarCount(0, 1)
        assertAvatarCount(1, 1)
        compose.onAllNodes(hasText(role), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun lineBreakAndLongTextContinuationDoNotReceiveTheReplyAvatar() {
        val text = "正文".repeat(2_100)
        val message = ChatMessage(sender = "ai", content = image + "<br>" + text, roleName = role)
        compose.setContent { Timeline(message) }

        awaitRows(4)
        assertTrue(slices.get()[0].standaloneContent)
        assertAvatarCount(0, 1)
        assertAvatarCount(1, 0)
        assertAvatarCount(2, 1)
        assertAvatarCount(3, 0)
        compose.onAllNodes(hasText(role), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun appendedStreamingReplyGetsItsOwnAvatarBeforeTheSourceCloses() {
        val source = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
        val message = ChatMessage(sender = "ai", contentStream = source, roleName = role)
        compose.setContent { Timeline(message) }
        try {
            compose.waitUntil(20_000) { source.subscriptionCount > 0 }
            runBlocking { source.emit(image + "\n") }
            awaitRows(1)
            assertTrue(slices.get()[0].standaloneContent)
            assertAvatarCount(0, 1)

            runBlocking { source.emit("后续正文。\n") }
            awaitRows(2)
            assertAvatarCount(0, 1)
            assertAvatarCount(1, 1)
            assertFalse(slices.get()[1].state.streamParsingCompletedSuccessfully)
        } finally {
            source.close()
        }
    }

    @Composable
    private fun Timeline(message: ChatMessage) {
        MaterialTheme {
            CompositionLocalProvider(LocalThemePreferenceSnapshot provides theme) {
                val entries = rememberChatTimelineEntries(
                    messages = listOf(message),
                    chatId = "waifu-avatar-regression",
                    showThinkingProcess = true,
                    showStatusTags = true,
                    toolCollapseMode = ToolCollapseMode.ALL,
                    waifuEnabled = true,
                )
                val currentSlices = entries.map { requireNotNull(it.slice) }
                SideEffect { slices.set(currentSlices) }
                Column {
                    entries.forEachIndexed { index, entry ->
                        key(entry.key) {
                            Box(Modifier.testTag("slice-$index")) {
                                CompositionLocalProvider(LocalChatMessageSlice provides currentSlices[index]) {
                                    WaifuReplyAvatarRow(enabled = true, message = message, onAvatarLongPress = null) {
                                        // 只检查真实分块和头像归属，不解码图片或测量图片尺寸。
                                        Box(Modifier.fillMaxWidth().height(44.dp))
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
        compose.waitUntil(20_000) { slices.get().size == count }
    }

    private fun assertAvatarCount(row: Int, count: Int) {
        val matcher = hasContentDescription("AI Avatar") and hasAnyAncestor(hasTestTag("slice-$row"))
        compose.onAllNodes(matcher, useUnmergedTree = true).assertCountEquals(count)
    }
}
