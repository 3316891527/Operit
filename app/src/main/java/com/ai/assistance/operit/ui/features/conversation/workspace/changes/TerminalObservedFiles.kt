package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.serialization.Serializable

@Serializable
internal data class TerminalObservedFile(val stamp: String, val snapshot: ChangeSnapshot)

@Serializable
internal data class TerminalObservationBaseline(
    val files: Map<String, TerminalObservedFile> = emptyMap(),
    val unreadable: Set<String> = emptySet(),
)

/** 首次扫描建立基线，后续只替换收到事件的子树；不可读目录保留旧基线。 */
internal fun scanTerminalFiles(
    root: File,
    before: TerminalObservationBaseline,
    paths: Set<String>? = null,
    excluded: (File) -> Boolean = { false },
    byteBudget: Long = 64L * 1024 * 1024,
    onDirectory: (File) -> Unit = {},
    snapshotBytes: (ByteArray) -> ChangeSnapshot,
): TerminalObservationBaseline {
    val rootPath = root.toPath().toAbsolutePath().normalize()
    val result = if (paths == null) linkedMapOf() else before.files.toMutableMap()
    val unreadable = if (paths == null) linkedSetOf() else before.unreadable.toMutableSet()
    var remaining = byteBudget
    fun relative(path: Path) = rootPath.relativize(path).toString().replace(File.separatorChar, '/')
    fun contains(parent: String, child: String) = parent.isEmpty() || parent == child || child.startsWith("$parent/")
    fun unknown(path: Path) {
        val name = relative(path)
        unreadable += name
        before.files.filterKeys { contains(name, it) }.forEach { (key, value) -> result[key] = value }
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
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
            if (excluded(dir.toFile()) || relative(dir).split('/').any { it == ".git" }) FileVisitResult.SKIP_SUBTREE
            else {
                onDirectory(dir.toFile())
                FileVisitResult.CONTINUE
            }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (excluded(file.toFile()) || relative(file).split('/').any { it == ".git" }) return FileVisitResult.CONTINUE
            val name = relative(file)
            val previous = before.files[name]
            val stamp = terminalFileStamp(attrs)
            // 事件路径即使长度和时间戳相同也重读，覆盖保留时间戳的写入。
            val oldUnchanged = paths == null && previous?.stamp == stamp && previous.snapshot.kind != SnapshotKind.UNAVAILABLE
            try {
                result[name] = TerminalObservedFile(stamp, if (oldUnchanged) checkNotNull(previous).snapshot else read(file, attrs))
            } catch (error: Exception) {
                unknown(file)
                if (previous == null) result[name] = TerminalObservedFile(stamp, ChangeSnapshot(SnapshotKind.UNAVAILABLE, attrs.size(), detail = error.message))
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, error: java.io.IOException): FileVisitResult {
            unknown(file)
            return FileVisitResult.CONTINUE
        }
    }
    val selected = paths?.sortedBy { it.length }?.fold(mutableListOf<String>()) { kept, path ->
        require(!path.startsWith('/') && path.split('/').none { it == ".." }) { "Invalid observation path" }
        if (kept.none { contains(it, path) }) kept += path
        kept
    } ?: listOf("")
    for (name in selected) {
        val path = rootPath.resolve(name).normalize()
        if (paths != null) {
            // 普通文件的旧基线没有子路径，单文件更新无需遍历整个观察根的键集。
            if (name in before.files) result.remove(name) else result.keys.removeAll { contains(name, it) }
            unreadable.removeAll { contains(name, it) }
        }
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
    return TerminalObservationBaseline(result, unreadable)
}

private fun terminalFileStamp(attrs: BasicFileAttributes): String =
    "${attrs.lastModifiedTime()}:${attrs.size()}:${attrs.fileKey()}"

internal fun TerminalObservationBaseline.snapshot(path: String): ChangeSnapshot = files[path]?.snapshot
    ?: if (unreadable.any { it.isEmpty() || path == it || path.startsWith("$it/") })
        ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "Previous observation could not read this path")
    else ChangeSnapshot(SnapshotKind.ABSENT)
