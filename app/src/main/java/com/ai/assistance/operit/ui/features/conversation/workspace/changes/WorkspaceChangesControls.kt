// SPDX-License-Identifier: AGPL-3.0-only
package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 保留紧凑的工具栏，同时为触屏、无障碍和鼠标提供名称与可用状态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangeIconButton(
    image: ImageVector, label: String, onClick: () -> Unit,
    selected: Boolean = false, enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(5.dp)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState(),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp) {
            IconButton(
                onClick = onClick, enabled = enabled,
                modifier = Modifier.size(28.dp).padding(2.dp)
                    .background(if (selected) colors.primary.copy(alpha = 0.13f) else Color.Transparent, shape)
                    .border(1.dp, if (selected) colors.primary.copy(alpha = 0.45f) else Color.Transparent, shape),
            ) {
                Icon(image, contentDescription = label, modifier = Modifier.size(14.dp),
                    tint = if (!enabled) colors.onSurface.copy(alpha = 0.38f) else if (selected) colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun changeAdditionColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF3FB950) else Color(0xFF1A7F37)

@Composable
internal fun changeDeletionColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFFF7B72) else Color(0xFFCF222E)

@Composable
internal fun ChangeDiffStats(additions: Int, deletions: Int, modifier: Modifier = Modifier, known: Boolean = true) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (known) "+$additions" else "+…", color = changeAdditionColor(), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal)
        Text(if (known) "−$deletions" else "−…", color = changeDeletionColor(), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal)
    }
}

@Composable
internal fun ChangeStatus(status: String) {
    val color = when {
        'D' in status -> changeDeletionColor()
        status.any { it == 'A' || it == '?' } -> changeAdditionColor()
        'R' in status || 'C' in status -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Text(status.trim(), Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
        color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, maxLines = 1)
}
