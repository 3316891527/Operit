package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File

/** 小规模扫描语义测试使用内存适配器，生产实现仅持有磁盘索引。 */
internal data class TerminalObservationBaseline(
    val files: Map<String, TerminalObservedFile> = emptyMap(),
    val unreadable: Set<String> = emptySet(),
) : TerminalObservationReader {
    override fun file(path: String): TerminalObservedFile? = files[path]
    override fun isUnreadable(path: String): Boolean = anyObservationAncestor(path, unreadable::contains)
}

internal fun memoryScanTerminalFiles(
    root: File,
    before: TerminalObservationBaseline = TerminalObservationBaseline(),
    paths: Set<String>? = null,
    excluded: (File) -> Boolean = { false },
    byteBudget: Long = 64L * 1024 * 1024,
    onDirectory: (File) -> Unit = {},
    checkCancelled: () -> Unit = {},
    snapshotBytes: (ByteArray) -> ChangeSnapshot,
): TerminalObservationBaseline {
    val selected = selectedObservationPaths(paths).toSet()
    val files = before.files.filterKeys { !anyObservationAncestor(it, selected::contains) }.toMutableMap()
    val unreadable = before.unreadable.filterNot { anyObservationAncestor(it, selected::contains) }.toMutableSet()
    val target = object : TerminalObservationWriter {
        override fun put(path: String, file: TerminalObservedFile) { files[path] = file }
        override fun unreadable(path: String) { unreadable += path }
        override fun preserveSubtree(path: String) {
            before.files.filterKeys { it == path || path.isEmpty() || it.startsWith("$path/") }.forEach { (name, file) -> files[name] = file }
        }
    }
    scanTerminalFiles(root, before, target, paths, excluded, byteBudget, onDirectory, checkCancelled, snapshotBytes)
    return TerminalObservationBaseline(files, unreadable)
}