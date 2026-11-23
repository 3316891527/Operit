package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import java.util.Locale
import com.ai.assistance.operit.util.files.KnownTextFiles
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceBrowserSettings
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.isWorkspaceEntryHidden

internal enum class WorkspaceArchiveKind { ZIP, SEVEN_Z, RAR, TAR, TAR_GZIP, TAR_BZIP2, TAR_XZ, TAR_ZSTD }
internal enum class WorkspaceFileRoute { ARCHIVE, TEXT, PREVIEW, SYSTEM }

private val zipContainers = setOf("zip", "jar", "apk", "war", "ear", "xapk", "apks", "apkm", "toolpkg",
    "aab", "ipa", "whl", "vsix", "epub", "cbz", "nupkg")

/** 长后缀先匹配；容器别名共用 ZIP 能力，文档扩展名仍走文档预览。 */
internal fun workspaceArchiveKind(path: String): WorkspaceArchiveKind? {
    val name = path.substringAfterLast('/').lowercase(Locale.ROOT)
    return when {
        name.endsWith(".tar.gz") || name.endsWith(".tgz") -> WorkspaceArchiveKind.TAR_GZIP
        name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tbz") -> WorkspaceArchiveKind.TAR_BZIP2
        name.endsWith(".tar.xz") || name.endsWith(".txz") -> WorkspaceArchiveKind.TAR_XZ
        name.endsWith(".tar.zst") || name.endsWith(".tzst") -> WorkspaceArchiveKind.TAR_ZSTD
        name.substringAfterLast('.', "") in zipContainers -> WorkspaceArchiveKind.ZIP
        name.endsWith(".7z") -> WorkspaceArchiveKind.SEVEN_Z
        name.endsWith(".rar") || name.endsWith(".cbr") -> WorkspaceArchiveKind.RAR
        name.endsWith(".tar") -> WorkspaceArchiveKind.TAR
        else -> null
    }
}

internal fun workspaceFileRoute(path: String, mimeType: String = ""): WorkspaceFileRoute {
    if (workspaceArchiveKind(path) != null) return WorkspaceFileRoute.ARCHIVE
    val name = path.substringAfterLast('/')
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (name.indexOf('.') <= 0) return WorkspaceFileRoute.TEXT
    val mime = mimeType.lowercase(Locale.ROOT)
    if (mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/") ||
        mime in setOf("application/pdf", "application/msword", "application/vnd.ms-excel") ||
        mime.startsWith("application/vnd.openxmlformats-officedocument.") ||
        extension in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif",
            "mp3", "wav", "m4a", "aac", "ogg", "opus", "flac", "mp4", "mkv", "webm", "avi", "mov", "3gp", "pdf", "doc", "docx", "xls", "xlsx")) {
        return WorkspaceFileRoute.PREVIEW
    }
    if (KnownTextFiles.mimeType(name) != null || extension == "lock" || TextMateCatalog.detectLanguage(name) != "text" ||
        mime.startsWith("text/") ||
        mime in setOf("application/json", "application/xml", "application/javascript", "application/yaml")) {
        return WorkspaceFileRoute.TEXT
    }
    return WorkspaceFileRoute.SYSTEM
}

internal data class WorkspaceArchiveEntry(
    val path: String,
    val directory: Boolean,
    val size: Long?,
    val packedSize: Long? = null,
    val modified: Long? = null,
    val encrypted: Boolean = false,
    val symbolicLink: Boolean = false,
) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
}

/** 只接受归档内部相对路径；目录索引与解压使用同一规范化规则。 */
internal fun workspaceArchivePath(value: String): String {
    require('\u0000' !in value && !value.startsWith('/') && !value.startsWith('\\')) { "Invalid archive path" }
    val normalized = value.replace('\\', '/')
    require(!Regex("^[A-Za-z]:").containsMatchIn(normalized)) { "Invalid archive path" }
    val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
    require(parts.none { it == ".." }) { "Invalid archive path" }
    return parts.joinToString("/")
}

