package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import android.content.Context
import android.os.Build
import android.system.Os
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.*
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.*
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.io.IOException
import com.ai.assistance.operit.R
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class WorkspaceArchiveSession(
    val source: WorkspaceLocation,
    val directory: File,
    val file: File,
    val kind: WorkspaceArchiveKind,
    val reader: WorkspaceArchiveReader,
    val stamp: PropertyNode,
    val password: String,
    val localVersion: String?,
    val legacyDigest: ByteArray?,
) {
    val entries: List<WorkspaceArchiveEntry> get() = reader.entries
    val openedEntries = mutableMapOf<String, WorkspaceArchiveOpenedEntry>()
}

internal data class WorkspaceArchiveOpenedEntry(val file: File, val version: String, val legacyDigest: ByteArray?)

/** 每个归档会话独立缓存，提交前核对来源；共享传输层覆盖本机、授权目录和远程来源。 */
internal class WorkspaceArchiveStore(context: Context) {
    private val context = context.applicationContext
    private val operations = FileBrowserOperations(this.context)
    private val mutex = Mutex()
    private val state = MutableStateFlow(WorkspaceArchiveProgress())
    val progress = state.asStateFlow()
    private fun engine() = WorkspaceArchiveEngine { state.value = it }
    private fun temporary(): File = File(context.cacheDir, "workspace_archives/${UUID.randomUUID()}").also { check(it.mkdirs()) }
    private fun item(location: WorkspaceLocation, directory: Boolean = false) =
        FileBrowserClipboardItem(location.path, location.environment, false, directory)
    private suspend fun stat(location: WorkspaceLocation): PropertyNode =
        WorkspacePropertiesRepository.open(context, WorkspacePropertiesTarget(location.path, location.environment)).read().node

