package com.ai.assistance.operit.core.agent

import android.os.Process
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.ChangeLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.ChangeSnapshot
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.FileChangeRecord
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.SnapshotKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic

class SubagentReportArtifactsTest {
    private lateinit var processMock: MockedStatic<Process>

    @Before
    fun setUpAndroidProcess() {
        // JVM 没有 Android 进程 UID；仅固定系统边界，路径归一化仍使用真实实现。
        processMock = mockStatic(Process::class.java)
        processMock.`when`<Int> { Process.myUid() }.thenReturn(10_000)
    }

    @After
    fun tearDownAndroidProcess() {
        processMock.close()
    }

    private val absent = ChangeSnapshot(SnapshotKind.ABSENT)
    private fun text(value: String) = ChangeSnapshot(SnapshotKind.TEXT, value.length.toLong(), value)
    private fun record(path: String, execution: String?, before: ChangeSnapshot, after: ChangeSnapshot, time: Long) =
        FileChangeRecord("record-$path-$time", "operation-$time", "root-chat", time, "write_file",
            ChangeLocation(path, "linux", "linux:local"), before, after, subagentExecutionId = execution)

    @Test fun rootChatEvidenceUsesExactExecutionInsteadOfTimeOrSiblingOwnership() {
        val records = listOf(
            record("/own", "current", absent, text("own"), 1),
            record("/sibling", "sibling", absent, text("other"), 2),
            record("/historical", "previous", absent, text("old"), Long.MAX_VALUE),
            record("/unknown", null, absent, text("unknown"), 4),
        )
        assertEquals(listOf("/own"), subagentProducedFiles(records, "current").map { it.path })
    }

    @Test fun creatingThenDeletingAFileDoesNotClaimPreservedOutput() {
        val records = listOf(record("/removed", "current", absent, text("created"), 1),
            record("/removed", "current", text("created"), absent, 2))
        assertTrue(subagentProducedFiles(records, "current").isEmpty())
    }

    @Test fun modificationThenReversionDoesNotClaimNewOutput() {
        val records = listOf(record("/restored", "current", text("original"), text("changed"), 1),
            record("/restored", "current", text("changed"), text("original"), 2))
        assertTrue(subagentProducedFiles(records, "current").isEmpty())
    }

    @Test fun multipleWritesUseFinalStateAndDoNotDuplicateThePath() {
        val records = listOf(record("/script.sh", "current", text("first"), text("final"), 2),
            record("/script.sh", "current", absent, text("first"), 1))
        assertEquals(listOf("/script.sh"), subagentProducedFiles(records, "current").map { it.path })
    }

    @Test fun unreadableSnapshotsAndReadOnlyRecordsAreNotOutputs() {
        val records = listOf(record("/unavailable", "current", absent, ChangeSnapshot(SnapshotKind.UNAVAILABLE), 1),
            record("/read", "current", text("same"), text("same"), 2))
        assertTrue(subagentProducedFiles(records, "current").isEmpty())
    }
}