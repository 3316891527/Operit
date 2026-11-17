package com.ai.assistance.operit.ui.features.conversation.workspace.git

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.PreparedReviewDiff
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceDiff
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorLineIndex
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTextSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceGitBlameDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val uncommitted = GitLineProvenance("/repo", "android", "android", null)
    private fun request(version: Int = 0) = GitBlameRequest(EditorTextSnapshot(EditorLineIndex.fromText("current code\n"), version, Any()), 1)

    @Test fun uncommittedLineHasNoHistoryAction() {
        val selected = request()
        compose.setContent {
            MaterialTheme { WorkspaceGitBlameDialog("/repo/file.kt", selected, { uncommitted }, { error("不应查询未提交内容的历史") }, {}) }
        }
        compose.onNodeWithText(context.getString(R.string.workspace_blame_uncommitted)).assertIsDisplayed()
        compose.onNodeWithText("current code").assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.workspace_blame_show_diff)).assertCountEquals(0)
    }

    @Test fun queryFailureStaysDistinctFromUncommittedAndCanRetry() {
        val calls = AtomicInteger()
        val selected = request()
        compose.setContent {
            MaterialTheme {
                WorkspaceGitBlameDialog("/repo/file.kt", selected, {
                    if (calls.incrementAndGet() == 1) error("specific failure")
                    uncommitted
                }, { error("不应打开历史差异") }, {})
            }
        }
        compose.onNodeWithText("specific failure").assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.workspace_blame_uncommitted)).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.workspace_blame_retry)).performClick()
        compose.onNodeWithText(context.getString(R.string.workspace_blame_uncommitted)).assertIsDisplayed()
        assertEquals(2, calls.get())
    }

    @Test fun closingWhileLoadingCancelsTheQuery() {
        var visible by mutableStateOf(true)
        val started = AtomicBoolean()
        val cancelled = AtomicBoolean()
        val selected = request()
        compose.setContent {
            MaterialTheme {
                if (visible) {
                    WorkspaceGitBlameDialog("/repo/file.kt", selected, {
                        pending(started, cancelled)
                    }, { error("不应打开历史差异") }, { visible = false })
                }
            }
        }
        compose.waitUntil(5000) { started.get() }
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.waitUntil(5000) { cancelled.get() }
        compose.onAllNodesWithText(context.getString(R.string.workspace_blame_title)).assertCountEquals(0)
    }

    @Test fun switchingRequestCancelsOldSnapshotAndDisplaysNewResult() {
        var selected by mutableStateOf(request())
        val started = AtomicBoolean()
        val cancelled = AtomicBoolean()
        compose.setContent {
            MaterialTheme {
                WorkspaceGitBlameDialog("/repo/file.kt", selected, { query ->
                    if (query.snapshot.version == 0) {
                        pending(started, cancelled)
                    } else uncommitted
                }, { error("不应打开历史差异") }, {})
            }
        }
        compose.waitUntil(5000) { started.get() }
        compose.runOnIdle { selected = request(1) }
        compose.waitUntil(5000) { cancelled.get() }
        compose.onNodeWithText(context.getString(R.string.workspace_blame_uncommitted)).assertIsDisplayed()
    }

    @Test fun openingHistoryAndReturningRetainsOriginalProvenance() {
        val selected = request()
        val line = GitBlameLine("a".repeat(40), 3, 1, "Test author", "test@example.invalid", 1700000000L, "+0800",
            "subject", "old/file.kt", "current code", false, "b".repeat(40), "old/file.kt")
        val source = uncommitted.copy(line = line)
        val calls = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                WorkspaceGitBlameDialog("/repo/file.kt", selected, { calls.incrementAndGet(); source }, {
                    GitBlameCommitDiff(source, "subject\n\nFull message", PreparedReviewDiff(WorkspaceDiff.compare("old\n", "new\n"), null, null, 3),
                        "b".repeat(40), "old/file.kt")
                }, {})
            }
        }
        compose.onNodeWithText("Test author\ntest@example.invalid").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.workspace_blame_show_diff)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.workspace_blame_commit_diff)).assertIsDisplayed()
        compose.onNodeWithText("subject\n\nFull message").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText("Test author\ntest@example.invalid").performScrollTo().assertIsDisplayed()
        assertEquals(1, calls.get())
        assertEquals("current code\n", selected.snapshot.toString())
    }
    private suspend fun pending(started: AtomicBoolean, cancelled: AtomicBoolean): Nothing {
        started.set(true)
        try {
            awaitCancellation()
        } finally {
            cancelled.set(true)
        }
    }
}
