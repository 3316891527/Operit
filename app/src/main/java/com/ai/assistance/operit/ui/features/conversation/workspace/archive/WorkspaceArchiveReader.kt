package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import java.io.Closeable
import java.io.File
import java.io.InputStream
import net.lingala.zip4j.ZipFile

/** 会话持有一次解析的中央目录，单文件读取不重新枚举整包。 */
internal class WorkspaceArchiveReader(
    val file: File,
    val kind: WorkspaceArchiveKind,
    val password: String,
    val entries: List<WorkspaceArchiveEntry>,
    private val zip: ZipFile?,
) : Closeable {
    val entriesByPath = entries.associateBy { it.path }
    private val headers = if (zip == null) emptyMap() else zip.fileHeaders.associateBy { workspaceArchivePath(it.fileName) }

    fun openZipEntry(entry: WorkspaceArchiveEntry): InputStream {
        check(kind == WorkspaceArchiveKind.ZIP)
        return checkNotNull(zip).getInputStream(checkNotNull(headers[entry.path]))
    }

    override fun close() { zip?.close() }
}

internal enum class WorkspaceArchiveStage { PREPARING, READING, EXTRACTING, WRITING, VERIFYING, TRANSFERRING, PUBLISHING }

/** 字节对应未压缩数据，条目完成只在实际读取或写入成功后增加。 */
internal data class WorkspaceArchiveProgress(
    val entry: String = "",
    val bytes: Long = 0,
    val total: Long? = null,
    val stage: WorkspaceArchiveStage = WorkspaceArchiveStage.PREPARING,
    val completedEntries: Int = 0,
    val totalEntries: Int? = null,
    val entryBytes: Long = 0,
    val entryTotal: Long? = null,
) {
    val entryFraction: Float?
        get() = entryTotal?.let { if (it == 0L) 0f else (entryBytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }
    val fraction: Float?
        get() {
            val bytesTotal = total
            if (stage == WorkspaceArchiveStage.TRANSFERRING && bytesTotal != null && bytesTotal > 0)
                return (bytes.toDouble() / bytesTotal).toFloat().coerceIn(0f, if (completedEntries < (totalEntries ?: 0)) 0.999f else 1f)
            val count = totalEntries ?: return null
            if (count == 0) return 1f
            val current = entryFraction
            return if (current == null) (completedEntries.toFloat() / count).coerceIn(0f, 1f)
            else ((completedEntries + current) / count).coerceIn(0f, 1f)
        }
}
