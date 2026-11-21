package com.ai.assistance.operit.core.tools.commands

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 在应用缓存中打包源码，拒绝符号链接，避免打包目录之外的文件或陷入循环。 */
internal object PlatformPackageFiles {
    fun archiveDirectory(directory: File, cacheDirectory: File): File {
        val root = directory.canonicalFile
        require(root.isDirectory) { "Not a ToolPkg source directory: $directory" }
        require(File(root, "manifest.hjson").isFile || File(root, "manifest.json").isFile) {
            "ToolPkg directory must contain manifest.hjson or manifest.json: $root"
        }
        check(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) { "Cannot create package staging directory" }
        require(!cacheDirectory.canonicalFile.toPath().startsWith(root.toPath())) { "Package staging directory must be outside the source directory" }
        val archive = File.createTempFile("operit-editor-", ".toolpkg", cacheDirectory)
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { output ->
                root.walkTopDown().onEnter { folder ->
                    require(!Files.isSymbolicLink(folder.toPath())) { "ToolPkg contains a symbolic link: $folder" }
                    folder.name !in setOf(".git", "__pycache__")
                }.filter { file ->
                    require(!Files.isSymbolicLink(file.toPath())) { "ToolPkg contains a symbolic link: $file" }
                    file.isFile && file.name !in setOf(".DS_Store", "Thumbs.db")
                }.forEach { file ->
                    output.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { input -> input.copyTo(output) }
                    output.closeEntry()
                }
            }
            return archive
        } catch (error: Exception) {
            archive.delete()
            throw error
        }
    }

    fun destination(directory: File, packageName: String, extension: String): File {
        val stem = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
        val digest = MessageDigest.getInstance("SHA-256").digest(packageName.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(directory, "$stem-$digest.$extension")
    }

    /** 先完整写入旁路文件，再替换正式包，扫描器不会看到复制到一半的脚本或归档。 */
    fun replace(source: File, target: File) {
        if (source.canonicalFile == target.canonicalFile) return
        val parent = requireNotNull(target.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create external package directory: $parent" }
        val staged = File.createTempFile(".operit-editor-", ".pending", parent)
        try {
            source.inputStream().use { input -> staged.outputStream().use { output -> input.copyTo(output) } }
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            staged.delete()
        }
    }
}