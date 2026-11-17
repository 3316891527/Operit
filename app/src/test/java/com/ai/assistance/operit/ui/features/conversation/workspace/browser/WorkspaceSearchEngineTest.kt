package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSearchEngineTest {
    private val root = WorkspaceLocation("/")
    private fun entry(path: String, directory: Boolean = false, size: Long? = 4L,
        modified: Long? = 100L, link: Boolean = false) = WorkspaceSearchEntry(
        WorkspaceLocation(path), path.substringAfterLast('/'), directory, size, modified, link)
    private class Source(val tree: Map<String, List<WorkspaceSearchEntry>>) : WorkspaceSearchSource {
        val listed = mutableListOf<String>()
        val contentRead = mutableListOf<String>()
        override suspend fun children(location: WorkspaceLocation): List<WorkspaceSearchEntry> {
            listed += location.path
            return tree[location.path] ?: throw IOException("不可读目录")
        }
        override suspend fun findContent(entry: WorkspaceSearchEntry, pattern: Regex): WorkspaceContentHit? {
            contentRead += entry.location.path
            if (entry.name == "binary") throw WorkspaceContentSkippedException()
            return if (pattern.containsMatchIn("Hello 中文")) WorkspaceContentHit(3, "Hello 中文") else null
        }
    }
    private suspend fun search(source: Source, options: WorkspaceSearchOptions = WorkspaceSearchOptions(),
        settings: WorkspaceBrowserSettings = WorkspaceBrowserSettings()): WorkspaceSearchProgress =
        WorkspaceSearchEngine(source).search(root, options, settings) {}

    @Test fun defaultsSearchNamesRecursivelyWithoutCaseSensitivity() = runBlocking<Unit> {
        val source = Source(mapOf("/" to listOf(entry("/folder", true)), "/folder" to listOf(entry("/folder/ReadMe.MD"))))
        assertEquals(listOf("ReadMe.MD"), search(source, WorkspaceSearchOptions(name = "readme")).hits.map { it.entry.name })
        assertTrue(source.contentRead.isEmpty())
        assertTrue(search(source, WorkspaceSearchOptions(name = "readme", recursive = false)).hits.isEmpty())
    }
    @Test fun wildcardIsAnchoredAndCaseCanBeEnabled() = runBlocking<Unit> {
        val source = Source(mapOf("/" to listOf(entry("/A.KT"), entry("/a.kt.bak"))))
        assertEquals(1, search(source, WorkspaceSearchOptions(name = "*.kt", mode = WorkspaceMatchMode.GLOB)).hits.size)
        assertTrue(search(source, WorkspaceSearchOptions(name = "*.kt", mode = WorkspaceMatchMode.GLOB, ignoreCase = false)).hits.isEmpty())
        assertEquals(1, search(source, WorkspaceSearchOptions(name = "^A[.]KT$", mode = WorkspaceMatchMode.REGEX)).hits.size)
    }
    @Test fun metadataFiltersUseKnownValuesAndAnExclusiveUpperDate() = runBlocking<Unit> {
        val source = Source(mapOf("/" to listOf(entry("/ok", size = 10, modified = 100),
            entry("/late", size = 10, modified = 200), entry("/unknown", size = null), entry("/small", size = 9))))
        val options = WorkspaceSearchOptions(minimumBytes = 10, maximumBytes = 10, modifiedAfter = 100, modifiedBefore = 200)
        assertEquals(listOf("ok"), search(source, options).hits.map { it.entry.name })
    }
    @Test fun hiddenAndSymbolicDirectoriesAreNotTraversed() = runBlocking<Unit> {
        val source = Source(mapOf("/" to listOf(entry("/.git", true), entry("/private", true), entry("/loop", true, link = true))))
        val settings = WorkspaceBrowserSettings(hidden = setOf(WorkspaceLocation("/private")))
        assertEquals(listOf("loop"), search(source, settings = settings).hits.map { it.entry.name })
        assertEquals(listOf("/"), source.listed)
        val all = search(source, WorkspaceSearchOptions(includeHidden = true), settings)
        assertEquals(3, all.hits.size)
        assertEquals(2L, all.unreadable)
    }
    @Test fun contentIsReadOnlyAfterNameTypeAndSizeFilters() = runBlocking<Unit> {
        val source = Source(mapOf("/" to listOf(entry("/match.txt"), entry("/skip.bin"), entry("/folder", true), entry("/tiny.txt", size = 1))))
        val options = WorkspaceSearchOptions(name = "*.txt", mode = WorkspaceMatchMode.GLOB, content = "中文", minimumBytes = 4,
            type = WorkspaceSearchType.FILES, recursive = false)
        val hit = search(source, options).hits.single()
        assertEquals(3, hit.content!!.line)
        assertEquals(listOf("/match.txt"), source.contentRead)
        assertEquals(2, search(source, options.copy(name = "")).hits.size)
    }
    @Test fun skippedContentAndUnavailableChildrenRemainVisibleInSummary() = runBlocking<Unit> {
        val source = Source(mapOf("/" to listOf(entry("/binary"), entry("/unreadable", true))))
        val result = search(source, WorkspaceSearchOptions(content = "hello"))
        assertTrue(result.hits.isEmpty())
        assertEquals(1L, result.skippedContents)
        assertEquals(1L, result.unreadable)
        assertTrue(result.finished)
    }
    @Test fun limitStopsTraversalAndInvalidRegexDoesNotReadDirectories() = runBlocking<Unit> {
        val source = Source(mapOf("/" to (1..20).map { entry("/file$it") }))
        val result = search(source, WorkspaceSearchOptions(resultLimit = 2))
        assertEquals(2, result.hits.size)
        assertTrue(result.limited)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { search(source, WorkspaceSearchOptions(name = "[", mode = WorkspaceMatchMode.REGEX)) }
        }
    }
    @Test fun rootFailuresAndCancellationAreNotConvertedToEmptySuccess() {
        assertThrows(IOException::class.java) { runBlocking { search(Source(emptyMap())) } }
        val source = object : WorkspaceSearchSource {
            override suspend fun children(location: WorkspaceLocation): List<WorkspaceSearchEntry> = throw CancellationException("取消")
            override suspend fun findContent(entry: WorkspaceSearchEntry, pattern: Regex): WorkspaceContentHit? = null
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { WorkspaceSearchEngine(source).search(root, WorkspaceSearchOptions(), WorkspaceBrowserSettings()) {} }
        }
    }
    @Test fun selectedScopeReadsOnlyChosenSubtreesUsingCurrentLinkMetadata() = runBlocking<Unit> {
        val chosen = entry("/chosen", true)
        val link = entry("/link", true, link = true)
        val source = Source(mapOf("/" to listOf(chosen, link, entry("/other", true)),
            "/chosen" to listOf(entry("/chosen/a.txt"))))
        val scoped = WorkspaceSelectedSearchSource(source, root, listOf(chosen, link.copy(symbolicLink = false)))
        val result = WorkspaceSearchEngine(scoped).search(root, WorkspaceSearchOptions(), WorkspaceBrowserSettings()) {}
        assertEquals(setOf("/chosen", "/chosen/a.txt", "/link"), result.hits.map { it.entry.location.path }.toSet())
        assertEquals(listOf("/", "/chosen"), source.listed)
    }
    @Test fun selectedScopeStillExcludesManualAndDotHiddenEntriesByDefault() = runBlocking<Unit> {
        val entries = listOf(entry("/.hidden"), entry("/manual"), entry("/visible"))
        val source = Source(mapOf("/" to entries))
        val scoped = WorkspaceSelectedSearchSource(source, root, entries)
        val settings = WorkspaceBrowserSettings(hidden = setOf(WorkspaceLocation("/manual")))
        val result = WorkspaceSearchEngine(scoped).search(root, WorkspaceSearchOptions(), settings) {}
        assertEquals(listOf("visible"), result.hits.map { it.entry.name })
        val all = WorkspaceSearchEngine(scoped).search(root, WorkspaceSearchOptions(includeHidden = true), settings) {}
        assertEquals(3, all.hits.size)
    }
}
