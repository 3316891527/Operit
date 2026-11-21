package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem

internal fun workspaceArchiveExtension(kind: WorkspaceArchiveKind): String = when (kind) {
    WorkspaceArchiveKind.ZIP -> "zip"
    WorkspaceArchiveKind.SEVEN_Z -> "7z"
    WorkspaceArchiveKind.RAR -> "rar"
    WorkspaceArchiveKind.TAR -> "tar"
    WorkspaceArchiveKind.TAR_GZIP -> "tar.gz"
    WorkspaceArchiveKind.TAR_BZIP2 -> "tar.bz2"
    WorkspaceArchiveKind.TAR_XZ -> "tar.xz"
    WorkspaceArchiveKind.TAR_ZSTD -> "tar.zst"
}

/** 来源是普通文件时去掉原扩展名，带点的目录名与无扩展名的隐藏文件保持原名。 */
internal fun workspaceCompressionBaseName(target: FileBrowserClipboardItem): String {
    val name = target.path.trimEnd('/').substringAfterLast('/')
    if (target.isDirectory) return name
    val compound = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst").firstOrNull { name.endsWith(it, ignoreCase = true) && name.length > it.length }
    if (compound != null) return name.dropLast(compound.length)
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(0, dot) else name
}

/** 默认名及源文件原名补全格式后缀；用户改名时填写的后缀原样保留。 */
internal fun workspaceCompressionFileName(name: String, kind: WorkspaceArchiveKind, source: FileBrowserClipboardItem): String {
    val original = source.path.trimEnd('/').substringAfterLast('/')
    val dot = name.lastIndexOf('.')
    val hasExtension = dot > 0 && dot < name.lastIndex
    val keepExtension = hasExtension && name != original && name != workspaceCompressionBaseName(source)
    return if (keepExtension) name else name + "." + workspaceArchiveExtension(kind)
}
