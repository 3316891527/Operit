package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRendererState
import com.ai.assistance.operit.ui.common.markdown.lazy.LocalMarkdownRenderFragment
import com.ai.assistance.operit.ui.common.markdown.rememberDisplayMessageSections
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.stream.rememberRevisableTextStream
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.CustomXmlRenderer
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.ThinkToolsXmlNodeGrouper
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.rememberMessageToolExecutions
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.LocalChatMessageSlice
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.ProvideAiMarkdownTextLayoutSettings
import com.ai.assistance.operit.ui.theme.script.themeScriptAvatarSize
import com.ai.assistance.operit.util.markdown.toCharStream

/** 过程直接复用消息节点与卡片状态，不进入任何聊天样式的外壳。 */
@Composable
internal fun WaifuProcessMessage(
    message: ChatMessage,
    textColor: Color,
    enableDialogs: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalThemePreferenceSnapshot.current
    val slice = LocalChatMessageSlice.current
    val rendererState = slice?.state ?: remember(message.timestamp) { StreamMarkdownRendererState() }
    val xmlRenderer = remember(theme.showThinkingProcess, theme.showStatusTags, enableDialogs) {
        CustomXmlRenderer(
            showThinkingProcess = theme.showThinkingProcess,
            showStatusTags = theme.showStatusTags,
            enableDialogs = enableDialogs,
            waifuProcessMode = true,
        )
    }
    val grouper = slice?.grouper ?: remember(theme.showThinkingProcess) {
        ThinkToolsXmlNodeGrouper(showThinkingProcess = theme.showThinkingProcess, groupProcessNodes = false)
    }
    grouper.executionTimings = rememberMessageToolExecutions(message)
    ProvideAiMarkdownTextLayoutSettings {
        key(message.timestamp) {
            val stream = if (LocalMarkdownRenderFragment.current != null) null else rememberRevisableTextStream(message.contentStream)
            // 与正文头像列共享宽度，再给过程内容保留独立缩进。
            val contentModifier = modifier.fillMaxWidth().padding(
                start = themeScriptAvatarSize("assistantAvatar", 32.dp) + 12.dp, end = 16.dp,
            )
            if (stream != null) {
                val charStream = remember(stream) { stream.toCharStream() }
                StreamMarkdownRenderer(
                    markdownStream = charStream,
                    textColor = textColor,
                    backgroundColor = Color.Transparent,
                    xmlRenderer = xmlRenderer,
                    nodeGrouper = grouper,
                    enableDialogs = enableDialogs,
                    modifier = contentModifier,
                    state = rendererState,
                )
            } else {
                StreamMarkdownRenderer(
                    sections = rememberDisplayMessageSections(message),
                    textColor = textColor,
                    backgroundColor = Color.Transparent,
                    xmlRenderer = xmlRenderer,
                    nodeGrouper = grouper,
                    enableDialogs = enableDialogs,
                    modifier = contentModifier,
                    state = rendererState,
                )
            }
        }
    }
}
