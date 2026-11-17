package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation

class WorkspaceArchiveDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "archive-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val engine = WorkspaceArchiveEngine()
    @After fun clean() { root.deleteRecursively() }
    private suspend fun roundTrip(kind: WorkspaceArchiveKind) {
        val source = File(root, "中文.txt").apply { writeText("hello 中文\n") }
        val output = File(root, "archive")
        engine.create(output, kind, engine.inputs(listOf(source)), "", 1)
        engine.verify(output, kind, "")
        val destination = File(root, "extracted")
        engine.extract(output, kind, "", emptySet(), destination)
        assertEquals(source.readText(), File(destination, source.name).readText())
    }
    @Test fun sevenZipUsesThePackagedNativeLibrary() = runBlocking<Unit> { roundTrip(WorkspaceArchiveKind.SEVEN_Z) }
    @Test fun tarRoundTripsUnicode() = runBlocking<Unit> { roundTrip(WorkspaceArchiveKind.TAR) }
    @Test fun compressedTarRoundTripsUnicode() = runBlocking<Unit> { roundTrip(WorkspaceArchiveKind.TAR_GZIP) }
    private fun fixture(name: String): File {
        val data = InstrumentationRegistry.getInstrumentation().context.assets.open("archives/$name.base64").use { it.readBytes() }
        return File(root, name).apply { writeBytes(Base64.decode(data, Base64.DEFAULT)) }
    }
    @Test fun rar5StoredFixtureListsAndExtracts() = runBlocking<Unit> {
        val file = fixture("rar5-stored.rar")
        assertEquals(listOf("helloworld.txt"), engine.list(file, WorkspaceArchiveKind.RAR, "").map { it.path })
        val destination = File(root, "rar")
        engine.extract(file, WorkspaceArchiveKind.RAR, "", emptySet(), destination)
        assertEquals("hello libarchive test suite!\n", File(destination, "helloworld.txt").readText())
    }
    @Test fun compressedTarFixturesSupportBzip2AndXzFilters() = runBlocking<Unit> {
        listOf("sample.tar.bz2" to WorkspaceArchiveKind.TAR_BZIP2, "sample.tar.xz" to WorkspaceArchiveKind.TAR_XZ).forEach { (name, kind) ->
            val destination = File(root, name + ".extracted")
            engine.extract(fixture(name), kind, "", emptySet(), destination)
            assertEquals("hello 中文\n", File(destination, "folder/中文.txt").readText())
        }
    }
    @Test fun sourceChangesWithTheSameSizeAndTimestampPreventCommit() = runBlocking<Unit> {
        val archive = File(root, "original.zip")
        val input = File(root, "a.txt").apply { writeText("original") }
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(input)), "", 1)
        val store = WorkspaceArchiveStore(context)
        val session = store.open(WorkspaceLocation(archive.path, "android"), "")
        try {
            val stamp = archive.lastModified()
            val changed = archive.readBytes().also { it[40] = (it[40].toInt() xor 1).toByte() }
            archive.writeBytes(changed)
            check(archive.setLastModified(stamp))
            assertThrows(IllegalStateException::class.java) { runBlocking { store.modify(session, rename = "a.txt" to "b.txt") } }
            assertArrayEquals(changed, archive.readBytes())
        } finally { store.close(session) }
    }
    @Test fun zipCommitPublishesACompleteReadableArchive() = runBlocking<Unit> {
        val archive = File(root, "original.zip")
        val input = File(root, "a.txt").apply { writeText("original") }
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(input)), "", 1)
        val store = WorkspaceArchiveStore(context)
        val session = store.open(WorkspaceLocation(archive.path, "android"), "")
        try { store.modify(session, rename = "a.txt" to "b.txt") } finally { store.close(session) }
        engine.verify(archive, WorkspaceArchiveKind.ZIP, "")
        assertEquals(listOf("b.txt"), engine.list(archive, WorkspaceArchiveKind.ZIP, "").map { it.path })
        assertTrue(root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun localBrowsingUsesTheSourceAndReopeningAnEntryReusesItsUnmodifiedCache() = runBlocking<Unit> {
        val archive = File(root, "browse.zip")
        val input = File(root, "hello.txt").apply { writeText("hello") }
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(input)), "", 1)
        val store = WorkspaceArchiveStore(context)
        val session = store.open(WorkspaceLocation(archive.path), "")
        try {
            assertEquals(archive.canonicalFile, session.file.canonicalFile)
            assertTrue(session.directory.listFiles()!!.isEmpty())
            val entry = session.entries.single()
            val first = store.openEntry(session, entry)
            assertEquals(first, store.openEntry(session, entry))
            val timestamp = first.lastModified()
            first.writeText("HELLO")
            check(first.setLastModified(timestamp))
            val fresh = store.openEntry(session, entry)
            assertNotEquals(first, fresh)
            assertEquals("hello", fresh.readText())
            assertEquals("HELLO", first.readText())
        } finally { store.close(session) }
        assertTrue(archive.isFile)
    }

    @Test fun entryReadRefusesASourceChangedAfterTheIndexWasOpened() = runBlocking<Unit> {
        val archive = File(root, "changed.zip")
        val input = File(root, "hello.txt").apply { writeText("hello") }
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(input)), "", 1)
        val store = WorkspaceArchiveStore(context)
        val session = store.open(WorkspaceLocation(archive.path), "")
        try {
            archive.appendBytes(byteArrayOf(0))
            assertThrows(IllegalStateException::class.java) { runBlocking { store.openEntry(session, session.entries.single()) } }
        } finally { store.close(session) }
    }

    @Test fun localExtractionPublishesChildrenAndRemovesOnlyItsOwnTemporaryDirectory() = runBlocking<Unit> {
        val folder = File(root, "source").apply { check(mkdir()) }
        File(folder, "a.txt").writeText("a")
        File(folder, "b.txt").writeText("b")
        val archive = File(root, "extract.zip")
        engine.create(archive, WorkspaceArchiveKind.ZIP, engine.inputs(listOf(folder)), "", 1)
        val destination = File(root, "destination").apply { check(mkdir()) }
        val existing = File(destination, "keep.txt").apply { writeText("keep") }
        val store = WorkspaceArchiveStore(context)
        val session = store.open(WorkspaceLocation(archive.path), "")
        try {
            store.extract(session, emptySet(), WorkspaceLocation(destination.path))
            assertEquals("a", File(destination, "source/a.txt").readText())
            assertEquals("b", File(destination, "source/b.txt").readText())
            assertEquals("keep", existing.readText())
            assertTrue(destination.listFiles()!!.none { it.name.startsWith(".operit-extract-") })
            assertEquals(session.entries.size, store.progress.value.completedEntries)
        } finally { store.close(session) }
    }
}
