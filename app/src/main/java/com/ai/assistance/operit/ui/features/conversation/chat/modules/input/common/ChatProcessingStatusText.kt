package com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.ui.theme.script.text.ThemeText

/** 状态提示不随批量工具名或多行进度文本撑高输入栏，完整内容仍保留在文字语义中。 */
@Composable
internal fun ChatProcessingStatusText(message: String, modifier: Modifier = Modifier) {
    ThemeText(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
