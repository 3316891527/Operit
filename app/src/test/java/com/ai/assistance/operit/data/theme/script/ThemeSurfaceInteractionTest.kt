package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ThemeSurfaceInteractionTest {
    private val manifest = ThemeScriptManifest(1, "assets/main.js")
    private fun validate(id: String, surface: ThemeScriptSurface) = ThemeScriptContract.validate(ThemeScriptAppearance(1, surfaces = mapOf(id to surface)), manifest)

    @Test
    fun transitionBoundaryValuesAndNativeRippleAreValidForEveryInteractiveTarget() {
        ThemeScriptContract.interactiveSurfaceIds.forEach { id ->
            listOf(0, 150, 1000).forEach { duration ->
                validate(id, ThemeScriptSurface(transition = ThemeStateTransition(duration), ripple = ThemeRipple(color = "#80FFFFFF")))
            }
        }
    }

    @Test
    fun rejectsInvalidDurationsColorsAndNonInteractiveTargets() {
        listOf(-1, 1001).forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) { validate("button", ThemeScriptSurface(transition = ThemeStateTransition(duration))) }
        }
        assertThrows(IllegalArgumentException::class.java) { validate("button", ThemeScriptSurface(ripple = ThemeRipple(color = "red"))) }
        listOf("background", "page.packages", "settingsCard").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { validate(id, ThemeScriptSurface(transition = ThemeStateTransition())) }
            assertThrows(IllegalArgumentException::class.java) { validate(id, ThemeScriptSurface(ripple = ThemeRipple())) }
        }
    }

    @Test
    fun optionalExtensionsPreserveOldSurfaceFieldsAndSurviveModesAndJson() {
        val json = ThemeScriptContract.json
        val old = json.encodeToString(ThemeScriptSurface())
        assertFalse(old.contains("transition"))
        assertFalse(old.contains("ripple"))
        val button = ThemeScriptSurface(fillColor = "#123456", transition = ThemeStateTransition(220), ripple = ThemeRipple(false),
            states = mapOf("pressed" to ThemeAppearanceState(opacity = .5f)))
        val appearance = ThemeScriptAppearance(1, modes = mapOf("dark" to ThemeAppearanceMode(surfaces = mapOf("button" to button))))
        ThemeScriptContract.validate(appearance, manifest)
        val restored = json.decodeFromString<ThemeScriptAppearance>(json.encodeToString(appearance))
        assertEquals(appearance, restored)
        val active = restored.forMode("dark").surfaces.getValue("button").forState("pressed")
        assertEquals(button.transition, active.transition)
        assertEquals(button.ripple, active.ripple)
        assertEquals(.5f, active.opacity, 0f)
    }
}
