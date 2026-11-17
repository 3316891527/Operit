package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class WorkspaceDirectorySnapshot(
    val location: WorkspaceLocation,
    val entries: List<WorkspaceSearchEntry> = emptyList(),
    val readable: Boolean = false,
)

/** 目标读取成功才提交导航；权限错误、取消和过期请求都不能替换当前可读目录。 */
internal class WorkspaceDirectoryNavigation(
    initial: WorkspaceLocation,
    private val children: suspend (WorkspaceLocation) -> List<WorkspaceSearchEntry>,
) {
    private val revision = AtomicLong()
    private val state = MutableStateFlow(WorkspaceDirectorySnapshot(initial))
    val snapshot = state.asStateFlow()

    suspend fun open(location: WorkspaceLocation): Boolean {
        val request = revision.incrementAndGet()
        val entries = children(location)
        currentCoroutineContext().ensureActive()
        if (revision.get() != request) return false
        state.value = WorkspaceDirectorySnapshot(location, entries, readable = true)
        return true
    }
}
