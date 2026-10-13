package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel

/** 两处入口共用原有上下文统计，避免顶栏迁移后丢失用量详情。 */
@Composable
fun ChatContextUsageIndicator(actualViewModel: ChatViewModel) {
    val currentWindowSize by actualViewModel.currentWindowSize.collectAsState()
    val maxWindowSizeInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val inputTokenCount by actualViewModel.inputTokenCount.collectAsState()
    val outputTokenCount by actualViewModel.outputTokenCount.collectAsState()
    // 统计信息
    val maxWindowSize = (maxWindowSizeInK * 1024).toLong().coerceAtLeast(0L)
    val totalTokenCount = inputTokenCount + outputTokenCount
    val contextUsagePercentage =
            if (maxWindowSize > 0) {
                ((currentWindowSize.toDouble() / maxWindowSize.toDouble()) * 100.0)
                    .coerceAtMost(999.0)
                    .toFloat()
            } else {
                0f
            }

    // 使用一个状态来跟踪是否显示详细信息
    val (showDetailedStats, setShowDetailedStats) = remember { mutableStateOf(false) }

    Box {
        // 主要显示（圆环进度）
        val progress = (contextUsagePercentage / 100f).coerceIn(0f, 1f)
        val animatedProgress by animateFloatAsState(targetValue = progress, label = "TokenProgressAnimation")
        val progressColor = when {
            contextUsagePercentage > 90 -> MaterialTheme.colorScheme.error
            contextUsagePercentage > 75 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

        Box(
            modifier = Modifier
                .clickable { setShowDetailedStats(!showDetailedStats) }
                .size(32.dp)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.fillMaxSize(),
                color = progressColor,
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                text = "${contextUsagePercentage.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
        }

        // 简化的下拉框
        DropdownMenu(
                expanded = showDetailedStats,
                onDismissRequest = { setShowDetailedStats(false) },
                modifier =
                        Modifier.width(IntrinsicSize.Min)
                                .background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.context_window, currentWindowSize)) },
                onClick = {},
                enabled = false
            )

            DropdownMenuItem(
                    text = { Text(stringResource(R.string.input_tokens, inputTokenCount)) },
                    onClick = {},
                    enabled = false
            )
            DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.output_tokens, outputTokenCount))
                    },
                    onClick = {},
                    enabled = false
            )
            DropdownMenuItem(
                    text = {
                        Text(
                                stringResource(R.string.total_tokens, totalTokenCount),
                                style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight =
                                                        androidx.compose.ui.text.font
                                                                .FontWeight.Bold
                                        ),
                                color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {},
                    enabled = false
            )

        }
    }
}
