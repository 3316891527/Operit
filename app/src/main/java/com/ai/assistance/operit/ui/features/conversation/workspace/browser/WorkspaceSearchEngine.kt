package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class WorkspaceMatchMode { TEXT, GLOB, REGEX }
internal enum class WorkspaceSearchType { ALL, FILES, DIRECTORIES }

internal data class WorkspaceSearchOptions(
    val name: String = "",
    val content: String = "",
    val mode: WorkspaceMatchMode = WorkspaceMatchMode.TEXT,
    val contentRegex: Boolean = false,
    val ignoreCase: Boolean = true,
    val recursive: Boolean = true,
    val includeHidden: Boolean = false,
    val type: WorkspaceSearchType = WorkspaceSearchType.ALL,
    val minimumBytes: Long? = null,
    val maximumBytes: Long? = null,
    val modifiedAfter: Long? = null,
    val modifiedBefore: Long? = null,
    val resultLimit: Int = 500,
) {
    fun validate() {
        require(resultLimit > 0)
        require(minimumBytes == null || minimumBytes >= 0)
        require(maximumBytes == null || maximumBytes >= 0)
        require(minimumBytes == null || maximumBytes == null || minimumBytes <= maximumBytes)
        require(modifiedAfter == null || modifiedBefore == null || modifiedAfter < modifiedBefore)
    }
}

internal data class WorkspaceSearchEntry(
    val location: WorkspaceLocation,
    val name: String,
    val directory: Boolean,
    val size: Long?,
    val modified: Long?,
    val symbolicLink: Boolean = false,
)

internal data class WorkspaceContentHit(val line: Int, val snippet: String)
internal data class WorkspaceSearchHit(val entry: WorkspaceSearchEntry, val content: WorkspaceContentHit? = null)
internal data class WorkspaceSearchProgress(
    val hits: List<WorkspaceSearchHit> = emptyList(),
    val visited: Long = 0,
    val unreadable: Long = 0,
    val skippedContents: Long = 0,
    val finished: Boolean = false,
    val limited: Boolean = false,
)

internal interface WorkspaceSearchSource {
    suspend fun children(location: WorkspaceLocation): List<WorkspaceSearchEntry>
    suspend fun findContent(entry: WorkspaceSearchEntry, pattern: Regex): WorkspaceContentHit?
}

internal fun workspaceNamePattern(value: String, mode: WorkspaceMatchMode, ignoreCase: Boolean): Regex {
    if (value.isEmpty()) return Regex("")
    val pattern = when (mode) {
        WorkspaceMatchMode.TEXT -> Regex.escape(value)
        WorkspaceMatchMode.REGEX -> value
        WorkspaceMatchMode.GLOB -> "^" + value.map { char ->
            when (char) { '*' -> ".*"; '?' -> "."; else -> Regex.escape(char.toString()) }
        }.joinToString("") + "$"
    }
    return Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
}

/** 名称与元数据先筛选，再按需读取文本；目录链接不参与递归。 */
internal class WorkspaceSearchEngine(private val source: WorkspaceSearchSource) {
    suspend fun search(
        root: WorkspaceLocation,
        options: WorkspaceSearchOptions,
        settings: WorkspaceBrowserSettings,
        onProgress: suspend (WorkspaceSearchProgress) -> Unit,
    ): WorkspaceSearchProgress {
        options.validate()
        val namePattern = workspaceNamePattern(options.name, options.mode, options.ignoreCase)
        val contentPattern = if (options.content.isNotEmpty()) workspaceNamePattern(
            options.content, if (options.contentRegex) WorkspaceMatchMode.REGEX else WorkspaceMatchMode.TEXT,
            options.ignoreCase,
        ) else null
        val hits = mutableListOf<WorkspaceSearchHit>()
        val pending = ArrayDeque<WorkspaceLocation>()
        val seen = mutableSetOf<String>()
        pending.add(root)
        var visited = 0L
        var unreadable = 0L
        var skipped = 0L
        var lastReport = System.nanoTime()
        fun snapshot(finished: Boolean = false, limited: Boolean = false) = WorkspaceSearchProgress(
            hits.toList(), visited, unreadable, skipped, finished, limited,
        )
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = pending.removeLast()
            if (!seen.add(directory.key)) continue
            val entries = try {
                source.children(directory)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                if (directory == root) throw failure
                unreadable++
                onProgress(snapshot())
                continue
            }
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                visited++
                if (System.nanoTime() - lastReport >= 150_000_000L) {
                    onProgress(snapshot())
                    lastReport = System.nanoTime()
                }
                if (!options.includeHidden && (entry.name.startsWith('.') || isWorkspacePathManuallyHidden(entry.location, settings.hidden))) continue
                if (entry.directory && !entry.symbolicLink && options.recursive) pending.add(entry.location)
                val typeMatches = when (options.type) {
                    WorkspaceSearchType.ALL -> true
                    WorkspaceSearchType.FILES -> !entry.directory
                    WorkspaceSearchType.DIRECTORIES -> entry.directory
                }
                val sizeMatches = (options.minimumBytes == null || (entry.size != null && entry.size >= options.minimumBytes)) &&
                    (options.maximumBytes == null || (entry.size != null && entry.size <= options.maximumBytes))
                val timeMatches = (options.modifiedAfter == null || (entry.modified != null && entry.modified >= options.modifiedAfter)) &&
                    (options.modifiedBefore == null || (entry.modified != null && entry.modified < options.modifiedBefore))
                if (typeMatches && sizeMatches && timeMatches && namePattern.containsMatchIn(entry.name)) {
                    var content: WorkspaceContentHit? = null
                    var matches = contentPattern == null
                    if (contentPattern != null && !entry.directory && !entry.symbolicLink) {
                        try {
                            content = source.findContent(entry, contentPattern)
                            matches = content != null
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: WorkspaceContentSkippedException) {
                            skipped++
                        } catch (_: Exception) {
                            unreadable++
                        }
                    }
                    if (matches) {
                        hits += WorkspaceSearchHit(entry, content)
                        if (hits.size >= options.resultLimit) return snapshot(true, true).also { onProgress(it) }
                    }
                }
            }
        }
        return snapshot(true).also { onProgress(it) }
    }
}

internal class WorkspaceContentSkippedException : Exception()
/** 根层重新读取真实元数据，限制搜索范围时也保留链接和隐藏条目的处理规则。 */
internal class WorkspaceSelectedSearchSource(
    private val delegate: WorkspaceSearchSource,
    private val root: WorkspaceLocation,
    selected: List<WorkspaceSearchEntry>,
) : WorkspaceSearchSource {
    private val selectedKeys = selected.map { it.location.key }.toSet()
    override suspend fun children(location: WorkspaceLocation): List<WorkspaceSearchEntry> =
        delegate.children(location).let { entries -> if (location == root) entries.filter { it.location.key in selectedKeys } else entries }
    override suspend fun findContent(entry: WorkspaceSearchEntry, pattern: Regex): WorkspaceContentHit? = delegate.findContent(entry, pattern)
}
