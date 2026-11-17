package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceFileMenuDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun disabledArchiveActionsStayVisibleAndKeepTheirPositionsWhenEnabled() {
        var copies = 0
        var properties = 0
        val actions = mutableStateOf(WorkspaceFileActions(extract = true, archive = {}, properties = { properties++ }))
        compose.setContent { MaterialTheme { WorkspaceFileActionMenu("archive", {}, actions.value) } }
        val labels = listOf(R.string.workspace_browser_search, R.string.workspace_archive_extract, R.string.file_manager_copy,
            R.string.file_manager_cut, R.string.file_manager_paste, R.string.file_menu_rename, R.string.workspace_properties_title,
            R.string.workspace_open_with, R.string.workspace_browser_add_bookmark, R.string.workspace_browser_hide,
            R.string.file_menu_share, R.string.file_manager_delete)
        for (label in labels) compose.onNodeWithText(context.getString(label)).assertIsDisplayed()
        val copy = compose.onNodeWithText(context.getString(R.string.file_manager_copy))
        copy.assertIsNotEnabled()
        val before = copy.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(context.getString(R.string.workspace_properties_title)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, properties)
            assertEquals(0, copies)
            actions.value = actions.value.copy(copy = { copies++ })
        }
        copy.assertIsEnabled().performClick()
        assertEquals(before, copy.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(1, copies) }
    }

    @Test fun composeRendersTheImportedGradientVector() {
        compose.setContent { MaterialTheme { Image(painterResource(R.drawable.workspace_file_kotlin), "Kotlin") } }
        compose.onNodeWithContentDescription("Kotlin").assertIsDisplayed()
    }
}