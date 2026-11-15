package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceBrowserSettings
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceArchiveZipTest {
    @get:Rule val temporary = TemporaryFolder()
    private val engine = WorkspaceArchiveEngine()
    private fun file(name: String, content: String) = File(temporary.newFolder(), name).apply { writeText(content) }
    private fun output() = File(temporary.newFolder(), "archive.zip")

    @Test fun encryptedZipRoundTripPreservesUnicodeNestedFilesAndEmptyDirectories() = runBlocking<Unit> {
        val root = temporary.newFolder("来源")
        File(root, "子目录").mkdir()
        File(root, "子目录/中文.txt").writeText("hello 中文\n")
        File(root, "空目录").mkdir()
        val archive = output()
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(root)), "secret", 1)
        engine.verify(archive, WorkspaceArchiveKind.ZIP, "secret")
        assertTrue(engine.list(archive, WorkspaceArchiveKind.ZIP, "").any { !it.directory && it.encrypted })
        val destination = temporary.newFolder()
        engine.extract(archive, WorkspaceArchiveKind.ZIP, "secret", emptySet(), destination)
        assertEquals("hello 中文\n", File(destination, "来源/子目录/中文.txt").readText())
        assertTrue(File(destination, "来源/空目录").isDirectory)
    }
    @Test fun zipRewriteRenamesDirectoriesRemovesEntriesAndAddsFilesWithoutChangingSource() = runBlocking<Unit> {
        val root = temporary.newFolder("folder")
        File(root, "keep.txt").writeText("keep")
        File(root, "remove.txt").writeText("remove")
        val original = output()
        engine.create(original, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(root)), "", 1)
        val before = original.readBytes()
        val changed = output()
        engine.rewriteZip(original, changed, "", remove = setOf("folder/remove.txt"), rename = "folder" to "renamed",
            additions = engine.inputs(listOf(file("new.txt", "new"))))
        assertArrayEquals(before, original.readBytes())
        val extracted = temporary.newFolder()
        engine.extract(changed, WorkspaceArchiveKind.ZIP, "", emptySet(), extracted)
        assertEquals("keep", File(extracted, "renamed/keep.txt").readText())
        assertFalse(File(extracted, "renamed/remove.txt").exists())
        assertEquals("new", File(extracted, "new.txt").readText())
    }
    @Test fun wrongPasswordsAndCorruptDataDoNotVerify() = runBlocking<Unit> {
        val archive = output()
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(file("file.txt", "private"))), "secret", 1)
        assertThrows(Exception::class.java) { runBlocking { engine.verify(archive, WorkspaceArchiveKind.ZIP, "wrong") } }
        val bytes = archive.readBytes()
        bytes[40] = (bytes[40].toInt() xor 0x40).toByte()
        archive.writeBytes(bytes)
        assertThrows(Exception::class.java) { runBlocking { engine.verify(archive, WorkspaceArchiveKind.ZIP, "secret") } }
    }
    @Test fun existingNamesAndFileParentConflictsRejectZipRewrite() = runBlocking<Unit> {
        val archive = output()
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(file("a", "a"), file("b", "b"))), "", 1)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.rewriteZip(archive, output(), "", rename = "a" to "b") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.rewriteZip(archive, output(), "", rename = "a" to "b/child") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            engine.rewriteZip(archive, output(), "", additions = engine.inputs(listOf(file("a", "duplicate"))))
        } }
    }
    @Test fun extractionRejectsTraversalBeforeCreatingAnOutsideFile() {
        val archive = output()
        ZipOutputStream(archive.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("../escape")); zip.write(byteArrayOf(1)); zip.closeEntry() }
        val destination = temporary.newFolder()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.extract(archive, WorkspaceArchiveKind.ZIP, "", emptySet(), destination) } }
        assertTrue(destination.listFiles()!!.isEmpty())
        assertFalse(File(destination.parentFile, "escape").exists())
    }
    @Test fun cancellationDuringRewritePreservesTheOriginalZip() = runBlocking<Unit> {
        val archive = output()
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(file("large.txt", "a".repeat(700_000)))), "", 1)
        val before = archive.readBytes()
        assertThrows(CancellationException::class.java) { runBlocking {
            val job = coroutineContext.job
            val cancelling = WorkspaceArchiveEngine { if (it.entry == "renamed.txt") job.cancel() }
            cancelling.rewriteZip(archive, output(), "", rename = "large.txt" to "renamed.txt")
        } }
        assertArrayEquals(before, archive.readBytes())
    }
    @Test fun rewritePreservesTheEncryptionStateOfRetainedEntries() = runBlocking<Unit> {
        val plain = output()
        engine.create(plain, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(file("plain.txt", "plain"))), "", 1)
        ZipFile(plain).use { it.setComment("archive comment") }
        val mixed = output()
        engine.rewriteZip(plain, mixed, "secret", additions = engine.inputs(listOf(file("private.txt", "private"))))
        ZipFile(mixed, "secret".toCharArray()).use { zip ->
            assertFalse(zip.getFileHeader("plain.txt").isEncrypted)
            assertTrue(zip.getFileHeader("private.txt").isEncrypted)
            assertEquals("archive comment", zip.comment)
            ZipFile(plain).use { original ->
                assertEquals(original.getFileHeader("plain.txt").lastModifiedTimeEpoch, zip.getFileHeader("plain.txt").lastModifiedTimeEpoch)
                assertArrayEquals(original.getFileHeader("plain.txt").externalFileAttributes, zip.getFileHeader("plain.txt").externalFileAttributes)
            }
        }
        engine.verify(mixed, WorkspaceArchiveKind.ZIP, "secret")
    }
    @Test fun sourceLinksAreRejectedBeforeCompression() {
        val root = temporary.newFolder()
        val target = File(root, "target").apply { writeText("data") }
        val link = File(root, "link")
        Files.createSymbolicLink(link.toPath(), target.toPath())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.inputs(listOf(root)) } }
    }
    @Test fun allExtractionKeepsHiddenFilesEvenWhenTheListFiltersThemOut() = runBlocking<Unit> {
        val root = temporary.newFolder("source")
        File(root, ".hidden").writeText("hidden data")
        File(root, "visible.txt").writeText("visible data")
        val archive = output()
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(root)), "", 1)
        val entries = engine.list(archive, WorkspaceArchiveKind.ZIP, "")
        assertEquals(listOf("visible.txt"), workspaceArchiveVisibleChildren(entries, "source", WorkspaceLocation(archive.path),
            WorkspaceBrowserSettings()).map { it.name })
        val destination = temporary.newFolder()
        engine.extract(archive, WorkspaceArchiveKind.ZIP, "", emptySet(), destination)
        assertEquals("hidden data", File(destination, "source/.hidden").readText())
        assertEquals("visible data", File(destination, "source/visible.txt").readText())
    }

    @Test fun extractionReportsRealEntryCountsAndUncompressedBytes() = runBlocking<Unit> {
        val folder = temporary.newFolder("many")
        File(folder, "first.txt").writeText("中文")
        File(folder, "sub").mkdir()
        File(folder, "sub/second.txt").writeText("x".repeat(600_000))
        File(folder, "empty").mkdir()
        val inputs = engine.inputs(listOf(folder))
        val archive = output()
        engine.create(archive, WorkspaceArchiveKind.ZIP, inputs, "", 1)
        val reports = mutableListOf<WorkspaceArchiveProgress>()
        WorkspaceArchiveEngine(nanoTime = { 0L }) { reports += it }
            .extract(archive, WorkspaceArchiveKind.ZIP, "", emptySet(), temporary.newFolder())
        val extraction = reports.filter { it.stage == WorkspaceArchiveStage.EXTRACTING }
        assertTrue(extraction.all { it.totalEntries == inputs.size })
        assertEquals((0..inputs.size).toList(), extraction.map { it.completedEntries }.distinct())
        assertEquals(inputs.filterNot { it.directory }.sumOf { it.size }, extraction.last().bytes)
        assertEquals(1f, checkNotNull(extraction.last().fraction), 0f)
        assertTrue(extraction.any { it.entry.endsWith("second.txt") && it.entryBytes > 0 })
    }

    @Test fun oneReaderOpensSeparateEntriesWithoutRepeatingTheDirectoryRead() = runBlocking<Unit> {
        val archive = output()
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(file("a.txt", "a"), file("b.txt", "b"))), "", 1)
        val reports = mutableListOf<WorkspaceArchiveProgress>()
        val reading = WorkspaceArchiveEngine { reports += it }
        reading.openReader(archive, WorkspaceArchiveKind.ZIP, "").use { reader ->
            reports.clear()
            val first = temporary.newFolder()
            reading.extract(reader, setOf("a.txt"), first)
            val second = temporary.newFolder()
            reading.extract(reader, setOf("b.txt"), second)
            assertEquals("a", File(first, "a.txt").readText())
            assertFalse(File(first, "b.txt").exists())
            assertEquals("b", File(second, "b.txt").readText())
            assertFalse(File(second, "a.txt").exists())
            assertTrue(reports.none { it.stage == WorkspaceArchiveStage.READING })
        }
    }

    @Test fun cancelledEntryIsNotCountedAsExtracted() = runBlocking<Unit> {
        val archive = output()
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(file("cancel.txt", "data"))), "", 1)
        val reports = mutableListOf<WorkspaceArchiveProgress>()
        assertThrows(CancellationException::class.java) { runBlocking {
            val job = coroutineContext.job
            WorkspaceArchiveEngine {
                reports += it
                if (it.stage == WorkspaceArchiveStage.EXTRACTING && it.entry == "cancel.txt") job.cancel()
            }.extract(archive, WorkspaceArchiveKind.ZIP, "", emptySet(), temporary.newFolder())
        } }
        assertTrue(reports.filter { it.stage == WorkspaceArchiveStage.EXTRACTING }.all { it.completedEntries == 0 })
    }

    @Test fun fastEntryCompletionPreservesBytesAcrossWritingVerificationAndExtraction() = runBlocking<Unit> {
        val inputs = engine.inputs(listOf(file("first.txt", "中文"), file("empty.txt", ""), file("last.txt", "x".repeat(8192))))
        val archive = output()
        val reports = mutableListOf<WorkspaceArchiveProgress>()
        // 固定时钟使所有字节更新都落在节流窗口内，完成通知必须独立保留真实数据。
        val frozen = WorkspaceArchiveEngine(nanoTime = { 0L }) { reports += it }
        frozen.create(archive, WorkspaceArchiveKind.ZIP, inputs, "", 1)
        frozen.verify(archive, WorkspaceArchiveKind.ZIP, "")
        frozen.extract(archive, WorkspaceArchiveKind.ZIP, "", emptySet(), temporary.newFolder())
        for (stage in listOf(WorkspaceArchiveStage.WRITING, WorkspaceArchiveStage.VERIFYING, WorkspaceArchiveStage.EXTRACTING)) {
            val phase = reports.filter { it.stage == stage }
            for ((index, input) in inputs.withIndex()) {
                val entry = phase.filter { it.entry == input.path }
                assertEquals(listOf(0L, input.size).distinct(), entry.map { it.entryBytes }.distinct())
                assertEquals(index, entry.first().completedEntries)
                val completed = entry.last()
                assertEquals(index + 1, completed.completedEntries)
                assertEquals(input.size, completed.entryBytes)
                assertNull(completed.entryTotal)
                assertNull(completed.entryFraction)
                assertEquals((index + 1).toFloat() / inputs.size, checkNotNull(completed.fraction), 0f)
            }
            assertEquals(inputs.sumOf { it.size }, phase.last().bytes)
            assertTrue(phase.zipWithNext().all { (before, after) -> checkNotNull(after.fraction) >= checkNotNull(before.fraction) })
        }
    }
}
