package com.ai.assistance.operit.ui.common.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.InputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 通用 SVG、PNG、JPEG 和 WebP 解码；图片等比例居中于指定大小的正方形画布。 */
object ImageAssetLoader {
    private val rasterExtensions = setOf("png", "jpg", "jpeg", "webp")
    private val rasterMimeTypes = setOf("image/png", "image/jpeg", "image/webp")

    /** 从 assets 读取图片，所有调用方共用同一解码入口。 */
    fun load(context: Context, assetPath: String, sizePx: Int): Bitmap? {
        return try {
            context.assets.open(assetPath).use { input ->
                load(input = input, mimeType = null, fileName = assetPath, sizePx = sizePx)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun load(
        bytes: ByteArray,
        mimeType: String?,
        fileName: String?,
        sizePx: Int
    ): Bitmap? {
        return bytes.inputStream().use { input ->
            load(input = input, mimeType = mimeType, fileName = fileName, sizePx = sizePx)
        }
    }

    fun load(
        input: InputStream,
        mimeType: String?,
        fileName: String?,
        sizePx: Int
    ): Bitmap? {
        require(sizePx > 0) { "Image size must be positive" }
        return try {
            when {
                isSvg(mimeType, fileName) -> renderSvgToBitmap(input, sizePx)
                isRaster(mimeType, fileName) -> scaleBitmapToBitmap(input, sizePx)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isSvg(mimeType: String?, fileName: String?): Boolean {
        return mimeType.equals("image/svg+xml", ignoreCase = true) ||
            fileName.extension().equals("svg", ignoreCase = true)
    }

    private fun isRaster(mimeType: String?, fileName: String?): Boolean {
        return mimeType?.trim()?.lowercase()?.let { it in rasterMimeTypes } == true ||
            fileName.extension().lowercase() in rasterExtensions
    }

    /** SVG 预览和导出共享解析结果；该入口将解析错误交由调用方显示。 */
    fun readSvg(input: InputStream): SvgImageAsset = SvgImageAsset.read(input)

    private fun renderSvgToBitmap(input: InputStream, sizePx: Int): Bitmap =
        readSvg(input).renderSquare(sizePx)

    private fun scaleBitmapToBitmap(input: InputStream, sizePx: Int): Bitmap? {
        val source = BitmapFactory.decodeStream(input) ?: return null
        if (source.width == sizePx && source.height == sizePx) return source
        val scale = sizePx / max(source.width, source.height).toFloat()
        val scaledWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF(
                (sizePx - scaledWidth) / 2f,
                (sizePx - scaledHeight) / 2f,
                (sizePx + scaledWidth) / 2f,
                (sizePx + scaledHeight) / 2f
            ),
            null
        )
        source.recycle()
        return bitmap
    }

    private fun String?.extension(): String {
        return this?.substringAfterLast('.', "").orEmpty()
    }
}

/** 异步读取 assets 图片，保留素材颜色，不应用业务层的主题滤镜。 */
@Composable
fun rememberImageAssetPainter(assetPath: String, size: Dp = 24.dp): Painter? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by
        produceState<ImageBitmap?>(initialValue = null, context, assetPath, sizePx) {
            value =
                withContext(Dispatchers.IO) {
                    ImageAssetLoader.load(context, assetPath, sizePx)?.asImageBitmap()
                }
        }
    return bitmap?.let { BitmapPainter(it) }
}

/** 异步解码图片字节，按图片身份、内容、格式与尺寸重新加载。 */
@Composable
fun rememberImageAssetPainter(
    imageKey: Any?,
    bytes: ByteArray?,
    mimeType: String?,
    fileName: String?,
    size: Dp = 24.dp
): Painter? {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by
        produceState<ImageBitmap?>(
            initialValue = null,
            imageKey,
            bytes?.contentHashCode(),
            mimeType,
            fileName,
            sizePx
        ) {
            value =
                bytes?.let {
                    withContext(Dispatchers.IO) {
                        ImageAssetLoader.load(
                            bytes = it,
                            mimeType = mimeType,
                            fileName = fileName,
                            sizePx = sizePx
                        )?.asImageBitmap()
                    }
                }
        }
    return bitmap?.let { BitmapPainter(it) }
}
