package com.ai.assistance.operit.ui.theme.script

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import com.ai.assistance.operit.data.theme.script.*
import java.io.ByteArrayOutputStream
import java.util.Locale
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

    @Test fun bannerKeepsCompactEnglishTotalsAndToastContainsOnlyLocalizedReason() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val issue = ThemeResourceLimitException(ThemeResourceLimit.AssetBytes, 8_388_608, 0, 8_388_609,
            incomingIsLowerBound = true, resource = "photo.JPG")
        val wrapped = IllegalArgumentException("Invalid archive", issue)
        val banner = "Theme script resource exceeds size limit (≥8,388,609 B/8,388,608 B)"
        for (language in listOf("zh-CN", "en")) {
            val config = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val context = target.createConfigurationContext(config)
            assertEquals(banner, themeResourceErrorMessage(wrapped))
            val toast = requireNotNull(themeResourceErrorToast(context, wrapped))
            assertEquals(if (language == "en") "Theme resource file exceeds the size limit" else "主题资源文件超过大小限制", toast)
            assertFalse(toast.any { it.isDigit() })
            assertFalse(toast.contains('/'))
            assertFalse(toast.contains("photo.JPG"))
            val pixels = ThemeResourceLimitException(ThemeResourceLimit.BitmapPixels, 41_943_040, 40_000_000, 2_000_000)
            assertEquals("Theme drawing assets exceed the decoded pixel budget (42,000,000/41,943,040)", themeResourceErrorMessage(pixels))
            assertEquals(if (language == "en") "Theme images exceed the decoded pixel limit" else "主题图片解码后的总像素超过限制",
                themeResourceErrorToast(context, pixels))
            assertNull(themeResourceErrorToast(context, IllegalArgumentException("Other error")))
        }
    }
}
