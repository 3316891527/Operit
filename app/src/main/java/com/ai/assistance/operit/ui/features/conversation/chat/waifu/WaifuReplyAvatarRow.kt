package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.LocalChatMessageSlice
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.script.themeScriptAvatarSize
import com.ai.assistance.operit.ui.theme.script.themeScriptDecoration
import com.ai.assistance.operit.ui.theme.script.themeScriptShape
import kotlinx.coroutines.flow.flowOf

/** 命令框式正文也按消息展示头像，懒加载的后续片段沿用同一缩进。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WaifuReplyAvatarRow(
    enabled: Boolean,
    message: ChatMessage,
    onAvatarLongPress: ((String) -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val slice = LocalChatMessageSlice.current
    val theme = LocalThemePreferenceSnapshot.current
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val characterCards = remember(context) { CharacterCardManager.getInstance(context) }
    val avatarUri by produceState(theme.customAiAvatarUri, message.roleName, theme.customAiAvatarUri, slice?.appearance, slice?.first) {
        if (slice?.first == false) return@produceState
        val appearance = slice?.appearance
        val cacheKey = message.roleName to theme.customAiAvatarUri
        val cachedFlow = appearance?.avatarFlows?.get(cacheKey)
        val avatarFlow = if (cachedFlow != null) {
            cachedFlow
        } else {
            val card = characterCards.findCharacterCardByName(message.roleName)
            val flow = if (card != null) preferences.getAiAvatarForCharacterCardFlow(card.id) else flowOf(theme.customAiAvatarUri)
            appearance?.avatarFlows?.put(cacheKey, flow)
            flow
        }
        avatarFlow.collect { value = it }
    }
    val avatarShape = remember(theme.avatarShape, theme.avatarCornerRadius) {
        if (theme.avatarShape == UserPreferencesManager.AVATAR_SHAPE_SQUARE) RoundedCornerShape(theme.avatarCornerRadius.dp) else CircleShape
    }
    val avatarSize = themeScriptAvatarSize("assistantAvatar", 32.dp)
    Row(Modifier.fillMaxWidth()) {
        if (slice?.first != false) {
            val avatarModifier = Modifier.padding(top = 2.dp).size(avatarSize)
                .clip(themeScriptShape("assistantAvatar", avatarShape))
                .themeScriptDecoration("assistantAvatar", originalShape = avatarShape)
                .combinedClickable(onClick = {}, onLongClick = { onAvatarLongPress?.invoke(message.roleName) })
            if (!avatarUri.isNullOrEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(avatarUri)),
                    contentDescription = "AI Avatar",
                    modifier = avatarModifier,
                )
            } else {
                Icon(Icons.Default.Assistant, contentDescription = "AI Avatar", tint = Color.Unspecified, modifier = avatarModifier)
            }
        } else {
            Spacer(Modifier.size(avatarSize))
        }
        Box(Modifier.weight(1f)) { content() }
    }
}