package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileOpenException
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileOpenReason
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceChangesActionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = WorkspaceChangeStore.get(context)
    private val chat = "review-action-${UUID.randomUUID()}"
    private var visible by mutableStateOf(true)

    @After fun cleanRecords() {
        compose.runOnIdle { visible = false }
        runBlocking { store.removeRecords(store.records(chatId = chat).map { it.id }.toSet()) }
    }

    @Test fun missingCurrentFileShowsErrorWithoutLeavingHistoricalDiff() {
        seed()
        compose.setContent {
            MaterialTheme {
                if (visible) WorkspaceChangesScreen(chat, null, "android", true, true,
                    onOpenFile = { _, _ -> throw WorkspaceFileOpenException(WorkspaceFileOpenReason.MISSING) }, onPreviewFile = {})
            }
        }
        openEditorAction()
        compose.onNodeWithText(context.getString(R.string.workspace_open_missing), substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_changes_open_with)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_records_manage)).assertIsDisplayed()
    }

    @Test fun deletingSelectedRecordRefreshesTheFileListToEmpty() {
        seed()
        compose.setContent {
            MaterialTheme {
                if (visible) WorkspaceChangesScreen(chat, null, "android", true, true, onOpenFile = { _, _ -> }, onPreviewFile = {})
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription(context.getString(R.string.workspace_records_manage)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_records_manage)).performClick()
        compose.onNodeWithText(context.getString(R.string.workspace_records_remove_one)).performClick()
        compose.onNodeWithText(context.getString(R.string.workspace_records_remove_message, 1)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.delete)).performClick()
        compose.waitUntil(5000) { store.records(chatId = chat).isEmpty() }
        compose.onNodeWithText(context.getString(R.string.workspace_changes_empty_records)).assertIsDisplayed()
    }

    @Test fun leavingTheReviewCancelsPendingOpen() {
        seed()
        val started = AtomicBoolean()
        val cancelled = AtomicBoolean()
        compose.setContent {
            MaterialTheme {
                if (visible) WorkspaceChangesScreen(chat, null, "android", true, true,
                    onOpenFile = { _, _ -> pending(started, cancelled) }, onPreviewFile = {})
            }
        }
        openEditorAction()
        compose.waitUntil(5000) { started.get() }
        compose.runOnIdle { visible = false }
        compose.waitUntil(5000) { cancelled.get() }
        assertTrue(cancelled.get())
    }

    private suspend fun pending(started: AtomicBoolean, cancelled: AtomicBoolean): Nothing {
        started.set(true)
        try { awaitCancellation() } finally { cancelled.set(true) }
    }

    private fun openEditorAction() {
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription(context.getString(R.string.workspace_changes_open_with)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_changes_open_with)).performClick()
        compose.onNodeWithText(context.getString(R.string.workspace_changes_open_editor)).performClick()
    }

    private fun seed() {
        val blob = store.putBlob("historical content\n".toByteArray())
        store.save(listOf(FileChangeRecord(UUID.randomUUID().toString(), UUID.randomUUID().toString(), chat, 100, "create_file",
            ChangeLocation("/missing-${UUID.randomUUID()}.txt", "android", "android"), ChangeSnapshot(SnapshotKind.ABSENT),
            ChangeSnapshot(SnapshotKind.TEXT, blob = blob), additions = 1, deletions = 0)))
    }
}