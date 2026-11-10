package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ThemePageBackgroundsTest {
    @Test
    fun independentPagePaintWinsAndAnEmptyPageFollowsTheChat() {
        val paint = ThemeScriptSurface(fillColor = "#123456")
        assertEquals("page.packages", pageBackgroundSource("page.packages", paint, paint))
        assertEquals(CHAT_PAGE_BACKGROUND_ID, pageBackgroundSource("page.packages", ThemeScriptSurface(), paint))
        assertNull(pageBackgroundSource("page.packages", null, null))
        assertNull(pageBackgroundSource(CHAT_PAGE_BACKGROUND_ID, ThemeScriptSurface(), ThemeScriptSurface()))
    }

    @Test
    fun independentLayersRemainValidPagePaint() {
        val page = ThemeScriptSurface(layers = listOf(ThemeScriptLayer("rect", color = "#123456")))
        assertEquals("page.packages", pageBackgroundSource("page.packages", page, ThemeScriptSurface(fillColor = "#654321")))
    }

    @Test
    fun bulkFollowRemovesAllPageOverridesInEveryModeAndPreservesChatAndOtherComponents() {
        val paint = ThemeScriptSurface(fillColor = "#123456")
        val surfaces = (ThemeStyleTargets.pageIds + setOf("background", "button")).associateWith { paint }
        val appearance = ThemeScriptAppearance(1, surfaces = surfaces,
            modes = mapOf("light" to ThemeAppearanceMode(surfaces = surfaces), "dark" to ThemeAppearanceMode(surfaces = surfaces)))
        val result = appearance.followChatBackgrounds()
        assertEquals(setOf(CHAT_PAGE_BACKGROUND_ID, "background", "button"), result.surfaces.keys)
        result.modes.values.forEach { assertEquals(result.surfaces, it.surfaces) }
        assertEquals(surfaces, appearance.surfaces)
        assertEquals(result, result.followChatBackgrounds())
    }
    @Test
    fun everyPageRetainsIndependentGradientsInBothModesAfterJsonRoundTrip() {
        val gradient = ThemeGradient(stops = listOf(ThemeGradientStop(0f, "#123456"), ThemeGradientStop(1f, "#654321")))
        val surfaces = ThemeStyleTargets.pageIds.associateWith { ThemeScriptSurface(fillGradient = gradient) }
        val before = ThemeScriptAppearance(1, modes = mapOf("light" to ThemeAppearanceMode(surfaces = surfaces), "dark" to ThemeAppearanceMode(surfaces = surfaces)))
        val json = ThemeScriptContract.json
        val after = json.decodeFromString<ThemeScriptAppearance>(json.encodeToString(before))
        ThemeScriptContract.validate(after, ThemeScriptManifest(1, "assets/main.js"))
        listOf("light", "dark").forEach { mode ->
            val active = after.forMode(mode)
            ThemeStyleTargets.pageIds.forEach { id ->
                val page = active.surfaces.getValue(id)
                assertEquals(gradient, page.fillGradient)
                assertEquals(id, pageBackgroundSource(id, page, active.surfaces[CHAT_PAGE_BACKGROUND_ID]))
            }
        }
    }

}
