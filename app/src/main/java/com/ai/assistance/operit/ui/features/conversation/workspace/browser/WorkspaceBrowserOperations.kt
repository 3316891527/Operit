package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.system.Os
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.files.transfer.FileCopyObserver
import com.ai.assistance.operit.core.files.transfer.FileCopyObservers
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceCopyProgress
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceDeleteOperation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.workspaceMimeTypeForPath
import android.net.Uri
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.SafWorkspacePropertySource
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.WorkspacePropertiesTarget
import java.util.UUID
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import com.ai.assistance.operit.core.files.transfer.LocalTransferPaths
import com.ai.assistance.operit.core.files.transfer.LocalFileDeletion
import com.ai.assistance.operit.terminal.utils.SSHFileConnectionManager
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.WorkspacePropertyAccess
import com.ai.assistance.operit.core.tools.DirectoryListingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 界面操作使用现有文件工具，保留每次工具调用的成功或失败结果。 */
internal class FileBrowserOperations(context: Context) {
    private val context = context.applicationContext
    private val access = WorkspacePropertyAccess(this.context)
    private val handler = AIToolHandler.getInstance(this.context)
    private val copyState = MutableStateFlow<WorkspaceCopyProgress?>(null)
    val copyProgress = copyState.asStateFlow()
    private val deletionSource = WorkspaceSearchRepository(this.context)
    private val deletion = WorkspaceDeleteOperation(
        describe = { target ->
            val node = com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.WorkspacePropertiesRepository
                .open(this.context, WorkspacePropertiesTarget(target.path, target.environment)).read().node
            target.copy(isDirectory = node.directory && !node.symbolicLink)
        },
        children = { target ->
            deletionSource.children(WorkspaceLocation(target.path, workspaceEnvironment(target.environment))).map { child ->
                FileBrowserClipboardItem(child.location.path, child.location.environment, false, child.directory && !child.symbolicLink)
            }
        },
        deleteItem = { target ->
            val local = localFile(target.path, target.environment)
            if (local != null) {
                access.requireReadable(local)
                val execution = currentCoroutineContext()
                // 清单已经展开，目录只允许删除空目录，不能递归吞掉并发新增的内容。
                LocalFileDeletion.delete(local.toPath(), false) { execution.ensureActive() }
            } else {
                if (target.isDirectory) check(deletionSource.children(WorkspaceLocation(target.path,
                    workspaceEnvironment(target.environment))).isEmpty()) { "Directory changed during deletion" }
                execute("delete_file", parameters(target.environment, "path" to target.path, "recursive" to "false"))
            }
        },
    )
    val deleteProgress = deletion.progress

    private fun parameters(environment: String?, vararg values: Pair<String, String>) = buildList {
        values.forEach { (name, value) -> add(ToolParameter(name, value)) }
        environment?.let { add(ToolParameter("environment", it)) }
    }

    private fun execute(name: String, parameters: List<ToolParameter>) =
        handler.executeTool(AITool(name, parameters)).also { check(it.success) { it.error.orEmpty() } }

    internal fun localFile(path: String, environment: String?): File? {
        val env = workspaceEnvironment(environment)
        return when {
            env == "android" -> LocalTransferPaths.resolve(context, path, env)
            env == "linux" && SSHFileConnectionManager.getInstance(context).getFileSystemProvider() == null ->
                LocalTransferPaths.resolve(context, path, env)
            else -> null
        }
    }

    private suspend fun moveEntry(source: String, destination: String, environment: String?) {
        if (workspaceEnvironment(environment).startsWith("repo:") &&
            source.substringBeforeLast('/') == destination.substringBeforeLast('/')) {
            val document = SafWorkspacePropertySource.open(context, WorkspacePropertiesTarget(source, environment))
            checkNotNull(android.provider.DocumentsContract.renameDocument(context.contentResolver, Uri.parse(document.root), destination.substringAfterLast('/')))
            return
        }
        val from = localFile(source, environment)
        val to = localFile(destination, environment)
        if (from != null && to != null) {
            access.requireReadable(from)
            access.requireReadable(to.parentFile!!)
            // Android 的文件系统提供器没有实现 getFileStore，使用原生 stat 的设备号判断同设备移动。
            val sourceDevice = Os.stat(from.parentFile!!.absolutePath).st_dev
            val destinationDevice = Os.stat(to.parentFile!!.absolutePath).st_dev
            if (sourceDevice == destinationDevice) {
                Files.move(from.toPath(), to.toPath())
                return
            }
        }
        execute("move_file", parameters(environment, "source" to source, "destination" to destination))
    }

