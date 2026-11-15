package com.ai.assistance.operit.ui.common.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.config.model.ApiProviderType
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageAssetLoaderAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun svgWithOnlyAnOffsetViewBoxKeepsItsColorsAndCenteredAspectRatio() {
        val bytes = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="4 8 20 10">
              <rect x="4" y="8" width="10" height="10" fill="#ff0000"/>
              <rect x="14" y="8" width="10" height="10" fill="#0000ff"/>
            </svg>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val images = listOf(
            ImageAssetLoader.load(bytes, "image/svg+xml", null, 64),
            bytes.inputStream().use { ImageAssetLoader.load(it, null, "image.SVG", 64) },
        )
        images.forEach { decoded ->
            val bitmap = checkNotNull(decoded)
            try {
                assertEquals(64, bitmap.width)
                assertEquals(64, bitmap.height)
                assertEquals(AndroidColor.RED, bitmap.getPixel(8, 32))
                assertEquals(AndroidColor.BLUE, bitmap.getPixel(56, 32))
                assertEquals(0, AndroidColor.alpha(bitmap.getPixel(32, 4)))
                assertEquals(0, AndroidColor.alpha(bitmap.getPixel(32, 60)))
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun rasterFormatsKeepColorAndTransparentPaddingThroughMimeAndExtensionInputs() {
        val color = AndroidColor.rgb(22, 163, 74)
        val source = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try {
            for ((format, mime, name) in listOf(
                Triple(Bitmap.CompressFormat.PNG, "image/png", "image.PNG"),
                Triple(Bitmap.CompressFormat.JPEG, "image/jpeg", "image.JPEG"),
                Triple(Bitmap.CompressFormat.WEBP, "image/webp", "image.WEBP"),
            )) {
                val bytes = ByteArrayOutputStream().use { output ->
                    assertTrue(source.compress(format, 100, output))
                    output.toByteArray()
                }
                val images = listOf(
                    ImageAssetLoader.load(bytes, mime, null, 64),
                    bytes.inputStream().use { ImageAssetLoader.load(it, null, name, 64) },
                )
                images.forEach { decoded ->
                    val bitmap = checkNotNull(decoded) { name }
                    try {
                        assertEquals(64, bitmap.width)
                        assertEquals(64, bitmap.height)
                        assertColorClose(color, bitmap.getPixel(32, 32), 10)
                        assertEquals(0, AndroidColor.alpha(bitmap.getPixel(32, 4)))
                        assertEquals(0, AndroidColor.alpha(bitmap.getPixel(32, 60)))
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            source.recycle()
        }
    }

    @Test
    fun everyBuiltInProviderAndTheRetainedIflowAssetDecodeToVisiblePixels() {
        val ids = ApiProviderType.values().map { it.name } + "IFLOW"
        for (id in ids) {
            val path = checkNotNull(ProviderLogoLoader.findLogoAssetPath(context, id)) { id }
            val files = checkNotNull(context.assets.list("model_logos/$id"))
            assertEquals(id, 1, files.count { it.endsWith(".svg", true) || it.endsWith(".png", true) })
            val bitmap = checkNotNull(ProviderLogoLoader.loadLogoBitmap(context, id, 64)) { path }
            try {
                assertEquals(id, 64, bitmap.width)
                assertEquals(id, 64, bitmap.height)
                assertTrue(id, pixels(bitmap).any { AndroidColor.alpha(it) > 0 })
            } finally {
                bitmap.recycle()
            }
        }
        assertEquals(
            ProviderLogoLoader.findLogoAssetPath(context, "GOOGLE"),
            ProviderLogoLoader.findLogoAssetPath(context, "GOOGLE/legacy-model"),
        )
        assertNull(ProviderLogoLoader.findLogoAssetPath(context, "__unregistered_provider__"))
    }

    @Test
    fun monochromeProviderPaintersFollowBothThemeForegroundsWithoutLosingTheirShape() {
        val ids = listOf("OPENAI", "OLLAMA", "OTHER", "ANTHROPIC", "ANTIGRAVITY", "LMSTUDIO")
        val foregrounds = listOf(lightColorScheme().onSurface, darkColorScheme().onSurface)
        for (id in ids) {
            val bitmap = checkNotNull(ProviderLogoLoader.loadLogoBitmap(context, id, 64))
            try {
                val original = pixels(bitmap)
                val solidPixels = original.indices.filter { AndroidColor.alpha(original[it]) == 255 }
                assertTrue(id, solidPixels.isNotEmpty())
                for (foreground in foregrounds) {
                    val rendered = draw(ProviderLogoLoader.createPainter(bitmap.asImageBitmap(), id, foreground))
                    try {
                        val actual = pixels(rendered)
                        original.indices.forEach { index ->
                            assertEquals("$id alpha $index", AndroidColor.alpha(original[index]), AndroidColor.alpha(actual[index]))
                        }
                        solidPixels.forEach { index -> assertColorClose(foreground.toArgb(), actual[index], 1) }
                    } finally {
                        rendered.recycle()
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun coloredProviderPaintersRetainOriginalPixelsInBothThemesIncludingLegacyIdentity() {
        val catalog = context.assets.open("model_logos/sources.lobe-icons.json").bufferedReader().use { JSONObject(it.readText()) }
        val entries = catalog.getJSONArray("assets")
        val ids = (0 until entries.length()).map { entries.getJSONObject(it).getString("asset").substringBefore('/') } +
            listOf("INFINIAI", "IFLOW", "FOUR_ROUTER", "MINIMAX")
        val foregrounds = listOf(lightColorScheme().onSurface, darkColorScheme().onSurface)
        for (id in ids) {
            val bitmap = checkNotNull(ProviderLogoLoader.loadLogoBitmap(context, id, 64))
            try {
                val expected = pixels(bitmap)
                val providerIds = if (id == "GOOGLE") listOf(id, "$id/legacy-model") else listOf(id)
                for (providerId in providerIds) for (foreground in foregrounds) {
                    val rendered = draw(ProviderLogoLoader.createPainter(bitmap.asImageBitmap(), providerId, foreground))
                    try {
                        assertArrayEquals(providerId, expected, pixels(rendered))
                    } finally {
                        rendered.recycle()
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun malformedOrUnsupportedImagesDoNotProduceAnUnrelatedBitmap() {
        assertNull(ImageAssetLoader.load("<svg><broken>".toByteArray(), "image/svg+xml", "broken.svg", 32))
        assertNull(ImageAssetLoader.load(byteArrayOf(1, 2, 3), "image/png", "broken.png", 32))
        assertNull(ImageAssetLoader.load(byteArrayOf(1, 2, 3), "application/octet-stream", "data.bin", 32))
        assertThrows(IllegalArgumentException::class.java) {
            ImageAssetLoader.load(byteArrayOf(), "image/png", "image.png", 0)
        }
    }

    private fun draw(painter: Painter): Bitmap {
        val target = ImageBitmap(64, 64)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(target), Size(64f, 64f)) {
            with(painter) { draw(size = size) }
        }
        return target.asAndroidBitmap()
    }

    private fun pixels(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

    private fun assertColorClose(expected: Int, actual: Int, tolerance: Int) {
        for (shift in listOf(0, 8, 16, 24)) {
            assertTrue("expected=${expected.toUInt().toString(16)}, actual=${actual.toUInt().toString(16)}",
                abs((expected ushr shift and 255) - (actual ushr shift and 255)) <= tolerance)
        }
    }
}
