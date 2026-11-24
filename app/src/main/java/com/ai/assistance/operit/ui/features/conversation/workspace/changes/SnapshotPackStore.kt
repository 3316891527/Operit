package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/** 对象内容仍以 SHA-256 定位，位置索引与 objects 分开保存，清理内容不会损坏索引文件。 */
internal class SnapshotPackStore(
    private val objects: File,
    private val syncDirectory: (File) -> Unit = { directory -> FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } },
    private val openDatabase: () -> TerminalIndexDatabase,
) : Closeable {
    companion object {
        const val TARGET_BYTES = 1024 * 1024
        const val MAX_OBJECT_BYTES = 2 * 1024 * 1024
        const val VOLUME_HEADER = 16
        const val RECORD_HEADER = 40
        private const val VOLUME_MAGIC = 0x4f504b32
        private const val RECORD_MAGIC = 0x424c4f42
        private val HASH = Regex("[0-9a-f]{64}")
        private val VOLUME = Regex("[0-9a-f-]{36}\\.pack")

        fun hash(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        private fun hex(bytes: ByteArray): String {
            val alphabet = "0123456789abcdef"
            return CharArray(bytes.size * 2).also { chars ->
                bytes.forEachIndexed { index, byte ->
                    val value = byte.toInt() and 255
                    chars[index * 2] = alphabet[value ushr 4]
                    chars[index * 2 + 1] = alphabet[value and 15]
                }
            }.concatToString()
        }
        private fun digest(hash: String) = ByteArray(32) { index ->
            ((hash[index * 2].digitToInt(16) shl 4) or hash[index * 2 + 1].digitToInt(16)).toByte()
        }
    }

    private data class Writer(val name: String, val file: File, val stream: RandomAccessFile, var committed: Long, var registered: Boolean = true)
    private var database: TerminalIndexDatabase? = null
    private var index: SnapshotPackIndex? = null
    private var writer: Writer? = null
    private var recovered = false
    private var closed = false

    private fun index(): SnapshotPackIndex {
        check(!closed) { "Snapshot store is closed" }
        index?.let { return it }
        val database = openDatabase()
        return try {
            SnapshotPackIndex(database).also { this.database = database; index = it }
        } catch (error: Exception) { database.close(); throw error }
    }

    /** 索引仅发布已同步的完整记录；重启只删除孤立卷并截断未提交尾部。 */
    private fun recover() {
        if (recovered) return
        val index = index()
        var after = ""
        while (true) {
            val page = index.volumes(after)
            if (page.isEmpty()) break
            for (volume in page) {
                val file = File(objects, volume.name)
                if (Files.notExists(file.toPath())) { index.missingVolume(volume.name); continue }
                try {
                    RandomAccessFile(file, "r").use { stream ->
                        validateHeader(stream)
                        if (stream.length() < volume.committed) throw IOException("Committed snapshot volume is truncated: ${volume.name}")
                        if (stream.length() > volume.committed) FileChannel.open(file.toPath(), StandardOpenOption.WRITE).use { channel ->
                            channel.truncate(volume.committed)
                            channel.force(true)
                        }
                    }
                } catch (error: IOException) {
                    if (!Files.notExists(file.toPath())) throw error
                    index.missingVolume(volume.name)
                }
            }
            after = page.last().name
        }
        if (!Files.notExists(objects.toPath())) Files.newDirectoryStream(objects.toPath()).use { paths ->
            for (path in paths) {
                val name = path.fileName.toString()
                if (VOLUME.matches(name) && index.volume(name) == null) Files.deleteIfExists(path)
            }
        }
        recovered = true
    }

    @Synchronized
    fun contains(hash: String): Boolean {
        require(HASH.matches(hash))
        recover()
        val entry = index().entry(hash)
        return if (entry != null) entry.live && !Files.notExists(File(objects, entry.volume).toPath())
        else !Files.notExists(File(objects, hash).toPath())
    }

    @Synchronized
    fun read(hash: String): ByteArray? {
        require(HASH.matches(hash))
        recover()
        val entry = index().entry(hash)
        if (entry != null) return if (entry.live) readEntry(entry) else null
        // 这是尚未迁移的 v1 对象，已索引、清理或损坏的 v2 内容不再从旧副本读取。
        return readLegacy(hash)
    }

    private fun readLegacy(hash: String): ByteArray? {
        val file = File(objects, hash)
        return missingRead(file) {
            if (file.length() > MAX_OBJECT_BYTES) throw IOException("Snapshot exceeds the object limit: $hash")
            RandomAccessFile(file, "r").use { stream ->
                val length = stream.length()
                if (length > MAX_OBJECT_BYTES) throw IOException("Snapshot exceeds the object limit: $hash")
                ByteArray(length.toInt()).also { bytes ->
                    stream.readFully(bytes)
                    if (hash(bytes) != hash) throw IOException("Legacy snapshot checksum mismatch: $hash")
                }
            }
        }
    }

    private fun readEntry(entry: SnapshotPackEntry): ByteArray? {
        val file = File(objects, entry.volume)
        return missingRead(file) {
            RandomAccessFile(file, "r").use { stream ->
                validateHeader(stream)
                if (entry.length !in 0..MAX_OBJECT_BYTES || entry.offset < VOLUME_HEADER) throw IOException("Invalid snapshot index: ${entry.hash}")
                stream.seek(entry.offset)
                if (stream.readInt() != RECORD_MAGIC || stream.readInt() != entry.length) throw IOException("Invalid snapshot record: ${entry.hash}")
                val digest = ByteArray(32).also(stream::readFully)
                if (hex(digest) != entry.hash) throw IOException("Snapshot identity mismatch: ${entry.hash}")
                ByteArray(entry.length).also { bytes ->
                    stream.readFully(bytes)
                    if (hash(bytes) != entry.hash) throw IOException("Snapshot checksum mismatch: ${entry.hash}")
                }
            }
        }
    }

    private inline fun <T> missingRead(file: File, read: () -> T): T? = try { read() } catch (error: FileNotFoundException) {
        if (!Files.notExists(file.toPath())) throw error
        null
    }

    @Synchronized
    fun put(bytes: ByteArray): String {
        require(bytes.size <= MAX_OBJECT_BYTES) { "Snapshot exceeds the object limit" }
        recover()
        val hash = hash(bytes)
        val existing = index().entry(hash)
        if (existing?.live == true && !Files.notExists(File(objects, existing.volume).toPath())) return hash
        append(hash, bytes)
        return hash
    }

    private fun append(hash: String, bytes: ByteArray) {
        val index = index()
        try {
            val writer = writable(RECORD_HEADER + bytes.size)
            val offset = writer.committed
            writer.stream.seek(offset)
            writeRecord(writer.stream, hash, bytes)
            writer.stream.fd.sync()
            // 新卷的头部与首条内容一起同步，再持久化目录项并用同一事务发布卷和对象。
            if (!writer.registered) syncDirectory(objects)
            // 外部清理可能在打开句柄之后发生；不能把已取消链接的卷发布为可读对象。
            if (Files.notExists(writer.file.toPath())) throw FileNotFoundException("Snapshot volume was cleared during append")
            val end = writer.stream.filePointer
            val sealed = end >= TARGET_BYTES
            index.commit(SnapshotPackEntry(hash, writer.name, offset, bytes.size, true), end, sealed, createVolume = !writer.registered)
            writer.registered = true
            writer.committed = end
            if (sealed) closeWriter()
        } catch (error: Exception) {
            closeWriter()
            recovered = false
            // 提交结果不明确时只关闭句柄，由下次恢复依据持久索引决定尾部边界。
            throw error
        }
    }

    private fun writable(recordSize: Int): Writer {
        val index = index()
        var current = writer
        if (current != null && Files.notExists(current.file.toPath())) {
            closeWriter()
            index.missingVolume(current.name)
            current = null
        }
        if (current == null) {
            val active = index.active()
            if (active != null) {
                val file = File(objects, active.name)
                if (Files.notExists(file.toPath())) index.missingVolume(active.name)
                else current = Writer(active.name, file, RandomAccessFile(file, "rw"), active.committed).also { writer = it }
            }
        }
        if (current != null && current.committed + recordSize > TARGET_BYTES) {
            index.seal(current.name)
            closeWriter()
            current = null
        }
        if (current == null) {
            current = newVolume().also { writer = it }
        }
        return current
    }

    private fun newVolume(): Writer {
        val createdDirectory = Files.notExists(objects.toPath())
        Files.createDirectories(objects.toPath())
        if (createdDirectory) syncDirectory(checkNotNull(objects.parentFile))
        val name = UUID.randomUUID().toString() + ".pack"
        val file = File(objects, name)
        Files.createFile(file.toPath())
        val stream = RandomAccessFile(file, "rw")
        try {
            stream.writeInt(VOLUME_MAGIC); stream.writeInt(2); stream.writeLong(0)
            return Writer(name, file, stream, VOLUME_HEADER.toLong(), registered = false)
        } catch (error: Exception) { stream.close(); throw error }
    }

    private fun validateHeader(stream: RandomAccessFile) {
        stream.seek(0)
        if (stream.readInt() != VOLUME_MAGIC || stream.readInt() != 2 || stream.readLong() != 0L) throw IOException("Unsupported snapshot volume header")
    }

    private fun writeRecord(stream: RandomAccessFile, hash: String, bytes: ByteArray) {
        stream.writeInt(RECORD_MAGIC); stream.writeInt(bytes.size); stream.write(digest(hash)); stream.write(bytes)
    }

    /** 每次只读一个旧对象；提交和核对完成后才移除原文件，取消不会发布半条记录。 */
    fun migrateLegacy(checkCancelled: () -> Unit = {}, onBatch: () -> Unit = {}) {
        synchronized(this) { recover() }
        if (Files.notExists(objects.toPath())) return
        var batch = 0
        Files.newDirectoryStream(objects.toPath()).use { paths ->
            for (path in paths) {
                val hash = path.fileName.toString()
                if (!HASH.matches(hash)) continue
                checkCancelled()
                synchronized(this) {
                    val known = index().entry(hash)
                    if (known == null) {
                        val bytes = readLegacy(hash)
                        if (bytes != null) {
                            append(hash, bytes)
                            if (!checkNotNull(read(hash)).contentEquals(bytes)) throw IOException("Snapshot migration verification failed: $hash")
                        }
                    } else if (known.live && !Files.notExists(File(objects, known.volume).toPath())) {
                        // 中断后可能已经发布新位置，但尚未移除旧文件，删除前仍校验已提交内容。
                        readEntry(known)
                    }
                    checkCancelled()
                    Files.deleteIfExists(path)
                }
                if (++batch == 64) { onBatch(); batch = 0 }
            }
        }
    }

    /** 调用方已核对操作、工作区基线和终端基线引用，卷内对象先标记删除。 */
    @Synchronized
    fun remove(hashes: Set<String>) {
        if (hashes.isEmpty()) return
        require(hashes.all(HASH::matches))
        recover()
        for (hash in hashes) Files.deleteIfExists(File(objects, hash).toPath())
        index().remove(hashes)
        writer?.let { index().seal(it.name); closeWriter() }
    }

    /** 一轮最多处理指定字节数，逐对象复制，内存不随历史对象总数增长。 */
    @Synchronized
    fun compact(byteBudget: Long = 4L * TARGET_BYTES, checkCancelled: () -> Unit = {}): Long {
        recover()
        val index = index()
        var consumed = 0L
        var after = ""
        while (true) {
            val page = index.volumes(after)
            if (page.isEmpty()) break
            for (volume in page) {
                checkCancelled()
                if (!volume.sealed || volume.dead == 0L || volume.dead * 2 < volume.committed - VOLUME_HEADER) continue
                if (consumed + volume.committed > byteBudget) continue
                val file = File(objects, volume.name)
                if (Files.notExists(file.toPath())) { index.missingVolume(volume.name); continue }
                if (index.liveEntries(volume.name).isEmpty()) {
                    index.missingVolume(volume.name)
                    Files.deleteIfExists(file.toPath())
                    consumed += volume.committed
                    continue
                }
                val replacement = newVolume()
                try {
                    var cursor = ""
                    while (true) {
                        val entries = index.liveEntries(volume.name, cursor)
                        if (entries.isEmpty()) break
                        for (entry in entries) {
                            checkCancelled()
                            val bytes = readEntry(entry) ?: throw FileNotFoundException("Snapshot volume was cleared during compaction")
                            writeRecord(replacement.stream, entry.hash, bytes)
                        }
                        cursor = entries.last().hash
                    }
                    val end = replacement.stream.filePointer
                    replacement.stream.fd.sync()
                    syncDirectory(objects)
                    checkCancelled()
                    if (Files.notExists(replacement.file.toPath())) throw FileNotFoundException("Snapshot replacement volume was cleared")
                    index.replaceVolume(volume.name, SnapshotPackVolume(replacement.name, end, 0, true)) {
                        var offset = VOLUME_HEADER.toLong()
                        var cursor = ""
                        while (true) {
                            val entries = index.liveEntries(volume.name, cursor)
                            if (entries.isEmpty()) break
                            for (entry in entries) {
                                index.relocate(entry, replacement.name, offset)
                                offset += RECORD_HEADER + entry.length
                            }
                            cursor = entries.last().hash
                        }
                    }
                    Files.deleteIfExists(file.toPath())
                    consumed += volume.committed
                } catch (error: Exception) { closeWriter(); recovered = false; throw error }
                finally { replacement.stream.close() }
            }
            after = page.last().name
        }
        return consumed
    }

    private fun closeWriter() { writer?.stream?.close(); writer = null }

    @Synchronized
    override fun close() {
        if (closed) return
        closeWriter()
        database?.close()
        closed = true
    }
}