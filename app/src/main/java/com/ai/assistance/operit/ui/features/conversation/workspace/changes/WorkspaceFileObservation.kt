package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import kotlinx.serialization.Serializable

@Serializable
internal data class ObservedWorkspaceFile(val stamp: String, val snapshot: ChangeSnapshot)

/** 扫描成功后才计算缺失路径，部分目录读取失败不能生成删除记录。 */
internal fun observedWorkspaceChanges(
    before: Map<String, ObservedWorkspaceFile>, after: Map<String, ObservedWorkspaceFile>,
): List<String> = (before.keys + after.keys).filter { path ->
    val old = before[path]
    val next = after[path]
    old?.snapshot != next?.snapshot || (old != null && next != null && old.stamp.isNotEmpty() &&
        old.stamp != next.stamp && next.snapshot.kind in setOf(SnapshotKind.TOO_LARGE, SnapshotKind.UNAVAILABLE))
}.sorted()

/** .git 保存引用和索引，不属于用户编辑的项目文件。 */
internal fun observedWorkspacePath(path: String): Boolean = path.split('/').none { it == ".git" }

internal fun observedChangeSource(toolNames: Collection<String>, manualTerminal: Boolean): String =
    toolNames.distinct().let { names ->
        when {
            names.size == 1 -> names.single()
            names.isNotEmpty() -> "filesystem"
            manualTerminal -> "terminal"
            else -> "filesystem"
        }
    }
