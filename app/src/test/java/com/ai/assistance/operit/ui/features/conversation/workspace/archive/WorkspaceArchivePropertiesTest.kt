package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import org.junit.Assert.*
import org.junit.Test

class WorkspaceArchivePropertiesTest {
    @Test fun singleFilePreservesMetadataAndExcludesSiblings() {
        val file = WorkspaceArchiveEntry("folder/中文.txt", false, 100L, 60L, 1_700_000_000_000L, encrypted = true)
        val info = workspaceArchiveProperties(listOf(file, WorkspaceArchiveEntry("folder/other.txt", false, 9L)), setOf(file.path))
        assertEquals(file, info.single)
        assertEquals(100L, checkNotNull(info.bytes))
        assertEquals(60L, checkNotNull(info.packedBytes))
        assertEquals(1L, info.files)
        assertEquals(0L, info.directories)
    }

    @Test fun implicitDirectoryIncludesNestedAndHiddenEntriesButExcludesItselfAndPrefixSiblings() {
        val entries = listOf(
            WorkspaceArchiveEntry("root/deep/child/.hidden", false, 4L, 2L),
            WorkspaceArchiveEntry("root/empty", true, null),
            WorkspaceArchiveEntry("root/b.txt", false, 6L, 3L),
            WorkspaceArchiveEntry("root-other/c.txt", false, 100L, 50L),
        )
        val info = workspaceArchiveProperties(entries, setOf("root"))
        assertTrue(checkNotNull(info.single).directory)
        assertNull(checkNotNull(info.single).modified)
        assertEquals(10L, checkNotNull(info.bytes))
        assertEquals(5L, checkNotNull(info.packedBytes))
        assertEquals(2L, info.files)
        assertEquals(3L, info.directories)
    }

    @Test fun overlappingMultiSelectionCountsTheUnionAndIncludesSelectedDirectories() {
        val entries = listOf(
            WorkspaceArchiveEntry("root/deep/a", false, 4L, 2L),
            WorkspaceArchiveEntry("root/b", false, 6L, 3L),
            WorkspaceArchiveEntry("other", false, 7L, 4L),
        )
        val info = workspaceArchiveProperties(entries, setOf("root", "root/deep", "root/deep/a", "other"))
        assertNull(info.single)
        assertEquals(17L, checkNotNull(info.bytes))
        assertEquals(9L, checkNotNull(info.packedBytes))
        assertEquals(3L, info.files)
        assertEquals(2L, info.directories)
    }

    @Test fun emptyDirectoriesHaveZeroContentAndDifferentSingleAndBatchCounts() {
        val entries = listOf(WorkspaceArchiveEntry("empty", true, null), WorkspaceArchiveEntry("also-empty", true, null))
        val single = workspaceArchiveProperties(entries, setOf("empty"))
        assertEquals(0L, checkNotNull(single.bytes))
        assertEquals(0L, checkNotNull(single.packedBytes))
        assertEquals(0L, single.files)
        assertEquals(0L, single.directories)
        val batch = workspaceArchiveProperties(entries, setOf("empty", "also-empty"))
        assertEquals(0L, checkNotNull(batch.bytes))
        assertEquals(2L, batch.directories)
    }

    @Test fun unknownAndNegativeSizesDoNotTurnIntoZeroAndTotalsRemainIndependent() {
        for (unknown in listOf(null, -1L)) {
            val info = workspaceArchiveProperties(listOf(
                WorkspaceArchiveEntry("root/a", false, 10L, 5L),
                WorkspaceArchiveEntry("root/b", false, unknown, 2L),
            ), setOf("root"))
            assertNull(info.bytes)
            assertEquals(7L, checkNotNull(info.packedBytes))
            assertEquals(2L, info.files)
        }
        val unpackedOnly = workspaceArchiveProperties(listOf(WorkspaceArchiveEntry("file", false, 10L)), setOf("file"))
        assertEquals(10L, checkNotNull(unpackedOnly.bytes))
        assertNull(unpackedOnly.packedBytes)
    }

    @Test fun longOverflowIsUnknownWhileTheLargestRepresentableTotalRemainsValid() {
        val entries = listOf(
            WorkspaceArchiveEntry("root/a", false, Long.MAX_VALUE - 1, Long.MAX_VALUE),
            WorkspaceArchiveEntry("root/b", false, 1L, 1L),
        )
        val limit = workspaceArchiveProperties(entries, setOf("root"))
        assertEquals(Long.MAX_VALUE, checkNotNull(limit.bytes))
        assertNull(limit.packedBytes)
        val overflow = workspaceArchiveProperties(entries + WorkspaceArchiveEntry("root/c", false, 1L, 0L), setOf("root"))
        assertNull(overflow.bytes)
        assertNull(overflow.packedBytes)
    }

    @Test fun symbolicLinkMetadataIsCountedOnceWithoutFollowingItsTarget() {
        val link = WorkspaceArchiveEntry("root/link", false, 8L, 4L, symbolicLink = true)
        val entries = listOf(link, WorkspaceArchiveEntry("target/content", false, 1_000L, 500L))
        val info = workspaceArchiveProperties(entries, setOf("root"))
        assertEquals(8L, checkNotNull(info.bytes))
        assertEquals(1L, info.files)
        assertEquals(0L, info.directories)
        assertTrue(checkNotNull(workspaceArchiveProperties(entries, setOf(link.path)).single).symbolicLink)
    }
}
