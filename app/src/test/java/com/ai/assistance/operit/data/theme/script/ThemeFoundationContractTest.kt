package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class ThemeFoundationContractTest {
    private val manifest = ThemeScriptManifest(1, "assets/main.js", assets = mapOf("body" to "assets/body.ttf"))
    private val gradient = ThemeGradient(stops = listOf(ThemeGradientStop(0f, "#00112233"), ThemeGradientStop(1f, "#FFEEDDCC")))
    private fun validate(surface: ThemeScriptSurface) = ThemeScriptContract.validate(
        ThemeScriptAppearance(1, surfaces = mapOf("page.packages" to surface)), manifest)
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Invalid declaration was accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun oldDeclarationsDoNotAcquireNewFields() {
        val json = ThemeScriptContract.json
        val surface = json.encodeToJsonElement(ThemeScriptSurface.serializer(), ThemeScriptSurface()) as JsonObject
        assertFalse(surface.containsKey("fillGradient"))
        val appearance = json.encodeToJsonElement(ThemeScriptAppearance.serializer(), ThemeScriptAppearance(1)) as JsonObject
        assertFalse(appearance.containsKey("textStyles"))
        val text = json.encodeToJsonElement(ThemeAppearanceText.serializer(), ThemeAppearanceText()) as JsonObject
        assertFalse(text.containsKey("weight"))
        val layer = json.encodeToJsonElement(ThemeScriptLayer.serializer(), ThemeScriptLayer("rect", color = "#112233")) as JsonObject
        assertFalse(layer.containsKey("gradient"))
    }

    @Test fun modesPreservePageLayersTargetFontsAndFractionalOutlines() {
        val model = ThemeAppearanceText(family = "asset:body", scale = 1.2f, weight = 700, stroke = ThemeTextStroke(width = .05f))
        val appearance = ThemeScriptAppearance(1,
            surfaces = mapOf("background" to ThemeScriptSurface(fillColor = "#FFFFFF")),
            textStyles = mapOf("chat.modelName" to model),
            modes = mapOf("dark" to ThemeAppearanceMode(
                surfaces = mapOf("page.packages" to ThemeScriptSurface(fillGradient = gradient.copy(type = "radial"),
                    layers = listOf(ThemeScriptLayer("ellipse", gradient = gradient)))),
                textStyles = mapOf("chat.modelName" to model.copy(stroke = ThemeTextStroke(width = 0f))))))
        val json = ThemeScriptContract.json.encodeToString(ThemeScriptAppearance.serializer(), appearance)
        val restored = ThemeScriptContract.decodeResult(json, manifest)
        assertEquals(appearance, restored)
        assertEquals(model, restored.forMode("light").textStyles.getValue("chat.modelName"))
        assertEquals(0f, restored.forMode("dark").textStyles.getValue("chat.modelName").stroke!!.width, 0f)
        assertEquals("#FFFFFF", restored.forMode("dark").surfaces.getValue("background").fillColor)
        assertEquals("radial", restored.forMode("dark").surfaces.getValue("page.packages").fillGradient!!.type)
    }

    @Test fun malformedOrConflictingGradientPaintIsRejected() {
        rejects { validate(ThemeScriptSurface(fillColor = "#FFFFFF", fillGradient = gradient)) }
        val bad = listOf(
            gradient.copy(type = "sweep"), gradient.copy(stops = emptyList()),
            gradient.copy(stops = List(17) { ThemeGradientStop(it / 16f, "#112233") }),
            gradient.copy(stops = listOf(ThemeGradientStop(0f, "red"), ThemeGradientStop(1f, "#112233"))),
            gradient.copy(stops = listOf(ThemeGradientStop(0f, "#112233"), ThemeGradientStop(0f, "#445566"))),
            gradient.copy(stops = listOf(ThemeGradientStop(Float.NaN, "#112233"), ThemeGradientStop(1f, "#445566"))),
            gradient.copy(stops = listOf(ThemeGradientStop(.2f, "#112233"), ThemeGradientStop(1f, "#445566"))),
            gradient.copy(start = gradient.end), gradient.copy(radius = 0f), gradient.copy(radius = Float.POSITIVE_INFINITY),
            gradient.copy(center = ThemeGradientPoint(-.1f, .5f)),
        )
        bad.forEach { rejects { validate(ThemeScriptSurface(fillGradient = it)) } }
        rejects { validate(ThemeScriptSurface(layers = listOf(ThemeScriptLayer("rect", color = "#FFFFFF", gradient = gradient)))) }
        rejects { validate(ThemeScriptSurface(layers = listOf(ThemeScriptLayer("image", asset = "body", gradient = gradient)))) }
    }

    @Test fun stateFillOverridesGradientAndResetLeavesGlobalBackgroundIntact() {
        val surface = ThemeScriptSurface(fillGradient = gradient, states = mapOf("pressed" to ThemeAppearanceState(fillColor = "#112233")))
        assertNull(surface.forState("pressed").fillGradient)
        assertEquals(gradient, surface.forState("always").fillGradient)
        val project = ThemeScriptAppearance(1, surfaces = mapOf("background" to ThemeScriptSurface(fillColor = "#FFFFFF"), "page.packages" to surface),
            modes = mapOf("dark" to ThemeAppearanceMode(surfaces = mapOf("page.packages" to ThemeScriptSurface()))))
        val resolved = project.forMode("dark")
        assertEquals(ThemeScriptSurface(), resolved.surfaces.getValue("page.packages"))
        assertEquals("#FFFFFF", resolved.surfaces.getValue("background").fillColor)
    }

    @Test fun unknownTargetsMissingFontsAndInvalidWeightsAreRejected() {
        for (target in listOf("chat.arbitrary", "page.unknown")) rejects {
            ThemeScriptContract.validate(ThemeScriptAppearance(1, textStyles = mapOf(target to ThemeAppearanceText())), manifest)
        }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, surfaces = mapOf("page.unknown" to ThemeScriptSurface())), manifest) }
        for (weight in listOf(0, 1001)) rejects {
            ThemeScriptContract.validate(ThemeScriptAppearance(1, textStyles = mapOf("chat.modelName" to ThemeAppearanceText(weight = weight))), manifest)
        }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, textStyles = mapOf("bodySmall" to ThemeAppearanceText(family = "asset:missing"))), manifest) }
        rejects { validate(ThemeScriptSurface(padding = ThemeScriptInsets(top = 1f))) }
        for (weight in listOf(1, 1000)) ThemeScriptContract.validate(ThemeScriptAppearance(1, textStyles = mapOf("chat.modelName" to ThemeAppearanceText(weight = weight))), manifest)
    }
}
