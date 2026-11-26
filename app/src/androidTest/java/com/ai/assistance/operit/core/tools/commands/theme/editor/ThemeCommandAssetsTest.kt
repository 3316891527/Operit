package com.ai.assistance.operit.core.tools.commands.theme.editor

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.resources.ManagedResourceFiles
import com.ai.assistance.operit.data.theme.script.ThemeScriptContract
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 只使用隔离缓存目录和合成素材，不读取现有偏好或用户附件。 */
@RunWith(AndroidJUnit4::class)
class ThemeCommandAssetsTest {
    private lateinit var application: Context
    private lateinit var workspace: File
    private lateinit var managed: File
    private lateinit var sources: File
    private lateinit var assets: ThemeCommandAssets
    private val current = ThemePreferenceValues.defaultVisual()

    @Before
    fun setUp() {
        application = InstrumentationRegistry.getInstrumentation().targetContext
        workspace = File(application.cacheDir, "theme-command-assets-${UUID.randomUUID()}")
        managed = File(workspace, "files").apply { check(mkdirs()) }
        sources = File(workspace, "sources").apply { check(mkdirs()) }
        assets = ThemeCommandAssets(object : ContextWrapper(application) {
            override fun getFilesDir(): File = managed
        })
    }

    @After
    fun tearDown() {
        check(workspace.deleteRecursively())
    }

    @Test
    fun allImageFieldsBecomeIndependentTrackedResources() {
        val source = image(File(sources, "中文 空格.bin"))
        val fields = ThemeCommandFields.resourceKinds.filterValues { it == "image" }.keys
        val changes: Map<String, Any?> = fields.associateWith { source.absolutePath } +
            ("show_message_timestamp" to true)
        val prepared = assets.prepare(changes, current)
        try {
            assertEquals(fields.size, prepared.createdFiles.size)
            assertEquals(fields.size, prepared.createdFiles.toSet().size)
            assertEquals(true, prepared.changes["show_message_timestamp"])
            fields.forEach { field ->
                val reference = prepared.changes[field] as String
                assertEquals("file", Uri.parse(reference).scheme)
                val imported = File(requireNotNull(Uri.parse(reference).path))
                assertEquals(managed.canonicalFile, imported.parentFile.canonicalFile)
                assertNotEquals(source.canonicalFile, imported.canonicalFile)
                val decoded = requireNotNull(BitmapFactory.decodeFile(imported.absolutePath))
                assertEquals(3, decoded.width)
                assertEquals(2, decoded.height)
                decoded.recycle()
            }
            val references = ManagedResourceFiles.references(prepared.changes.filterValues { it != null }.mapValues { requireNotNull(it.value) })
            assertEquals(fields.size, references.size)
            val deleted = ManagedResourceFiles(managed).deleteUnreferenced(references, emptySet()) {
                throw AssertionError("合成资源应能被现有清理机制删除：$it")
            }
            assertEquals(prepared.createdFiles.map { it.canonicalFile }.toSet(), deleted)
            assertTrue(source.isFile)
        } finally {
            prepared.discard()
        }
    }

    @Test
    fun fileUriCopySurvivesSourceDeletionAndCommittedDiscard() {
        val source = image(File(sources, "附件 空格.png"))
        val prepared = assets.prepare(mapOf("custom_user_avatar_uri" to Uri.fromFile(source).toString()), current)
        val imported = prepared.createdFiles.single()
        assertTrue(source.delete())
        prepared.commit()
        prepared.discard()
        prepared.discard()
        assertTrue(imported.isFile)
        assertEquals(Uri.fromFile(imported).toString(), prepared.changes["custom_user_avatar_uri"])
    }

