package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeInputPlaceholderTest {
    private val manifest = ThemeScriptManifest(1, "assets/main.js")

    private fun roundTrip(appearance: ThemeScriptAppearance): ThemeScriptAppearance {
        val document = manifest.copy(appearance = appearance)
        val encoded = ThemeScriptContract.json.encodeToString(ThemeScriptManifest.serializer(), document)
        return ThemeScriptContract.decodeManifest(encoded).appearance
    }

    @Test
    fun oldThemesKeepTheNativePlaceholderWithoutAddingAnExportField() {
        for (declaration in listOf("{}", "{\"placeholder\":null}")) {
            val original = ThemeScriptContract.decodeResult(
                """{"apiVersion":1,"surfaces":{"input":$declaration}}""", manifest,
            )
            val restored = roundTrip(original)
            assertNull(restored.surfaces.getValue("input").placeholder)
            val surface = ThemeScriptContract.json.encodeToJsonElement(
                ThemeScriptSurface.serializer(), restored.surfaces.getValue("input"),
            ).jsonObject
            assertFalse(surface.containsKey("placeholder"))
        }
    }

    @Test
    fun explicitEmptyPlaceholderSurvivesValidationAndThemeExport() {
        val original = ThemeScriptContract.decodeResult(
            """{"apiVersion":1,"surfaces":{"input":{"placeholder":""}}}""", manifest,
        )
        assertEquals("", roundTrip(original).surfaces.getValue("input").placeholder)
    }

    @Test
    fun customTextIncludingWhitespaceIsPreserved() {
        for (text in listOf("今天想聊什么？", "  ", "第一行\n第二行", "Hello 👋")) {
            val original = ThemeScriptAppearance(1, surfaces = mapOf("input" to ThemeScriptSurface(placeholder = text)))
            assertEquals(text, roundTrip(original).surfaces.getValue("input").placeholder)
        }
    }

    @Test
    fun lightAndDarkModesKeepIndependentPlaceholderChoices() {
        val original = ThemeScriptAppearance(
            1,
            surfaces = mapOf("input" to ThemeScriptSurface(placeholder = "公共提示")),
            modes = mapOf(
                "light" to ThemeAppearanceMode(surfaces = mapOf("input" to ThemeScriptSurface(placeholder = ""))),
                "dark" to ThemeAppearanceMode(surfaces = mapOf("input" to ThemeScriptSurface(placeholder = "深色提示"))),
            ),
        )
        val restored = roundTrip(original)
        assertEquals("", restored.forMode("light").surfaces.getValue("input").placeholder)
        assertEquals("深色提示", restored.forMode("dark").surfaces.getValue("input").placeholder)
        assertEquals("公共提示", restored.surfaces.getValue("input").placeholder)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unrelatedSurfacesCannotDeclareAChatPlaceholder() {
        ThemeScriptContract.decodeResult(
            """{"apiVersion":1,"surfaces":{"composer":{"placeholder":""}}}""", manifest,
        )
    }
}
