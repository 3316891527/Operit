package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Read-only decoding. Preview is sampled; export uses the original oriented resolution. */
internal suspend fun loadImageEditBitmap(context: Context, uri: Uri, preview: Boolean): Bitmap =
    withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { context.getString(R.string.image_editor_load_failed) }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            context.getString(R.string.image_editor_load_failed)
        }
        // Source, output and annotation layer can be alive together. Never silently lower export quality.
        if (!preview) require(bounds.outWidth.toLong() * bounds.outHeight * 12L < Runtime.getRuntime().maxMemory() * 0.7) {
            context.getString(R.string.image_editor_too_large)
        }
        var sample = 1
        while (preview && max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val orientation = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            requireNotNull(BitmapFactory.decodeStream(input, null, options)) {
                context.getString(R.string.image_editor_load_failed)
            }
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) decoded else {
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (oriented !== decoded) decoded.recycle()
            oriented
        }
    }

/** Pixelation samples the image, but its exported strokes contain only the processed pixels. */
internal class ImageEditRenderer(private val source: Bitmap) : AutoCloseable {
    private val width = source.width.toFloat()
    private val height = source.height.toFloat()
    private val shortSide = min(width, height)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixelated = Bitmap.createScaledBitmap(source, max(1, source.width / 24), max(1, source.height / 24), false)
    private val mosaic = BitmapShader(pixelated, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(width / pixelated.width, height / pixelated.height) })
    }

    fun draw(canvas: Canvas, document: ImageEditDocument, pending: ImageEditMark? = null) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        canvas.drawBitmap(source, 0f, 0f, paint)
        val layer = canvas.saveLayer(0f, 0f, width, height, null)
        try {
            document.marks.forEach { drawMark(canvas, it) }
            if (pending != null) drawMark(canvas, pending)
        } finally {
            canvas.restoreToCount(layer)
        }
    }

    private fun drawMark(canvas: Canvas, mark: ImageEditMark) {
        paint.reset()
        paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        when (mark) {
            is ImageEditMark.Stroke -> {
                if (mark.points.isEmpty()) return
                paint.color = mark.color
                paint.strokeWidth = max(1f, mark.width * shortSide)
                paint.style = Paint.Style.STROKE
                when (mark.tool) {
                    ImageEditTool.MOSAIC -> { paint.shader = mosaic; paint.isFilterBitmap = false }
                    ImageEditTool.ERASER -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    else -> Unit
                }
                val path = Path().apply {
                    moveTo(mark.points.first().x * width, mark.points.first().y * height)
                    mark.points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
                }
                if (mark.points.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(mark.points[0].x * width, mark.points[0].y * height, paint.strokeWidth / 2f, paint)
                } else canvas.drawPath(path, paint)
            }
            is ImageEditMark.Shape -> {
                paint.color = mark.color
                paint.strokeWidth = max(1f, mark.width * shortSide)
                paint.style = Paint.Style.STROKE
                val x1 = mark.start.x * width
                val y1 = mark.start.y * height
                val x2 = mark.end.x * width
                val y2 = mark.end.y * height
                val rect = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                when (mark.tool) {
                    ImageEditTool.RECTANGLE -> canvas.drawRect(rect, paint)
                    ImageEditTool.OVAL -> canvas.drawOval(rect, paint)
                    ImageEditTool.ARROW -> {
                        canvas.drawLine(x1, y1, x2, y2, paint)
                        val angle = atan2(y2 - y1, x2 - x1)
                        val head = max(paint.strokeWidth * 4f, shortSide * 0.025f)
                        for (side in listOf(-1f, 1f)) {
                            val a = angle + side * 0.55f
                            canvas.drawLine(x2, y2, x2 - head * cos(a), y2 - head * sin(a), paint)
                        }
                    }
                    else -> Unit
                }
            }
            is ImageEditMark.Text -> {
                paint.color = mark.color
                paint.textSize = max(1f, mark.size * shortSide)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.CENTER
                val lines = mark.text.lines()
                val lineHeight = paint.fontSpacing
                val firstBaseline = mark.position.y * height - (lines.size - 1) * lineHeight / 2f - (paint.ascent() + paint.descent()) / 2f
                lines.forEachIndexed { index, line ->
                    canvas.drawText(line, mark.position.x * width, firstBaseline + index * lineHeight, paint)
                }
            }
        }
    }

    fun export(document: ImageEditDocument): Bitmap {
        val crop = document.crop
        val left = floor(crop.left * width).toInt().coerceIn(0, source.width - 1)
        val top = floor(crop.top * height).toInt().coerceIn(0, source.height - 1)
        val right = ceil(crop.right * width).toInt().coerceIn(left + 1, source.width)
        val bottom = ceil(crop.bottom * height).toInt().coerceIn(top + 1, source.height)
        val output = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.translate(-left.toFloat(), -top.toFloat())
            draw(canvas, document)
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }

    override fun close() { if (pixelated !== source) pixelated.recycle() }
}

/** The sole outgoing artifact is a flattened PNG in cleanOnExit, not a MediaStore item. */
internal suspend fun saveImageEdit(context: Context, source: Uri, document: ImageEditDocument): File =
    withContext(Dispatchers.IO) {
        val bitmap = loadImageEditBitmap(context, source, preview = false)
        try {
            ImageEditRenderer(bitmap).use { renderer ->
                val output = renderer.export(document)
                try {
                    currentCoroutineContext().ensureActive()
                    val file = File(OperitPaths.cleanOnExitDir(), "edited_${UUID.randomUUID()}.png")
                    try {
                        file.outputStream().buffered().use { stream ->
                            check(output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                context.getString(R.string.image_editor_save_failed)
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        file
                    } catch (error: Throwable) {
                        file.delete()
                        throw error
                    }
                } finally { output.recycle() }
            }
        } finally { bitmap.recycle() }
    }
