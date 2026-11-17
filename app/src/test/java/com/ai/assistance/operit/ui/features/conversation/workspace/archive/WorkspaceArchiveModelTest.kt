package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import org.junit.Assert.*
import java.io.IOException
import net.lingala.zip4j.exception.ZipException
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceBrowserSettings
import org.junit.Test
import com.google.gson.JsonParser

class WorkspaceArchiveModelTest {
    private fun entry(path: String, directory: Boolean = false) = WorkspaceArchiveEntry(path, directory, if (directory) null else 1L)

    @Test fun longSuffixesAndZipContainersRouteToArchives() {
        mapOf("A.TAR.GZ" to WorkspaceArchiveKind.TAR_GZIP, "a.tgz" to WorkspaceArchiveKind.TAR_GZIP,
            "a.tar.bz2" to WorkspaceArchiveKind.TAR_BZIP2, "a.tar.xz" to WorkspaceArchiveKind.TAR_XZ,
            "a.tar.zst" to WorkspaceArchiveKind.TAR_ZSTD, "a.7z" to WorkspaceArchiveKind.SEVEN_Z,
            "a.rar" to WorkspaceArchiveKind.RAR, "a.tar" to WorkspaceArchiveKind.TAR,
            "a.toolpkg" to WorkspaceArchiveKind.ZIP, "a.apk" to WorkspaceArchiveKind.ZIP,
            "a.jar" to WorkspaceArchiveKind.ZIP).forEach { (path, kind) ->
            assertEquals(kind, workspaceArchiveKind(path))
            assertEquals(WorkspaceFileRoute.ARCHIVE, workspaceFileRoute(path, "text/plain"))
        }
        assertNull(workspaceArchiveKind("a.docx"))
        assertNull(workspaceArchiveKind("a.crx"))
    }
    @Test fun extensionlessAndTextFilesUseTextWhileDocumentsKeepPreview() {
        listOf("/folder/README", "/folder/.env", "file.JSON", "source.kt", "index.html").forEach {
            assertEquals(WorkspaceFileRoute.TEXT, workspaceFileRoute(it))
        }
        assertEquals(WorkspaceFileRoute.TEXT, workspaceFileRoute("README", "image/png"))
        listOf("a.docx", "a.xlsx", "a.pdf", "a.svg", "a.jpg").forEach {
            assertEquals(WorkspaceFileRoute.PREVIEW, workspaceFileRoute(it))
        }
        assertEquals(WorkspaceFileRoute.TEXT, workspaceFileRoute("a.ts", "text/plain"))
        assertEquals(WorkspaceFileRoute.PREVIEW, workspaceFileRoute("a.ts", "video/mp2t"))
        assertEquals(WorkspaceFileRoute.SYSTEM, workspaceFileRoute("a.unknown"))
    }
    @Test fun everyBundledTextMateFileAssociationUsesTheEditorRoute() {
        val catalog = checkNotNull(javaClass.getResourceAsStream("/catalog.json")).bufferedReader(Charsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject
        }
        // 输入来自实际资源清单，验证文件路由与高亮目录之间的完整关联。
        val paths = buildList {
            addAll(catalog.getAsJsonObject("extensions").keySet().map { "sample$it" })
            addAll(catalog.getAsJsonObject("fileNames").keySet())
            addAll(catalog.getAsJsonObject("fileNamePrefixes").keySet().map { "${it}workspace" })
        }
        assertTrue(paths.size > 1000)
        for (path in paths) {
            assertEquals(path, WorkspaceFileRoute.TEXT, workspaceFileRoute(path, "application/octet-stream"))
        }
    }
    @Test fun additionalLanguagesAndActionScriptUseTextWhileBinaryFormatsKeepTheirRoutes() {
        for (path in listOf("/src/Main.AS", "src/app.clj", "src/app.ex", "src/app.erl", "src/app.hs",
            "src/app.jl", "src/app.nim", "src/app.nix", "src/app.rkt", "src/app.scala", "src/app.sol", "src/app.zig")) {
            assertEquals(path, WorkspaceFileRoute.TEXT, workspaceFileRoute(path, "application/octet-stream"))
        }
        assertEquals(WorkspaceFileRoute.ARCHIVE, workspaceFileRoute("app.apk", "text/plain"))
        assertEquals(WorkspaceFileRoute.PREVIEW, workspaceFileRoute("clip.mod", "audio/mod"))
        assertEquals(WorkspaceFileRoute.PREVIEW, workspaceFileRoute("drawing.svg", "image/svg+xml"))
        assertEquals(WorkspaceFileRoute.SYSTEM, workspaceFileRoute("Main.class", "application/java-vm"))
        assertEquals(WorkspaceFileRoute.SYSTEM, workspaceFileRoute("movie.swf", "application/x-shockwave-flash"))
    }
    @Test fun pathsRejectTraversalAbsolutePathsAndNulls() {
        listOf("../outside", "a/../../outside", "/root", "\\root", "C:/root", "a\\..\\outside", "a\u0000b").forEach {
            assertThrows(IllegalArgumentException::class.java) { workspaceArchivePath(it) }
        }
        assertEquals("folder/中文.txt", workspaceArchivePath("./folder//中文.txt"))
        assertEquals("a/b", workspaceArchivePath("a\\b"))
    }
    @Test fun directoryListingSynthesizesParentsAndDoesNotLeakPrefixSiblings() {
        val entries = listOf(entry("folder/deep/a.txt"), entry("folder/b.txt"), entry("folder2/c.txt"), entry("z.txt"))
        assertEquals(listOf("folder", "folder2", "z.txt"), workspaceArchiveChildren(entries, "").map { it.path })
        assertEquals(listOf("folder/deep", "folder/b.txt"), workspaceArchiveChildren(entries, "folder").map { it.path })
        assertEquals(listOf("folder/deep/a.txt", "folder/b.txt"), workspaceArchiveSelection(entries, setOf("folder")).map { it.path })
    }
    @Test fun duplicatePathsAndFilesUsedAsParentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { validateWorkspaceArchiveEntries(listOf(entry("a"), entry("a"))) }
        assertThrows(IllegalArgumentException::class.java) { validateWorkspaceArchiveEntries(listOf(entry("a"), entry("a/b"))) }
        validateWorkspaceArchiveEntries(listOf(entry("a", true), entry("a/b")))
    }
    @Test fun archiveListingHonorsDotFilePreferenceWithoutChangingTheIndex() {
        val source = WorkspaceLocation("/package.zip")
        val entries = listOf(entry(".git/config"), entry(".env"), entry("visible.txt"))
        assertEquals(listOf("visible.txt"), workspaceArchiveVisibleChildren(entries, "", source, WorkspaceBrowserSettings()).map { it.path })
        assertEquals(setOf(".git", ".env", "visible.txt"), workspaceArchiveVisibleChildren(entries, "", source,
            WorkspaceBrowserSettings(showDotFiles = true)).map { it.path }.toSet())
        assertEquals(3, entries.size)
    }
    @Test fun archiveManualHiddenRulesStayBoundToTheirSource() {
        val source = WorkspaceLocation("/package.zip")
        val entries = listOf(entry("private/file.txt"), entry("public.txt"))
        val settings = WorkspaceBrowserSettings(hidden = setOf(WorkspaceLocation("/package.zip!/private")))
        assertEquals(listOf("public.txt"), workspaceArchiveVisibleChildren(entries, "", source, settings).map { it.path })
        assertEquals(2, workspaceArchiveVisibleChildren(entries, "", WorkspaceLocation("/other.zip"), settings).size)
        assertEquals(2, workspaceArchiveVisibleChildren(entries, "", source, settings.copy(showManuallyHidden = true)).size)
    }
    @Test fun decryptionIsRequestedOnlyForEncryptedContent() {
        requireWorkspaceArchivePassword(listOf(entry("plain.txt")), "")
        assertThrows(WorkspaceArchivePasswordRequiredException::class.java) {
            requireWorkspaceArchivePassword(listOf(entry("private.txt").copy(encrypted = true)), "")
        }
        requireWorkspaceArchivePassword(listOf(entry("private.txt").copy(encrypted = true)), "secret")
    }
    @Test fun passwordChallengesDoNotMisclassifyPathsOrCorruptData() {
        val wrong = ZipException("口令错误", ZipException.Type.WRONG_PASSWORD)
        assertTrue(workspaceArchiveNeedsPassword(wrong))
        assertTrue(workspaceArchiveNeedsPassword(IOException("读取失败", wrong)))
        assertFalse(workspaceArchiveNeedsPassword(IOException("/sdcard/password/archive.zip")))
        assertFalse(workspaceArchiveNeedsPassword(ZipException("校验失败", ZipException.Type.CHECKSUM_MISMATCH)))
    }
}
