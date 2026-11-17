package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

internal data class WorkspaceArchiveInput(val file: File, val path: String, val directory: Boolean, val size: Long, val modified: Long)

/** ZIP 使用流式写入，其余格式经同一个原生读取器解析，RAR5 不依赖旧版 RAR 读取库。 */
internal class WorkspaceArchiveEngine(
    private val nanoTime: () -> Long = System::nanoTime,
    private val onProgress: (WorkspaceArchiveProgress) -> Unit = {},
) {
    private var completed = 0L
    private var lastReport = 0L
    private var task = WorkspaceArchiveProgress()
    private var entrySizes = emptyMap<String, Long?>()

    private fun beginTask(stage: WorkspaceArchiveStage, entries: List<WorkspaceArchiveEntry>) {
        completed = 0L
        lastReport = 0L
        entrySizes = entries.associate { it.path to if (it.directory) 0L else it.size }
        var total: Long? = 0L
        for (size in entrySizes.values) {
            val sum = total
            total = if (sum == null || size == null || size < 0 || size > Long.MAX_VALUE - sum) null else sum + size
        }
        task = WorkspaceArchiveProgress(stage = stage, total = total, totalEntries = entries.size)
        onProgress(task)
    }

    private fun progress(name: String, bytes: Int = 0) {
        completed = Math.addExact(completed, bytes.toLong())
        val sameEntry = task.entry == name
        task = task.copy(entry = name, bytes = completed,
            entryBytes = if (sameEntry) Math.addExact(task.entryBytes, bytes.toLong()) else bytes.toLong(),
            entryTotal = if (sameEntry) task.entryTotal else entrySizes[name])
        val now = nanoTime()
        if (bytes == 0 || now - lastReport > 150_000_000L) {
            onProgress(task)
            lastReport = now
        }
    }

    private fun entryCompleted() {
        // 快速条目也必须通知最终字节数；结束当前条目比例，避免与完成数量重复计入进度。
        task = task.copy(completedEntries = task.completedEntries + 1, entryTotal = null)
        onProgress(task)
    }
    private suspend fun copy(input: InputStream, output: OutputStream, name: String) {
        val buffer = ByteArray(256 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            progress(name, count)
        }
    }
    private suspend fun <T> readNative(file: File, password: String, block: suspend (Long) -> T): T {
        val archive = Archive.readNew()
        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray(Charsets.UTF_8))
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            if (password.isNotEmpty()) Archive.readAddPassphrase(archive, password.toByteArray(Charsets.UTF_8))
            Archive.readOpenFileName(archive, file.absolutePath.toByteArray(Charsets.UTF_8), 256L * 1024)
            return block(archive)
        } finally { Archive.readFree(archive) }
    }
    private fun nativeEntry(entry: Long): WorkspaceArchiveEntry {
        val path = workspaceArchivePath(checkNotNull(ArchiveEntry.pathnameUtf8(entry)))
        val type = ArchiveEntry.filetype(entry)
        return WorkspaceArchiveEntry(path, type == ArchiveEntry.AE_IFDIR,
            ArchiveEntry.size(entry).takeIf { ArchiveEntry.sizeIsSet(entry) && it >= 0 },
            modified = ArchiveEntry.mtime(entry).takeIf { ArchiveEntry.mtimeIsSet(entry) }?.let { Math.multiplyExact(it, 1000L) },
            encrypted = ArchiveEntry.isEncrypted(entry),
            symbolicLink = type !in setOf(ArchiveEntry.AE_IFDIR, ArchiveEntry.AE_IFREG) || ArchiveEntry.hardlinkIsSet(entry))
    }
    suspend fun openReader(file: File, kind: WorkspaceArchiveKind, password: String): WorkspaceArchiveReader {
        onProgress(WorkspaceArchiveProgress(file.name, stage = WorkspaceArchiveStage.READING))
        val zip = if (kind == WorkspaceArchiveKind.ZIP) ZipFile(file, password.toCharArray()) else null
        try {
            val entries = if (zip != null) {
                zip.fileHeaders.map { header ->
                    currentCoroutineContext().ensureActive()
                    WorkspaceArchiveEntry(workspaceArchivePath(header.fileName), header.isDirectory,
                        header.uncompressedSize.takeIf { it >= 0 }, header.compressedSize.takeIf { it >= 0 },
                        modified = header.lastModifiedTimeEpoch, encrypted = header.isEncrypted,
                        symbolicLink = header.externalFileAttributes.let { it.size >= 4 && (it[3].toInt() and 0xf0) == 0xa0 })
                }
            } else readNative(file, password) { archive ->
                buildList {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = Archive.readNextHeader(archive)
                        if (entry == 0L) break
                        add(nativeEntry(entry))
                        Archive.readDataSkip(archive)
                    }
                }
            }
            val content = entries.filter { it.path.isNotEmpty() }
            validateWorkspaceArchiveEntries(content)
            return WorkspaceArchiveReader(file, kind, password, content, zip)
        } catch (failure: Exception) {
            zip?.close()
            throw failure
        }
    }

    suspend fun list(file: File, kind: WorkspaceArchiveKind, password: String): List<WorkspaceArchiveEntry> =
        openReader(file, kind, password).use { it.entries }

    suspend fun extract(file: File, kind: WorkspaceArchiveKind, password: String, selected: Set<String>, destination: File): List<File> =
        openReader(file, kind, password).use { extract(it, selected, destination) }

    /** 已打开会话直接使用索引；ZIP 只读取所选条目的数据流。 */
    suspend fun extract(reader: WorkspaceArchiveReader, selected: Set<String>, destination: File): List<File> {
        val entries = reader.entries
        val chosen = if (selected.isEmpty()) entries else workspaceArchiveSelection(entries, selected)
        require(selected.isEmpty() || selected.all { path -> chosen.any { it.path == path || it.path.startsWith("$path/") } }) {
            "Archive entry does not exist"
        }
        requireWorkspaceArchivePassword(chosen, reader.password)
        require(chosen.none { it.symbolicLink }) { "Archive links cannot be extracted" }
        val byPath = chosen.associateBy { it.path }
        val names = byPath.keys
        check(destination.isDirectory || destination.mkdirs())
        beginTask(WorkspaceArchiveStage.EXTRACTING, chosen)
        fun target(entry: WorkspaceArchiveEntry): File {
            val result = File(destination, entry.path)
            require(result.toPath().normalize().startsWith(destination.toPath().normalize()))
            if (entry.directory) check(result.isDirectory || result.mkdirs())
            else check(result.parentFile!!.isDirectory || result.parentFile!!.mkdirs())
            return result
        }
        if (reader.kind == WorkspaceArchiveKind.ZIP) {
            for (entry in chosen) {
                currentCoroutineContext().ensureActive()
                progress(entry.path)
                val output = target(entry)
                if (!entry.directory) reader.openZipEntry(entry).use { input ->
                    output.outputStream().buffered().use { copy(input, it, entry.path) }
                }
                entryCompleted()
            }
        } else readNative(reader.file, reader.password) { archive ->
            val buffer = ByteBuffer.allocateDirect(256 * 1024)
            val bytes = ByteArray(buffer.capacity())
            val seen = mutableSetOf<String>()
            while (true) {
                currentCoroutineContext().ensureActive()
                val header = Archive.readNextHeader(archive)
                if (header == 0L) break
                val entry = nativeEntry(header)
                if (entry.path !in names) { Archive.readDataSkip(archive); continue }
                check(seen.add(entry.path)) { "Duplicate archive entry" }
                progress(entry.path)
                val output = target(entry)
                if (!entry.directory) output.outputStream().buffered().use { stream ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        buffer.clear()
                        Archive.readData(archive, buffer)
                        val count = buffer.position()
                        if (count == 0) break
                        buffer.flip()
                        buffer.get(bytes, 0, count)
                        stream.write(bytes, 0, count)
                        progress(entry.path, count)
                    }
                }
                entryCompleted()
            }
            check(seen == names) { "Archive entries changed" }
        }
        progress("")
        return checkNotNull(destination.listFiles()).toList()
    }

    suspend fun verify(file: File, kind: WorkspaceArchiveKind, password: String) {
        openReader(file, kind, password).use { reader ->
            beginTask(WorkspaceArchiveStage.VERIFYING, reader.entries)
            if (kind == WorkspaceArchiveKind.ZIP) {
                val bytes = ByteArray(256 * 1024)
                for (entry in reader.entries) {
                    currentCoroutineContext().ensureActive()
                    progress(entry.path)
                    if (!entry.directory) {
                        var size = 0L
                        reader.openZipEntry(entry).use { input ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(bytes)
                                if (count < 0) break
                                if (count == 0) continue
                                size = Math.addExact(size, count.toLong())
                                progress(entry.path, count)
                            }
                        }
                        entry.size?.let { check(size == it) { "Archive entry size mismatch" } }
                    }
                    entryCompleted()
                }
            } else readNative(file, password) { archive ->
                val buffer = ByteBuffer.allocateDirect(256 * 1024)
                val expected = reader.entries.associateBy { it.path }
                val seen = mutableSetOf<String>()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val header = Archive.readNextHeader(archive)
                    if (header == 0L) break
                    val entry = nativeEntry(header)
                    if (entry.path.isEmpty()) { Archive.readDataSkip(archive); continue }
                    val indexed = checkNotNull(expected[entry.path]) { "Archive entries changed" }
                    check(seen.add(entry.path) && indexed.directory == entry.directory) { "Archive entries changed" }
                    progress(entry.path)
                    if (!entry.directory) {
                        var size = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            buffer.clear()
                            Archive.readData(archive, buffer)
                            val count = buffer.position()
                            if (count == 0) break
                            size = Math.addExact(size, count.toLong())
                            progress(entry.path, count)
                        }
                        indexed.size?.let { check(size == it) { "Archive entry size mismatch" } }
                    } else Archive.readDataSkip(archive)
                    entryCompleted()
                }
                check(seen == expected.keys) { "Archive entries changed" }
            }
            progress("")
        }
    }
    suspend fun inputs(files: List<File>): List<WorkspaceArchiveInput> {
        val result = mutableListOf<WorkspaceArchiveInput>()
        for (root in files) {
            val pending = ArrayDeque<Pair<File, String>>()
            pending.add(root to workspaceArchivePath(root.name))
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (file, path) = pending.removeLast()
                val node = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                require(!node.isSymbolicLink && (node.isRegularFile || node.isDirectory)) { "Archive sources cannot contain links or special files" }
                result += WorkspaceArchiveInput(file, path, node.isDirectory, node.size(), node.lastModifiedTime().toMillis())
                if (node.isDirectory) Files.newDirectoryStream(file.toPath()).use { stream ->
                    for (child in stream) pending.add(child.toFile() to "$path/${child.fileName}")
                }
            }
        }
        require(result.map { it.path }.distinct().size == result.size) { "Duplicate archive entry" }
        return result
    }
    private fun parameters(name: String, directory: Boolean, password: String, level: Int): ZipParameters = ZipParameters().apply {
        fileNameInZip = name + if (directory) "/" else ""
        compressionMethod = CompressionMethod.DEFLATE
        compressionLevel = when (level) { 0 -> CompressionLevel.FAST; 2 -> CompressionLevel.MAXIMUM; else -> CompressionLevel.NORMAL }
        isEncryptFiles = password.isNotEmpty() && !directory
        if (isEncryptFiles) encryptionMethod = EncryptionMethod.AES
    }
    private suspend fun writeInput(zip: ZipOutputStream, input: WorkspaceArchiveInput, password: String, level: Int, prefix: String = "") {
        currentCoroutineContext().ensureActive()
        val path = workspaceArchivePath(prefix + input.path)
        progress(path)
        zip.putNextEntry(parameters(path, input.directory, password, level).apply { lastModifiedFileTime = input.modified })
        if (!input.directory) {
            val before = Files.readAttributes(input.file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            check(!before.isSymbolicLink && before.size() == input.size && before.lastModifiedTime().toMillis() == input.modified) { "Archive source changed" }
            Files.newInputStream(input.file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { copy(it, zip, path) }
            val after = Files.readAttributes(input.file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            check(after.size() == input.size && after.lastModifiedTime() == before.lastModifiedTime()) { "Archive source changed" }
        }
        zip.closeEntry()
        entryCompleted()
    }
    suspend fun create(file: File, kind: WorkspaceArchiveKind, inputs: List<WorkspaceArchiveInput>, password: String, level: Int) {
        require(inputs.isNotEmpty())
        validateWorkspaceArchiveEntries(inputs.map { WorkspaceArchiveEntry(it.path, it.directory, it.size) })
        beginTask(WorkspaceArchiveStage.WRITING, inputs.map { WorkspaceArchiveEntry(it.path, it.directory, it.size) })
        if (kind == WorkspaceArchiveKind.ZIP) ZipOutputStream(file.outputStream().buffered(), password.toCharArray()).use { zip ->
            for (input in inputs) writeInput(zip, input, password, level)
        } else {
            require(password.isEmpty()) { "Encryption is available for ZIP creation" }
            val archive = Archive.writeNew()
            try {
                Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray(Charsets.UTF_8))
                when (kind) {
                    WorkspaceArchiveKind.SEVEN_Z -> Archive.writeSetFormat7zip(archive)
                    WorkspaceArchiveKind.TAR -> Archive.writeSetFormatPaxRestricted(archive)
                    WorkspaceArchiveKind.TAR_GZIP -> { Archive.writeSetFormatPaxRestricted(archive); Archive.writeAddFilterGzip(archive) }
                    else -> error("Unsupported archive output format")
                }
                Archive.writeOpenFileName(archive, file.absolutePath.toByteArray(Charsets.UTF_8))
                val bytes = ByteArray(256 * 1024)
                for (input in inputs) {
                    currentCoroutineContext().ensureActive()
                    progress(input.path)
                    val entry = ArchiveEntry.new1()
                    try {
                        ArchiveEntry.setPathnameUtf8(entry, input.path)
                        ArchiveEntry.setFiletype(entry, if (input.directory) ArchiveEntry.AE_IFDIR else ArchiveEntry.AE_IFREG)
                        ArchiveEntry.setPerm(entry, if (input.directory) 0x1ed else 0x1a4)
                        ArchiveEntry.setSize(entry, if (input.directory) 0L else input.size)
                        ArchiveEntry.setMtime(entry, input.modified / 1000, (input.modified % 1000) * 1_000_000)
                        Archive.writeHeader(archive, entry)
                        if (!input.directory) {
                            val attributes = Files.readAttributes(input.file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                            check(!attributes.isSymbolicLink && attributes.size() == input.size && attributes.lastModifiedTime().toMillis() == input.modified) { "Archive source changed" }
                            Files.newInputStream(input.file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { source ->
                                var size = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = source.read(bytes)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    val buffer = ByteBuffer.wrap(bytes, 0, count)
                                    while (buffer.hasRemaining()) {
                                        currentCoroutineContext().ensureActive()
                                        val before = buffer.position()
                                        Archive.writeData(archive, buffer)
                                        check(buffer.position() > before) { "Archive writer did not consume input" }
                                    }
                                    size = Math.addExact(size, count.toLong())
                                    progress(input.path, count)
                                }
                                val after = Files.readAttributes(input.file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                                check(!after.isSymbolicLink && size == input.size && after.size() == input.size && after.lastModifiedTime().toMillis() == input.modified) { "Archive source changed" }
                            }
                        }
                        Archive.writeFinishEntry(archive)
                        entryCompleted()
                    } finally { ArchiveEntry.free(entry) }
                }
                Archive.writeClose(archive)
            } finally { Archive.writeFree(archive) }
        }
        progress("")
    }
    /** 原包只读，完整重建到另一个文件后由来源层提交，取消不会改写原包。 */
    suspend fun rewriteZip(source: File, output: File, password: String, remove: Set<String> = emptySet(),
        rename: Pair<String, String>? = null, additions: List<WorkspaceArchiveInput> = emptyList(), prefix: String = "") {
        require(source.canonicalFile != output.canonicalFile) { "Archive rewrite requires a separate output file" }
        val entries = list(source, WorkspaceArchiveKind.ZIP, password)
        remove.forEach { require(workspaceArchivePath(it) == it && it.isNotEmpty()) }
        if (rename != null) {
            val (from, to) = rename
            require(from.isNotEmpty() && to.isNotEmpty() && workspaceArchivePath(from) == from && workspaceArchivePath(to) == to)
            require(to != from && !to.startsWith(from + "/")) { "Invalid archive rename" }
            require(entries.any { it.path == from || it.path.startsWith(from + "/") }) { "Archive entry does not exist" }
            require(entries.none { it.path == to || it.path.startsWith(to + "/") }) { "Archive entry already exists" }
        }
        fun removed(path: String) = remove.any { path == it || path.startsWith("$it/") }
        fun renamed(path: String): String = if (rename != null && (path == rename.first || path.startsWith(rename.first + "/")))
            rename.second + path.removePrefix(rename.first) else path
        val retained = entries.filterNot { removed(it.path) }
        requireWorkspaceArchivePassword(retained, password)
        require(retained.none { it.symbolicLink }) { "Archive links cannot be rewritten" }
        validateWorkspaceArchiveEntries(retained.map { it.copy(path = renamed(it.path)) } + additions.map {
            WorkspaceArchiveEntry(workspaceArchivePath(prefix + it.path), it.directory, it.size)
        })
        beginTask(WorkspaceArchiveStage.WRITING, retained.map { it.copy(path = renamed(it.path)) } + additions.map {
            WorkspaceArchiveEntry(workspaceArchivePath(prefix + it.path), it.directory, it.size)
        })
        ZipFile(source, password.toCharArray()).use { original ->
            ZipOutputStream(output.outputStream().buffered(), password.toCharArray()).use { zip ->
                original.comment?.let { zip.setComment(it) }
                for (header in original.fileHeaders) {
                    currentCoroutineContext().ensureActive()
                    val name = workspaceArchivePath(header.fileName)
                    if (name.isEmpty() || removed(name)) continue
                    val path = renamed(name)
                    progress(path)
                    zip.putNextEntry(parameters(path, header.isDirectory, if (header.isEncrypted) password else "", 1).apply {
                        lastModifiedFileTime = header.lastModifiedTimeEpoch
                        if (header.isEncrypted) encryptionMethod = header.encryptionMethod
                    })
                    if (!header.isDirectory) original.getInputStream(header).use { copy(it, zip, path) }
                    zip.closeEntry().apply {
                        externalFileAttributes = header.externalFileAttributes.copyOf()
                        internalFileAttributes = header.internalFileAttributes.copyOf()
                        versionMadeBy = header.versionMadeBy
                        fileComment = header.fileComment
                    }
                    entryCompleted()
                }
                for (input in additions) writeInput(zip, input, password, 1, prefix)
            }
        }
        verify(output, WorkspaceArchiveKind.ZIP, password)
        progress("")
    }
}