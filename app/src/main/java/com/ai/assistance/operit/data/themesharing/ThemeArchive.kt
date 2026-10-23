package com.ai.assistance.operit.data.themesharing

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** 完整主题只接受根配置和 assets 资源；校验与解包均在预览缓存目录中完成。 */
internal object ThemeArchive {
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    private const val MAX_ASSET_BYTES = 256L * 1024 * 1024
    private const val MAX_ENTRIES = 32
    private val assetPath = Regex("assets/[A-Za-z0-9_-][A-Za-z0-9._-]*\\.[A-Za-z0-9]{1,10}")

    fun isAssetPath(path: String): Boolean =
        path.length <= 160 && assetPath.matches(path) && !path.contains("..")

    fun read(archive: File, directory: File): PreparedTheme {
        try {
            if (archive.length() > MAX_ARCHIVE_BYTES) tooLarge()
            if (!directory.mkdirs()) throw ThemeTransferException(ThemeTransferError.IO_ERROR)
            val assets = mutableMapOf<String, File>()
            var document: ThemeTransferDocument? = null
            val names = mutableSetOf<String>()
            var totalBytes = 0L
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (!names.add(name) || names.size > MAX_ENTRIES) invalid()
                    if (entry.isDirectory) {
                        if (name != "assets/") invalid()
                        continue
                    }
                    if (name != "manifest.json" && !isAssetPath(name)) invalid()
                    val limit =
                        if (name == "manifest.json") ThemeTransferSchema.MAX_JSON_BYTES.toLong()
                        else MAX_ASSET_BYTES
                    if (entry.size > limit) tooLarge()
                    val target = File(directory, name)
                    target.parentFile?.mkdirs()
                    val crc = CRC32()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var entryBytes = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                entryBytes += count
                                totalBytes += count
                                if (entryBytes > limit || totalBytes > MAX_ARCHIVE_BYTES) tooLarge()
                                crc.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            if (entryBytes != entry.size || crc.value != entry.crc) invalid()
                        }
                    }
                    if (name == "manifest.json") {
                        document = ThemeTransferSchema.decode(target.readBytes(), full = true)
                    } else {
                        if (target.length() == 0L)
                            throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
                        assets[name] = target
                    }
                }
            }
            val parsed =
                document ?: throw ThemeTransferException(ThemeTransferError.MISSING_MANIFEST)
            val references =
                parsed.values.strings
                    .filterKeys { it in ThemeTransferSchema.assetKeys }
                    .values
                    .toSet()
            if (!assets.keys.containsAll(references))
                throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
            if (assets.keys != references) invalid()
            return PreparedTheme(parsed, directory, assets)
        } catch (e: Exception) {
            directory.deleteRecursively()
            if (e is ThemeTransferException) throw e
            throw ThemeTransferException(ThemeTransferError.INVALID_ARCHIVE, e)
        }
    }

    fun write(output: OutputStream, document: ThemeTransferDocument, assets: Map<String, File>) {
        if (!document.full) invalid()
        val references =
            document.values.strings
                .filterKeys { it in ThemeTransferSchema.assetKeys }
                .values
                .toSet()
        if (assets.keys != references)
            throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
        val manifest = ThemeTransferSchema.encode(document)
        var total = manifest.size.toLong()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
            assets.forEach { (name, file) ->
                if (!isAssetPath(name) || !file.isFile || file.length() == 0L)
                    throw ThemeTransferException(ThemeTransferError.MISSING_ASSET)
                zip.putNextEntry(ZipEntry(name))
                total += file.inputStream().use { copyLimited(it, zip, MAX_ASSET_BYTES) }
                if (total > MAX_ARCHIVE_BYTES) tooLarge()
                zip.closeEntry()
            }
        }
    }

    fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > limit) tooLarge()
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun invalid(): Nothing =
        throw ThemeTransferException(ThemeTransferError.INVALID_ARCHIVE)

    private fun tooLarge(): Nothing = throw ThemeTransferException(ThemeTransferError.TOO_LARGE)
}
