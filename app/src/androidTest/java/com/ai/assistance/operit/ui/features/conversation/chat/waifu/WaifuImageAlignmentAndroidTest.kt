package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.common.markdown.MarkdownImageRenderer
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WaifuImageAlignmentAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun waifuImagesUseTheStartEdgeAndOrdinaryImagesKeepTheirSizeAndPosition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("waifu-alignment-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(24, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        try {
            file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            val url = android.net.Uri.fromFile(file).toString()
            val theme = ThemePreferenceSnapshot(
                source = "test",
                values = ThemePreferenceValues.defaultVisual().withBoolean("show_role_name", false),
            )
            compose.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalThemePreferenceSnapshot provides theme) {
                        Column(Modifier.width(300.dp)) {
                            for ((name, image) in listOf("emotion" to "![]($url)", "markdown" to "![图片]($url)")) {
                                WaifuReplyAvatarRow(
                                    enabled = true, message = ChatMessage(sender = "ai", content = image),
                                    onAvatarLongPress = null,
                                ) {
                                    Box(Modifier.fillMaxWidth().testTag(name)) {
                                        MarkdownImageRenderer(image, maxImageHeight = 80, enableDialogs = false)
                                    }
                                }
                            }
                            Box(Modifier.fillMaxWidth().testTag("ordinary")) {
                                MarkdownImageRenderer("![]($url)", maxImageHeight = 80, enableDialogs = false)
                            }
                        }
                    }
                }
            }
            compose.waitUntil(20_000) {
                redBounds("emotion") != null && redBounds("markdown") != null && redBounds("ordinary") != null
            }
            val emotion = requireNotNull(redBounds("emotion"))
            val markdown = requireNotNull(redBounds("markdown"))
            val ordinary = requireNotNull(redBounds("ordinary"))
            assertTrue(emotion.left < emotion.containerWidth * 0.15f)
            assertTrue(markdown.left < markdown.containerWidth * 0.15f)
            assertTrue(ordinary.left > ordinary.containerWidth * 0.2f)
            assertTrue(abs(emotion.width - ordinary.width) <= 1)
            assertTrue(abs(emotion.height - ordinary.height) <= 1)
            assertTrue(abs(markdown.width - emotion.width) <= 1)
            assertTrue(abs(markdown.height - emotion.height) <= 1)
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    private data class ImageBounds(val left: Int, val width: Int, val height: Int, val containerWidth: Int)

    private fun redBounds(tag: String): ImageBounds? {
        val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        var left = image.width
        var right = -1
        var top = image.height
        var bottom = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val color = image.getPixel(x, y)
            if (Color.red(color) > 220 && Color.green(color) < 35 && Color.blue(color) < 35) {
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
        }
        return if (right < 0) null else ImageBounds(left, right - left + 1, bottom - top + 1, image.width)
    }
}
