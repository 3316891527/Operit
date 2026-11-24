package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import android.util.Base64
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.builtin.ToolGetter
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.terminal.utils.SSHFileConnectionManager
import com.ai.assistance.operit.terminal.TerminalManager
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException

internal const val CHANGE_SNAPSHOT_LIMIT = SnapshotPackStore.MAX_OBJECT_BYTES

/** 直接复用文件后端读取原始字节，不触发工具生命周期或额外权限弹窗。 */
internal class WorkspaceChangeAccess(
    private val context: Context,
    storeProvider: () -> WorkspaceChangeStore = { WorkspaceChangeStore.get(context) },
    private val snapshotLease: WorkspaceChangeStore.SnapshotLease? = null,
) {
    private val store by lazy(storeProvider)
    private val tools by lazy { ToolGetter.getFileSystemTools(context) }

    suspend fun location(path: String, environment: String?): ChangeLocation {
        val env = changeEnvironment(environment)
        return ChangeLocation(changePath(path), env, resolvedBackend(context, env))
    }

    /** 本机目录链接不能把快照采集带到绑定目录之外。 */
    fun withinWorkspace(location: ChangeLocation, workspace: ChangeLocation): Boolean {
        if (relativeChangePath(workspace, location) == null) return false
        val root = hostFile(workspace) ?: return true
        val target = hostFile(location) ?: return false
        return changePathContains(root.canonicalPath, target.canonicalPath)
    }

    /** 目录内链接及 Android/Linux 映射使用同一物理写入锁。 */
    fun mutationIdentity(location: ChangeLocation): ChangeLocation = hostFile(location)?.let {
        ChangeLocation(changePath(it.canonicalPath), "android", "android")
    } ?: location.historyLocation()

    private fun hostFile(location: ChangeLocation): java.io.File? = when (location.backend) {
        "android" -> java.io.File(location.path)
        "linux:local" -> java.io.File(com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping.mapLinuxPathToHostPath(
            location.path, java.io.File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu"),
            context.filesDir.path, context.applicationInfo.dataDir, context.packageName,
            context.getSharedPreferences("terminal_settings", Context.MODE_PRIVATE).getBoolean("chroot_enabled", false)))
        else -> null
    }

    suspend fun snapshot(location: ChangeLocation, byteBudget: Long = CHANGE_SNAPSHOT_LIMIT.toLong()): ChangeSnapshot {
        return try {
            check(location.backend == backend(context, location.environment)) { "File backend changed during operation" }
            val existsResult = tools.fileExists(tool("file_exists", location))
            check(existsResult.success) { existsResult.error ?: "Cannot inspect file" }
            val exists = existsResult.result as FileExistsData
            when {
                !exists.exists -> ChangeSnapshot(SnapshotKind.ABSENT)
                exists.isDirectory -> ChangeSnapshot(SnapshotKind.DIRECTORY)
                exists.size > CHANGE_SNAPSHOT_LIMIT -> ChangeSnapshot(SnapshotKind.TOO_LARGE, exists.size)
                exists.size > byteBudget -> ChangeSnapshot(SnapshotKind.UNAVAILABLE, exists.size, detail = "Operation snapshot byte limit reached")
                else -> {
                    val result = tools.readFileBinary(tool("read_file_binary", location))
                    check(result.success) { result.error ?: "Cannot read file" }
                    val data = result.result as BinaryFileContentData
                    val bytes = Base64.decode(data.contentBase64, Base64.DEFAULT)
                    check(bytes.size.toLong() == data.size) { "Incomplete file snapshot" }
                    if (bytes.size > CHANGE_SNAPSHOT_LIMIT) ChangeSnapshot(SnapshotKind.TOO_LARGE, bytes.size.toLong())
                    else if (bytes.size > byteBudget) ChangeSnapshot(SnapshotKind.UNAVAILABLE, bytes.size.toLong(), detail = "Operation snapshot byte limit reached")
                    else ChangeSnapshot(if (decodeChangeText(bytes) == null) SnapshotKind.BINARY else SnapshotKind.TEXT,
                        bytes.size.toLong(), store.putBlob(bytes, lease = snapshotLease))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ChangeSnapshot(SnapshotKind.UNAVAILABLE, detail = error.message)
        }
    }

    suspend fun directoryEntries(location: ChangeLocation): List<DirectoryListingData.FileEntry> {
        check(location.backend == backend(context, location.environment)) { "File backend changed during operation" }
        val result = tools.listFiles(tool("list_files", location))
        check(result.success) { result.error ?: "Cannot list directory" }
        return (result.result as DirectoryListingData).entries.filter { it.name != "." && it.name != ".." }
    }

    suspend fun children(location: ChangeLocation): List<ChangeLocation> = directoryEntries(location).map {
        location.copy(path = changePath(location.path + "/" + it.name))
    }

    private fun tool(name: String, location: ChangeLocation) = AITool(name, listOf(
        ToolParameter("path", location.path), ToolParameter("environment", location.environment),
    ))

    companion object {
        /** Linux 的首次读取可能建立 SSH 连接，先解析后端再冻结历史来源。 */
        suspend fun resolvedBackend(context: Context, environment: String?): String {
            if (changeEnvironment(environment) == "linux" && SSHFileConnectionManager.getInstance(context).getFileSystemProvider() == null) {
                TerminalManager.getInstance(context).getFileSystemProvider()
            }
            return backend(context, environment)
        }

        fun backend(context: Context, environment: String?): String {
            val env = changeEnvironment(environment)
            if (env != "linux") return env
            val ssh = SSHFileConnectionManager.getInstance(context)
            return if (ssh.getFileSystemProvider() != null) "linux:ssh:${checkNotNull(ssh.getCurrentConnectionId())}" else "linux:local"
        }
    }
}

internal fun decodeChangeText(bytes: ByteArray): String? {
    if (bytes.any { it == 0.toByte() }) return null
    return try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) { null }
}
