package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import kotlinx.coroutines.CompletableDeferred

/** 仅同一物理路径或祖先目录互斥，无关文件不等待下载、复制或解压结束。 */
internal class WorkspaceFileMutationLocks {
    private val monitor = Any()
    private data class Mutation(val locations: List<ChangeLocation>, val identities: List<ChangeLocation>)
    private val active = linkedMapOf<Any, Mutation>()
    private var changed = CompletableDeferred<Unit>()

    fun activeLocations(): List<ChangeLocation> = synchronized(monitor) { active.values.flatMap { it.locations } }

    suspend fun acquire(paths: List<ChangeLocation>, identities: List<ChangeLocation> = paths): java.io.Closeable {
        val token = Any()
        while (true) {
            val wait = synchronized(monitor) {
                if (active.values.flatMap { it.identities }.any { busy -> identities.any { overlaps(busy, it) } }) changed
                else { active[token] = Mutation(paths, identities); null }
            }
            if (wait == null) break
            wait.await()
        }
        return java.io.Closeable {
            synchronized(monitor) {
                if (active.remove(token) != null) {
                    val previous = changed
                    changed = CompletableDeferred()
                    previous.complete(Unit)
                }
            }
        }
    }

    suspend fun <T> withPaths(paths: List<ChangeLocation>, block: suspend () -> T): T =
        acquire(paths).use { block() }

    private fun overlaps(left: ChangeLocation, right: ChangeLocation): Boolean =
        left.backend == right.backend &&
            (changePathContains(left.path, right.path) || changePathContains(right.path, left.path))
}

internal val workspaceFileMutations = WorkspaceFileMutationLocks()

/** 父目录写入覆盖整个观察根，挂载别名按同一物理路径匹配。 */
internal fun mutationObservationPaths(root: ChangeLocation, locations: List<ChangeLocation>): List<String> =
    locations.mapNotNull { location ->
        relativeChangePath(root, location) ?: if (relativeChangePath(location, root) != null) "" else null
    }.distinct()

/** 正在进行的写入不可发布中间内容；完成后由精确快照或重新入队的事件推进基线。 */
internal fun TerminalObservationIndex.Scan.excludeActiveWrites(root: ChangeLocation) {
    exclude(mutationObservationPaths(root, workspaceFileMutations.activeLocations()))
}
