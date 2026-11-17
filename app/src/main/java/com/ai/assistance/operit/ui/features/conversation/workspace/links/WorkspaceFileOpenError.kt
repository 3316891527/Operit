package com.ai.assistance.operit.ui.features.conversation.workspace.links

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

internal enum class WorkspaceFileOpenReason { MISSING, PERMISSION, BACKEND, DIRECTORY, TOO_LARGE, NOT_TEXT, READ_FAILED }
internal class WorkspaceFileOpenException(val reason: WorkspaceFileOpenReason, detail: String? = null) : IOException(detail)

/** 只归类明确诊断；无法确定原因时保留读取失败，不能把所有异常都当作文件已删除。 */
internal fun workspaceFileOpenReason(failure: Throwable): WorkspaceFileOpenReason {
    var current: Throwable? = failure
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is WorkspaceFileOpenException) return current.reason
        if (current is SecurityException || current is AccessDeniedException) return WorkspaceFileOpenReason.PERMISSION
        if (current is NoSuchFileException) return WorkspaceFileOpenReason.MISSING
        val detail = current.message.orEmpty()
        if (Regex("permission denied|access denied|EACCES|EPERM|SecurityException|权限不足|没有.*权限|未授权", RegexOption.IGNORE_CASE).containsMatchIn(detail)) return WorkspaceFileOpenReason.PERMISSION
        if (Regex("no such file|file does not exist|ENOENT|文件不存在|文件已不存在", RegexOption.IGNORE_CASE).containsMatchIn(detail)) return WorkspaceFileOpenReason.MISSING
        if (Regex("non-text file|not.*text file|非文本", RegexOption.IGNORE_CASE).containsMatchIn(detail)) return WorkspaceFileOpenReason.NOT_TEXT
        current = current.cause
    }
    return WorkspaceFileOpenReason.READ_FAILED
}