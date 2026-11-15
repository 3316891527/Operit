package com.ai.assistance.operit.core.files.transfer

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalFileDeletionTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun recursiveDeletionRemovesHiddenFilesAndEmptyFoldersWithoutFollowingLinks() {
        val outside = temporary.newFolder("outside")
        File(outside, "keep").writeText("保留")
        val root = temporary.newFolder("root")
        File(root, ".hidden").writeText("删除")
        File(root, "empty").mkdir()
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        LocalFileDeletion.delete(root.toPath(), true) {}
        assertFalse(root.exists())
        assertEquals("保留", File(outside, "keep").readText())
    }
    @Test fun cancellationStopsInsideDirectoryAndDoesNotDeleteRemainingItems() {
        val root = temporary.newFolder("cancel")
        repeat(10) { File(root, "file$it").writeText("内容") }
        var checks = 0
        assertThrows(CancellationException::class.java) {
            LocalFileDeletion.delete(root.toPath(), true) { if (++checks == 5) throw CancellationException("取消") }
        }
        assertTrue(root.exists())
        assertTrue(root.listFiles()!!.size in 1..9)
    }
    @Test fun nonRecursiveDirectoryDeletionFailsWithoutRemovingChildren() {
        val root = temporary.newFolder("nonrecursive")
        File(root, "keep").writeText("保留")
        assertThrows(java.nio.file.DirectoryNotEmptyException::class.java) { LocalFileDeletion.delete(root.toPath(), false) {} }
        assertTrue(File(root, "keep").exists())
    }
}
