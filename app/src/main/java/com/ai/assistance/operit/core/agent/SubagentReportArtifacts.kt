package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.ui.features.conversation.workspace.changes.ChangeLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.FileChangeRecord
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.SnapshotKind
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.historyLocation

/** 以真实执行身份取证，不能把根聊天中兄弟任务、旧执行或稍晚落盘的记录当成本次产出。 */
internal fun subagentProducedFiles(records: List<FileChangeRecord>, executionId: String): List<ChangeLocation> =
    records.filter { it.subagentExecutionId == executionId }
        .groupBy { it.location.historyLocation().key }.values.mapNotNull { changes ->
            val ordered = changes.sortedBy { it.timestamp }
            val first = ordered.first()
            val last = ordered.last()
            // 创建后删除、改完又还原都没有保留下来的新产出。
            if (first.before == last.after || last.after.kind !in
                setOf(SnapshotKind.TEXT, SnapshotKind.BINARY, SnapshotKind.TOO_LARGE, SnapshotKind.DIRECTORY)) null
            else last.location
        }
