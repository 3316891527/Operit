package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.OpenFileInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceHtmlPreviewAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeFindMatchesRenderedTextAndExcludesHiddenTextAndSourceAttributes() {
        val file = OpenFileInfo("/preview.html", """
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>
            <p data-token="sourceNeedle">shownNeedle</p>
            <p hidden>hiddenNeedle</p><p style="display:none">hiddenNeedle</p>
            <script>window.sourceNeedle = "hiddenNeedle";</script>
            </body></html>
        """.trimIndent(), 0L)
        compose.setContent { MaterialTheme { WorkspaceHtmlPreview(file, true, {}, null) } }
        search("shownNeedle", "1/1")
        search("sourceNeedle", "0/0")
        search("shownNeedle", "1/1")
        search("hiddenNeedle", "0/0")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_search_next)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.workspace_search_previous)).assertIsNotEnabled()
    }

    @Test fun changedFileContentReloadsAndRecountsTheCurrentQuery() {
        val file = mutableStateOf(OpenFileInfo("/preview.html", "<html><body><p>needle</p></body></html>", 0L))
        compose.setContent { MaterialTheme { WorkspaceHtmlPreview(file.value, true, {}, null) } }
        search("needle", "1/1")
        compose.runOnIdle { file.value = file.value.copy(content = "<html><body><p>needle</p><p>needle</p></body></html>") }
        awaitCount("1/2")
    }

    private fun search(query: String, count: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(query)
        awaitCount(count)
    }

    private fun awaitCount(count: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(count).fetchSemanticsNodes().isNotEmpty() }
    }
}
