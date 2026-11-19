package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceChangesStateTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = WorkspaceChangeStore.get(context)
    private val chat = "review-state-${UUID.randomUUID()}"
    private val location = ChangeLocation("/$chat/result.kt", "android", "android")
    private var visible by mutableStateOf(true)
    private lateinit var owner: ReviewLifecycleOwner

    @After fun cleanRecords() {
        compose.runOnIdle { visible = false }
        runBlocking { store.removeRecords(store.records(chatId = chat).map { it.id }.toSet()) }
    }

    @Test fun defaultHistorySummaryIncludesDeletionFromAnEarlierOperation() {
        seed("keep\nremoved code\n", "keep\n", 100)
        seed("keep\n", "keep\nadded code\n", 200)
        showScreen()
        waitForText("removed code")
        compose.onNodeWithText("removed code").assertIsDisplayed()
        compose.onNodeWithText("added code").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.workspace_changes_record_scope)).assertIsDisplayed()
    }

    @Test fun singleOperationCanSwitchBackToSummary() {
        seed("", "initial file", 100, "create_file")
        showScreen()
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(100L))
        val summary = context.getString(R.string.workspace_changes_record_summary, time)
        waitForText("initial file")
        compose.onNodeWithText(summary, substring = true).performClick()
        compose.onNodeWithText("create_file", substring = true).performClick()
        compose.onNodeWithText("create_file", substring = true).assertIsDisplayed().performClick()
        compose.onNodeWithText(summary, substring = true).performClick()
        compose.onNodeWithText(summary, substring = true).assertIsDisplayed()
        compose.onNodeWithText("create_file", substring = true).assertDoesNotExist()
        compose.onNodeWithText("initial file").assertIsDisplayed()
    }

    @Test fun resumeKeepsTheManualLoadAndExpandedContext() {
        val before = (1..2100).joinToString("\n") { "line $it" }
        val after = before.replace("line 2100", "changed")
        seed(before, after, 100)
        showScreen()
        loadAndExpand(before, after)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        waitForText("line 1")
        compose.onNodeWithText("line 1").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.workspace_changes_load_diff)).assertDoesNotExist()
    }

    @Test fun changedContentsRequireTheirOwnManualLoadPermission() {
        val before = (1..2100).joinToString("\n") { "line $it" }
        val after = before.replace("line 2100", "first change")
        seed(before, after, 100)
        showScreen()
        loadAndExpand(before, after)
        val next = after.replace("line 2099", "second change")
        seed(after, next, 200)
        waitForText(context.getString(R.string.workspace_changes_load_diff))
        compose.onNodeWithText(context.getString(R.string.workspace_changes_load_diff)).assertIsDisplayed()
    }

    private fun loadAndExpand(before: String, after: String) {
        val load = context.getString(R.string.workspace_changes_load_diff)
        waitForText(load)
        compose.onNodeWithText(load).performClick()
        val gap = WorkspaceDiff.items(WorkspaceDiff.compare(before, after), false, false, emptySet())
            .filterIsInstance<ReviewDiffItem.Gap>().single()
        val expand = context.getString(R.string.workspace_changes_expand, gap.count)
        waitForText(expand)
        compose.onNodeWithText(expand).performClick()
        compose.onNodeWithText("line 1").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun showScreen() {
        compose.runOnUiThread {
            owner = ReviewLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    Box(Modifier.size(800.dp, 600.dp)) {
                        if (visible) WorkspaceChangesScreen(chat, null, "android", true, true,
                            onOpenFile = { _, _ -> }, onPreviewFile = {})
                    }
                }
            }
        }
    }

    private fun seed(before: String, after: String, timestamp: Long, tool: String = "edit_file") {
        val old = if (before.isEmpty()) ChangeSnapshot(SnapshotKind.ABSENT)
            else ChangeSnapshot(SnapshotKind.TEXT, blob = store.putBlob(before.toByteArray(Charsets.UTF_8)))
        store.save(listOf(FileChangeRecord(UUID.randomUUID().toString(), UUID.randomUUID().toString(), chat, timestamp, tool,
            location, old,
            ChangeSnapshot(SnapshotKind.TEXT, blob = store.putBlob(after.toByteArray(Charsets.UTF_8))))))
    }

    private class ReviewLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}