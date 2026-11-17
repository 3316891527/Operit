package com.ai.assistance.operit.ui.features.conversation.workspace.links

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.permissions.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.ui.features.conversation.workspace.OpenFileInfo
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.WorkspaceFileRoute
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceFileRoute
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserOperations
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceChangeAccess
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.workspaceMimeTypeForFile
import com.ai.assistance.operit.ui.features.conversation.workspace.text.WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES
import com.ai.assistance.operit.ui.features.conversation.workspace.text.readWorkspaceTextWithinLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class WorkspaceFileLinkContent(val info: FileExistsData, val file: OpenFileInfo?, val route: WorkspaceFileRoute)

/** 普通链接与差异页共享一次实际读取，差异页成功读取后才创建查看窗口。 */
internal suspend fun readWorkspaceFileLink(context: Context, target: WorkspaceFileLink, expectedBackend: String? = null): WorkspaceFileLinkContent = withContext(Dispatchers.IO) {
    checkWorkspaceFileBackend(context, expectedBackend, target.environment)
    if (expectedBackend != null && target.environment == "android" &&
        androidPermissionPreferences.getPreferredPermissionLevel() !in setOf(AndroidPermissionLevel.ADMIN, AndroidPermissionLevel.ROOT)) {
        try {
            Os.stat(target.path)
            if (!Os.access(target.path, OsConstants.R_OK)) throw WorkspaceFileOpenException(WorkspaceFileOpenReason.PERMISSION)
        } catch (failure: ErrnoException) {
            when (failure.errno) {
                OsConstants.ENOENT, OsConstants.ENOTDIR -> throw WorkspaceFileOpenException(WorkspaceFileOpenReason.MISSING, failure.message)
                OsConstants.EACCES, OsConstants.EPERM -> throw WorkspaceFileOpenException(WorkspaceFileOpenReason.PERMISSION, failure.message)
                else -> throw failure
            }
        }
    }
    val handler = AIToolHandler.getInstance(context)
    val operations = FileBrowserOperations(context)
    val parameters = listOf(
        ToolParameter("path", target.path),
        ToolParameter("environment", target.environment),
    )
    val exists = handler.executeTool(AITool("file_exists", parameters))
    check(exists.success) { exists.error.orEmpty() }
    val info = exists.result
    check(info is FileExistsData) { "Invalid file metadata response" }
    if (!info.exists) throw WorkspaceFileOpenException(WorkspaceFileOpenReason.MISSING)
    if (expectedBackend != null && target.editAsText && info.isDirectory) throw WorkspaceFileOpenException(WorkspaceFileOpenReason.DIRECTORY)
    val mimeType = if (info.isDirectory) "" else workspaceMimeTypeForFile(context, target.path, target.environment)
    val route = if (target.editAsText && !info.isDirectory) WorkspaceFileRoute.TEXT else workspaceFileRoute(target.path, mimeType)
    if (expectedBackend != null && route == WorkspaceFileRoute.TEXT && info.size !in 0L..WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES)
        throw WorkspaceFileOpenException(WorkspaceFileOpenReason.TOO_LARGE)
    val openedFile = if (info.isDirectory) null else {
        when {
            route == WorkspaceFileRoute.ARCHIVE -> null
            route == WorkspaceFileRoute.PREVIEW ->
                OpenFileInfo(target.path, "", System.currentTimeMillis(),
                    mimeType = mimeType, environment = target.environment)
            route == WorkspaceFileRoute.TEXT -> readWorkspaceTextWithinLimit(info.size) {
                val read = handler.executeTool(AITool("read_file_full", parameters + ToolParameter("text_only", "true")))
                check(read.success) { read.error.orEmpty() }
                val result = read.result
                check(result is FileContentData) { context.getString(R.string.file_error_open_failed) }
                val initialLine = if (target.anchor == null) target.line else {
                    checkNotNull(findWorkspaceMarkdownAnchorLine(result.content, target.anchor)) {
                        context.getString(R.string.workspace_markdown_anchor_not_found, target.anchor)
                    }
                }
                OpenFileInfo(target.path, result.content, System.currentTimeMillis(),
                    mimeType = "text/plain", environment = target.environment,
                    initialLine = initialLine)
            }
            else -> { operations.externalOpen(target.path, target.environment, false); null }
        }
    }
    checkWorkspaceFileBackend(context, expectedBackend, target.environment)
    WorkspaceFileLinkContent(info, openedFile, route)
}

internal fun checkWorkspaceFileBackend(context: Context, expected: String?, environment: String) {
    if (expected != null && WorkspaceChangeAccess.backend(context, environment) != expected)
        throw WorkspaceFileOpenException(WorkspaceFileOpenReason.BACKEND)
}

internal fun workspaceFileOpenMessage(context: Context, path: String, failure: Throwable): String {
    val reason = workspaceFileOpenReason(failure)
    val label = context.getString(when (reason) {
        WorkspaceFileOpenReason.MISSING -> R.string.workspace_open_missing
        WorkspaceFileOpenReason.PERMISSION -> R.string.workspace_open_permission
        WorkspaceFileOpenReason.BACKEND -> R.string.workspace_open_backend
        WorkspaceFileOpenReason.DIRECTORY -> R.string.workspace_open_directory
        WorkspaceFileOpenReason.TOO_LARGE -> R.string.workspace_open_too_large
        WorkspaceFileOpenReason.NOT_TEXT -> R.string.workspace_open_not_text
        WorkspaceFileOpenReason.READ_FAILED -> R.string.workspace_open_failed
    })
    return listOfNotNull(label, path, failure.message?.takeIf { it.isNotBlank() }).joinToString("\n")
}