    internal fun requireNewDestination(path: String, environment: String?) {
        val local = localFile(path, environment)
        if (local != null) {
            access.requireReadable(local.parentFile!!)
            check(!Files.exists(local.toPath(), LinkOption.NOFOLLOW_LINKS)) { context.getString(R.string.file_op_confirm_overwrite) }
            return
        }
        val result = execute("file_exists", parameters(environment, "path" to path))
        check(result.result is FileExistsData && !(result.result as FileExistsData).exists) {
            context.getString(R.string.file_op_confirm_overwrite)
        }
    }

    suspend fun rename(source: String, destination: String, environment: String?) = withContext(Dispatchers.IO) {
        if (source == destination) return@withContext
        requireNewDestination(destination, environment)
        moveEntry(source, destination, environment)
        Unit
    }

    private suspend fun deleteEntry(path: String, environment: String?, directory: Boolean) {
        val local = localFile(path, environment)
        if (local != null) {
            access.requireReadable(local)
            val executionContext = currentCoroutineContext()
            LocalFileDeletion.delete(local.toPath(), directory) { executionContext.ensureActive() }
        } else execute("delete_file", parameters(environment, "path" to path, "recursive" to directory.toString()))
    }

    suspend fun delete(path: String, environment: String?, directory: Boolean): Unit =
        delete(listOf(FileBrowserClipboardItem(path, environment, cut = false, isDirectory = directory)))

    suspend fun delete(
        targets: List<FileBrowserClipboardItem>,
        onDeleted: suspend (FileBrowserClipboardItem) -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        deletionSource.beginSearch()
        try { deletion.run(targets, onDeleted) } finally { deletionSource.beginSearch() }
    }

    suspend fun paste(directory: String, environment: String?) =
        transfer(FileBrowserClipboard.content.value, directory, environment, clearClipboard = true)

    /** 直接移动和归档复用同一传输路径，不改写用户剪贴板。 */
    suspend fun transfer(items: List<FileBrowserClipboardItem>, directory: String, environment: String?, clearClipboard: Boolean = false) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val destinationEnv = workspaceEnvironment(environment)
        val executionContext = currentCoroutineContext()
        fun progress(item: FileBrowserClipboardItem, index: Int) = WorkspaceCopyProgress(
            source = item.path,
            destination = directory.trimEnd('/') + "/" + item.path.trimEnd('/').substringAfterLast('/'),
            sourceEnvironment = item.environment ?: "android",
            destinationEnvironment = destinationEnv,
            itemIndex = index + 1,
            itemCount = items.size,
            moving = item.cut,
        )
        copyState.value = progress(items.first(), 0)
        try {
            val localParent = localFile(directory, environment)
            val existingNames = if (localParent != null) {
                access.requireReadable(localParent)
                check(Files.isDirectory(localParent.toPath())) { context.getString(R.string.workspace_error_invalid_path, directory) }
                // 本机仅查询选中的目标，粘贴少量文件不必枚举整个大目录。
                items.map { it.path.trimEnd('/').substringAfterLast('/') }.filter { name ->
                    Files.exists(File(localParent, name).toPath(), LinkOption.NOFOLLOW_LINKS)
                }.toSet()
            } else {
                val listing = execute("list_files", parameters(environment, "path" to directory)).result
                check(listing is DirectoryListingData) { context.getString(R.string.workspace_error_invalid_path, directory) }
                listing.entries.map { it.name }.toSet()
            }
            val targets = items.map { item ->
                val sourceEnv = workspaceEnvironment(item.environment)
                val destination = directory.trimEnd('/') + "/" + item.path.trimEnd('/').substringAfterLast('/')
                check(sourceEnv != destinationEnv ||
                    (destination != item.path && !destination.startsWith(item.path.trimEnd('/') + "/"))) {
                    context.getString(R.string.workspace_invalid_paste)
                }
                item to destination
            }
            check(targets.map { it.second }.distinct().size == targets.size) {
                context.getString(R.string.file_op_confirm_overwrite)
            }
            // 全批次先检查目标，避免遇到重名时已经移动了前面的文件。
            targets.forEach { (_, destination) ->
                check(destination.substringAfterLast('/') !in existingNames) { context.getString(R.string.file_op_confirm_overwrite) }
            }
            for ((index, target) in targets.withIndex()) {
                executionContext.ensureActive()
                val (item, destination) = target
                val sourceEnv = workspaceEnvironment(item.environment)
                val itemProgress = progress(item, index)
                copyState.value = itemProgress
                if (localParent != null) {
                    check(!Files.exists(File(localParent, destination.substringAfterLast('/')).toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        context.getString(R.string.file_op_confirm_overwrite)
                    }
                } else requireNewDestination(destination, environment)
                if (item.cut && sourceEnv == destinationEnv) {
                    moveEntry(item.path, destination, environment)
                } else {
                    val token = FileCopyObservers.register(FileCopyObserver(
                        onProgress = { copyState.value = itemProgress.copy(detail = it) },
                        checkCancelled = { executionContext.ensureActive() },
                    ))
                    try {
                        execute("copy_file", listOf(ToolParameter("source", item.path), ToolParameter("destination", destination),
                            ToolParameter("source_environment", sourceEnv), ToolParameter("dest_environment", destinationEnv),
                            ToolParameter("recursive", item.isDirectory.toString()), ToolParameter(FileCopyObservers.PARAMETER, token)))
                    } finally {
                        FileCopyObservers.remove(token)
                    }
                    // 取消或复制失败时不删除来源；整项成功后才允许完成跨环境剪切。
                    executionContext.ensureActive()
                    if (item.cut) {
                        copyState.value = checkNotNull(copyState.value).copy(finishing = true)
                        // 剪切收尾仍属于移动任务，不弹出第二个删除进度窗口。
                        deleteEntry(item.path, item.environment, item.isDirectory)
                    }
                }
                if (item.cut && clearClipboard) FileBrowserClipboard.clear(item)
            }
        } finally {
            copyState.value = null
        }
    }

