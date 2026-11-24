package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaifuAvatarLayoutAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenTitleDoesNotLeaveAnAvatarSizedRowBeforeTheReply() {
        val titleVisible = mutableStateOf(true)
        compose.setContent {
            WaifuAvatarLayout(
                avatarSize = 32.dp, showAvatar = true,
                avatar = { Box(Modifier.size(32.dp).testTag("avatar")) }, modifier = Modifier.testTag("row"),
            ) {
                Column {
                    if (titleVisible.value) Box(Modifier.height(14.dp).fillMaxWidth())
                    Box(Modifier.height(18.dp).fillMaxWidth().testTag("thought"))
                }
            }
        }
        compose.runOnIdle { titleVisible.value = false }
        val row = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        val thought = compose.onNodeWithTag("thought").fetchSemanticsNode().boundsInRoot
        val avatar = compose.onNodeWithTag("avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(thought.height, row.height, 0.1f)
        assertTrue(avatar.height > row.height)
        assertEquals(row.top, thought.top, 0.1f)
    }

    @Test fun continuationRowsKeepOnlyTheAvatarColumnWidth() {
        compose.setContent {
            WaifuAvatarLayout(
                avatarSize = 48.dp, showAvatar = false,
                avatar = { Box(Modifier.size(48.dp).testTag("avatar")) }, modifier = Modifier.testTag("row"),
            ) { Box(Modifier.height(12.dp).fillMaxWidth().testTag("body")) }
        }
        compose.onNodeWithTag("avatar").assertDoesNotExist()
        val row = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
        assertEquals(body.height, row.height, 0.1f)
        assertTrue(body.left > row.left)
    }
}