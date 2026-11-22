package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** 从具体路径向上查找命中项，避免逐个比较批量事件中的所有路径。 */
internal inline fun anyObservationAncestor(path: String, matches: (String) -> Boolean): Boolean {
    if (matches(path)) return true
    var slash = path.lastIndexOf('/')
    while (slash >= 0) {
        if (matches(path.substring(0, slash))) return true
        slash = path.lastIndexOf('/', slash - 1)
    }
    return path.isNotEmpty() && matches("")
}

/** 首次扫描建立基线，后续只替换收到事件的子树；不可读目录保留旧基线。 */
internal fun scanTerminalFiles(
    root: File,
    before: TerminalObservationReader,
    target: TerminalObservationWriter,
    paths: Set<String>? = null,
    excluded: (File) -> Boolean = { false },
    byteBudget: Long = 64L * 1024 * 1024,
    onDirectory: (File) -> Unit = {},
    checkCancelled: () -> Unit = {},
    snapshotBytes: (ByteArray) -> ChangeSnapshot,
) {
    val rootPath = root.toPath().toAbsolutePath().normalize()
    var remaining = byteBudget
    fun relative(path: Path) = rootPath.relativize(path).toString().replace(File.separatorChar, '/')
    fun unknown(path: Path) {
        val name = relative(path)
        target.unreadable(name)
        target.preserveSubtree(name)
    }
    fun read(path: Path, attrs: BasicFileAttributes): ChangeSnapshot = when {
        attrs.isSymbolicLink -> snapshotBytes(Files.readSymbolicLink(path).toString().toByteArray(Charsets.UTF_8))
        !attrs.isRegularFile -> ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "Not a regular file")
        attrs.size() > CHANGE_SNAPSHOT_LIMIT -> ChangeSnapshot(SnapshotKind.TOO_LARGE, attrs.size())
        attrs.size() > remaining -> ChangeSnapshot(SnapshotKind.UNAVAILABLE, attrs.size(), detail = "Observation snapshot byte budget reached")
        else -> {
            val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= CHANGE_SNAPSHOT_LIMIT) {
                    val size = input.read(buffer, 0, minOf(buffer.size, CHANGE_SNAPSHOT_LIMIT + 1 - output.size()))
                    if (size < 0) break
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            }
            remaining -= bytes.size
            val next = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            check(terminalFileStamp(attrs) == terminalFileStamp(next)) { "File changed during snapshot: $path" }
            snapshotBytes(bytes)
        }
    }
    val visitor = object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            checkCancelled()
            if (excluded(dir.toFile()) || relative(dir).split('/').any { it == ".git" }) return FileVisitResult.SKIP_SUBTREE
            onDirectory(dir.toFile())
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            checkCancelled()
            if (excluded(file.toFile()) || relative(file).split('/').any { it == ".git" }) return FileVisitResult.CONTINUE
            val name = relative(file)
            val previous = before.file(name)
            val stamp = terminalFileStamp(attrs)
            // 事件路径即使长度和时间戳相同也重读，覆盖保留时间戳的写入。
            val oldUnchanged = paths == null && previous?.stamp == stamp && previous.snapshot.kind != SnapshotKind.UNAVAILABLE
            try {
                target.put(name, TerminalObservedFile(stamp, if (oldUnchanged) checkNotNull(previous).snapshot else read(file, attrs)))
            } catch (error: Exception) {
                checkCancelled()
                unknown(file)
                if (previous == null) target.put(name, TerminalObservedFile(stamp, ChangeSnapshot(SnapshotKind.UNAVAILABLE, attrs.size(), detail = error.message)))
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, error: java.io.IOException): FileVisitResult {
            // 不可读目录可能在进入 preVisitDirectory 前失败，失败分支也须遵守排除规则。
            if (!excluded(file.toFile())) unknown(file)
            return FileVisitResult.CONTINUE
        }
    }
    val selected = selectedObservationPaths(paths)
    for (name in selected) {
        checkCancelled()
        val path = rootPath.resolve(name).normalize()
        // 路径的任意中间段是链接时，不能沿链接读到观察范围之外。
        var parent = path.parent
        var linkedParent = false
        while (parent != null && parent.startsWith(rootPath)) {
            if (Files.isSymbolicLink(parent)) linkedParent = true
            if (parent == rootPath) break
            parent = parent.parent
        }
        if (linkedParent || excluded(path.toFile())) continue
        if (Files.notExists(path, NOFOLLOW_LINKS)) continue
        try { Files.walkFileTree(path, visitor) } catch (error: java.io.IOException) { unknown(path) }
    }
}

private fun terminalFileStamp(attrs: BasicFileAttributes): String =
    "${attrs.lastModifiedTime()}:${attrs.size()}:${attrs.fileKey()}"


internal fun selectedObservationPaths(paths: Set<String>?): List<String> {
    if (paths == null) return listOf("")
    require(paths.isNotEmpty()) { "No observation paths" }
    val selected = linkedSetOf<String>()
    for (path in paths.sortedBy { it.length }) {
        require(!path.startsWith('/') && !path.contains('\u0000') && path.split('/').none { it == ".." || it == "." }) { "Invalid observation path" }
        if (!anyObservationAncestor(path, selected::contains)) selected += path
    }
    return selected.toList()
}
