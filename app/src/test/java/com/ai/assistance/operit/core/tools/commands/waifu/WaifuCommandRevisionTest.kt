package com.ai.assistance.operit.core.tools.commands.waifu

import com.ai.assistance.operit.data.preferences.WaifuSettings
import org.junit.Assert.*
import org.junit.Test

class WaifuCommandRevisionTest {
    private val settings = WaifuSettings(
        enabled = true, charDelay = 400, removePunctuation = true, enableEmoticons = true,
        enableSelfie = true, enableMergeSend = true, mergeSendDelayMs = 1500,
        customPrompt = "提示词", selfiePrompt = "自拍词",
    )
    @Test fun revisionIsStableAndHasTheExpectedWireShape() {
        val revision = WaifuCommandRevision.of("card", "one", settings)
        assertTrue(revision.matches(Regex("[0-9a-f]{64}")))
        assertEquals(revision, WaifuCommandRevision.of("card", "one", settings.copy()))
    }
    @Test fun revisionBindsBothTargetTypeAndExactTargetId() {
        val revision = WaifuCommandRevision.of("card", "one", settings)
        assertNotEquals(revision, WaifuCommandRevision.of("group", "one", settings))
        assertNotEquals(revision, WaifuCommandRevision.of("card", "two", settings))
        assertNotEquals(
            WaifuCommandRevision.of("card", "a\nb", settings),
            WaifuCommandRevision.of("card\na", "b", settings),
        )
    }
    @Test fun promptsHaveUnambiguousBoundariesIncludingNewlinesAndNullCharacters() {
        for ((left, right) in listOf(
            settings.copy(customPrompt = "a\nb", selfiePrompt = "c") to settings.copy(customPrompt = "a", selfiePrompt = "b\nc"),
            settings.copy(customPrompt = "a\u0000b", selfiePrompt = "c") to settings.copy(customPrompt = "a", selfiePrompt = "b\u0000c"),
            settings.copy(customPrompt = "中文😀", selfiePrompt = "") to settings.copy(customPrompt = "中文", selfiePrompt = "😀"),
        )) {
            assertNotEquals(WaifuCommandRevision.of("card", "one", left), WaifuCommandRevision.of("card", "one", right))
        }
    }
    @Test fun everyOneOfTheNineSettingsChangesTheRevision() {
        val revision = WaifuCommandRevision.of("card", "one", settings)
        val variants = listOf(
            settings.copy(enabled = false), settings.copy(charDelay = 401),
            settings.copy(removePunctuation = false), settings.copy(enableEmoticons = false),
            settings.copy(enableSelfie = false), settings.copy(enableMergeSend = false),
            settings.copy(mergeSendDelayMs = 1501), settings.copy(customPrompt = "另一个提示词"),
            settings.copy(selfiePrompt = "另一个自拍词"),
        )
        assertEquals(9, variants.size)
        variants.forEach { assertNotEquals(revision, WaifuCommandRevision.of("card", "one", it)) }
    }
}
