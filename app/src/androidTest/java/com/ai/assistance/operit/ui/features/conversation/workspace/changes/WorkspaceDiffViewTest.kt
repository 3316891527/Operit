package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceDiffViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pureAdditionUsesTheAvailableWidthWhileRetainingTheSplitPreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = WorkspaceDiffOptions(split = true)
        val diff = PreparedReviewDiff(WorkspaceDiff.compare("", "new content"), null, null, 11)
        compose.setContent { MaterialTheme { WorkspaceDiffView(diff, Modifier.size(360.dp, 400.dp), options) } }
        compose.onNodeWithText(context.getString(R.string.workspace_changes_added_only)).assertIsDisplayed()
        compose.onNodeWithText("new content").assertIsDisplayed()
        compose.runOnIdle { assertTrue(options.split) }
    }

    @Test fun pureDeletionShowsTheRemovedTextAndRetainsTheSplitPreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = WorkspaceDiffOptions(split = true)
        val diff = PreparedReviewDiff(WorkspaceDiff.compare("removed content", ""), null, null, 15)
        compose.setContent { MaterialTheme { WorkspaceDiffView(diff, Modifier.size(360.dp, 400.dp), options) } }
        compose.onNodeWithText(context.getString(R.string.workspace_changes_deleted_only)).assertIsDisplayed()
        compose.onNodeWithText("removed content").assertIsDisplayed()
        compose.onNodeWithText("−").assertIsDisplayed()
        compose.runOnIdle { assertTrue(options.split) }
    }

    @Test fun expandedContextSurvivesSavedStateRestoration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = WorkspaceDiffOptions()
        val before = (1..20).joinToString("\n") { "line $it" }
        val after = before.replace("line 20", "changed")
        val gap = WorkspaceDiff.items(WorkspaceDiff.compare(before, after), false, false, emptySet())
            .filterIsInstance<ReviewDiffItem.Gap>().single()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val diff = PreparedReviewDiff(WorkspaceDiff.compare(before, after), null, null, 12)
            MaterialTheme { WorkspaceDiffView(diff, Modifier.size(700.dp, 500.dp), options) }
        }
        compose.onNodeWithText(context.getString(R.string.workspace_changes_expand, gap.count)).performClick()
        compose.onNodeWithText("line 1").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("line 1").assertIsDisplayed()
    }

    @Test fun viewControlsAndFileChangesRetainReviewPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = WorkspaceDiffOptions()
        val selected = mutableStateOf(false)
        val first = PreparedReviewDiff(WorkspaceDiff.compare("before", "after"), null, null, 12)
        val second = PreparedReviewDiff(WorkspaceDiff.compare("other before", "other after"), null, null, 12)
        compose.setContent {
            MaterialTheme {
                key(selected.value) {
                    WorkspaceDiffView(if (selected.value) second else first, Modifier.size(700.dp, 400.dp), options)
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_changes_split)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_changes_wrap)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_changes_full)).performClick()
        compose.runOnIdle { selected.value = true }
        compose.onNodeWithText(context.getString(R.string.workspace_changes_before)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.workspace_changes_after)).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(options.split)
            assertTrue(options.wrap)
            assertTrue(options.full)
        }
    }
}