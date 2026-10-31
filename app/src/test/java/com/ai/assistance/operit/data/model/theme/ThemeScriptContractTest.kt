package com.ai.assistance.operit.data.model.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeScriptContractTest {
    private val manifest = ThemeScriptManifest(1, "assets/main.js", mapOf("badge" to "assets/badge.svg"))
    private fun rejects(action: () -> Unit) {
        try { action(); fail("应拒绝无效脚本协议") } catch (_: IllegalArgumentException) { }
    }
    private fun appearance(layer: ThemeScriptLayer) = ThemeScriptAppearance(
        1, mapOf("userMessage" to ThemeScriptSurface(layers = listOf(layer))),
    )

    @Test
    fun acceptsAllSurfacesAndDeclaredImages() {
        val result = ThemeScriptAppearance(1, ThemeScriptContract.surfaceIds.associateWith {
            ThemeScriptSurface(layers = listOf(ThemeScriptLayer("image", asset = "badge", fit = "crop")))
        })
        ThemeScriptContract.validate(result, manifest)
        assertEquals(setOf("assets/main.js", "assets/badge.svg"), ThemeScriptContract.references(manifest))
    }

    @Test
    fun rejectsUnsafePathsVersionsAndExcessiveAssets() {
        listOf("../main.js", "assets/../main.js", "https://example.invalid/main.js", "assets/main.ts").forEach {
            rejects { ThemeScriptContract.validate(manifest.copy(entry = it)) }
        }
        rejects { ThemeScriptContract.validate(manifest.copy(apiVersion = 2)) }
        rejects { ThemeScriptContract.validate(manifest.copy(assets = (1..49).associate { "a$it" to "assets/$it.png" })) }
        rejects { ThemeScriptContract.validate(manifest.copy(assets = mapOf("main" to manifest.entry))) }
    }

    @Test
    fun rejectsUnknownSurfacesLayersAndAssets() {
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf("toolbar" to ThemeScriptSurface())), manifest) }
        rejects { ThemeScriptContract.validate(appearance(ThemeScriptLayer("html", color = "#112233")), manifest) }
        rejects { ThemeScriptContract.validate(appearance(ThemeScriptLayer("image", asset = "missing")), manifest) }
        rejects { ThemeScriptContract.validate(appearance(ThemeScriptLayer("rect")), manifest) }
    }

    @Test
    fun boundsPaddingAndPaintStayInsideContract() {
        val valid = ThemeScriptLayer("rect", color = "#AA112233", radius = 128f, strokeWidth = 16f)
        ThemeScriptContract.validate(appearance(valid), manifest)
        listOf(
            valid.copy(opacity = Float.NaN), valid.copy(radius = 129f), valid.copy(strokeWidth = -1f),
            valid.copy(bounds = ThemeScriptBounds(x = 0.5f, width = 1f)),
            valid.copy(color = "red"), valid.copy(state = "hover"), valid.copy(placement = "overlay"),
        ).forEach { invalid -> rejects { ThemeScriptContract.validate(appearance(invalid), manifest) } }
        rejects {
            ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf(
                "background" to ThemeScriptSurface(padding = ThemeScriptInsets(top = 1f)),
            )), manifest)
        }
        rejects {
            ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf(
                "composer" to ThemeScriptSurface(padding = ThemeScriptInsets(end = 49f)),
            )), manifest)
        }
    }

    @Test
    fun layerBudgetAppliesAcrossAllSurfaces() {
        val layer = ThemeScriptLayer("ellipse", color = "#123456")
        val surface = ThemeScriptSurface(layers = List(64) { layer })
        ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf("userMessage" to surface, "composer" to surface)), manifest)
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, ThemeScriptContract.surfaceIds.associateWith { surface }), manifest) }
    }

    @Test
    fun rejectsUnknownOutputFieldsDeepJsonAndOversizedResults() {
        rejects { ThemeScriptContract.decodeResult("""{"apiVersion":1,"script":"code"}""", manifest) }
        rejects { ThemeScriptContract.decodeResult("[".repeat(1000) + "0" + "]".repeat(1000), manifest) }
        rejects { ThemeScriptContract.decodeManifest("[".repeat(1000) + "0" + "]".repeat(1000)) }
        rejects { ThemeScriptContract.decodeResult(" ".repeat(ThemeScriptContract.MAX_RESULT_BYTES + 1), manifest) }
        assertEquals(ThemeScriptAppearance(1), ThemeScriptContract.decodeResult("""{"apiVersion":1}""", manifest))
    }

    @Test
    fun extendedStylesIconsAndPalettesRoundTripAsManifestData() {
        val appearance = ThemeScriptAppearance(
            1,
            surfaces = mapOf(
                "button" to ThemeScriptSurface(shape = ThemeAppearanceShape("cut", 12f), strokeColor = "#445566", strokeWidth = 2f),
                "assistantAvatar" to ThemeScriptSurface(size = 48f, shape = ThemeAppearanceShape("circle")),
                "assistantMessage" to ThemeScriptSurface(messageWidth = 0.8f, textStyle = ThemeAppearanceText(family = "serif", scale = 1.1f)),
            ),
            icons = mapOf("composer.send" to ThemeAppearanceIcon("badge", 32f, useContentTint = true)),
            palette = ThemeAppearancePalette(light = mapOf("surfaceContainerHighest" to "#112233"), dark = mapOf("onSurface" to "#FFEEDD")),
        )
        val full = manifest.copy(appearance = appearance)
        val json = ThemeScriptContract.json.encodeToString(ThemeScriptManifest.serializer(), full)
        assertEquals(full, ThemeScriptContract.decodeManifest(json))
    }

    @Test
    fun rejectsUnsupportedStyleValuesAndUndeclaredIcons() {
        listOf(
            ThemeScriptSurface(shape = ThemeAppearanceShape("polygon")),
            ThemeScriptSurface(strokeWidth = 17f),
            ThemeScriptSurface(fillColor = "transparent"),
            ThemeScriptSurface(textStyle = ThemeAppearanceText(scale = Float.NaN)),
            ThemeScriptSurface(elevation = -1f),
            ThemeScriptSurface(size = 32f),
            ThemeScriptSurface(messageWidth = 0.8f),
        ).forEach { surface ->
            rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, mapOf("button" to surface)), manifest) }
        }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, icons = mapOf("unknown" to ThemeAppearanceIcon("badge"))), manifest) }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, icons = mapOf("composer.send" to ThemeAppearanceIcon("missing"))), manifest) }
        rejects { ThemeScriptContract.validate(ThemeScriptAppearance(1, palette = ThemeAppearancePalette(light = mapOf("unknown" to "#112233"))), manifest) }
    }

    @Test
    fun acceptsNativeStatesAndNineSliceButRejectsInvalidSliceBounds() {
        for (state in listOf("selected", "disabled", "error")) {
            ThemeScriptContract.validate(appearance(ThemeScriptLayer("rect", color = "#112233", state = state)), manifest)
        }
        val sliced = ThemeScriptLayer("nineSlice", asset = "badge", slices = ThemeScriptInsets(8f, 8f, 8f, 8f))
        ThemeScriptContract.validate(appearance(sliced), manifest)
        rejects { ThemeScriptContract.validate(appearance(sliced.copy(slices = ThemeScriptInsets(end = -1f))), manifest) }
        rejects { ThemeScriptContract.validate(appearance(sliced.copy(slices = ThemeScriptInsets(end = 2049f))), manifest) }
    }
}
