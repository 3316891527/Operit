package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** 成功遍历完整目录后才交付清单；链接记录目标文本，不跟随目录链接。 */
internal fun observeLocalWorkspace(
    root: File,
    before: Map<String, ObservedWorkspaceFile>,
    forced: Set<String>,
    excluded: (File) -> Boolean,
    snapshotBytes: (ByteArray) -> ChangeSnapshot,
): Map<String, ObservedWorkspaceFile> {
    val result = linkedMapOf<String, ObservedWorkspaceFile>()
    scanLocalWorkspace(root, { before[it] }, forced, excluded, { path, file -> result[path] = file }, snapshotBytes)
    return result
}

internal fun scanLocalWorkspace(
    root: File,
    before: (String) -> ObservedWorkspaceFile?,
    forced: Set<String>,
    excluded: (File) -> Boolean,
    onFile: (String, ObservedWorkspaceFile) -> Unit,
    snapshotBytes: (ByteArray) -> ChangeSnapshot,
    paths: Set<String>? = null,
) {
    val rootPath = root.toPath()
    if (Files.notExists(rootPath, LinkOption.NOFOLLOW_LINKS)) return
    check(Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)) { "Workspace is not a directory: $root" }
    fun stamp(attrs: BasicFileAttributes) = "${attrs.lastModifiedTime()}:${attrs.size()}:${attrs.fileKey()}"
    val visitor = object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val path = rootPath.relativize(dir).toString().replace(File.separatorChar, '/')
            return if (observedWorkspacePath(path) && !excluded(dir.toFile())) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
        }
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val path = rootPath.relativize(file).toString().replace(File.separatorChar, '/')
            if (!observedWorkspacePath(path) || excluded(file.toFile())) return FileVisitResult.CONTINUE
            val prior = before(path)
            val needsRead = anyObservationAncestor(path, forced::contains)
            val snapshot = if (prior != null && prior.stamp == stamp(attrs) && !needsRead) prior.snapshot else when {
                attrs.isSymbolicLink -> snapshotBytes(Files.readSymbolicLink(file).toString().toByteArray(Charsets.UTF_8))
                !attrs.isRegularFile -> ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = "Not a regular file")
                attrs.size() > CHANGE_SNAPSHOT_LIMIT -> ChangeSnapshot(SnapshotKind.TOO_LARGE, attrs.size())
                else -> {
                    val bytes = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= CHANGE_SNAPSHOT_LIMIT) {
                            val count = input.read(buffer, 0, minOf(buffer.size, CHANGE_SNAPSHOT_LIMIT + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val after = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                    if (stamp(attrs) != stamp(after)) throw IOException("File changed during workspace scan: $file")
                    snapshotBytes(bytes)
                }
            }
            onFile(path, ObservedWorkspaceFile(stamp(attrs), snapshot))
            return FileVisitResult.CONTINUE
        }
    }
    for (name in selectedObservationPaths(paths)) {
        val path = rootPath.resolve(name).normalize()
        var parent = path.parent
        var linkedParent = false
        while (parent != null && parent.startsWith(rootPath)) {
            if (Files.isSymbolicLink(parent)) linkedParent = true
            if (parent == rootPath) break
            parent = parent.parent
        }
        if (!linkedParent && !Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) Files.walkFileTree(path, visitor)
    }
}
