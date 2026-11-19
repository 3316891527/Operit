package com.ai.assistance.operit.ui.features.conversation.workspace.process

import android.os.FileObserver
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

data class WorkspaceFileChange(
    val relativePath: String,
    val kind: WorkspaceFileChangeKind,
    val createdInPlace: Boolean = false,
)

enum class WorkspaceFileChangeKind {
    CREATED,
    MODIFIED,
    DELETED,
    MOVED
}

class DepthLimitedFileObserver(
    private val rootDir: File,
    maxDepth: Int,
    private val shouldIgnore: (relativePath: String, isDirectory: Boolean) -> Boolean,
    private val discoverDescendantsOnEvent: Boolean = true,
    private val onChange: (WorkspaceFileChange) -> Unit,
) {
    companion object {
        private const val TAG = "DepthLimitedFileObserver"
        private const val EVENT_MASK =
            FileObserver.CREATE or
                FileObserver.DELETE or
                FileObserver.MODIFY or
                FileObserver.CLOSE_WRITE or
                FileObserver.MOVED_FROM or
                FileObserver.MOVED_TO or
                FileObserver.DELETE_SELF or
                FileObserver.MOVE_SELF
    }

    private val maxDepth = maxDepth.coerceAtLeast(0)
    private val observers = LinkedHashMap<String, DirectoryObserver>()
    private val rootPath: String = rootDir.canonicalFile.absolutePath

    @Synchronized
    internal fun isWatchingDirectory(directory: File): Boolean = directory.absolutePath in observers

    @Synchronized
    fun start() {
        if (observers.isNotEmpty()) return
        if (!rootDir.exists() || !rootDir.isDirectory) return
        watchDirectory(rootDir.canonicalFile)
        addExistingDirectories(rootDir.canonicalFile, 0)
    }

    @Synchronized
    fun stop() {
        observers.values.forEach { observer ->
            runCatching { observer.stopWatching() }
                .onFailure { AppLogger.w(TAG, "Failed to stop observer for ${observer.directory}", it) }
        }
        observers.clear()
    }

    /** 基线枚举进入目录时立即订阅，该轮扫描无需再递归遍历一次目录。 */
    @Synchronized
    internal fun watchDirectory(directory: File): Boolean {
        if (!Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS)) return false
        val canonicalDir = directory.canonicalFile
        val relativePath = relativePathFor(canonicalDir) ?: return false
        if (shouldIgnore(relativePath, true)) return false
        val depth = depthOf(relativePath)
        if (depth > maxDepth) return false

        val key = canonicalDir.absolutePath
        if (observers.containsKey(key)) return false

        val observer = DirectoryObserver(canonicalDir)
        observer.startWatching()
        observers[key] = observer
        return true
    }

    private fun addExistingDirectories(directory: File, depth: Int) {
        if (depth >= maxDepth) return
        directory.listFiles()
            ?.asSequence()
            ?.filter { Files.isDirectory(it.toPath(), NOFOLLOW_LINKS) }
            ?.forEach { child ->
                val relativePath = relativePathFor(child.canonicalFile) ?: return@forEach
                if (shouldIgnore(relativePath, true)) return@forEach
                if (watchDirectory(child)) addExistingDirectories(child, depth + 1)
            }
    }

    @Synchronized
    private fun removeDirectoryObservers(directory: File) {
        val canonicalPath = directory.canonicalFile.absolutePath
        val keysToRemove =
            observers.keys.filter { key ->
                key == canonicalPath || key.startsWith("$canonicalPath${File.separator}")
            }
        keysToRemove.forEach { key ->
            observers.remove(key)?.stopWatching()
        }
    }

    private fun relativePathFor(file: File): String? {
        val fullPath = file.toPath().toAbsolutePath().normalize().toString()
        if (fullPath == rootPath) return ""
        if (!fullPath.startsWith("$rootPath${File.separator}")) return null
        return fullPath.removePrefix("$rootPath${File.separator}").replace(File.separatorChar, '/')
    }

    private fun depthOf(relativePath: String): Int {
        val rel = relativePath.trim('/').replace('\\', '/')
        if (rel.isBlank()) return 0
        return rel.count { it == '/' } + 1
    }

    private fun kindFor(event: Int): WorkspaceFileChangeKind? {
        val normalized = event and FileObserver.ALL_EVENTS
        return when {
            normalized and (FileObserver.CREATE or FileObserver.MOVED_TO) != 0 ->
                WorkspaceFileChangeKind.CREATED
            normalized and (FileObserver.DELETE or FileObserver.MOVED_FROM) != 0 ->
                WorkspaceFileChangeKind.DELETED
            normalized and (FileObserver.MODIFY or FileObserver.CLOSE_WRITE) != 0 ->
                WorkspaceFileChangeKind.MODIFIED
            normalized and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0 ->
                WorkspaceFileChangeKind.MOVED
            else -> null
        }
    }

    private inner class DirectoryObserver(val directory: File) :
        FileObserver(directory.absolutePath, EVENT_MASK) {
        override fun onEvent(event: Int, path: String?) {
            // SELF 通知没有子路径，仍须让日志看到观察目录本身被移动或删除。
            val changedFile = path?.takeIf { it.isNotBlank() }?.let { File(directory, it) } ?: directory
            val relativePath = relativePathFor(changedFile) ?: return
            val eventKind = kindFor(event) ?: return

            val isDirectoryNow = Files.isDirectory(changedFile.toPath(), NOFOLLOW_LINKS)
            val wasObservedDirectory =
                synchronized(this@DepthLimitedFileObserver) {
                    observers.containsKey(changedFile.absolutePath)
                }
            if (shouldIgnore(relativePath, isDirectoryNow || wasObservedDirectory)) return

            if (eventKind == WorkspaceFileChangeKind.CREATED && isDirectoryNow) {
                synchronized(this@DepthLimitedFileObserver) {
                    watchDirectory(changedFile)
                    // 终端日志会在采集事件子树时注册后代，避免阻塞 Android 共享事件线程。
                    if (discoverDescendantsOnEvent) addExistingDirectories(changedFile, depthOf(relativePath))
                }
            }

            if (wasObservedDirectory && (eventKind == WorkspaceFileChangeKind.DELETED || eventKind == WorkspaceFileChangeKind.MOVED)) {
                removeDirectoryObservers(changedFile)
            }

            onChange(
                WorkspaceFileChange(
                    relativePath = relativePath,
                    kind = eventKind,
                    createdInPlace = event and FileObserver.CREATE != 0,
                )
            )
        }
    }
}
