package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.core.files.transfer.LocalTransferPaths
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.terminal.utils.SSHFileConnectionManager
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.SafWorkspacePropertySource
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.WorkspacePropertiesTarget
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.WorkspacePropertyAccess
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.FilterInputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 本机来源批量读取目录属性，授权目录与远程来源保留各自的访问协议。 */
internal class WorkspaceSearchRepository(private val context: Context) : WorkspaceSearchSource {
    private val handler = AIToolHandler.getInstance(context)
    private val access = WorkspacePropertyAccess(context)
    private val safSources = mutableMapOf<WorkspaceLocation, SafWorkspacePropertySource>()

    fun beginSearch() { safSources.clear() }

    private fun localFile(location: WorkspaceLocation) = when {
        location.environment == "android" -> LocalTransferPaths.resolve(context, location.path, "android")
        location.environment == "linux" && SSHFileConnectionManager.getInstance(context).getFileSystemProvider() == null ->
            LocalTransferPaths.resolve(context, location.path, "linux")
        else -> null
    }

    override suspend fun children(location: WorkspaceLocation): List<WorkspaceSearchEntry> = withContext(Dispatchers.IO) {
        val file = localFile(location)
        if (file != null) {
            access.requireReadable(file)
            val entries = mutableListOf<WorkspaceSearchEntry>()
            Files.newDirectoryStream(file.toPath()).use { stream ->
                for (path in stream) {
                    currentCoroutineContext().ensureActive()
                    val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                    val name = path.fileName.toString()
                    entries += WorkspaceSearchEntry(workspaceChild(location, name), name,
                        attributes.isDirectory, attributes.size(), attributes.lastModifiedTime().toMillis(), attributes.isSymbolicLink)
                }
            }
            entries
        } else if (location.environment.startsWith("repo:", true)) {
            val source = safSources[location] ?: SafWorkspacePropertySource.open(context,
                WorkspacePropertiesTarget(location.path, location.environment)).also { safSources[location] = it }
            source.namedChildren().map { (name, node) ->
                val child = workspaceChild(location, name)
                safSources[child] = source.childSource(node)
                WorkspaceSearchEntry(child, name, node.directory, node.size, node.modified, node.symbolicLink)
            }
        } else {
            val result = handler.executeTool(AITool("list_files", listOf(
                ToolParameter("path", location.path), ToolParameter("environment", location.environment),
            )))
            check(result.success && result.result is DirectoryListingData) { result.error.orEmpty() }
            (result.result as DirectoryListingData).entries.filter { it.name != "." && it.name != ".." }.map {
                WorkspaceSearchEntry(workspaceChild(location, it.name), it.name, it.isDirectory,
                    it.size.takeIf { size -> size >= 0 }, directoryModifiedTime(it.lastModified), it.permissions.startsWith("l"))
            }
        }
    }

    override suspend fun findContent(entry: WorkspaceSearchEntry, pattern: Regex): WorkspaceContentHit? = withContext(Dispatchers.IO) {
        if (entry.size != null && entry.size > CONTENT_LIMIT_BYTES) throw WorkspaceContentSkippedException()
        val local = localFile(entry.location)
        when {
            local != null -> {
                access.requireReadable(local)
                local.inputStream().use { findWorkspaceContent(it, pattern) }
            }
            entry.location.environment.startsWith("repo:", true) -> {
                val source = safSources[entry.location] ?: SafWorkspacePropertySource.open(context,
                    WorkspacePropertiesTarget(entry.location.path, entry.location.environment))
                checkNotNull(context.contentResolver.openInputStream(Uri.parse(source.root))).use {
                    findWorkspaceContent(it, pattern)
                }
            }
            else -> {
                if (entry.size == null) throw WorkspaceContentSkippedException()
                val result = handler.executeTool(AITool("read_file_full", listOf(
                    ToolParameter("path", entry.location.path), ToolParameter("environment", entry.location.environment),
                    ToolParameter("text_only", "true"),
                )))
                check(result.success && result.result is FileContentData) { result.error.orEmpty() }
                val text = (result.result as FileContentData).content
                if (text.length > CONTENT_LIMIT_BYTES || '\u0000' in text) throw WorkspaceContentSkippedException()
                var hit: WorkspaceContentHit? = null
                for ((index, line) in text.lineSequence().withIndex()) {
                    currentCoroutineContext().ensureActive()
                    if (pattern.containsMatchIn(line)) {
                        hit = WorkspaceContentHit(index + 1, line.take(240))
                        break
                    }
                }
                hit
            }
        }
    }

    companion object {
        const val CONTENT_LIMIT_BYTES = 8L * 1024 * 1024
    }
}

/** 文本按行扫描，识别常见 BOM；二进制及超出限制的文件明确计入跳过数。 */
internal suspend fun findWorkspaceContent(input: InputStream, pattern: Regex): WorkspaceContentHit? {
    // 以实际字节数限制输入，未知大小或超长单行也不能绕过限制。
    val bounded = object : FilterInputStream(input) {
        private var consumed = 0L
        private fun account(count: Int): Int {
            if (count > 0) consumed += count
            if (consumed > WorkspaceSearchRepository.CONTENT_LIMIT_BYTES) throw WorkspaceContentSkippedException()
            return count
        }
        override fun read(): Int = super.read().also { if (it >= 0) account(1) }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            account(`in`.read(bytes, offset, minOf(length.toLong(), WorkspaceSearchRepository.CONTENT_LIMIT_BYTES - consumed + 1).toInt()))
    }
    val buffered = BufferedInputStream(bounded)
    buffered.mark(4096)
    val sample = ByteArray(4096)
    var count = 0
    while (count < sample.size) {
        currentCoroutineContext().ensureActive()
        val read = buffered.read(sample, count, sample.size - count)
        if (read < 0) break
        if (read == 0) {
            val one = buffered.read()
            if (one < 0) break
            sample[count++] = one.toByte()
        } else count += read
    }
    buffered.reset()
    val charset: Charset
    val bom: Int
    when {
        count >= 2 && sample[0] == 0xff.toByte() && sample[1] == 0xfe.toByte() -> { charset = Charsets.UTF_16LE; bom = 2 }
        count >= 2 && sample[0] == 0xfe.toByte() && sample[1] == 0xff.toByte() -> { charset = Charsets.UTF_16BE; bom = 2 }
        else -> {
            if ((0 until count.coerceAtLeast(0)).any { sample[it] == 0.toByte() }) throw WorkspaceContentSkippedException()
            charset = Charsets.UTF_8
            bom = if (count >= 3 && sample[0] == 0xef.toByte() && sample[1] == 0xbb.toByte() && sample[2] == 0xbf.toByte()) 3 else 0
        }
    }
    repeat(bom) { buffered.read() }
    val decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    val reader = java.io.InputStreamReader(buffered, decoder).buffered()
    var lineNumber = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        val line = reader.readLine() ?: return null
        lineNumber++
        if ('\u0000' in line) throw WorkspaceContentSkippedException()
        val match = pattern.find(line)
        if (match != null) {
            val start = (match.range.first - 60).coerceAtLeast(0)
            return WorkspaceContentHit(lineNumber, line.substring(start, (start + 240).coerceAtMost(line.length)))
        }
    }
}