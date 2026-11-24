package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

/** 外部清理内容对象后索引仍可存在；缺失快照必须标记为不可阅览，不能生成空文件差异。 */
internal class WorkspaceSnapshotReader(private val objects: File) {
    private data class SnapshotText(val kind: SnapshotKind, val text: String?, val cleared: Boolean = false)

    fun readableRecords(records: List<FileChangeRecord>): List<FileChangeRecord> {
        val available = mutableMapOf<String, Boolean>()
        fun present(snapshot: ChangeSnapshot): Boolean {
            val hash = snapshot.blob ?: return true
            return available.getOrPut(hash) { !Files.notExists(File(objects, hash).toPath()) }
        }
        return records.filter { present(it.before) && present(it.after) }
    }

    fun content(before: ChangeSnapshot, after: ChangeSnapshot): ReviewContent {
        val left = text(before)
        val right = text(after)
        val cleared = left.cleared || right.cleared
        return ReviewContent(
            before = left.text, after = right.text, beforeKind = left.kind, afterKind = right.kind,
            detail = if (cleared) null else listOfNotNull(before.detail, after.detail).distinct().joinToString("\n").takeIf { it.isNotEmpty() },
            snapshotsCleared = cleared,
        )
    }

    private fun text(snapshot: ChangeSnapshot): SnapshotText = when (snapshot.kind) {
        SnapshotKind.ABSENT -> SnapshotText(snapshot.kind, "")
        SnapshotKind.TEXT -> {
            val file = File(objects, checkNotNull(snapshot.blob))
            try {
                SnapshotText(snapshot.kind, file.readText(Charsets.UTF_8))
            } catch (error: FileNotFoundException) {
                // 仅接受对象已经被删除的情况；权限、介质和其他读取失败仍交给调用方记录。
                if (!Files.notExists(file.toPath())) throw error
                SnapshotText(SnapshotKind.UNAVAILABLE, null, cleared = true)
            }
        }
        else -> {
            val file = snapshot.blob?.let { File(objects, it) }
            if (file != null && Files.notExists(file.toPath())) SnapshotText(SnapshotKind.UNAVAILABLE, null, cleared = true)
            else SnapshotText(snapshot.kind, null)
        }
    }
}