/** 补齐归档省略的父目录，当前层只显示直接子项，目录排在文件前面。 */
internal fun workspaceArchiveChildren(entries: List<WorkspaceArchiveEntry>, directory: String): List<WorkspaceArchiveEntry> {
    val prefix = directory.takeIf(String::isNotEmpty)?.plus("/").orEmpty()
    val children = linkedMapOf<String, WorkspaceArchiveEntry>()
    for (entry in entries) {
        if (!entry.path.startsWith(prefix) || entry.path == directory) continue
        val remainder = entry.path.removePrefix(prefix)
        val name = remainder.substringBefore('/')
        if (name.isEmpty()) continue
        val path = prefix + name
        val child = if ('/' in remainder) WorkspaceArchiveEntry(path, true, null) else entry
        val existing = children[path]
        if (existing == null || entry.path == path) children[path] = child
    }
    return children.values.sortedWith(compareBy<WorkspaceArchiveEntry> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

internal fun workspaceArchiveSelection(entries: List<WorkspaceArchiveEntry>, selected: Set<String>): List<WorkspaceArchiveEntry> =
    entries.filter { entry -> selected.any { entry.path == it || entry.path.startsWith(it.trimEnd('/') + "/") } }

/** 重复路径与文件占用父目录都在读索引时拒绝，浏览和解压共享同一棵有效目录树。 */
internal fun validateWorkspaceArchiveEntries(entries: List<WorkspaceArchiveEntry>) {
    val paths = entries.map { it.path }
    require(paths.distinct().size == paths.size) { "Duplicate archive entry" }
    val files = entries.filterNot { it.directory }.map { it.path }.toSet()
    for (path in paths) {
        require(path.isNotEmpty() && workspaceArchivePath(path) == path) { "Invalid archive path" }
        var parent = path.substringBeforeLast('/', "")
        while (parent.isNotEmpty()) {
            require(parent !in files) { "Archive path conflicts with a file" }
            parent = parent.substringBeforeLast('/', "")
        }
    }
}

/** 展示过滤复用浏览偏好；传给解压引擎的完整条目索引保持不变。 */
internal fun workspaceArchiveVisibleChildren(entries: List<WorkspaceArchiveEntry>, directory: String,
    source: WorkspaceLocation, settings: WorkspaceBrowserSettings): List<WorkspaceArchiveEntry> =
    workspaceArchiveChildren(entries, directory).filterNot { entry ->
        isWorkspaceEntryHidden(source.copy(path = source.path + "!/" + entry.path), entry.name, settings)
    }

internal class WorkspaceArchivePasswordRequiredException : Exception("Archive decryption password required")

internal fun requireWorkspaceArchivePassword(entries: List<WorkspaceArchiveEntry>, password: String) {
    if (password.isEmpty() && entries.any { it.encrypted && !it.directory }) throw WorkspaceArchivePasswordRequiredException()
}

/** 只识别明确的口令错误，不把损坏归档或不支持的加密算法当成密码输入问题。 */
internal fun workspaceArchiveNeedsPassword(failure: Throwable): Boolean = generateSequence(failure) { it.cause }.any {
    it is WorkspaceArchivePasswordRequiredException ||
        (it is net.lingala.zip4j.exception.ZipException && it.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD) ||
        (it is me.zhanghai.android.libarchive.ArchiveException && it.message.orEmpty().let { message ->
            message.contains("passphrase", ignoreCase = true) || message.contains("password", ignoreCase = true)
        })
}

internal data class WorkspaceArchiveProperties(
    val single: WorkspaceArchiveEntry?,
    val bytes: Long?,
    val packedBytes: Long?,
    val files: Long,
    val directories: Long,
)

/** 未知大小和溢出均保持未知，不能把不完整的目录统计显示成零或负数。 */
private fun archivePropertyBytes(values: List<Long?>): Long? {
    var total = 0L
    for (value in values) {
        if (value == null || value < 0 || value > Long.MAX_VALUE - total) return null
        total += value
    }
    return total
}

/** 补齐隐式目录并按路径去重；单目录不计自身，多选与外部批量属性一样计入所选目录。 */
internal fun workspaceArchiveProperties(entries: List<WorkspaceArchiveEntry>, selected: Set<String>): WorkspaceArchiveProperties {
    require(selected.isNotEmpty())
    // 通过祖先路径匹配选择，避免大批多选时逐条遍历整个选择集合。
    fun included(path: String): Boolean {
        var current = path
        while (current.isNotEmpty()) {
            if (current in selected) return true
            current = current.substringBeforeLast('/', "")
        }
        return false
    }
    val chosen = entries.filter { included(it.path) }
    require(chosen.isNotEmpty()) { "Archive entry does not exist" }
    val single = if (selected.size == 1) {
        val path = selected.single()
        val explicit = entries.firstOrNull { it.path == path }
        if (explicit != null) explicit else {
            require(entries.any { it.path.startsWith(path + "/") }) { "Archive directory does not exist" }
            WorkspaceArchiveEntry(path, directory = true, size = null)
        }
    } else null
    val directories = mutableSetOf<String>()
    for (entry in chosen) {
        if (entry.directory && !entry.symbolicLink) directories.add(entry.path)
        var parent = entry.path.substringBeforeLast('/', "")
        while (parent.isNotEmpty() && included(parent)) {
            directories.add(parent)
            parent = parent.substringBeforeLast('/', "")
        }
    }
    if (single?.directory == true) directories.remove(single.path)
    val files = chosen.filterNot { it.directory && !it.symbolicLink }.distinctBy { it.path }
    return WorkspaceArchiveProperties(single, archivePropertyBytes(files.map { it.size }),
        archivePropertyBytes(files.map { it.packedSize }), files.size.toLong(), directories.size.toLong())
}
