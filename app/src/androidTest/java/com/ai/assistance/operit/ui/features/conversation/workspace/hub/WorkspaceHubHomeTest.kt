package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionBrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceHubHomeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun outputsAppearOnlyWithARecordAndDisappearWithoutAnEmptySlot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { Home(path.value) } }
        val title = context.getString(R.string.workspace_outputs_title)
        val terminals = context.getString(R.string.workspace_hub_terminal_work)
        compose.onNodeWithText(title).assertDoesNotExist()
        val before = compose.onNodeWithText(terminals).getUnclippedBoundsInRoot().top
        compose.runOnIdle { path.value = "/work/demo/index.html" }
        compose.onNodeWithText(title).assertIsDisplayed()
        val withOutput = compose.onNodeWithText(terminals).getUnclippedBoundsInRoot().top
        assertTrue(withOutput > before)
        compose.runOnIdle { path.value = null }
        compose.onNodeWithText(title).assertDoesNotExist()
        assertEquals(before, compose.onNodeWithText(terminals).getUnclippedBoundsInRoot().top)
    }

    @Test fun browserSessionRowsRetainTheirOpenAndCloseDestinations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var opened: String? = null
        var closed: String? = null
        val tab = WebSessionBrowserTab("browser-test", "Example", "https://example.com", true, false)
        compose.setContent { MaterialTheme { Home(null, listOf(tab), { opened = it }, { closed = it }) } }
        compose.onNodeWithText("Example").performClick()
        compose.runOnIdle { assertEquals(tab.sessionId, opened) }
        compose.onNodeWithContentDescription(context.getString(R.string.close) + " Example").performClick()
        compose.runOnIdle { assertEquals(tab.sessionId, closed) }
    }

    @androidx.compose.runtime.Composable
    private fun Home(path: String?, tabs: List<WebSessionBrowserTab> = emptyList(),
        open: (String) -> Unit = {}, close: (String) -> Unit = {}) {
        WorkspaceHubHome(currentChat = null, sessions = emptyList(), browserTabs = tabs, outputPath = path,
            onOpenWorkspace = {}, onCreateWorkspace = {}, onSelectWorkspace = {}, onOpenChanges = {}, onOpenOutputs = {},
            onNewTerminal = {}, onTerminal = {}, onCloseTerminal = {}, onNewBrowser = {}, onBrowser = open, onCloseBrowser = close)
    }
}
