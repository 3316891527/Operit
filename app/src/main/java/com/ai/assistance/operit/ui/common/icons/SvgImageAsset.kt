package com.ai.assistance.operit.ui.common.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 已解析的 SVG；提供原始尺寸、透明位图和等比例输出，不应用品牌或主题着色。 */
class SvgImageAsset private constructor(private val svg: SVG) {
    private val viewBox = svg.documentViewBox
    val width: Float = when {
        svg.documentWidth > 0f -> svg.documentWidth
        viewBox != null && viewBox.width() > 0f -> viewBox.width()
        else -> 24f
    }
    val height: Float = when {
        svg.documentHeight > 0f -> svg.documentHeight
        viewBox != null && viewBox.height() > 0f -> viewBox.height()
        else -> 24f
    }

    init { require(width.isFinite() && height.isFinite() && width > 0 && height > 0) }

    /** 宽高由调用方明确选择，SVG 自身的 preserveAspectRatio 规则仍然生效。 */
    fun render(widthPx: Int, heightPx: Int): Bitmap {
        require(widthPx > 0 && heightPx > 0 && widthPx.toLong() * heightPx <= MAX_PIXELS) {
            "SVG bitmap dimensions exceed the supported range"
        }
        svg.setDocumentWidth(widthPx.toFloat())
        svg.setDocumentHeight(heightPx.toFloat())
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        try {
            svg.renderToCanvas(Canvas(bitmap))
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    fun renderFitted(maxSidePx: Int): Bitmap {
        require(maxSidePx > 0)
        val scale = maxSidePx / max(width, height)
        return render((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    }

    /** 标识入口保留既有正方形画布和按比例居中的规则。 */
    internal fun renderSquare(sizePx: Int): Bitmap {
        require(sizePx > 0 && sizePx.toLong() * sizePx <= MAX_PIXELS)
        val scale = sizePx / max(width, height)
        val scaledWidth = width * scale
        val scaledHeight = height * scale
        svg.setDocumentWidth(scaledWidth)
        svg.setDocumentHeight(scaledHeight)
        val picture = svg.renderToPicture()
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.translate((sizePx - scaledWidth) / 2f, (sizePx - scaledHeight) / 2f)
            picture.draw(canvas)
        }
    }

    companion object {
        const val MAX_PIXELS: Long = 16L * 1024 * 1024
        internal fun read(input: InputStream): SvgImageAsset = SvgImageAsset(SVG.getFromInputStream(input))
    }
}
