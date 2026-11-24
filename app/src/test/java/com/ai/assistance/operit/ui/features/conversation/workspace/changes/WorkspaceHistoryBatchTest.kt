package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceHistoryBatchTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun bytes(value: Int, size: Int = 1024) = ByteArray(size) { (value + it).toByte() }.also { java.nio.ByteBuffer.wrap(it).putInt(value) }
    private val database get() = File(temporary.root, "objects.sqlite")
    private val objects get() = File(temporary.root, "objects")

    @Test fun oneBatchPublishesAllPositionsWithItsRecordInOneTransaction() {
        val real = JdbcTerminalIndexDatabase(database)
        var transactions = 0
        val db = object : TerminalIndexDatabase by real {
            override fun <T> transaction(block: () -> T): T { transactions++; return real.transaction(block) }
        }
        SnapshotPackStore(objects, openDatabase = { db }).use { store ->
            store.withDatabase { WorkspaceHistoryIndex(it, "/storage/emulated/0") }
            val initial = transactions
            val hashes = (0 until 100).map { store.put(bytes(it), batched = true) }
            real.query("SELECT count(*) FROM snapshot_objects").use { it.next(); assertEquals(0L, it.long(0)) }
            assertArrayEquals(bytes(50), store.read(hashes[50]))
            store.commit { connection ->
                connection.execute("CREATE TABLE batch_marker (value INTEGER)")
                connection.execute("INSERT INTO batch_marker(value) VALUES(1)")
            }
            assertEquals(initial + 1, transactions)
            real.query("SELECT count(*) FROM snapshot_objects").use { it.next(); assertEquals(100L, it.long(0)) }
            hashes.forEachIndexed { index, hash -> assertArrayEquals(bytes(index), store.read(hash)) }
        }
    }

    @Test fun countBoundaryCommitsBeforeItCanHoldMoreThan128Positions() {
        val db = JdbcTerminalIndexDatabase(database)
        SnapshotPackStore(objects, openDatabase = { db }).use { store ->
            repeat(257) { store.put(bytes(it), batched = true) }
            db.query("SELECT count(*) FROM snapshot_objects").use { it.next(); assertEquals(256L, it.long(0)) }
            store.flush()
            db.query("SELECT count(*) FROM snapshot_objects").use { it.next(); assertEquals(257L, it.long(0)) }
        }
    }

    @Test fun byteBoundaryAndLargeObjectsNeverSplitRecordsOrLosePendingPositions() {
        SnapshotPackStore(objects, openDatabase = { JdbcTerminalIndexDatabase(database) }).use { store ->
            val first = store.put(bytes(1, 700_000), batched = true)
            val second = store.put(bytes(2, 700_000), batched = true)
            val large = store.put(bytes(3, SnapshotPackStore.MAX_OBJECT_BYTES), batched = true)
            store.flush()
            assertArrayEquals(bytes(1, 700_000), store.read(first))
            assertArrayEquals(bytes(2, 700_000), store.read(second))
            assertArrayEquals(bytes(3, SnapshotPackStore.MAX_OBJECT_BYTES), store.read(large))
            assertEquals(3, objects.listFiles().orEmpty().count { it.extension == "pack" })
        }
    }

    @Test fun recordFailureRollsBackEveryPositionAndDropsUncommittedCaches() {
        val db = JdbcTerminalIndexDatabase(database)
        SnapshotPackStore(objects, openDatabase = { db }).use { store ->
            val old = store.put(bytes(500))
            val file = objects.listFiles().orEmpty().single { it.extension == "pack" }
            val boundary = file.length()
            val pending = (0 until 20).map { store.put(bytes(it), batched = true) }
            assertThrows(IllegalStateException::class.java) { store.commit<Unit> { error("injected record failure") } }
            pending.forEach { assertFalse(store.contains(it)); assertNull(store.read(it)) }
            assertEquals(boundary, file.length())
            assertArrayEquals(bytes(500), store.read(old))
            val next = store.put(bytes(600), batched = true)
            store.flush()
            assertArrayEquals(bytes(600), store.read(next))
        }
    }

    @Test fun failedNewVolumeDirectorySyncPublishesNeitherRecordNorObject() {
        val db = JdbcTerminalIndexDatabase(database)
        var rejected = true
        SnapshotPackStore(objects, syncDirectory = { if (it == objects && rejected) throw IOException("injected directory failure") }, openDatabase = { db }).use { store ->
            val hash = store.put(bytes(1), batched = true)
            assertThrows(IOException::class.java) { store.commit { it.execute("CREATE TABLE unpublished (value INTEGER)") } }
            rejected = false
            assertFalse(store.contains(hash))
            db.query("SELECT count(*) FROM sqlite_master WHERE name='unpublished'").use { it.next(); assertEquals(0L, it.long(0)) }
            assertTrue(objects.listFiles().orEmpty().none { it.extension == "pack" })
        }
    }

    @Test fun pendingLeasesProtectSnapshotsBeforeBaselineRowsAreFlushedAndCancelReleasesThem() {
        TerminalObservationIndex(JdbcTerminalIndexDatabase(File(temporary.root, "baseline.sqlite"))).use { index ->
            val hash = "a".repeat(64)
            index.beginScan().use { scan ->
                scan.pinBlob(hash)
                assertTrue(index.containsBlob(hash))
                scan.put("a", TerminalObservedFile("stamp", ChangeSnapshot(SnapshotKind.TEXT, blob = hash)))
                scan.flush()
                assertTrue(index.containsBlob(hash))
            }
            assertFalse(index.containsBlob(hash))
            assertNull(index.file("a"))
        }
    }

    @Test fun baselineCannotPublishBeforeContentSyncAndInitializationFlagSurvivesReopen() {
        val file = File(temporary.root, "baseline.sqlite")
        var reject = true
        TerminalObservationIndex(JdbcTerminalIndexDatabase(file), beforeStageCommit = { if (reject) error("content not durable") }).use { index ->
            index.beginScan().use { scan ->
                scan.put("a", TerminalObservedFile("stamp", ChangeSnapshot(SnapshotKind.TEXT, blob = "a".repeat(64))))
                assertThrows(IllegalStateException::class.java) { scan.commit(completeInitialization = true) }
                assertNull(index.file("a"))
                reject = false
                scan.commit(completeInitialization = true)
            }
        }
        TerminalObservationIndex(JdbcTerminalIndexDatabase(file)).use { index -> assertTrue(index.initialized()); assertNotNull(index.file("a")) }
    }

    @Test fun contentSyncCallbackDoesNotHoldTheBaselineLock() {
        val executor = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val index = TerminalObservationIndex(JdbcTerminalIndexDatabase(File(temporary.root, "locks.sqlite")), beforeStageCommit = {
            entered.countDown(); check(release.await(3, TimeUnit.SECONDS))
        })
        val scan = index.beginScan()
        try {
            scan.pinBlob("a".repeat(64))
            val flushing = executor.submit { scan.flush() }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertTrue(executor.submit<Boolean> { index.containsBlob("a".repeat(64)) }.get(1, TimeUnit.SECONDS))
            release.countDown()
            flushing.get(3, TimeUnit.SECONDS)
        } finally { release.countDown(); executor.shutdownNow(); scan.close(); index.close() }
    }

    @Test fun inventoryFieldsSurviveEveryByteBoundaryIncludingUnicodeAndNewlines() = runBlocking {
        val data = "目录/ a\n.txt\u0000f:12:100\u0000b.txt\u0000l:2:101\u0000".toByteArray()
        for (boundary in 0..data.size) {
            val reader = WorkspaceInventoryReader()
            val files = linkedMapOf<String, String>()
            reader.accept(data.copyOfRange(0, boundary)) { path, stamp -> files[path] = stamp }
            reader.accept(data.copyOfRange(boundary, data.size)) { path, stamp -> files[path] = stamp }
            reader.finish()
            assertEquals(mapOf("目录/ a\n.txt" to "f:12:100", "b.txt" to "l:2:101"), files)
        }
    }

    @Test fun truncatedInventoryFailsRatherThanPublishingAnIncompleteBaseline() = runBlocking {
        val reader = WorkspaceInventoryReader()
        reader.accept("file\u0000stamp".toByteArray()) { _, _ -> }
        assertThrows(IllegalArgumentException::class.java) { reader.finish() }
        Unit
    }
    @Test fun savedSnapshotChoicesEvictOldestEntriesAndPreserveRecentRevisits() {
        var choices = ReviewViewChoices()
        repeat(20) { choices = choices.requestLoad("snapshot-" + it) }
        assertEquals(12, choices.snapshots.size)
        assertFalse(choices.forSnapshot("snapshot-0").loadRequested)
        assertTrue(choices.forSnapshot("snapshot-8").loadRequested)
        choices = choices.expand("snapshot-8", 4).requestLoad("snapshot-20")
        assertEquals(12, choices.snapshots.size)
        assertFalse(choices.forSnapshot("snapshot-9").loadRequested)
        assertEquals(setOf(4), choices.forSnapshot("snapshot-8").expanded)
        assertEquals(choices.snapshots, ReviewViewChoices.restore(choices.saved()).snapshots)
    }

}