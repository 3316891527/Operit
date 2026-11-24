package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotPackStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val objects get() = File(temporary.root, "objects")
    private val database get() = File(temporary.root, "objects-v2.sqlite")
    private val opened = mutableListOf<SnapshotPackStore>()
    private fun open(factory: () -> TerminalIndexDatabase = { JdbcTerminalIndexDatabase(database) }) =
        SnapshotPackStore(objects, openDatabase = factory).also(opened::add)
    private fun packs() = objects.listFiles().orEmpty().filter { it.extension == "pack" }
    private fun bytes(value: Int, size: Int = 4096) = ByteArray(size) { ((value + it) % 251).toByte() }.also {
        if (it.size >= 4) java.nio.ByteBuffer.wrap(it).putInt(value)
    }
    private fun legacy(value: String): String {
        objects.mkdirs()
        val data = value.toByteArray()
        return SnapshotPackStore.hash(data).also { File(objects, it).writeBytes(data) }
    }
    @After fun closeStores() { opened.forEach { it.close() } }

    @Test fun manySmallObjectsShareOneVolumeAndRoundTripExactBytes() {
        val store = open()
        val hashes = (0 until 120).map { store.put(bytes(it)) }
        assertEquals(1, packs().size)
        hashes.forEachIndexed { index, hash -> assertArrayEquals(bytes(index), store.read(hash)) }
        assertTrue(packs().single().length() < SnapshotPackStore.TARGET_BYTES)
    }

    @Test fun identicalContentKeepsGlobalIdentityWithoutAppending() {
        val store = open()
        val first = store.put(bytes(1))
        val size = packs().single().length()
        repeat(20) { assertEquals(first, store.put(bytes(1))) }
        assertEquals(size, packs().single().length())
    }

    @Test fun exactVolumeBoundarySealsAndTheNextObjectStartsANewVolume() {
        val store = open()
        val data = bytes(1, SnapshotPackStore.TARGET_BYTES - SnapshotPackStore.VOLUME_HEADER - SnapshotPackStore.RECORD_HEADER)
        val first = store.put(data)
        assertEquals(SnapshotPackStore.TARGET_BYTES.toLong(), packs().single().length())
        val next = store.put(bytes(2))
        assertEquals(2, packs().size)
        assertArrayEquals(data, store.read(first))
        assertArrayEquals(bytes(2), store.read(next))
    }

    @Test fun recordsAreNeverSplitAcrossTheTargetBoundary() {
        val store = open()
        store.put(bytes(1, 600_000))
        store.put(bytes(2, 600_000))
        assertEquals(2, packs().size)
        assertTrue(packs().all { it.length() < SnapshotPackStore.TARGET_BYTES })
    }

    @Test fun legalLargeObjectsReceiveADedicatedVolume() {
        val store = open()
        val data = bytes(1, SnapshotPackStore.MAX_OBJECT_BYTES)
        val large = store.put(data)
        val small = store.put(bytes(2))
        assertEquals(2, packs().size)
        assertEquals(1, packs().count { it.length() > SnapshotPackStore.TARGET_BYTES })
        assertArrayEquals(data, store.read(large))
        assertArrayEquals(bytes(2), store.read(small))
    }

    @Test fun oversizedInputFailsBeforeWritingARecord() {
        val store = open()
        assertThrows(IllegalArgumentException::class.java) { store.put(ByteArray(SnapshotPackStore.MAX_OBJECT_BYTES + 1)) }
        assertTrue(packs().isEmpty())
    }

    @Test fun emptyAndBinaryObjectsAreValid() {
        val store = open()
        val empty = store.put(byteArrayOf())
        val binary = store.put(byteArrayOf(0, -1, 1, 0))
        assertArrayEquals(byteArrayOf(), store.read(empty))
        assertArrayEquals(byteArrayOf(0, -1, 1, 0), store.read(binary))
    }

    @Test fun reopenResumesTheSameActiveVolume() {
        val first = open()
        val hash = first.put(bytes(1))
        first.close()
        val second = open()
        val next = second.put(bytes(2))
        assertEquals(1, packs().size)
        assertArrayEquals(bytes(1), second.read(hash))
        assertArrayEquals(bytes(2), second.read(next))
    }

    @Test fun recoveryTruncatesOnlyTheUncommittedTail() {
        val first = open()
        val hash = first.put(bytes(1))
        first.close()
        val pack = packs().single()
        val committed = pack.length()
        pack.appendBytes(ByteArray(100) { 7 })
        val second = open()
        assertArrayEquals(bytes(1), second.read(hash))
        assertEquals(committed, pack.length())
        second.put(bytes(2))
        assertEquals(1, packs().size)
    }

    @Test fun indexCommitFailureNeverPublishesTheAppendedRecord() {
        val db = JdbcTerminalIndexDatabase(database)
        val store = open { db }
        val retained = store.put(bytes(1))
        val committed = packs().single().length()
        db.failOn = "INSERT OR REPLACE INTO snapshot_objects"
        assertThrows(IllegalStateException::class.java) { store.put(bytes(2)) }
        db.failOn = null
        assertFalse(store.contains(SnapshotPackStore.hash(bytes(2))))
        assertEquals(committed, packs().single().length())
        assertArrayEquals(bytes(1), store.read(retained))
        val next = store.put(bytes(3))
        assertArrayEquals(bytes(3), store.read(next))
    }

    @Test fun failingTheFirstObjectCommitPublishesNeitherVolumeNorPosition() {
        val db = JdbcTerminalIndexDatabase(database)
        val store = open { db }
        store.contains(SnapshotPackStore.hash(bytes(0)))
        db.failOn = "INSERT OR REPLACE INTO snapshot_objects"
        assertThrows(IllegalStateException::class.java) { store.put(bytes(1)) }
        db.query("SELECT COUNT(*) FROM snapshot_volumes").use { rows -> assertTrue(rows.next()); assertEquals(0L, rows.long(0)) }
        db.query("SELECT COUNT(*) FROM snapshot_objects").use { rows -> assertTrue(rows.next()); assertEquals(0L, rows.long(0)) }
        db.failOn = null
        assertFalse(store.contains(SnapshotPackStore.hash(bytes(1))))
        assertTrue(packs().isEmpty())
        val hash = store.put(bytes(2))
        assertEquals(1, packs().size)
        assertArrayEquals(bytes(2), store.read(hash))
    }

    @Test fun failedDirectorySyncNeverPublishesTheNewVolume() {
        val db = JdbcTerminalIndexDatabase(database)
        var reject = true
        val store = SnapshotPackStore(objects, syncDirectory = { directory ->
            if (directory == objects && reject) throw IOException("injected directory sync failure")
        }, openDatabase = { db }).also(opened::add)
        assertThrows(IOException::class.java) { store.put(bytes(1)) }
        db.query("SELECT COUNT(*) FROM snapshot_volumes").use { rows -> assertTrue(rows.next()); assertEquals(0L, rows.long(0)) }
        reject = false
        assertFalse(store.contains(SnapshotPackStore.hash(bytes(1))))
        assertTrue(packs().isEmpty())
        val hash = store.put(bytes(2))
        assertArrayEquals(bytes(2), store.read(hash))
    }

    @Test fun failedRolloverKeepsPreviouslyCommittedContentReadable() {
        val db = JdbcTerminalIndexDatabase(database)
        val store = open { db }
        val first = store.put(bytes(1, 700_000))
        db.failOn = "INSERT OR REPLACE INTO snapshot_objects"
        assertThrows(IllegalStateException::class.java) { store.put(bytes(2, 700_000)) }
        db.failOn = null
        assertArrayEquals(bytes(1, 700_000), store.read(first))
        assertEquals(1, packs().size)
        val second = store.put(bytes(2, 700_000))
        assertEquals(2, packs().size)
        assertArrayEquals(bytes(2, 700_000), store.read(second))
    }

    @Test fun truncatedCommittedRecordsRaiseCorruptionInsteadOfPretendingTheyWereCleared() {
        val store = open()
        val hash = store.put(bytes(1))
        store.close()
        RandomAccessFile(packs().single(), "rw").use { it.setLength(20) }
        assertThrows(IOException::class.java) { open().read(hash) }
    }

    @Test fun checksumMismatchIsReportedWithoutReadingALegacyCopy() {
        val store = open()
        val hash = store.put(bytes(1))
        File(objects, hash).writeBytes(bytes(1))
        RandomAccessFile(packs().single(), "rw").use { it.seek(it.length() - 1); it.writeByte(99) }
        assertThrows(IOException::class.java) { store.read(hash) }
    }

    @Test fun unknownIndexVersionsAreRejected() {
        JdbcTerminalIndexDatabase(database).use { it.execute("PRAGMA user_version=999") }
        assertThrows(IllegalArgumentException::class.java) { open().put(bytes(1)) }
    }

    @Test fun wholeDirectoryCleanupKeepsTheStoreUsableWithoutReopening() {
        val store = open()
        val old = store.put(bytes(1))
        assertTrue(objects.deleteRecursively())
        assertFalse(store.contains(old))
        assertNull(store.read(old))
        val next = store.put(bytes(2))
        assertArrayEquals(bytes(2), store.read(next))
        assertFalse(store.contains(old))
    }

    @Test fun partialCleanupAffectsOnlyTheRemovedVolume() {
        val store = open()
        val first = store.put(bytes(1, 700_000))
        val firstFile = packs().single()
        val second = store.put(bytes(2, 700_000))
        assertTrue(firstFile.delete())
        assertFalse(store.contains(first))
        assertNull(store.read(first))
        assertArrayEquals(bytes(2, 700_000), store.read(second))
    }

    @Test fun clearedContentCanBeCapturedAgainWithTheSameHash() {
        val store = open()
        val hash = store.put(bytes(1))
        objects.deleteRecursively()
        assertEquals(hash, store.put(bytes(1)))
        assertArrayEquals(bytes(1), store.read(hash))
    }

    @Test fun oldObjectsRemainReadableUntilMigratedAndKeepTheirHashes() {
        val old = legacy("旧对象\n")
        val store = open()
        assertArrayEquals("旧对象\n".toByteArray(), store.read(old))
        store.migrateLegacy()
        assertFalse(File(objects, old).exists())
        assertArrayEquals("旧对象\n".toByteArray(), store.read(old))
        assertEquals(old, store.put("旧对象\n".toByteArray()))
    }

    @Test fun cancellingMigrationRetainsEveryUnpublishedSourceAndCanResume() {
        val hashes = (0 until 8).map { legacy("内容$it") }
        val store = open()
        var checks = 0
        assertThrows(CancellationException::class.java) { store.migrateLegacy(checkCancelled = { if (++checks == 5) throw CancellationException() }) }
        hashes.forEach { assertNotNull(store.read(it)) }
        assertTrue(objects.listFiles().orEmpty().any { it.name.length == 64 })
        store.migrateLegacy()
        hashes.forEach { assertFalse(File(objects, it).exists()); assertNotNull(store.read(it)) }
    }

    @Test fun migrationCommitFailureKeepsTheOriginalObject() {
        val hash = legacy("原始内容")
        val db = JdbcTerminalIndexDatabase(database)
        val store = open { db }
        store.contains(hash)
        db.failOn = "INSERT OR REPLACE INTO snapshot_objects"
        assertThrows(IllegalStateException::class.java) { store.migrateLegacy() }
        assertTrue(File(objects, hash).exists())
        db.failOn = null
        store.migrateLegacy()
        assertArrayEquals("原始内容".toByteArray(), store.read(hash))
    }

    @Test fun migrationRejectsCorruptLegacyContentAndRetainsTheSource() {
        val hash = legacy("原始内容")
        File(objects, hash).writeText("损坏")
        assertThrows(IOException::class.java) { open().migrateLegacy() }
        assertTrue(File(objects, hash).exists())
    }

    @Test fun clearedPacksDoNotResurrectAnInterruptedMigrationsOldCopy() {
        val hash = legacy("内容")
        val first = open()
        first.put("内容".toByteArray())
        first.close()
        assertTrue(packs().single().delete())
        val second = open()
        assertFalse(second.contains(hash))
        assertNull(second.read(hash))
        second.migrateLegacy()
        assertFalse(File(objects, hash).exists())
    }

    @Test fun removalDoesNotTouchOtherObjectsSharingTheVolume() {
        val store = open()
        val removed = store.put(bytes(1))
        val kept = store.put(bytes(2))
        store.remove(setOf(removed))
        assertNull(store.read(removed))
        assertArrayEquals(bytes(2), store.read(kept))
    }

    @Test fun compactionCopiesOnlyLiveObjectsAndKeepsHashesStable() {
        val store = open()
        val hashes = (0 until 6).map { store.put(bytes(it, 100_000)) }
        val old = packs().single()
        store.remove(hashes.take(4).toSet())
        assertTrue(store.compact() > 0)
        assertFalse(old.exists())
        assertEquals(1, packs().size)
        assertTrue(packs().single().length() < 250_000)
        hashes.take(4).forEach { assertNull(store.read(it)) }
        hashes.takeLast(2).forEachIndexed { index, hash -> assertArrayEquals(bytes(index + 4, 100_000), store.read(hash)) }
    }

    @Test fun whollyDeadVolumesAreRemovedWithoutCreatingEmptyReplacements() {
        val store = open()
        val hash = store.put(bytes(1))
        store.remove(setOf(hash))
        store.compact()
        assertTrue(packs().isEmpty())
        assertNull(store.read(hash))
    }

    @Test fun compactionRespectsItsByteBudget() {
        val store = open()
        val hash = store.put(bytes(1, 100_000))
        store.remove(setOf(hash))
        assertEquals(0L, store.compact(byteBudget = 100))
        assertEquals(1, packs().size)
        assertTrue(store.compact() > 0)
    }

    @Test fun cancellingCompactionPreservesTheOldCommittedLocations() {
        val store = open()
        val removed = store.put(bytes(1))
        val kept = store.put(bytes(2))
        val old = packs().single()
        store.remove(setOf(removed))
        var checks = 0
        assertThrows(CancellationException::class.java) { store.compact(checkCancelled = { if (++checks == 2) throw CancellationException() }) }
        assertTrue(old.exists())
        assertArrayEquals(bytes(2), store.read(kept))
        store.compact()
        assertArrayEquals(bytes(2), store.read(kept))
    }

    @Test fun failedCompactionTransactionKeepsTheOldLocations() {
        val db = JdbcTerminalIndexDatabase(database)
        val store = open { db }
        val removed = store.put(bytes(1))
        val kept = store.put(bytes(2))
        val old = packs().single()
        store.remove(setOf(removed))
        db.failOn = "UPDATE snapshot_objects SET volume="
        assertThrows(IllegalStateException::class.java) { store.compact() }
        db.failOn = null
        assertTrue(old.exists())
        assertArrayEquals(bytes(2), store.read(kept))
        store.compact()
        assertArrayEquals(bytes(2), store.read(kept))
    }

    @Test fun recoveryRemovesOnlyUnindexedPackFiles() {
        val first = open()
        val hash = first.put(bytes(1))
        first.close()
        val orphan = File(objects, UUID.randomUUID().toString() + ".pack").apply { writeText("partial") }
        val other = File(objects, "notes.txt").apply { writeText("keep") }
        val second = open()
        assertArrayEquals(bytes(1), second.read(hash))
        assertFalse(orphan.exists())
        assertTrue(other.exists())
    }

    @Test fun invalidHashesNeverAccessPathsOutsideTheObjectDirectory() {
        val store = open()
        assertThrows(IllegalArgumentException::class.java) { store.read("../outside") }
        assertThrows(IllegalArgumentException::class.java) { store.contains("bad") }
        assertThrows(IllegalArgumentException::class.java) { store.remove(setOf("../outside")) }
    }

    @Test fun repeatedObjectsAvoidPositionQueriesButStillNoticeExternalCleanup() {
        val real = JdbcTerminalIndexDatabase(database)
        var queries = 0
        val counted = object : TerminalIndexDatabase by real {
            override fun query(sql: String, arguments: List<String>): TerminalIndexRows {
                if (sql.startsWith("SELECT hash,volume,offset,length,live")) queries++
                return real.query(sql, arguments)
            }
        }
        val store = open { counted }
        val hash = store.put(bytes(1))
        val initial = queries
        repeat(20) { assertEquals(hash, store.put(bytes(1))); assertTrue(store.contains(hash)) }
        assertEquals(initial, queries)
        objects.deleteRecursively()
        assertFalse(store.contains(hash))
    }

    @Test fun thePositionCacheEvictsOlderHashesAtItsFixedLimit() {
        val real = JdbcTerminalIndexDatabase(database)
        var queries = 0
        val counted = object : TerminalIndexDatabase by real {
            override fun query(sql: String, arguments: List<String>): TerminalIndexRows {
                if (sql.startsWith("SELECT hash,volume,offset,length,live")) queries++
                return real.query(sql, arguments)
            }
        }
        val store = open { counted }
        val hashes = (0 until 260).map { store.put(bytes(it, 32)) }
        val initial = queries
        assertTrue(store.contains(hashes.first()))
        assertEquals(initial + 1, queries)
        assertTrue(store.contains(hashes.last()))
        assertEquals(initial + 1, queries)
    }

    @Test fun concurrentWritersShareOneCommittedObject() {
        val store = open()
        val workers = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val jobs = (0 until 12).map { workers.submit<String> { store.put(bytes(1)) } }
            assertEquals(1, jobs.map { it.get() }.distinct().size)
            assertEquals(1, packs().size)
            assertEquals((SnapshotPackStore.VOLUME_HEADER + SnapshotPackStore.RECORD_HEADER + bytes(1).size).toLong(), packs().single().length())
        } finally { workers.shutdownNow() }
    }
}
