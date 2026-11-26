package com.ai.assistance.operit.core.tools.commands

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.resources.ManagedResourceFiles
import com.ai.assistance.operit.data.theme.script.ThemeScriptContract
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer

/** 在调用方的 IO 上下文中导入主题资源；只有配置落盘后才能提交这一批文件。 */
internal class ThemeCommandAssets(private val context: Context) {
    fun prepare(changes: Map<String, Any?>, current: ThemePreferenceValues): Prepared {
        val normalized = changes.toMutableMap()
        val created = mutableListOf<File>()
        val root = context.filesDir.canonicalFile
        require(root.isDirectory) { "Theme resource directory is unavailable" }
        try {
            ThemeCommandFields.resourceKinds.forEach { (field, declaredKind) ->
                if (!changes.containsKey(field) || changes[field] == null) return@forEach
                val reference = changes[field]
                require(reference is String && reference.isNotBlank()) {
                    "$field must be a non-empty local resource reference"
                }
                val kind = if (field == "background_image_uri") {
                    val mediaType = if (changes.containsKey("background_media_type")) {
                        changes["background_media_type"]
                    } else {
                        current.string("background_media_type")
                    }
                    require(mediaType is String && (mediaType == "image" || mediaType == "video")) {
                        "background_media_type must be image or video"
                    }
                    mediaType
                } else {
                    declaredKind
                }
                require(kind in setOf("image", "video", "font")) { "Unsupported resource kind: $kind" }
                val prefix = resourcePrefix(field, kind)
                val limit = if (kind == "video") MAX_VIDEO_BYTES else ThemeScriptContract.MAX_ASSET_BYTES.toLong()
                // 即使来源已受管理，也保留独立副本，避免其他配置释放旧引用后使本批引用悬空。
                val target = File.createTempFile("${prefix}_", ".$kind", root)
                created += target
                openSource(reference, limit).use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(count.toLong() <= limit - copied) { "$field exceeds the resource size limit" }
                            output.write(buffer, 0, count)
                            copied += count
                        }
                        require(copied > 0L) { "$field is empty" }
                    }
                }
                when (kind) {
                    "image" -> validateImage(target)
                    "video" -> validateVideo(target)
                    "font" -> requireNotNull(Typeface.Builder(target).build()) { "$field is not a loadable font" }
                }
                normalized[field] = if (kind == "font") target.absolutePath else Uri.fromFile(target).toString()
            }
            return Prepared(normalized.toMap(), created.toList(), root)
        } catch (failure: Throwable) {
            try {
                discardCreated(root, created)
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    private fun openSource(reference: String, limit: Long): InputStream {
        if (reference.startsWith("/")) return openFile(File(reference), limit)
        val uri = Uri.parse(reference)
        return when (uri.scheme) {
            "file" -> {
                require(uri.query == null && uri.fragment == null) { "File references cannot contain a query or fragment" }
                require(uri.authority.isNullOrEmpty()) { "File resources must be local" }
                openFile(File(URI(reference)), limit)
            }
            "content" -> {
                require(!uri.authority.isNullOrEmpty()) { "Content resources require an authority" }
                val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?: throw IOException("Cannot open the content resource")
                try {
                    require(descriptor.length != 0L && descriptor.length <= limit) {
                        "Content resource is empty or exceeds the resource size limit"
                    }
                    // 此流关闭时一并关闭描述符；未知长度的提供方仍由复制循环执行有界读取。
                    descriptor.createInputStream()
                } catch (failure: Throwable) {
                    try {
                        descriptor.close()
                    } catch (closeFailure: Throwable) {
                        failure.addSuppressed(closeFailure)
                    }
                    throw failure
                }
            }
            else -> throw IllegalArgumentException("Only absolute Android paths, file URIs and content URIs are accepted")
        }
    }

    private fun openFile(file: File, limit: Long): InputStream {
        require(file.isAbsolute && file.isFile && file.canRead()) { "Resource must be a readable local file" }
        require(file.length() in 1L..limit) { "Resource is empty or exceeds the resource size limit" }
        return file.inputStream()
    }

    private fun validateImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Resource is not a decodable image" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= ThemeScriptContract.MAX_BITMAP_PIXELS) {
            "Image exceeds the theme pixel limit"
        }
        // 验证真实解码能力，同时避免为了验证就分配整张大图。
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_SIDE || bounds.outHeight / sample > MAX_IMAGE_SIDE ||
            (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > MAX_VALIDATION_PIXELS
        ) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }) ?: throw IllegalArgumentException("Resource image data cannot be decoded")
        bitmap.recycle()
    }

    private fun validateVideo(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IllegalArgumentException("Background resource has no video track")
            val format = extractor.getTrackFormat(track)
            require(format.containsKey(MediaFormat.KEY_WIDTH) && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                "Video dimensions are unavailable"
            }
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            require(width > 0 && height > 0 && width.toLong() * height <= ThemeScriptContract.MAX_BITMAP_PIXELS) {
                "Video exceeds the theme pixel limit"
            }
            extractor.selectTrack(track)
            // 只读取首个样本，单帧也受现有资源字节预算约束，不把整个视频载入内存。
            val sample = ByteBuffer.allocateDirect(ThemeScriptContract.MAX_ASSET_BYTES)
            require(extractor.readSampleData(sample, 0) > 0) { "Background video has no readable video samples" }
        } finally {
            extractor.release()
        }
    }

    private fun resourcePrefix(field: String, kind: String): String = when (field) {
        "background_image_uri" -> if (kind == "video") "background_video" else "background"
        "custom_user_avatar_uri" -> "user_avatar"
        "custom_ai_avatar_uri" -> "ai_avatar"
        "global_user_avatar_uri" -> "global_user_avatar"
        "custom_font_path" -> "custom_font"
        "bubble_user_custom_font_path" -> "bubble_user_font"
        "bubble_ai_custom_font_path" -> "bubble_ai_font"
        "bubble_user_image_uri" -> "bubble_user"
        "bubble_ai_image_uri" -> "bubble_ai"
        else -> throw IllegalArgumentException("Unsupported resource field: $field")
    }

    internal class Prepared internal constructor(
        val changes: Map<String, Any?>,
        val createdFiles: List<File>,
        private val root: File,
    ) {
        private var committed = false

        @Synchronized
        fun commit() {
            committed = true
        }

        @Synchronized
        fun discard() {
            if (!committed) discardCreated(root, createdFiles)
        }
    }

    companion object {
        // 与 ThemeArchive 的普通主题单资源上限保持一致。
        private const val MAX_VIDEO_BYTES = 256L * 1024 * 1024
        private const val MAX_IMAGE_SIDE = 4096
        private const val MAX_VALIDATION_PIXELS = 1_048_576L

        private fun discardCreated(root: File, created: List<File>) {
            val failures = mutableListOf<File>()
            ManagedResourceFiles(root).deleteUnreferenced(
                created.map { Uri.fromFile(it).toString() }.toSet(),
                emptySet(),
            ) { failures += it }
            if (failures.isNotEmpty()) {
                throw IOException("Cannot discard ${failures.size} uncommitted theme resources")
            }
        }
    }
}
