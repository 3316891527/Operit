package com.ai.assistance.operit.data.model.theme

import com.ai.assistance.operit.data.theme.script.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ThemeMediaResourceContractTest {
    @Test fun jpegExtensionsWorkForIconsImagesAndNineSliceInBothModes() {
        for (extension in listOf("jpg", "jpeg", "JPG", "JPEG", "JpEg")) {
            val path = "assets/图片/照片 1.$extension"
            assertTrue(path, ThemeAssetPaths.isFile(path))
            assertTrue(path, ThemeAssetFormats.isImage(path))
            assertTrue(ThemeAssetFormats.extension(path) in ThemeAssetFormats.resources)
            val surface = ThemeScriptSurface(layers = listOf(
                ThemeScriptLayer("image", asset = "photo"),
                ThemeScriptLayer("nineSlice", asset = "photo"),
            ))
            val appearance = ThemeScriptAppearance(1,
                surfaces = mapOf("background" to surface),
                icons = mapOf("composer.send" to ThemeAppearanceIcon("photo")),
                modes = mapOf("dark" to ThemeAppearanceMode(surfaces = mapOf("background" to surface))),
            )
            val manifest = ThemeScriptManifest(1, "assets/main.js", mapOf("photo" to path), appearance)
            ThemeScriptContract.validate(manifest)
            assertEquals(manifest, ThemeScriptContract.decodeManifest(ThemeScriptContract.json.encodeToString(ThemeScriptManifest.serializer(), manifest)))
        }
    }

    @Test fun formatDetectionIsIndependentOfTheDeviceLocale() {
        val former = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(ThemeAssetFormats.isImage("assets/PHOTO.JPEG"))
            assertTrue(ThemeAssetFormats.isFont("assets/FONT.TTF"))
        } finally { Locale.setDefault(former) }
    }

    @Test fun validJpegFormatDoesNotHideAnInvalidNameOrTraversal() {
        for (path in listOf("assets/photo(1).jpg", "assets/../photo.JPG", "assets/photo..jpeg")) {
            assertTrue(path, ThemeAssetFormats.isImage(path))
            assertFalse(path, ThemeAssetPaths.isFile(path))
        }
        assertFalse(ThemeAssetFormats.isImage("assets/photo.heic"))
        assertFalse(ThemeAssetFormats.isImage("assets/photo.jpg.exe"))
    }
}
