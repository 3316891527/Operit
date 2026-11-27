package com.ai.assistance.operit.ui.features.management.settings.screens.theme

import com.ai.assistance.operit.data.preferences.ThemeDraftConflictException
import com.ai.assistance.operit.data.preferences.ThemeDraftMerge
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ThemeEditorSessionMergeTest {
    private val initial = ThemePreferenceValues.defaultVisual()
    private val initialLayout = ChatLayoutSettings()

    private fun session(values: ThemePreferenceValues = initial): ThemeEditorSession {
        val preferences = mock<UserPreferencesManager>()
        whenever(preferences.recentColorsFlow).thenReturn(flowOf(emptyList()))
        // 默认不注册素材，模型回归无需访问真实文件。
        return ThemeEditorSession(preferences, values, "原名字", initialLayout)
    }

    @Test
    fun actualMergedThemeBecomesCleanDraftAndBaseline() {
        val session = session()
        session.setInt("custom_primary_color", 1)
        val submitted = session.currentValues
        val merged = submitted.withString("background_image_uri", "file:///command.png")
            .withString(ThemePreferenceValues.SCRIPT_URI_KEY, "file:///command-script.json")
        session.beginSave(submitted)
        session.markSaved(merged, "原名字", initialLayout)
        assertEquals(merged, session.currentValues)
        assertEquals(merged, session.currentBaselineValues)
        assertFalse(session.hasUnsavedChanges)
    }

    @Test
    fun ongoingEditsSurviveSaveAndNextSaveOnlyWritesThoseChanges() {
        val session = session()
        session.setInt("custom_primary_color", 1)
        val submitted = session.currentValues
        session.beginSave(submitted)
        session.setInt("custom_primary_color", 2)
        session.setFloat("font_scale", 1.3f)
        val merged = submitted.withString("background_image_uri", "file:///first-command.png")
        session.markSaved(merged, "原名字", initialLayout)
        assertEquals(merged.withInt("custom_primary_color", 2).withFloat("font_scale", 1.3f), session.currentValues)
        assertTrue(session.hasThemeChanges)
        val latest = merged.withString("background_image_uri", "file:///second-command.png")
        assertEquals(
            latest.withInt("custom_primary_color", 2).withFloat("font_scale", 1.3f),
            ThemeDraftMerge.merge(session.currentBaselineValues, session.currentValues, latest),
        )
    }

    @Test
    fun ongoingEditToExternallyMergedFieldStaysDirty() {
        val session = session()
        val submitted = session.currentValues
        session.beginSave(submitted)
        session.setInt("custom_primary_color", 3)
        val merged = submitted.withInt("custom_primary_color", 2)
        session.markSaved(merged, "原名字", initialLayout)
        assertEquals(3, session.currentValues.int("custom_primary_color"))
        assertEquals(2, session.currentBaselineValues.int("custom_primary_color"))
        assertTrue(session.hasUnsavedChanges)
    }

    @Test
    fun conflictingSaveKeepsDraftBaselineAndResetIntent() {
        val baseline = initial.withInt("custom_primary_color", 1)
        val session = session(baseline)
        session.reset()
        val submitted = session.currentValues
        session.beginSave(submitted)
        try {
            ThemeDraftMerge.merge(baseline, submitted, baseline.withInt("custom_primary_color", 2))
            fail("Expected conflict")
        } catch (cause: ThemeDraftConflictException) {
            assertEquals(setOf("ints:custom_primary_color"), cause.fields)
            session.cancelSave()
        }
        assertEquals(baseline, session.currentBaselineValues)
        assertEquals(submitted, session.currentValues)
        assertTrue(session.isResetRequested)
        assertTrue(session.hasUnsavedChanges)
    }

    @Test
    fun resetKeepsRoleIdentityAndMergesNewIdentityValues() {
        val baseline = initial.withString("custom_ai_avatar_uri", "file:///old-avatar.png")
            .withString("custom_chat_title", "原角色").withFloat("font_scale", 1.2f)
        val session = session(baseline)
        session.reset()
        assertEquals("file:///old-avatar.png", session.currentValues.string("custom_ai_avatar_uri"))
        assertEquals("原角色", session.currentValues.string("custom_chat_title"))
        val current = baseline.withString("custom_ai_avatar_uri", "file:///new-avatar.png")
            .withString("custom_chat_title", "新角色")
        val submitted = session.currentValues
        val merged = ThemeDraftMerge.merge(baseline, submitted, current)
        session.beginSave(submitted)
        session.markSaved(merged, "原名字", initialLayout)
        assertEquals("file:///new-avatar.png", session.currentValues.string("custom_ai_avatar_uri"))
        assertEquals("新角色", session.currentValues.string("custom_chat_title"))
        assertFalse(session.hasUnsavedChanges)
    }

    @Test
    fun resetRequestedDuringSaveIsStillPending() {
        val session = session()
        val submitted = session.currentValues
        session.beginSave(submitted)
        session.reset()
        session.markSaved(submitted, "原名字", initialLayout)
        assertTrue(session.isResetRequested)
        assertTrue(session.hasUnsavedChanges)
    }

    @Test
    fun savedThemeBaselineSurvivesLaterGlobalSaveOrPropagationFailure() {
        val session = session()
        session.setInt("custom_primary_color", 1)
        session.setGlobalUserName("新名字")
        val submitted = session.currentValues
        session.beginSave(submitted)
        val merged = submitted.withString("background_image_uri", "file:///command.png")
        session.markThemeSaved(merged)
        session.cancelSave()
        assertEquals(merged, session.currentBaselineValues)
        assertFalse(session.hasThemeChanges)
        assertTrue(session.hasGlobalUserNameChanges)
    }

    @Test
    fun savedAssetLeavesStagingBeforeDisposedSessionCancelsSave() {
        val session = session()
        val uri = "file:///saved-background.png"
        session.registerStagedAsset(uri)
        session.setString("background_image_uri", uri)
        val submitted = session.currentValues
        session.beginSave(submitted)
        session.setString("background_image_uri", "file:///ongoing-background.png")
        session.dispose()
        // 保存回调移交已持久素材的归属，随后取消不应再进入 Android 素材删除流程。
        session.markThemeSaved(submitted)
        session.cancelSave()
        assertEquals(uri, session.currentBaselineValues.string("background_image_uri"))
        assertEquals("file:///ongoing-background.png", session.currentValues.string("background_image_uri"))
        assertTrue(session.hasThemeChanges)
    }

    @Test
    fun ongoingGlobalNameEditSurvivesPartialAndCompleteSave() {
        val session = session()
        session.setGlobalUserName("提交名字")
        session.beginSave(session.currentValues)
        session.setGlobalUserName("继续输入")
        session.markGlobalUserNameSaved("提交名字", "提交名字")
        session.markSaved(session.currentValues, "提交名字", initialLayout)
        assertEquals("提交名字", session.currentBaselineGlobalUserName)
        assertEquals("继续输入", session.currentGlobalUserName)
        assertTrue(session.hasGlobalUserNameChanges)
    }

    @Test
    fun savedGlobalNameBaselineSurvivesLaterLayoutFailure() {
        val session = session()
        session.setGlobalUserName("新名字")
        session.beginSave(session.currentValues)
        session.markGlobalUserNameSaved("新名字", "新名字")
        session.cancelSave()
        assertEquals("新名字", session.currentBaselineGlobalUserName)
        assertFalse(session.hasGlobalUserNameChanges)
    }

    @Test
    fun blankNameUsesActuallyPersistedValue() {
        val session = session()
        session.setGlobalUserName("   ")
        session.beginSave(session.currentValues)
        session.markGlobalUserNameSaved("   ", "")
        session.markSaved(session.currentValues, "", initialLayout)
        assertEquals("", session.currentGlobalUserName)
        assertEquals("", session.currentBaselineGlobalUserName)
        assertFalse(session.hasUnsavedChanges)
    }
}
