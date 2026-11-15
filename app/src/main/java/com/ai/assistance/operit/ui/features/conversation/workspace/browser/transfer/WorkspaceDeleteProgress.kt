package com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.workspaceEnvironment
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 扫描阶段总数未知；删除阶段统计实际文件和子目录，不以外层选中数代替。 */
internal data class WorkspaceDeleteProgress(
    val target: FileBrowserClipboardItem,
    val completedItems: Int,
    val totalItems: Int,
    val scanning: Boolean = false,
) {
    val fraction: Float?
        get() = if (scanning) null else if (totalItems == 0) 1f else completedItems.toFloat() / totalItems
}

/** 先构建不跟随链接的后序清单，再逐项删除；顶层目标完整删除后才通知选择状态。 */
internal class WorkspaceDeleteOperation(
    private val describe: suspend (FileBrowserClipboardItem) -> FileBrowserClipboardItem,
    private val children: suspend (FileBrowserClipboardItem) -> List<FileBrowserClipboardItem>,
    private val deleteItem: suspend (FileBrowserClipboardItem) -> Unit,
) {
    private data class Pending(val target: FileBrowserClipboardItem, val expanded: Boolean, val root: FileBrowserClipboardItem?)
    private data class Planned(val target: FileBrowserClipboardItem, val root: FileBrowserClipboardItem?)
    private val state = MutableStateFlow<WorkspaceDeleteProgress?>(null)
    val progress = state.asStateFlow()

    suspend fun run(targets: List<FileBrowserClipboardItem>, onDeleted: suspend (FileBrowserClipboardItem) -> Unit = {}) {
        if (targets.isEmpty()) return
        val execution = currentCoroutineContext()
        val unique = targets.distinctBy { workspaceEnvironment(it.environment) to it.path.trimEnd('/') }
        try {
            execution.ensureActive()
            state.value = WorkspaceDeleteProgress(unique.first(), 0, 0, scanning = true)
            val described = unique.map { original ->
                execution.ensureActive()
                original to describe(original)
            }
            val roots = described.filter { (_, target) -> described.none { (_, parent) ->
                parent !== target && parent.isDirectory && workspaceEnvironment(parent.environment) == workspaceEnvironment(target.environment) &&
                    target.path.startsWith(parent.path.trimEnd('/') + "/")
            } }
            val plan = mutableListOf<Planned>()
            val seen = mutableSetOf<Pair<String, String>>()
            var discovered = 0
            var lastReport = 0L
            for ((root, target) in roots) {
                execution.ensureActive()
                val stack = ArrayDeque<Pending>()
                stack.add(Pending(target, false, root))
                while (stack.isNotEmpty()) {
                    execution.ensureActive()
                    val node = stack.removeLast()
                    if (node.expanded) {
                        plan += Planned(node.target, node.root)
                        continue
                    }
                    val key = workspaceEnvironment(node.target.environment) to node.target.path.trimEnd('/')
                    check(seen.add(key)) { "Duplicate deletion target" }
                    discovered++
                    val now = System.nanoTime()
                    if (now - lastReport > 150_000_000L) {
                        state.value = WorkspaceDeleteProgress(node.target, 0, discovered, scanning = true)
                        lastReport = now
                    }
                    if (node.target.isDirectory) {
                        stack.add(node.copy(expanded = true))
                        for (child in children(node.target).asReversed()) {
                            require(child.path.substringAfterLast('/') !in setOf("", ".", "..") &&
                                workspaceEnvironment(child.environment) == key.first &&
                                child.path.substringBeforeLast('/', "") == node.target.path.trimEnd('/')) {
                                "Deletion child is outside its parent"
                            }
                            stack.add(Pending(child, false, null))
                        }
                    } else plan += Planned(node.target, node.root)
                }
            }
            for ((index, node) in plan.withIndex()) {
                execution.ensureActive()
                val current = WorkspaceDeleteProgress(node.target, index, plan.size)
                state.value = current
                deleteItem(node.target)
                state.value = current.copy(completedItems = index + 1)
                // 部分删除不能把整个选中目录标记为成功，取消也不会撤销已完成的删除。
                node.root?.let { onDeleted(it) }
                execution.ensureActive()
            }
        } finally { state.value = null }
    }
}
