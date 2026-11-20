package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnhancedCodeBlockAndroidTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyAndLongLanguageLabelsKeepActionsAtTheSameRightEdge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(300.dp)) {
                    EnhancedCodeBlock(code = "plain")
                    EnhancedCodeBlock(code = "typed", language = "kotlin")
                    EnhancedCodeBlock(code = "long label", language = "very-long-language-name".repeat(12))
                }
            }
        }
        val copies = compose.onAllNodesWithContentDescription(context.getString(R.string.common_copy_code))
            .fetchSemanticsNodes()
        val wraps = compose.onAllNodesWithContentDescription(context.getString(R.string.common_disable_autowrap))
            .fetchSemanticsNodes()
        assertEquals(3, copies.size)
        assertEquals(3, wraps.size)
        for (index in 1..2) {
            assertEquals(copies[0].boundsInRoot.right, copies[index].boundsInRoot.right, .5f)
            assertEquals(wraps[0].boundsInRoot.right, wraps[index].boundsInRoot.right, .5f)
        }
    }

    @Test
    fun streamingMermaidOpensPreviewOnlyAfterFenceClosesAndCanReturnToCode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val closed = mutableStateOf(false)
        val renderDescription = context.getString(R.string.common_render_mermaid)
        val codeDescription = context.getString(R.string.common_show_code)
        val fullscreenDescription = context.getString(R.string.common_fullscreen_preview)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMarkdownRenderMode provides MarkdownRenderMode.STREAMING) {
                    EnhancedCodeBlock(code = "flowchart LR\nA --> B", language = "mermaid", closed = closed.value)
                }
            }
        }
        compose.onNodeWithContentDescription(renderDescription).assertIsNotEnabled()
        compose.onNodeWithContentDescription(fullscreenDescription).assertDoesNotExist()
        compose.runOnIdle { closed.value = true }
        compose.onNodeWithContentDescription(codeDescription).assertIsEnabled()
        compose.onNodeWithContentDescription(fullscreenDescription).assertIsEnabled()
        compose.onNodeWithContentDescription(codeDescription).performClick()
        compose.onNodeWithContentDescription(renderDescription).assertIsEnabled()
        compose.onNodeWithContentDescription(fullscreenDescription).assertDoesNotExist()
    }
}
