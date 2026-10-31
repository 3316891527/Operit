package com.ai.assistance.operit.ui.theme.script

import android.graphics.BitmapFactory
import android.graphics.Picture
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.caverock.androidsvg.SVG

internal sealed interface ThemeScriptImage {
    data class Bitmap(val image: ImageBitmap) : ThemeScriptImage
    data class Vector(val picture: Picture) : ThemeScriptImage
}

/** 素材随不可变资源束准备，绘制阶段不读取文件或重新解析 SVG。 */
internal object ThemeScriptAssets {
    fun prepare(bundle: ThemeScriptBundle): Map<String, ThemeScriptImage> {
        var bitmapPixels = 0L
        val decoded = bundle.assets.mapValues { (path, bytes) ->
            if (path.endsWith(".svg", ignoreCase = true)) {
                ThemeScriptImage.Vector(SVG.getFromString(bytes.toString(Charsets.UTF_8)).renderToPicture(512, 512))
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                bitmapPixels += options.outWidth.toLong() * options.outHeight
                require(bitmapPixels in 1..4_194_304L) { "Theme drawing assets exceed the decoded pixel budget" }
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Cannot decode theme bitmap" }
                ThemeScriptImage.Bitmap(bitmap.asImageBitmap())
            }
        }
        return bundle.manifest.assets.mapValues { (_, path) -> decoded.getValue(path) }
    }
    fun validateAppearance(appearance: com.ai.assistance.operit.data.model.theme.ThemeScriptAppearance, images: Map<String, ThemeScriptImage>) {
        appearance.surfaces.values.flatMap { it.layers }.filter { it.type == "nineSlice" }.forEach { layer ->
            val bitmap = images.getValue(requireNotNull(layer.asset)) as? ThemeScriptImage.Bitmap
                ?: error("Nine-slice skins require bitmap assets")
            val edge = layer.slices
            require(edge.start.roundToInt() + edge.end.roundToInt() < bitmap.image.width && edge.top.roundToInt() + edge.bottom.roundToInt() < bitmap.image.height) {
                "Nine-slice insets must leave a nonempty image center"
            }
        }
    }
}
