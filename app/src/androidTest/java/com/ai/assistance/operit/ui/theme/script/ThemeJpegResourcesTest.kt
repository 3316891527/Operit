package com.ai.assistance.operit.ui.theme.script

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import com.ai.assistance.operit.data.theme.script.*
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeJpegResourcesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun realJpegBytesValidateArchiveAndDecodeWithEitherExtensionAndCase() {
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        val jpeg = try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
        for (extension in listOf("jpg", "jpeg", "JPG", "JPEG")) {
            val path = "assets/图片/照片.$extension"
            val surface = ThemeScriptSurface(layers = listOf(ThemeScriptLayer("image", asset = "photo"), ThemeScriptLayer("nineSlice", asset = "photo")))
            val appearance = ThemeScriptAppearance(1, surfaces = mapOf("background" to surface),
                icons = mapOf("composer.send" to ThemeAppearanceIcon("photo")))
            val manifest = ThemeScriptManifest(1, "assets/main.js", mapOf("photo" to path), appearance)
            val bundle = ThemeScriptBundle(manifest, "module.exports={apiVersion:1,render(c){return c.appearance}}", mapOf(path to jpeg))
            ThemeScriptBundleStore.validate(bundle)
            val archive = ThemeScriptBundleStore.create(folder.newFolder(extension), bundle)
            val restored = ThemeScriptBundleStore.read(archive)
            assertEquals(manifest, restored.manifest)
            assertArrayEquals(jpeg, restored.assets.getValue(path))
            val images = ThemeScriptAssets.prepare(restored)
            val image = images.getValue("photo") as ThemeScriptImage.Bitmap
            assertEquals(4, image.image.width)
            assertEquals(3, image.image.height)
            ThemeScriptAssets.validateAppearance(appearance, images)
        }
    }
}
