package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

/** 同一工具标题行的空间分配：计时保留位置，参数预览使用剩余空间。 */
internal data class ToolSummaryWidths(
    val title: Int,
    val status: Int,
    val summary: Int,
    val statusGap: Int,
    val summaryGap: Int,
)

internal fun toolSummaryWidths(
    available: Int,
    preferredTitle: Int,
    preferredStatus: Int,
    minimumTitle: Int,
    gap: Int,
): ToolSummaryWidths {
    val width = available.coerceAtLeast(0)
    val spacing = gap.coerceAtLeast(0)
    val titleMinimum = minimumTitle.coerceIn(0, width / 3)
    val status = preferredStatus.coerceAtLeast(0)
        .coerceAtMost((width - titleMinimum - spacing).coerceAtLeast(0))
    val statusGap = if (status > 0) spacing.coerceAtMost(width - status) else 0
    val title = preferredTitle.coerceAtLeast(0).coerceAtMost(width - status - statusGap)
    val remaining = width - title - status - statusGap
    val summaryGap = if (remaining > spacing) spacing else 0
    return ToolSummaryWidths(title, status, remaining - summaryGap, statusGap, summaryGap)
}
