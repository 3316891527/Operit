package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import kotlinx.serialization.Serializable

/** 路径归属与对话归属分开保存，重新绑定工作区不改写历史。 */
@Serializable
internal data class ChangeLocation(val path: String, val environment: String, val backend: String) {
    val key: String get() = "$backend\u0000$path"
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
