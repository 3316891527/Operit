package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.ai.assistance.operit.util.markdown.toCharStream

/** 过程和独立内容复用原消息节点与卡片状态，外层头像列统一负责对齐。 */
@Composable
internal fun WaifuUnframedMessage(
    message: ChatMessage,
    textColor: Color,
    enableDialogs: Boolean,
    modifier: Modifier = Modifier,
    processMode: Boolean = true,
) {
    val theme = LocalThemePreferenceSnapshot.current
    val slice = LocalChatMessageSlice.current
    val rendererState = slice?.state ?: remember(message.timestamp) { StreamMarkdownRendererState() }
    val xmlRenderer = remember(theme.showThinkingProcess, theme.showStatusTags, enableDialogs, processMode) {
        CustomXmlRenderer(
            showThinkingProcess = theme.showThinkingProcess,
            showStatusTags = theme.showStatusTags,
            enableDialogs = enableDialogs,
            waifuProcessMode = processMode,
        )
    }
    val grouper = slice?.grouper ?: remember(theme.showThinkingProcess, processMode) {
        ThinkToolsXmlNodeGrouper(showThinkingProcess = theme.showThinkingProcess, groupProcessNodes = !processMode)
    }
    grouper.executionTimings = rememberMessageToolExecutions(message)
    ProvideAiMarkdownTextLayoutSettings {
        key(message.timestamp) {
            val stream = if (LocalMarkdownRenderFragment.current != null) null else rememberRevisableTextStream(message.contentStream)
            val contentModifier = modifier.fillMaxWidth()
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
