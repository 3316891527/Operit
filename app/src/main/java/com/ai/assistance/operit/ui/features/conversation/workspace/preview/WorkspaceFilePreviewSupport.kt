package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.core.tools.builtin.ToolGetter
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale
import com.ai.assistance.operit.util.files.KnownTextFiles
import com.ai.assistance.operit.ui.features.conversation.workspace.OpenFileInfo
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightConfiguration
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightFiles
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.WorkspaceFileRoute
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceFileRoute

private const val LOCAL_WORKSPACE_HOST = "127.0.0.1"

internal suspend fun workspaceMimeTypeForFile(
    context: Context,
    path: String,
    environment: String?
): String {
    val mimeType = workspaceMimeTypeForPath(path)
    // 内置源码与媒体后缀冲突时沿用内容采样；普通媒体和归档保持原打开方式。
    if (TextMateCatalog.detectLanguage(path) != "text" && workspaceShouldOpenAsDirectPreview(path, mimeType)) {
        val isText = ToolGetter.getFileSystemTools(context).isTextFile(path, environment)
        return workspaceMimeTypeForContent(path, mimeType, isText)
    }
    if (workspaceFileRoute(path, mimeType) != WorkspaceFileRoute.SYSTEM) return mimeType
    // 只为陌生格式读取配置，损坏配置本身仍能通过 JSON 文本入口打开修正。
    val configuration = EditorHighlightFiles.load(EditorHighlightFiles.directory(context))
    if (configuration.associatedLanguage(path) == null) return mimeType
    val isText = ToolGetter.getFileSystemTools(context).isTextFile(path, environment)
    return workspaceMimeTypeForContent(path, mimeType, isText, configuration)
}

/** 登记的陌生格式经内容确认后进入编辑器，二进制与已有预览类型保持原路由。 */
internal fun workspaceMimeTypeForContent(
    path: String, mimeType: String, isText: Boolean,
    configuration: EditorHighlightConfiguration = EditorHighlightConfiguration.Empty,
): String {
    val sourceConflict = TextMateCatalog.detectLanguage(path) != "text" && workspaceShouldOpenAsDirectPreview(path, mimeType)
    val customText = configuration.associatedLanguage(path) != null && workspaceFileRoute(path, mimeType) == WorkspaceFileRoute.SYSTEM
    return if (isText && (sourceConflict || customText)) "text/plain" else mimeType
}

internal fun workspaceMimeTypeForPath(path: String): String {
    KnownTextFiles.mimeType(path)?.let { return it }
    val extension = path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension.isBlank()) return "application/octet-stream"
    return when (extension) {
        "ts" -> "video/mp2t"

        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "webm" -> "video/webm"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "3gp" -> "video/3gpp"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pdf" -> "application/pdf"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}

internal val OpenFileInfo.isHtml: Boolean
    get() = name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)

internal val OpenFileInfo.isMarkdown: Boolean
    get() = name.endsWith(".md", ignoreCase = true) ||
        name.endsWith(".markdown", ignoreCase = true)

internal val OpenFileInfo.isImage: Boolean
    get() = resolvedMimeType.startsWith("image/", ignoreCase = true)

internal val OpenFileInfo.isAudio: Boolean
    get() = resolvedMimeType.startsWith("audio/", ignoreCase = true)

internal val OpenFileInfo.isVideo: Boolean
    get() = resolvedMimeType.startsWith("video/", ignoreCase = true)

internal val OpenFileInfo.isMediaPreviewable: Boolean
    get() = isImage || isAudio || isVideo

internal val OpenFileInfo.isPdf: Boolean
    get() = resolvedMimeType.equals("application/pdf", ignoreCase = true)

internal val OpenFileInfo.isWordDocument: Boolean
    get() = resolvedMimeType.equals("application/msword", ignoreCase = true) ||
        resolvedMimeType.equals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ignoreCase = true
        )

internal val OpenFileInfo.isSpreadsheetDocument: Boolean
    get() = resolvedMimeType.equals("application/vnd.ms-excel", ignoreCase = true) ||
        resolvedMimeType.equals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ignoreCase = true
        )

