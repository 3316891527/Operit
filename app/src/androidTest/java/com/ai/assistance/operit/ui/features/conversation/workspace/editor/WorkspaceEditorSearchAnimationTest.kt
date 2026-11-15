package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorSearchAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reversingSearchDuringScrollReachesTheNewMatchAndPreservesScopeAndContent() {
        val text = (1..160).joinToString("\n") { "line-$it" }
        val session = EditorSession(text)
        val scope = EditorSearchScope(0, text.length)
        session.document.searchScope = scope
        val far = text.indexOf("line-120")
        val active = mutableIntStateOf(0)
        val request = mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme {
                CodeEditor(code = text, language = "text", fileKey = "android:/search.txt", fileName = "search.txt", session = session,
                    searchMatches = listOf(0..5, far until far + 8), activeSearchMatch = active.intValue,
                    searchNavigationRequest = request.intValue)
            }
        }
        compose.runOnIdle { active.intValue = 1; request.intValue++ }
        compose.waitUntil(3_000) { compose.runOnIdle { session.scrollY > 0f } }
        compose.runOnIdle { active.intValue = 0; request.intValue++ }
        compose.waitUntil(3_000) { compose.runOnIdle { session.scrollY == 0f && session.status.selectionStart == 0 } }
        compose.runOnIdle {
            assertEquals(text, session.document.textString())
            assertEquals(scope, session.document.searchScope)
            assertEquals(0, session.status.selectionEnd)
        }
    }
}