    // inode 和高精度 ctime 能识别保留长度及 mtime 的改写，不需要为浏览复制整个本机文件。
    private fun localVersion(file: File): String {
        val value = Os.stat(file.absolutePath)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            "${value.st_dev}:${value.st_ino}:${value.st_size}:${value.st_mtim.tv_sec}:${value.st_mtim.tv_nsec}:${value.st_ctim.tv_sec}:${value.st_ctim.tv_nsec}"
        } else "${value.st_dev}:${value.st_ino}:${value.st_size}:${value.st_mtime}:${value.st_ctime}"
    }

    private suspend fun requireUnchanged(session: WorkspaceArchiveSession) {
        check(stat(session.source) == session.stamp) { "Archive source changed" }
        if (session.localVersion != null) check(localVersion(session.file) == session.localVersion) { "Archive source changed" }
        session.legacyDigest?.let { check(digest(session.file).contentEquals(it)) { "Archive source changed" } }
    }

    suspend fun open(source: WorkspaceLocation, password: String): WorkspaceArchiveSession = withContext(Dispatchers.IO) {
        val directory = temporary()
        var reader: WorkspaceArchiveReader? = null
        try {
            state.value = WorkspaceArchiveProgress(source.path, stage = WorkspaceArchiveStage.READING)
            val local = operations.localFile(source.path, source.environment)
            if (local != null) WorkspacePropertyAccess(context).requireReadable(local)
            val before = stat(source)
            require(!before.directory && !before.symbolicLink)
            val version = local?.let(::localVersion)
            val file = if (local != null) local else {
                operations.transfer(listOf(item(source)), directory.path, "android")
                File(directory, source.path.substringAfterLast('/'))
            }
            // API 26 没有公开纳秒时间字段，该平台使用内容摘要作为读取版本的一部分。
            val legacy = if (local != null && Build.VERSION.SDK_INT == Build.VERSION_CODES.O) digest(file) else null
            val kind = checkNotNull(workspaceArchiveKind(source.path))
            val opened = engine().openReader(file, kind, password)
            reader = opened
            val session = WorkspaceArchiveSession(source, directory, file, kind, opened, before, password, version, legacy)
            requireUnchanged(session)
            session
        } catch (failure: Exception) {
            reader?.close()
            directory.deleteRecursively()
            throw failure
        }
    }

    suspend fun close(session: WorkspaceArchiveSession) = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            try { session.reader.close() } finally { session.directory.deleteRecursively() }
        }
    }
    private suspend fun stagedInputs(targets: List<FileBrowserClipboardItem>, directory: File): List<WorkspaceArchiveInput> {
        val files = mutableListOf<File>()
        val roots = targets.filter { target -> targets.none { parent ->
            parent != target && parent.isDirectory && workspaceEnvironment(parent.environment) == workspaceEnvironment(target.environment) &&
                target.path.startsWith(parent.path.trimEnd('/') + "/")
        } }.distinctBy { workspaceEnvironment(it.environment) to it.path }
        for ((index, target) in roots.withIndex()) {
            currentCoroutineContext().ensureActive()
            val node = stat(WorkspaceLocation(target.path, workspaceEnvironment(target.environment)))
            require(!node.symbolicLink) { "Archive sources cannot contain links" }
            val local = operations.localFile(target.path, target.environment)
            if (local != null) {
                WorkspacePropertyAccess(context).requireReadable(local)
                require(!directory.canonicalFile.toPath().startsWith(local.canonicalFile.toPath())) { "Archive source contains its temporary directory" }
                files += local
            } else {
                if (node.directory) {
                    val repository = WorkspaceSearchRepository(context)
                    val pending = ArrayDeque<WorkspaceLocation>()
                    pending.add(WorkspaceLocation(target.path, workspaceEnvironment(target.environment)))
                    while (pending.isNotEmpty()) {
                        currentCoroutineContext().ensureActive()
                        for (entry in repository.children(pending.removeLast())) {
                            require(!entry.symbolicLink) { "Archive sources cannot contain links" }
                            if (entry.directory) pending.add(entry.location)
                        }
                    }
                }
                val parent = File(directory, "input-$index").also { check(it.mkdir()) }
                operations.transfer(listOf(target.copy(cut = false)), parent.path, "android")
                check(stat(WorkspaceLocation(target.path, workspaceEnvironment(target.environment))) == node) { "Archive source changed" }
                files += File(parent, target.path.trimEnd('/').substringAfterLast('/'))
            }
        }
        return engine().inputs(files)
    }
    suspend fun create(targets: List<FileBrowserClipboardItem>, destination: WorkspaceLocation, name: String,
        kind: WorkspaceArchiveKind, password: String, level: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(isValidWorkspaceEntryName(name))
            state.value = WorkspaceArchiveProgress(stage = WorkspaceArchiveStage.PREPARING)
            var stage = R.string.workspace_archive_stage_target
            try {
                operations.requireNewDestination(workspaceChild(destination, name).path, destination.environment)
                val root = temporary()
                try {
                    val output = File(root, name)
                    stage = R.string.workspace_archive_stage_sources
                    val inputs = stagedInputs(targets, root)
                    stage = R.string.workspace_archive_stage_write
                    engine().create(output, kind, inputs, password, level)
                    stage = R.string.workspace_archive_stage_verify
                    engine().verify(output, kind, password)
                    currentCoroutineContext().ensureActive()
                    stage = R.string.workspace_archive_stage_publish
                    publish(output, destination, name)
                } finally { root.deleteRecursively() }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                val reason = generateSequence<Throwable>(failure) { it.cause }.take(8).map { cause ->
                    val message = cause.localizedMessage
                    if (message.isNullOrBlank()) cause.javaClass.simpleName else cause.javaClass.simpleName + ": " + message
                }.distinct().joinToString("\n")
                throw IOException(context.getString(R.string.workspace_archive_create_failed_detail, name,
                    context.getString(stage), destination.environment + ": " + destination.path, reason), failure)
            }
        }
    }
    private suspend fun publish(file: File, destination: WorkspaceLocation, name: String) {
        val stagedName = ".$name.${UUID.randomUUID()}.tmp"
        val stagedLocal = File(file.parentFile, stagedName)
        Files.move(file.toPath(), stagedLocal.toPath())
        val staged = workspaceChild(destination, stagedName)
        val target = workspaceChild(destination, name)
        var published = false
        try {
            transferExtracted(listOf(stagedLocal), listOf(WorkspaceArchiveEntry(stagedLocal.name, false, stagedLocal.length())),
                checkNotNull(stagedLocal.parentFile), destination)
            state.value = WorkspaceArchiveProgress(name, stage = WorkspaceArchiveStage.PUBLISHING)
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) { operations.rename(staged.path, target.path, target.environment); published = true }
        } finally {
            if (!published) withContext(NonCancellable) {
                try { operations.delete(staged.path, staged.environment, false) }
                catch (failure: Exception) { AppLogger.e("WorkspaceArchive", "清理未发布的归档临时文件失败：${staged.path}", failure) }
            }
        }
    }
    private suspend fun transferExtracted(files: List<File>, entries: List<WorkspaceArchiveEntry>, root: File,
        destination: WorkspaceLocation) = coroutineScope {
        val regular = entries.filterNot { it.directory }
        val counts = files.map { file -> regular.count { it.path == file.name || it.path.startsWith(file.name + "/") } }
        val sizes = files.map { file -> regular.filter { it.path == file.name || it.path.startsWith(file.name + "/") }
            .fold(0L) { sum, entry -> Math.addExact(sum, File(root, entry.path).length()) } }
        val totalCount = counts.sum()
        val totalBytes = sizes.fold(0L, Math::addExact)
        state.value = WorkspaceArchiveProgress(stage = WorkspaceArchiveStage.TRANSFERRING,
            total = totalBytes, totalEntries = totalCount.takeIf { it > 0 })
        val forwarding = launch(start = CoroutineStart.UNDISPATCHED) {
            operations.copyProgress.filterNotNull().collect { current ->
                val index = current.itemIndex - 1
                val doneCount = counts.take(index).sum()
                val doneBytes = sizes.take(index).fold(0L, Math::addExact)
                val detail = current.detail
                state.value = WorkspaceArchiveProgress(entry = if (detail == null) current.source else detail.currentFile,
                    bytes = doneBytes + if (detail == null) 0L else detail.copiedBytes, total = totalBytes,
                    stage = WorkspaceArchiveStage.TRANSFERRING,
                    completedEntries = doneCount + if (detail == null) 0 else Math.toIntExact(detail.copiedFiles), totalEntries = totalCount.takeIf { it > 0 })
            }
        }
        try {
            operations.transfer(files.map { FileBrowserClipboardItem(it.path, "android", false, it.isDirectory) }, destination.path, destination.environment)
        } finally { forwarding.cancel() }
        state.value = WorkspaceArchiveProgress(bytes = totalBytes, total = totalBytes, stage = WorkspaceArchiveStage.TRANSFERRING,
            completedEntries = totalCount, totalEntries = totalCount)
    }

    suspend fun extract(session: WorkspaceArchiveSession, selected: Set<String>, destination: WorkspaceLocation) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireUnchanged(session)
            val chosen = if (selected.isEmpty()) session.entries else workspaceArchiveSelection(session.entries, selected)
            val names = chosen.map { it.path.substringBefore('/') }.distinct()
            for (name in names) operations.requireNewDestination(workspaceChild(destination, name).path, destination.environment)
            val local = operations.localFile(destination.path, destination.environment)
            val stage = if (local != null) {
                WorkspacePropertyAccess(context).requireReadable(local)
                File(local, ".operit-extract-${UUID.randomUUID()}").also { java.nio.file.Files.createDirectory(it.toPath()) }
            } else File(session.directory, "extract-${UUID.randomUUID()}").also { check(it.mkdir()) }
            val root = File(stage, "content")
            try {
                val files = engine().extract(session.reader, selected, root)
                requireUnchanged(session)
                if (local != null) {
                    // 隐藏暂存位于目标文件系统，发布只需重命名，不再逐字节复制一遍。
                    var publishedEntries = 0
                    for (file in files) {
                        currentCoroutineContext().ensureActive()
                        state.value = WorkspaceArchiveProgress(file.name, stage = WorkspaceArchiveStage.PUBLISHING,
                            completedEntries = publishedEntries, totalEntries = chosen.size)
                        Files.move(file.toPath(), File(local, file.name).toPath())
                        publishedEntries += chosen.count { it.path == file.name || it.path.startsWith(file.name + "/") }
                    }
                    state.value = WorkspaceArchiveProgress(stage = WorkspaceArchiveStage.PUBLISHING,
                        completedEntries = publishedEntries, totalEntries = chosen.size)
                } else if (files.isNotEmpty()) transferExtracted(files, chosen, root, destination)
            } finally { stage.deleteRecursively() }
        }
    }

    private var openedCachePrepared = false
    suspend fun openEntry(session: WorkspaceArchiveSession, entry: WorkspaceArchiveEntry, reportProgress: Boolean = true): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(!entry.directory && !entry.symbolicLink && session.reader.entriesByPath[entry.path] == entry)
            requireUnchanged(session)
            val cached = session.openedEntries[entry.path]
            if (cached != null && java.nio.file.Files.isRegularFile(cached.file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                localVersion(cached.file) == cached.version &&
                (cached.legacyDigest == null || digest(cached.file).contentEquals(cached.legacyDigest))) return@withLock cached.file
            // 打开的文件独立于会话；会话关闭后编辑器、分享面板和安装器仍可继续读取。
            val parent = File(context.cacheDir, "workspace_archive_opened").also { check(it.isDirectory || it.mkdirs()) }
            if (!openedCachePrepared) {
                parent.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.deleteRecursively() }
                openedCachePrepared = true
            }
            val root = File(parent, UUID.randomUUID().toString())
            try {
                val worker = if (reportProgress) engine() else WorkspaceArchiveEngine()
                worker.extract(session.reader, setOf(entry.path), root)
                requireUnchanged(session)
                File(root, entry.path).also { session.openedEntries[entry.path] = WorkspaceArchiveOpenedEntry(it, localVersion(it), if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) digest(it) else null) }
            } catch (failure: Exception) { root.deleteRecursively(); throw failure }
        }
    }
    private suspend fun digest(file: File): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(256 * 1024)
        file.inputStream().use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(bytes)
                if (count < 0) break
                hash.update(bytes, 0, count)
            }
        }
        return hash.digest()
    }
    private suspend fun commit(session: WorkspaceArchiveSession, output: File, baseline: File) {
        requireUnchanged(session)
        val checkDirectory = File(session.directory, "compare-${UUID.randomUUID()}").also { check(it.mkdir()) }
        try {
            operations.transfer(listOf(item(session.source)), checkDirectory.path, "android")
            check(digest(File(checkDirectory, session.file.name)).contentEquals(digest(baseline))) { "Archive source changed" }
        } finally { checkDirectory.deleteRecursively() }
        currentCoroutineContext().ensureActive()
        val local = operations.localFile(session.source.path, session.source.environment)
        if (local != null) {
            val staged = File(local.parentFile, ".${local.name}.${UUID.randomUUID()}.tmp")
            try {
                Files.copy(output.toPath(), staged.toPath())
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    requireUnchanged(session)
                    Files.move(staged.toPath(), local.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { staged.delete() }
        } else {
            val token = UUID.randomUUID().toString()
            val parent = session.source.path.substringBeforeLast('/', "").ifEmpty { "/" }
            val stagedName = ".${session.file.name}.$token.tmp"
            val localStaged = File(session.directory, stagedName)
            Files.copy(output.toPath(), localStaged.toPath())
            val staged = workspaceChild(WorkspaceLocation(parent, session.source.environment), stagedName)
            val backup = workspaceChild(WorkspaceLocation(parent, session.source.environment), ".${session.file.name}.$token.bak")
            var prepared = true
            try {
                operations.transfer(listOf(FileBrowserClipboardItem(localStaged.path, "android", false, false)), parent, session.source.environment)
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    check(stat(session.source) == session.stamp) { "Archive source changed" }
                    operations.rename(session.source.path, backup.path, backup.environment)
                    try { operations.rename(staged.path, session.source.path, staged.environment) } catch (failure: Exception) {
                        // 两次重命名组成提交事务；第二步失败必须还原原名，不能丢失原包。
                        try { operations.rename(backup.path, session.source.path, backup.environment) } catch (restore: Exception) { failure.addSuppressed(restore) }
                        throw failure
                    }
                    prepared = false
                    try { operations.delete(backup.path, backup.environment, false) } catch (failure: Exception) { AppLogger.e("WorkspaceArchive", "归档已提交，清理备份失败：${backup.path}", failure) }
                }
            } finally {
                if (prepared) withContext(NonCancellable) {
                    try { operations.delete(staged.path, staged.environment, false) }
                    catch (failure: Exception) { AppLogger.e("WorkspaceArchive", "清理未提交的归档失败：${staged.path}", failure) }
                }
            }
        }
    }
    suspend fun modify(session: WorkspaceArchiveSession, remove: Set<String> = emptySet(), rename: Pair<String, String>? = null,
        additions: List<FileBrowserClipboardItem> = emptyList(), directory: String = "") = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(session.kind == WorkspaceArchiveKind.ZIP)
            val root = File(session.directory, "edit-${UUID.randomUUID()}").also { check(it.mkdir()) }
            try {
                val output = File(root, "updated.zip")
                val inputs = stagedInputs(additions, root)
                requireUnchanged(session)
                val baseline = if (session.localVersion != null) {
                    state.value = WorkspaceArchiveProgress(session.file.name, stage = WorkspaceArchiveStage.PREPARING)
                    File(root, "source-snapshot.zip").also { snapshot ->
                        Files.copy(session.file.toPath(), snapshot.toPath())
                        requireUnchanged(session)
                    }
                } else session.file
                engine().rewriteZip(baseline, output, session.password, remove, rename, inputs, directory.takeIf(String::isNotEmpty)?.plus("/").orEmpty())
                state.value = WorkspaceArchiveProgress(session.file.name, stage = WorkspaceArchiveStage.PUBLISHING)
                commit(session, output, baseline)
            } finally { root.deleteRecursively() }
        }
    }
}