internal val OpenFileInfo.isReadOnlyDocumentPreviewable: Boolean
    get() = isPdf || isWordDocument || isSpreadsheetDocument

internal val OpenFileInfo.isReadOnlyPreview: Boolean
    get() = isMediaPreviewable || isReadOnlyDocumentPreviewable

internal val OpenFileInfo.resolvedMimeType: String
    get() = mimeType.ifBlank { workspaceMimeTypeForPath(path) }
internal fun workspaceShouldOpenAsDirectPreview(
    path: String,
    mimeType: String = workspaceMimeTypeForPath(path)
): Boolean {

    return mimeType.startsWith("image/", ignoreCase = true) ||
        mimeType.startsWith("audio/", ignoreCase = true) ||
        mimeType.startsWith("video/", ignoreCase = true) ||
        mimeType.equals("application/pdf", ignoreCase = true) ||
        mimeType.equals("application/msword", ignoreCase = true) ||
        mimeType.equals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ignoreCase = true
        ) ||
        mimeType.equals("application/vnd.ms-excel", ignoreCase = true) ||
        mimeType.equals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ignoreCase = true
        )
}

internal fun buildWorkspacePreviewUri(
    filePath: String,
    workspaceRootPath: String,
    workspaceEnv: String?,
    cacheBustToken: String? = null
): Uri? {
    val relativePath = resolveWorkspacePreviewRelativePath(filePath, workspaceRootPath, workspaceEnv)
    if (relativePath != null) {
        val builder = Uri.Builder()
            .scheme("http")
            .encodedAuthority("$LOCAL_WORKSPACE_HOST:${LocalWebServer.WORKSPACE_PORT}")
            .path(relativePath)
        if (!cacheBustToken.isNullOrBlank()) {
            builder.appendQueryParameter("v", cacheBustToken)
        }
        return builder.build()
    }

    if (workspaceEnv.isNullOrBlank()) {
        return Uri.fromFile(File(filePath))
    }

    return null
}

private fun resolveWorkspacePreviewRelativePath(
    filePath: String,
    workspaceRootPath: String,
    workspaceEnv: String?
): String? {
    return if (workspaceEnv.isNullOrBlank()) {
        resolveLocalWorkspaceRelativePath(filePath, workspaceRootPath)
    } else {
        resolveVirtualWorkspaceRelativePath(filePath, workspaceRootPath)
    }
}

private fun resolveLocalWorkspaceRelativePath(filePath: String, workspaceRootPath: String): String? {
    val canonicalFile = runCatching { File(filePath).canonicalPath.normalizeWorkspacePath() }
        .getOrElse { filePath.normalizeWorkspacePath() }
    val canonicalRoot = runCatching { File(workspaceRootPath).canonicalPath.normalizeWorkspacePath() }
        .getOrElse { workspaceRootPath.normalizeWorkspacePath() }
        .trimEnd('/')
        .ifBlank { "/" }

    if (canonicalFile == canonicalRoot) {
        return "/"
    }

    return if (canonicalFile.startsWith("$canonicalRoot/")) {
        "/" + canonicalFile.removePrefix("$canonicalRoot/").trimStart('/')
    } else {
        null
    }
}

private fun resolveVirtualWorkspaceRelativePath(filePath: String, workspaceRootPath: String): String? {
    val normalizedFile = filePath.normalizeWorkspacePath()
    val normalizedRoot = workspaceRootPath.normalizeWorkspacePath().trimEnd('/').ifBlank { "/" }

    if (normalizedFile == normalizedRoot) {
        return "/"
    }

    return if (normalizedRoot == "/") {
        normalizedFile.ensureLeadingSlash()
    } else if (normalizedFile.startsWith("$normalizedRoot/")) {
        "/" + normalizedFile.removePrefix("$normalizedRoot/").trimStart('/')
    } else {
        null
    }
}

private fun String.normalizeWorkspacePath(): String {
    val withForwardSlash = replace('\\', '/')
    val collapsed = withForwardSlash.replace(Regex("/+"), "/")
    return collapsed.ensureLeadingSlash()
}

private fun String.ensureLeadingSlash(): String {
    return if (startsWith("/")) this else "/$this"
}
