package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
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

internal data class WaifuMessageLayout(val joinedToPrevious: Boolean)
internal val LocalWaifuMessageLayout = staticCompositionLocalOf<WaifuMessageLayout?> { null }

/** 两种聊天样式共用头像和角色标题，连续过程与紧接正文只占一列头像。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WaifuReplyAvatarRow(
    enabled: Boolean,
    message: ChatMessage,
    joinedToPrevious: Boolean = false,
    onAvatarLongPress: ((String) -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val slice = LocalChatMessageSlice.current
    val showHeader = slice?.first != false && !joinedToPrevious
    val theme = LocalThemePreferenceSnapshot.current
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val characterCards = remember(context) { CharacterCardManager.getInstance(context) }
    val avatarUri by produceState(theme.customAiAvatarUri, message.roleName, theme.customAiAvatarUri, slice?.appearance, showHeader) {
        if (!showHeader) return@produceState
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
    WaifuAvatarLayout(
        avatarSize = avatarSize,
        showAvatar = showHeader,
        avatar = {
            val avatarModifier = Modifier.size(avatarSize)
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
        },
    ) {
        Column {
            if (showHeader) {
                val title = buildString {
                    if (theme.showRoleName && message.roleName.isNotBlank()) append(message.roleName)
                    if (theme.showModelName && message.modelName.isNotBlank()) {
                        if (isNotEmpty()) append(" | ")
                        append(message.modelName)
                    }
                    if (theme.showModelProvider && message.provider.isNotBlank()) {
                        if (isNotEmpty()) append(if (theme.showModelName && message.modelName.isNotBlank()) " by " else " | ")
                        append(message.provider)
                    }
                }
                if (title.isNotEmpty()) Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            CompositionLocalProvider(LocalWaifuMessageLayout provides WaifuMessageLayout(joinedToPrevious)) { content() }
        }
    }
}