    /** 所有文件准备成功后才展示分享面板，失败时保留用户选择并清理本次缓存。 */
    suspend fun share(items: List<FileBrowserClipboardItem>) = withContext(Dispatchers.IO) {
        require(items.isNotEmpty() && items.none { it.isDirectory }) { context.getString(R.string.workspace_selection_share_files_only) }
        val executionContext = currentCoroutineContext()
        val cache = File(context.cacheDir, "workspace_shared_files")
        pruneWorkspaceShareCache(cache)
        val session = File(cache, UUID.randomUUID().toString())
        check(session.mkdirs()) { context.getString(R.string.file_error_share_failed) }
        try {
            val uris = arrayListOf<Uri>()
            val types = mutableListOf<String>()
            val buffer = ByteArray(256 * 1024)
            suspend fun copyInput(input: java.io.InputStream, destination: File) {
                input.use { source -> destination.outputStream().use { output ->
                    while (true) {
                        executionContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count > 0) output.write(buffer, 0, count)
                    }
                } }
            }
            for ((index, item) in items.withIndex()) {
                executionContext.ensureActive()
                val directory = File(session, index.toString())
                check(directory.mkdir()) { context.getString(R.string.file_error_share_failed) }
                val file = File(directory, item.path.trimEnd('/').substringAfterLast('/'))
                val local = localFile(item.path, item.environment)
                when {
                    local != null -> {
                        access.requireReadable(local.canonicalFile)
                        copyInput(local.inputStream(), file)
                    }
                    workspaceEnvironment(item.environment).startsWith("repo:", true) -> {
                        val source = SafWorkspacePropertySource.open(context, WorkspacePropertiesTarget(item.path, item.environment))
                        copyInput(checkNotNull(context.contentResolver.openInputStream(Uri.parse(source.root))), file)
                    }
                    else -> {
                        val result = execute("read_file_binary", parameters(item.environment, "path" to item.path))
                        check(result.result is BinaryFileContentData) { context.getString(R.string.file_error_share_failed) }
                        executionContext.ensureActive()
                        file.writeBytes(Base64.decode((result.result as BinaryFileContentData).contentBase64, Base64.DEFAULT))
                    }
                }
                uris += FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                types += workspaceMimeTypeForPath(item.path)
            }
            val intent = workspaceShareIntent(uris, types)
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (failure: Exception) {
            session.deleteRecursively()
            throw failure
        }
    }

    suspend fun externalOpen(path: String, environment: String?, share: Boolean) {
        val file = withContext(Dispatchers.IO) {
            pruneWorkspaceShareCache(File(context.cacheDir, "workspace_shared_files"))
            if (environment.isNullOrBlank() || environment == "android") File(path)
            else {
                val directory = File(context.cacheDir, "workspace_shared_files/${UUID.randomUUID()}")
                check(directory.mkdirs()) { context.getString(R.string.file_error_share_failed) }
                try {
                    transfer(listOf(FileBrowserClipboardItem(path, environment, false, false)), directory.path, "android")
                    File(directory, path.substringAfterLast('/'))
                } catch (failure: Exception) { directory.deleteRecursively(); throw failure }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = if (share) Intent(Intent.ACTION_SEND).apply {
            type = workspaceMimeTypeForPath(path)
            putExtra(Intent.EXTRA_STREAM, uri)
        } else Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, workspaceMimeTypeForPath(path)) }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newRawUri(file.name, uri)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
