package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 头像单独测量和放置，行高由右侧内容决定，短思考与懒加载续行不会留下头像高度的空白。 */
@Composable
internal fun WaifuAvatarLayout(
    avatarSize: Dp,
    showAvatar: Boolean,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(avatarSize + 8.dp))
                Box(Modifier.weight(1f)) { content() }
            }
            if (showAvatar) Box { avatar() }
        },
    ) { measurables, constraints ->
        val body = measurables.first().measure(constraints.copy(minHeight = 0))
        val avatar = measurables.getOrNull(1)?.measure(Constraints.fixed(avatarSize.roundToPx(), avatarSize.roundToPx()))
        layout(body.width, body.height) {
            body.placeRelative(0, 0)
            avatar?.placeRelative(0, 2.dp.roundToPx())
        }
    }
}