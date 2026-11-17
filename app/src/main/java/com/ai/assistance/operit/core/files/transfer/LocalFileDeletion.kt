package com.ai.assistance.operit.core.files.transfer

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** 单次遍历删除本机目录，逐项检查取消，链接只删除自身。 */
internal object LocalFileDeletion {
    fun delete(path: Path, recursive: Boolean, checkCancelled: () -> Unit) {
        checkCancelled()
        if (!recursive || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(path)
            return
        }
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkCancelled()
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkCancelled()
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                checkCancelled()
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
