package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkspaceDiffTest {
    @Test fun `both historical texts reconstruct from the same diff rows`() {
        val old = listOf("package demo", "trace(1);", "old", "last")
        val next = listOf("first", "package demo", "trace(2);", "last", "extra")
        val diff = WorkspaceDiff.compare(old.joinToString("\n"), next.joinToString("\n"))
        assertEquals(old, diff.rows.filter { it.kind != DiffLineKind.ADD }.map { it.text })
        assertEquals(next, diff.rows.filter { it.kind != DiffLineKind.REMOVE }.map { it.text })
        assertEquals((1..old.size).toList(), diff.rows.mapNotNull { it.oldLine })
        assertEquals((1..next.size).toList(), diff.rows.mapNotNull { it.newLine })
        val pairs = WorkspaceDiff.items(diff, split = true, full = true, expanded = emptySet()).filterIsInstance<ReviewDiffItem.Line>()
        assertEquals(old, pairs.mapNotNull { it.left?.text })
        assertEquals(next, pairs.mapNotNull { it.right?.text })
    }

    @Test fun `folded gaps expand exact snapshot rows and retain both line numbers`() {
        val old = (1..80).map { "line $it" }
        val next = old.toMutableList().also { it[39] = "changed" }
        val diff = WorkspaceDiff.compare(old.joinToString("\n"), next.joinToString("\n"))
        val folded = WorkspaceDiff.items(diff, false, false, emptySet())
        val gaps = folded.filterIsInstance<ReviewDiffItem.Gap>()
        assertEquals(2, gaps.size)
        val expanded = WorkspaceDiff.items(diff, false, false, gaps.map { it.start }.toSet())
        assertFalse(expanded.any { it is ReviewDiffItem.Gap })
        assertEquals(diff.rows, expanded.filterIsInstance<ReviewDiffItem.Line>().map { it.right })
    }

    @Test fun `change navigation groups adjacent remove and add rows across view modes`() {
        val old = (1..40).map { "line $it" }
        val next = old.toMutableList().also { it[3] = "first change"; it[34] = "second change" }
        val document = WorkspaceDiff.compare(old.joinToString("\n"), next.joinToString("\n"))
        for (split in listOf(false, true)) for (full in listOf(false, true)) {
            val items = WorkspaceDiff.items(document, split, full, emptySet())
            val starts = WorkspaceDiff.changeStarts(items)
            assertEquals(2, starts.size)
            for (start in starts) {
                val item = items[start] as ReviewDiffItem.Line
                assertTrue(listOfNotNull(item.left, item.right).any { it.kind != DiffLineKind.CONTEXT })
            }
            val headers = items.filterIsInstance<ReviewDiffItem.Hunk>()
            assertEquals(if (full) 1 else 2, headers.size)
            if (full) {
                assertEquals(40, headers.single().oldCount)
                assertEquals(40, headers.single().newCount)
            }
        }
    }

    @Test fun `pure addition and deletion headers retain empty-side coordinates`() {
        for (split in listOf(false, true)) {
            val added = WorkspaceDiff.items(WorkspaceDiff.compare("", "first\nsecond"), split, true, emptySet()).filterIsInstance<ReviewDiffItem.Hunk>().single()
            assertEquals(ReviewDiffItem.Hunk(0, 0, 1, 2), added)
            val removed = WorkspaceDiff.items(WorkspaceDiff.compare("first\nsecond", ""), split, true, emptySet()).filterIsInstance<ReviewDiffItem.Hunk>().single()
            assertEquals(ReviewDiffItem.Hunk(1, 2, 0, 0), removed)
            assertTrue(WorkspaceDiff.changeStarts(WorkspaceDiff.items(WorkspaceDiff.compare("same", "same"), split, true, emptySet())).isEmpty())
        }
    }

    @Test fun `newline-only differences remain visible without fabricated added lines`() {
        val diff = WorkspaceDiff.compare("hello\n", "hello")
        assertTrue(diff.newlineChanged)
        assertEquals(0, diff.additions)
        assertEquals(0, diff.deletions)
        assertEquals(1, WorkspaceDiff.compare("", "\n").additions)
    }

    @Test fun `inline emphasis does not split emoji surrogate pairs`() {
        val ranges = WorkspaceDiff.changedRanges("a😀z", "a😁z")
        assertEquals(DiffRange(1, 3), ranges.first)
        assertEquals(DiffRange(1, 3), ranges.second)
    }

    @Test fun `binding scope respects path segments and environments`() {
        assertTrue(changePathContains("/workspace/", "/workspace/src/../main.as"))
        assertFalse(changePathContains("/workspace", "/workspace-other/main.as"))
        assertFalse(changePathContains("/workspace", "/workspace/../outside.as"))
        assertEquals("/workspace/ file \n.as", changePath("/workspace/ file \n.as"))
        assertEquals("android", changeEnvironment(null))
        assertEquals("linux", changeEnvironment("Linux"))
    }

    @Test fun `operation serialization keeps chat ownership and both immutable snapshots`() {
        val record = FileChangeRecord("record", "operation", "chat-A", 123, "edit_file",
            ChangeLocation("/outside/result.as", "linux", "linux:ssh:host"),
            ChangeSnapshot(SnapshotKind.TEXT, 10, "old-hash"), ChangeSnapshot(SnapshotKind.TEXT, 12, "new-hash"))
        assertEquals(record, Json.decodeFromString<FileChangeRecord>(Json.encodeToString(record)))
        assertEquals("chat-A", record.chatId)
        assertFalse(changePathContains("/workspace-B", record.location.path))
    }
}
