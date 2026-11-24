package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping
import kotlinx.serialization.Serializable

/** 路径归属与对话归属分开保存，重新绑定工作区不改写历史。 */
@Serializable
internal data class ChangeLocation(val path: String, val environment: String, val backend: String) {
    val key: String get() = "$backend\u0000$path"
}

/** 对照本地终端的存储挂载识别同一文件，保留记录原本的读写路径。 */
internal fun ChangeLocation.historyLocation(): ChangeLocation = historyLocation(PRootMountMapping.currentEmulatedStoragePath())

internal fun ChangeLocation.historyLocation(storage: String): ChangeLocation {
    if (backend != "android" && backend != "linux:local") return this
    val aliases = if (backend == "android") listOf("/sdcard", storage, "/storage/self/primary") else listOf("/sdcard", storage)
    val root = aliases.firstOrNull { path == it || path.startsWith("$it/") } ?: return this
    return ChangeLocation("/sdcard" + path.removePrefix(root), "android", "android")
}

/** 作用域比较也使用挂载身份，工作区与终端可使用不同的共享存储路径。 */
internal fun relativeChangePath(directory: ChangeLocation, file: ChangeLocation): String? =
    relativeChangePath(directory, file, PRootMountMapping.currentEmulatedStoragePath())

internal fun relativeChangePath(directory: ChangeLocation, file: ChangeLocation, storage: String): String? {
    val root = directory.historyLocation(storage)
    val target = file.historyLocation(storage)
    if (root.backend == target.backend && changePathContains(root.path, target.path)) {
        return if (root.path == target.path) "" else target.path.removePrefix(root.path.trimEnd('/') + "/")
    }
    // 上层 Linux 目录使用原有的逻辑路径，挂载内的比较优先命中上述规范路径。
    if (directory.backend != file.backend || !changePathContains(directory.path, file.path)) return null
    return if (directory.path == file.path) "" else file.path.removePrefix(directory.path.trimEnd('/') + "/")
}

@Serializable
internal enum class SnapshotKind { ABSENT, DIRECTORY, TEXT, BINARY, TOO_LARGE, UNAVAILABLE }

@Serializable
internal data class ChangeSnapshot(
    val kind: SnapshotKind,
    val size: Long = 0,
    val blob: String? = null,
    val detail: String? = null,
)

@Serializable
internal data class FileChangeRecord(
    val id: String,
    val operationId: String,
    val chatId: String,
    val timestamp: Long,
    val toolName: String,
    val location: ChangeLocation,
    val before: ChangeSnapshot,
    val after: ChangeSnapshot,
    val additions: Int? = null,
    val deletions: Int? = null,
    val executionId: String? = null,
    val sourceKind: String? = null,
    val subagentExecutionId: String? = null,
)

internal enum class ChangeReviewSource { OPERATIONS, WORKING_TREE, BRANCH, LAST_TURN }

internal data class ReviewFile(
    val id: String,
    val location: ChangeLocation,
    val displayPath: String,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val binary: Boolean = false,
    val records: List<FileChangeRecord> = emptyList(),
    val aggregateRecords: Boolean = false,
    val historyQuery: WorkspaceHistoryQuery? = null,
    val historyCount: Int = 0,
    val statisticsAvailable: Boolean = true,
    val initialContent: ReviewContent? = null,
    val gitRoot: String? = null,
    val gitPath: String? = null,
    val originalPath: String? = null,
    val baseCommit: String? = null,
    val headCommit: String? = null,
    val untracked: Boolean = false,
    val unborn: Boolean = false,
)

internal data class ReviewContent(
    val before: String?,
    val after: String?,
    val beforeKind: SnapshotKind,
    val afterKind: SnapshotKind,
    val detail: String? = null,
    val snapshotsCleared: Boolean = false,
) {
    val isText: Boolean get() = beforeKind in textKinds && afterKind in textKinds
    private val textKinds get() = setOf(SnapshotKind.TEXT, SnapshotKind.ABSENT)
}

/** 只折叠路径分隔符和点段，不去掉合法文件名里的空白字符。 */
internal fun changePath(path: String): String {
    require(!path.contains('\u0000')) { "Invalid path" }
    val parts = ArrayDeque<String>()
    path.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeLast()
            else -> parts.addLast(part)
        }
    }
    return "/" + parts.joinToString("/")
}

internal fun changePathContains(directory: String, path: String): Boolean {
    val root = changePath(directory)
    val target = changePath(path)
    return root == "/" || root == target || target.startsWith("$root/")
}

internal fun changeEnvironment(environment: String?): String = when {
    environment.isNullOrBlank() || environment.equals("android", ignoreCase = true) -> "android"
    environment.equals("linux", ignoreCase = true) -> "linux"
    environment.startsWith("repo:", ignoreCase = true) -> "repo:" + environment.substringAfter(':')
    else -> error("Unsupported environment: $environment")
}

internal fun recordStatus(record: FileChangeRecord): String = when {
    record.before.kind == SnapshotKind.ABSENT -> "A"
    record.after.kind == SnapshotKind.ABSENT -> "D"
    else -> "M"
}
