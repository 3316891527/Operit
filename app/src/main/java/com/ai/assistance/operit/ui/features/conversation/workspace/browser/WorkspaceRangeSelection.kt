package com.ai.assistance.operit.ui.features.conversation.workspace.browser

/** 锚点始终按当前可见顺序查找，排序或过滤后不复用旧下标。 */
internal data class WorkspaceRangeSelection(val selected: Set<String>, val anchor: String?)

internal fun selectWorkspaceRange(
    visibleNames: List<String>,
    selected: Set<String>,
    anchor: String?,
    target: String,
): WorkspaceRangeSelection {
    val targetIndex = visibleNames.indexOf(target)
    if (targetIndex < 0) return WorkspaceRangeSelection(selected, anchor)
    val anchorIndex = if (anchor == null) -1 else visibleNames.indexOf(anchor)
    val start = if (anchorIndex < 0) targetIndex else minOf(anchorIndex, targetIndex)
    val end = if (anchorIndex < 0) targetIndex else maxOf(anchorIndex, targetIndex)
    return WorkspaceRangeSelection(selected + visibleNames.subList(start, end + 1), target)
}