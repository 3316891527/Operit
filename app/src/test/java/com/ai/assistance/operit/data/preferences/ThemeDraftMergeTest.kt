package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ThemeDraftMergeTest {
    @Test
    fun unrelatedChangesMergeAcrossEveryValueType() {
        val baseline = ThemePreferenceValues(
            strings = mapOf("font_type" to "system"),
            booleans = mapOf("use_custom_colors" to false),
            ints = mapOf("custom_primary_color" to 1),
            floats = mapOf("font_scale" to 1f),
        )
        val draft = baseline.withFloat("font_scale", 1.2f)
        val current = baseline.withString("font_type", "file")
            .withBoolean("use_custom_colors", true)
            .withInt("custom_primary_color", 2)
        assertEquals(current.withFloat("font_scale", 1.2f), ThemeDraftMerge.merge(baseline, draft, current))
    }

    @Test
    fun noDraftChangesReturnCurrentIncludingNewFields() {
        val baseline = ThemePreferenceValues()
        val current = baseline.withString("background_image_uri", "file:///新背景.png")
            .withBoolean("use_background_image", true)
            .withInt("custom_primary_color", 42)
            .withFloat("background_image_opacity", 0.8f)
        assertEquals(current, ThemeDraftMerge.merge(baseline, baseline, current))
    }

    @Test
    fun matchingConcurrentEditsDoNotConflict() {
        val baseline = ThemePreferenceValues()
        val edited = ThemePreferenceValues(
            strings = mapOf("font_type" to "file"),
            booleans = mapOf("use_custom_colors" to true),
            ints = mapOf("custom_primary_color" to 2),
            floats = mapOf("font_scale" to 1.2f),
        )
        assertEquals(edited, ThemeDraftMerge.merge(baseline, edited, edited))
    }

    @Test
    fun allConflictingFieldsAreReportedWithoutMutatingInputs() {
        val baseline = ThemePreferenceValues(
            strings = mapOf("background_image_uri" to "file:///base.png"),
            booleans = mapOf("use_custom_colors" to false),
            ints = mapOf("custom_primary_color" to 1),
            floats = mapOf("font_scale" to 1f),
        )
        val draft = baseline.withString("background_image_uri", "file:///draft.png")
            .copy(booleans = emptyMap()).withInt("custom_primary_color", 2).withFloat("font_scale", 1.2f)
        val current = baseline.withString("background_image_uri", "file:///command.png")
            .withBoolean("use_custom_colors", true).withInt("custom_primary_color", 3).withFloat("font_scale", 1.3f)
        assertEquals(
            setOf("strings:background_image_uri", "booleans:use_custom_colors", "ints:custom_primary_color", "floats:font_scale"),
            conflicts(baseline, draft, current),
        )
        assertEquals("file:///draft.png", draft.string("background_image_uri"))
        assertEquals("file:///command.png", current.string("background_image_uri"))
    }

    @Test
    fun resourcesAndHiddenScriptUseTheirRealValues() {
        val baseline = ThemePreferenceValues(
            strings = mapOf(
                "background_image_uri" to "file:///old.png",
                ThemePreferenceValues.SCRIPT_URI_KEY to "file:///old-script.json",
            ),
        )
        val draft = baseline.withInt("custom_primary_color", 7)
        val current = baseline.withString("background_image_uri", "file:///new.png")
            .withString(ThemePreferenceValues.SCRIPT_URI_KEY, "file:///new-script.json")
            .withString("custom_font_path", "/private/字体.ttf")
        assertEquals(current.withInt("custom_primary_color", 7), ThemeDraftMerge.merge(baseline, draft, current))
        assertEquals(
            setOf("strings:${ThemePreferenceValues.SCRIPT_URI_KEY}"),
            conflicts(baseline, draft.withString(ThemePreferenceValues.SCRIPT_URI_KEY, null), current),
        )
    }

    @Test
    fun deletionAndConcurrentDeletionAreComparedAsValues() {
        val baseline = ThemePreferenceValues(strings = mapOf("custom_font_path" to "/base.ttf"))
        val removed = baseline.withString("custom_font_path", null)
        assertNull(ThemeDraftMerge.merge(baseline, removed, baseline).string("custom_font_path"))
        assertEquals(removed, ThemeDraftMerge.merge(baseline, removed, removed))
        assertEquals(
            setOf("strings:custom_font_path"),
            conflicts(baseline, baseline.withString("custom_font_path", "/draft.ttf"), removed),
        )
    }

    @Test
    fun concurrentAdditionsConflictWhenDifferent() {
        val baseline = ThemePreferenceValues()
        assertEquals(
            setOf("ints:custom_primary_color"),
            conflicts(baseline, baseline.withInt("custom_primary_color", 1), baseline.withInt("custom_primary_color", 2)),
        )
    }

    @Test
    fun resetPreservesUntouchedCurrentFieldsAndRejectsConflictingResetFields() {
        val baseline = ThemePreferenceValues.defaultVisual().withInt("custom_primary_color", 1)
        val reset = ThemePreferenceValues.defaultVisual()
        val current = baseline.withString("custom_ai_avatar_uri", "file:///avatar.png")
            .withString("custom_chat_title", "新名字").withFloat("font_scale", 1.3f)
        assertEquals(current.withInt("custom_primary_color", null), ThemeDraftMerge.merge(baseline, reset, current))
        assertEquals(
            setOf("ints:custom_primary_color"),
            conflicts(baseline, reset, current.withInt("custom_primary_color", 2)),
        )
    }

    @Test
    fun saveRebaseRetainsOngoingSameFieldEditAndUnrelatedPersistedChanges() {
        val submitted = ThemePreferenceValues().withInt("custom_primary_color", 1)
        val saved = submitted.withInt("custom_primary_color", 2).withString("background_image_uri", "file:///command.png")
        val ongoing = submitted.withInt("custom_primary_color", 3)
        assertEquals(saved.withInt("custom_primary_color", 3), ThemeDraftMerge.rebaseAfterSave(submitted, ongoing, saved))
        assertEquals(saved, ThemeDraftMerge.rebaseAfterSave(submitted, submitted, saved))
    }

    @Test
    fun profileNameMergePreservesCurrentAvatarAndAllowsSameName() {
        val current = mapOf("global_user_name" to "命令名字", "global_user_avatar_uri" to "file:///new-avatar.png")
        assertEquals(current, ThemeDraftMerge.mergeGlobalUserName("原名字", "原名字", current))
        assertEquals(current, ThemeDraftMerge.mergeGlobalUserName("原名字", "命令名字", current))
        assertEquals(
            current + ("global_user_name" to "草稿名字"),
            ThemeDraftMerge.mergeGlobalUserName("命令名字", "草稿名字", current),
        )
    }

    @Test
    fun profileNameConflictRejectsWithoutMutatingProfile() {
        val current = mapOf("global_user_name" to "命令名字", "global_user_avatar_uri" to "file:///new-avatar.png")
        try {
            ThemeDraftMerge.mergeGlobalUserName("原名字", "草稿名字", current)
            fail("Expected profile name conflict")
        } catch (cause: ThemeDraftConflictException) {
            assertEquals(setOf("strings:global_user_name"), cause.fields)
        }
        assertEquals("命令名字", current["global_user_name"])
    }

    @Test
    fun clearingProfileNamePreservesAvatarAndMatchesConcurrentClear() {
        val current = mapOf("global_user_name" to "原名字", "global_user_avatar_uri" to "file:///avatar.png")
        val cleared = current - "global_user_name"
        assertEquals(cleared, ThemeDraftMerge.mergeGlobalUserName("原名字", "   ", current))
        assertEquals(cleared, ThemeDraftMerge.mergeGlobalUserName("原名字", "", cleared))
    }

    private fun conflicts(
        baseline: ThemePreferenceValues,
        draft: ThemePreferenceValues,
        current: ThemePreferenceValues,
    ): Set<String> {
        try {
            ThemeDraftMerge.merge(baseline, draft, current)
            fail("Expected a theme draft conflict")
        } catch (cause: ThemeDraftConflictException) {
            return cause.fields
        }
        error("Unreachable")
    }
}
