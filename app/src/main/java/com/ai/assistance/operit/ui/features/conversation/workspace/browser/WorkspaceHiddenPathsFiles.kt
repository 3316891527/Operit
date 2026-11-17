package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.os.FileObserver
import android.util.AtomicFile
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun workspaceHiddenPathsText(hidden: Set<WorkspaceLocation>, environment: String): String {
    val paths = hidden.filter { it.environment == environment }.map { it.path }.sorted()
    return if (paths.isEmpty()) "" else paths.joinToString("\n", postfix = "\n")
}

/** 每行按完整路径匹配，保留路径内的空格；空行不构成规则。 */
internal fun workspaceHiddenPathsFromText(text: String, environment: String): Set<WorkspaceLocation> =
    text.lineSequence().filter { it.isNotBlank() }.map { WorkspaceLocation(it, environment) }.toSet()

/** 文本文件是隐藏规则的来源，JSON 中的旧记录仅用于首次建立文件。 */
internal class WorkspaceHiddenPathsFiles(private val directory: File) {
    companion object { private val mutex = Mutex() }
    private val marker get() = File(directory, ".initialized")
    private fun ensureDirectory() {
        directory.mkdirs()
        check(directory.isDirectory) { "Cannot create hidden paths directory: $directory" }
    }
    private fun file(environment: String) = File(directory, "hidden_" + URLEncoder.encode(environment, "UTF-8") + ".txt")

    private fun write(file: File, text: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
    }

    private fun initialize(legacy: Set<WorkspaceLocation>) {
        ensureDirectory()
        if (marker.isFile) return
        legacy.map { it.environment }.toSet().forEach { environment ->
            write(file(environment), workspaceHiddenPathsText(legacy, environment))
        }
        // 标记最后提交，清空文本文件后不会再次导入旧隐藏记录。
        write(marker, "1\n")
    }

    private fun readFiles(): Set<WorkspaceLocation> = buildSet {
        checkNotNull(directory.listFiles()) { "Cannot read hidden paths directory: $directory" }
            .filter { it.isFile && it.name.startsWith("hidden_") && it.name.endsWith(".txt") }
            .forEach { file ->
                val environment = URLDecoder.decode(file.name.removePrefix("hidden_").removeSuffix(".txt"), "UTF-8")
                addAll(workspaceHiddenPathsFromText(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }, environment))
            }
    }

    suspend fun read(legacy: Set<WorkspaceLocation>): Set<WorkspaceLocation> = withContext(Dispatchers.IO) {
        mutex.withLock { initialize(legacy); readFiles() }
    }

    suspend fun update(settings: WorkspaceBrowserSettings, transform: (WorkspaceBrowserSettings) -> WorkspaceBrowserSettings): WorkspaceBrowserSettings =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                initialize(settings.hidden)
                val current = settings.copy(hidden = readFiles())
                val next = transform(current)
                if (next.hidden != current.hidden) {
                    (current.hidden + next.hidden).map { it.environment }.toSet().forEach { environment ->
                        val before = workspaceHiddenPathsText(current.hidden, environment)
                        val after = workspaceHiddenPathsText(next.hidden, environment)
                        if (before != after) write(file(environment), after)
                    }
                }
                next
            }
        }

    suspend fun editableFile(environment: String, legacy: Set<WorkspaceLocation>): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            initialize(legacy)
            file(environment).also { if (!it.exists()) write(it, "") }
        }
    }

    /** 监听编辑器保存的原文件及原子替换，不把临时文件写入当成新规则。 */
    val changes = callbackFlow {
        ensureDirectory()
        val observer = object : FileObserver(directory.absolutePath, FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (path.startsWith("hidden_") && path.endsWith(".txt")) trySend(Unit)
            }
        }
        observer.startWatching()
        trySend(Unit)
        awaitClose { observer.stopWatching() }
    }.conflate().flowOn(Dispatchers.IO)
}