    @Test
    fun authorizedContentUriIsCopiedBeforeAttachmentRemoval() {
        val source = image(File(sources, "provider.png"))
        val uri = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", source)
        val prepared = assets.prepare(mapOf("bubble_ai_image_uri" to uri.toString()), current)
        try {
            val imported = prepared.createdFiles.single()
            assertEquals(Uri.fromFile(imported).toString(), prepared.changes["bubble_ai_image_uri"])
            assertTrue(source.delete())
            assertNotNull(BitmapFactory.decodeFile(imported.absolutePath)?.also { it.recycle() })
        } finally {
            prepared.discard()
        }
        assertTrue(managed.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun nullClearsAllResourceFieldsWithoutCreatingFiles() {
        val changes: Map<String, Any?> = ThemeCommandFields.resourceKinds.keys.associateWith { null }
        val prepared = assets.prepare(changes, current)
        assertEquals(changes, prepared.changes)
        assertTrue(prepared.createdFiles.isEmpty())
        prepared.discard()
        assertTrue(managed.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun laterFailureDiscardsWholeBatchAndProtectsExistingManagedSource() {
        val existing = image(File(managed, "user_avatar_existing.png"))
        val bad = File(sources, "invalid.png").apply { writeText("这不是图片", Charsets.UTF_8) }
        val imageFields = ThemeCommandFields.resourceKinds.filterValues { it == "image" }.keys.toList()
        expectFailure {
            assets.prepare(linkedMapOf(imageFields[0] to existing.absolutePath, imageFields[1] to bad.absolutePath), current)
        }
        assertEquals(setOf(existing), managed.listFiles().orEmpty().toSet())
        assertTrue(bad.isFile)
    }

    @Test
    fun separateBatchesNeverDeleteEachOthersFilesOrOriginal() {
        val source = image(File(managed, "user_avatar_original.png"))
        val first = assets.prepare(mapOf("custom_user_avatar_uri" to source.absolutePath), current)
        val second = assets.prepare(mapOf("custom_user_avatar_uri" to source.absolutePath), current)
        try {
            assertNotEquals(first.createdFiles.single(), second.createdFiles.single())
            first.discard()
            first.discard()
            assertFalse(first.createdFiles.single().exists())
            assertTrue(second.createdFiles.single().isFile)
            assertTrue(source.isFile)
            second.commit()
            second.discard()
            assertTrue(second.createdFiles.single().isFile)
        } finally {
            first.discard()
            second.discard()
        }
    }

    @Test
    fun forgedImageFontAndVideoAreRejectedWithoutOrphans() {
        val forged = File(sources, "forged.ttf").apply { writeText("invalid resource", Charsets.UTF_8) }
        listOf("custom_ai_avatar_uri", "custom_font_path", "bubble_user_custom_font_path", "bubble_ai_custom_font_path").forEach { field ->
            expectFailure { assets.prepare(mapOf(field to forged.absolutePath), current) }
            assertTrue(managed.listFiles().orEmpty().isEmpty())
        }
        expectFailure {
            assets.prepare(mapOf("background_image_uri" to forged.absolutePath, "background_media_type" to "video"), current)
        }
        assertTrue(managed.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun unsupportedSourcesAndOversizedFilesFailBeforePersistence() {
        listOf("https://example.com/image.png", "http://example.com/font.ttf", "relative.png", "file://remote/image.png", "file:///tmp/a.png?query=1").forEach { source ->
            expectFailure { assets.prepare(mapOf("custom_ai_avatar_uri" to source), current) }
            assertTrue(managed.listFiles().orEmpty().isEmpty())
        }
        val oversized = File(sources, "oversized.png")
        RandomAccessFile(oversized, "rw").use { it.setLength(ThemeScriptContract.MAX_ASSET_BYTES.toLong() + 1) }
        expectFailure { assets.prepare(mapOf("custom_ai_avatar_uri" to oversized.absolutePath), current) }
        assertTrue(managed.listFiles().orEmpty().isEmpty())
        val empty = File(sources, "empty.png").apply { check(createNewFile()) }
        expectFailure { assets.prepare(mapOf("custom_ai_avatar_uri" to empty.absolutePath), current) }
        assertTrue(managed.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun fontsUseAbsolutePathsAndRemainVisibleToManagedCleanup() {
        val source = File(sources, "合成字体.ttf").apply { writeBytes(syntheticFont()) }
        val fields = ThemeCommandFields.resourceKinds.filterValues { it == "font" }.keys
        val prepared = assets.prepare(fields.associateWith { Uri.fromFile(source).toString() }, current)
        try {
            fields.forEach { field ->
                val path = prepared.changes[field] as String
                assertTrue(path.startsWith("/"))
                assertEquals(managed.canonicalFile, File(path).parentFile.canonicalFile)
                assertNotNull(Typeface.Builder(File(path)).build())
            }
            assertEquals(fields.size, prepared.createdFiles.size)
        } finally {
            prepared.discard()
        }
        assertTrue(source.isFile)
        assertTrue(managed.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun backgroundUsesChangedMediaTypeAndCurrentMediaTypeAndRejectsAudioOnly() {
        val source = video(File(sources, "synthetic.mp4"))
        val videoCurrent = current.withString("background_media_type", "video")
        val fromCurrent = assets.prepare(mapOf("background_image_uri" to source.absolutePath), videoCurrent)
        try {
            assertTrue(fromCurrent.createdFiles.single().name.startsWith("background_video_"))
        } finally {
            fromCurrent.discard()
        }
        val fromChanges = assets.prepare(mapOf("background_image_uri" to source.absolutePath, "background_media_type" to "video"), current)
        fromChanges.discard()
        val picture = image(File(sources, "background.png"))
        val changedToImage = assets.prepare(mapOf("background_image_uri" to picture.absolutePath, "background_media_type" to "image"), videoCurrent)
        changedToImage.discard()
        expectFailure { assets.prepare(mapOf("background_image_uri" to picture.absolutePath), videoCurrent) }
        expectFailure { assets.prepare(mapOf("background_image_uri" to source.absolutePath), current) }
        val audio = File(sources, "audio.wav").apply {
            val bytes = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(52).put("WAVEfmt ".toByteArray()).putInt(16)
            bytes.putShort(1.toShort()).putShort(1.toShort()).putInt(8000).putInt(16000)
            bytes.putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(16)
            writeBytes(bytes.array())
        }
        expectFailure { assets.prepare(mapOf("background_image_uri" to audio.absolutePath), videoCurrent) }
        assertTrue(managed.listFiles().orEmpty().isEmpty())
    }

    private fun image(file: File): File {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xFF346699.toInt())
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun expectFailure(block: () -> Unit): Exception {
        try {
            block()
        } catch (failure: Exception) {
            return failure
        }
        throw AssertionError("无效资源必须失败，不能写入偏好或静默采用默认素材")
    }

    /** 编码一帧合成画面，验证真实容器和视频轨，不依赖设备上的用户媒体。 */
    private fun video(file: File): File {
        val encoder = MediaCodec.createEncoderByType("video/avc")
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var encoderStarted = false
        var muxerStarted = false
        try {
            val format = MediaFormat.createVideoFormat("video/avc", 320, 240).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 64000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 1)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            encoderStarted = true
            val inputIndex = encoder.dequeueInputBuffer(5_000_000)
            check(inputIndex >= 0) { "合成视频编码器未提供输入缓冲" }
            requireNotNull(encoder.getInputImage(inputIndex)).use { frame ->
                check(frame.width == 320 && frame.height == 240 && frame.planes.size == 3)
                // 按实际平面的行步长与像素步长写入，避免假定灵活 YUV 是连续字节布局。
                frame.planes.forEachIndexed { index, plane ->
                    val width = if (index == 0) frame.width else (frame.width + 1) / 2
                    val height = if (index == 0) frame.height else (frame.height + 1) / 2
                    val buffer = plane.buffer
                    val start = buffer.position()
                    for (row in 0 until height) {
                        for (column in 0 until width) {
                            buffer.put(start + row * plane.rowStride + column * plane.pixelStride, 0x80.toByte())
                        }
                    }
                }
            }
            encoder.queueInputBuffer(inputIndex, 0, 320 * 240 * 3 / 2, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            val info = MediaCodec.BufferInfo()
            var track = -1
            var finished = false
            var samples = 0
            repeat(50) {
                if (finished) return@repeat
                val outputIndex = encoder.dequeueOutputBuffer(info, 100_000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(!muxerStarted)
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outputIndex >= 0) {
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted)
                            muxer.writeSampleData(track, requireNotNull(encoder.getOutputBuffer(outputIndex)), info)
                            samples++
                        }
                        finished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally {
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            check(finished && samples > 0) { "合成视频编码未完成" }
        } finally {
            try {
                if (encoderStarted) encoder.stop()
            } finally {
                encoder.release()
                try {
                    if (muxerStarted) muxer.stop()
                } finally {
                    muxer.release()
                }
            }
        }
        return file
    }

    /** 构造含一个空字形的最小 TrueType 字体，全部表和校验和都来自合成数据。 */
    private fun syntheticFont(): ByteArray {
        fun table(size: Int, fill: ByteBuffer.() -> Unit): ByteArray =
            ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).apply(fill).array()
        fun ByteBuffer.shorts(vararg values: Int) { values.forEach { putShort(it.toShort()) } }
        fun checksum(bytes: ByteArray): Int {
            val padded = bytes.copyOf((bytes.size + 3) / 4 * 4)
            val data = ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN)
            var sum = 0
            while (data.hasRemaining()) sum += data.int
            return sum
        }
        val family = "OperitSynthetic".toByteArray(Charsets.UTF_16BE)
        val style = "Regular".toByteArray(Charsets.UTF_16BE)
        val tables = sortedMapOf(
            "OS/2" to table(78) {
                shorts(0, 600, 400, 5, 0, 650, 600, 0, 75, 650, 600, 0, 350, 50, 250, 0)
                put(ByteArray(10 + 16))
                put("TEST".toByteArray(Charsets.US_ASCII))
                shorts(0x40, 65, 65, 800, -200, 0, 800, 200)
            },
            "cmap" to table(44) {
                shorts(0, 1, 3, 1)
                putInt(12)
                shorts(4, 32, 0, 4, 4, 1, 0, 65, 0xFFFF, 0, 65, 0xFFFF, -65, 1, 0, 0)
            },
            "glyf" to ByteArray(12),
            "head" to table(54) {
                putInt(0x00010000).putInt(0x00010000).putInt(0).putInt(0x5F0F3CF5)
                shorts(3, 1000)
                putLong(0).putLong(0)
                shorts(0, 0, 0, 0, 0, 8, 2, 0, 0)
            },
            "hhea" to table(36) {
                putInt(0x00010000)
                shorts(800, -200, 0, 600, 0, 600, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1)
            },
            "hmtx" to table(4) { shorts(600, 0) },
            "loca" to table(4) { shorts(0, 6) },
            "maxp" to table(32) {
                putInt(0x00010000)
                shorts(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0)
            },
            "name" to table(30 + family.size + style.size) {
                shorts(0, 2, 30, 3, 1, 0x0409, 1, family.size, 0, 3, 1, 0x0409, 2, style.size, family.size)
                put(family).put(style)
            },
            "post" to table(32) { putInt(0x00030000) },
        )
        val headerSize = 12 + tables.size * 16
        val size = headerSize + tables.values.sumOf { (it.size + 3) / 4 * 4 }
        val font = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        font.putInt(0x00010000).shorts(tables.size, 128, 3, tables.size * 16 - 128)
        var offset = headerSize
        var headOffset = -1
        tables.forEach { (tag, bytes) ->
            font.put(tag.toByteArray(Charsets.US_ASCII)).putInt(checksum(bytes)).putInt(offset).putInt(bytes.size)
            System.arraycopy(bytes, 0, font.array(), offset, bytes.size)
            if (tag == "head") headOffset = offset
            offset += (bytes.size + 3) / 4 * 4
        }
        check(headOffset >= 0)
        font.putInt(headOffset + 8, 0xB1B0AFBA.toInt() - checksum(font.array()))
        return font.array()
    }
}