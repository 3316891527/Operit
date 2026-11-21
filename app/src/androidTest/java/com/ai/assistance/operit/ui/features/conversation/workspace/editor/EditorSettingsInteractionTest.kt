package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorSettingsInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedToggleClicksKeepTheSettingAndSwitchInSync() {
        val settings = mutableStateOf(EditorSettings())
        compose.setContent {
            MaterialTheme { EditorSettingsScreen(settings.value, { transform -> settings.value = transform(settings.value) }, {}) }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val row = compose.onNode(hasText(context.getString(R.string.workspace_browser_word_wrap)) and hasClickAction())
        val toggle = compose.onNode(hasText(context.getString(R.string.workspace_browser_word_wrap)) and isToggleable())
        repeat(4) { index ->
            row.performClick()
            compose.runOnIdle { assertEquals(index % 2 != 0, settings.value.wordWrap) }
            if (index % 2 == 0) toggle.assertIsOff() else toggle.assertIsOn()
        }
    }
}