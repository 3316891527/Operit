package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.format.formatElapsedDuration
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownRenderMode
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming.ToolTimingSummary
import kotlinx.coroutines.delay
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 只有真实计时数据参与显示，工具并行区间沿用现有合并规则。 */
@Composable
internal fun waifuElapsedLabel(summary: ToolTimingSummary): String {
    val streaming = LocalMarkdownRenderMode.current == MarkdownRenderMode.STREAMING
    val ticking = summary.ticking(streaming)
    val now by produceState(SystemClock.elapsedRealtime(), summary, ticking) {
        value = SystemClock.elapsedRealtime()
        while (ticking) {
            delay(1_000L)
            value = SystemClock.elapsedRealtime()
        }
    }
    return summary.elapsedMillis(now, streaming)?.let(::formatElapsedDuration).orEmpty()
}

@Composable
internal fun WaifuProcessHeader(
    thoughtCount: Int,
    toolCount: Int,
    timing: ToolTimingSummary,
    expanded: Boolean,
    textColor: Color,
    onToggle: () -> Unit,
) {
    val action = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    val title = when {
        thoughtCount > 0 -> stringResource(R.string.thinking_process)
        toolCount > 0 -> stringResource(R.string.tools_group_title_with_count, toolCount)
        else -> stringResource(R.string.tool_result_block)
    }
    val count = if (toolCount > 0 && thoughtCount > 0) stringResource(R.string.tools_group_title_with_count, toolCount) else ""
    val elapsed = waifuElapsedLabel(timing)
    Row(
        Modifier.fillMaxWidth().semantics { stateDescription = action }
            .clickable(role = Role.Button, onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = action,
            tint = textColor.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp).rotate(if (expanded) 90f else 0f),
        )
        Text(
            text = listOf(title, count).filter(String::isNotEmpty).joinToString(" · "),
            color = textColor.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (elapsed.isNotEmpty()) Text(elapsed, color = textColor.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
    }
}

/** 每个懒加载子条目绘制同一列层级线，折叠时不组合整组内容。 */
internal fun Modifier.waifuProcessRail(ink: Color): Modifier = drawBehind {
    drawLine(ink.copy(alpha = 0.12f), Offset.Zero, Offset(0f, size.height), strokeWidth = 1.dp.toPx())
}.padding(start = 12.dp, bottom = 6.dp)
