package com.ai.assistance.operit.ui.features.conversation.workspace.git

import android.content.Context
import android.util.Base64
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.terminal.provider.type.LocalTerminalProvider
import com.ai.assistance.operit.terminal.utils.SSHFileConnectionManager
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.WorkspaceChangeAccess
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.SnapshotKind
import java.util.UUID

internal data class GitCommandOutput(val bytes: ByteArray, val exitCode: Int, val error: String)

/** 隐藏执行传输使用编码帧，避免终端行协议破坏 Git 的 NUL 分隔内容。 */
internal class WorkspaceGitCommand(
    private val context: Context,
    private val directory: String,
    private val environment: String,
    private val backend: String,
) {
    private var local: LocalTerminalProvider? = null

    suspend fun text(vararg args: String): String = bytes(*args).toString(Charsets.UTF_8)

    suspend fun bytes(vararg args: String): ByteArray {
        val result = run(args.toList())
        check(result.exitCode == 0) { "git ${args.firstOrNull()} (${result.exitCode}): ${result.error}" }
        return result.bytes
    }

    suspend fun run(args: List<String>): GitCommandOutput {
        require(environment == "android" || environment == "linux") {
            context.getString(R.string.workspace_changes_git_environment)
        }
        check(WorkspaceChangeAccess.backend(context, environment) == backend) { "Workspace backend changed" }
        val argv = listOf("git", "--no-optional-locks", "--literal-pathspecs", "-c", "core.quotepath=false", "-c", "diff.relative=false", "-C", directory) + args
        val command = argv.joinToString(" ") { quoteGitArgument(it) }
        return framed(command)
    }

    /** NUL 分隔保留空白和换行文件名；失败不返回部分目录清单。 */
    suspend fun fileInventory(): Map<String, String> {
        val root = quoteGitArgument(directory)
        val result = framed("""cd -- $root && find . -name .git -prune -o \( -type f -o -type l \) -printf '%P\0%y:%s:%T@\0'""")
        check(result.exitCode == 0) { result.error }
        val fields = result.bytes.toString(Charsets.UTF_8).split('\u0000').dropLast(1)
        require(fields.size % 2 == 0) { "Invalid workspace inventory" }
        return fields.chunked(2).associate { it[0] to it[1] }
    }

    /** 原始字节最多读取上限加一，远端链接也只记录目标文本。 */
    suspend fun workspaceFileBytes(path: String): ByteArray {
        val target = quoteGitArgument(path)
        val result = framed("""
            if [ -L $target ]; then
                readlink -n -- $target
            elif [ -f $target ]; then
                head -c 2097153 -- $target
            else
                printf '%s' 'Workspace file is no longer readable' >&2
                false
            fi
        """.trimIndent())
        check(result.exitCode == 0) { result.error }
        return result.bytes
    }

    suspend fun workingFile(path: String): Pair<String?, SnapshotKind> {
        val target = quoteGitArgument(path)
        val result = framed("""
            if [ -L $target ]; then
                printf 'file\0'
                readlink -n -- $target
            elif [ ! -e $target ]; then
                printf 'absent\0'
            elif [ -d $target ]; then
                printf 'directory\0'
            elif [ ${'$'}(wc -c <$target) -gt 2097152 ]; then
                printf 'large\0'
            else
                printf 'file\0'
                cat -- $target
            fi
        """.trimIndent())
        check(result.exitCode == 0) { result.error }
        val separator = result.bytes.indexOf(0.toByte())
        check(separator >= 0) { "Invalid file response" }
        val kind = result.bytes.copyOfRange(0, separator).toString(Charsets.UTF_8)
        val bytes = result.bytes.copyOfRange(separator + 1, result.bytes.size)
        return when (kind) {
            "absent" -> "" to SnapshotKind.ABSENT
            "directory" -> null to SnapshotKind.DIRECTORY
            "large" -> null to SnapshotKind.TOO_LARGE
            "file" -> {
                if (bytes.size > 2097152) null to SnapshotKind.TOO_LARGE
                else {
                    val text = com.ai.assistance.operit.ui.features.conversation.workspace.changes.decodeChangeText(bytes)
                    text to if (text == null) SnapshotKind.BINARY else SnapshotKind.TEXT
                }
            }
            else -> error("Invalid file kind: $kind")
        }
    }

    private suspend fun framed(command: String): GitCommandOutput {
        check(WorkspaceChangeAccess.backend(context, environment) == backend) { "Workspace backend changed" }
        val marker = "OPERIT_GIT_${UUID.randomUUID().toString().replace("-", "")}"
        val script = """
            (
                operit_git_tmp=${'$'}(mktemp -d) || exit 1
                trap 'rm -rf -- "${'$'}operit_git_tmp"' EXIT
                {
                    $command
                } >"${'$'}operit_git_tmp/out" 2>"${'$'}operit_git_tmp/err"
                operit_git_status=${'$'}?
                if [ ${'$'}(wc -c <"${'$'}operit_git_tmp/out") -gt 4194304 ]; then
                    : >"${'$'}operit_git_tmp/out"
                    printf '%s' 'Git output exceeds review limit' >"${'$'}operit_git_tmp/err"
                    operit_git_status=1
                fi
                printf '$marker:%s:' "${'$'}operit_git_status"
                base64 <"${'$'}operit_git_tmp/out" | tr -d '\r\n'
                printf ':'
                base64 <"${'$'}operit_git_tmp/err" | tr -d '\r\n'
                printf ':$marker\n'
            )
        """.trimIndent()
        val result = if (backend.startsWith("linux:ssh:")) {
            val id = backend.removePrefix("linux:ssh:")
            SSHFileConnectionManager.getInstance(context).executeCommand(script, 30_000L, id).getOrThrow()
        } else {
            check(OfflineResources.ubuntuInstalled(context)) { context.getString(R.string.workspace_changes_git_runtime) }
            val provider = local ?: LocalTerminalProvider(context.applicationContext).also { local = it }
            provider.executeHiddenCommand(script, "workspace-review", 30_000L)
        }
        check(result.isOk && result.exitCode == 0) { "${result.error}\n${result.rawOutputPreview}" }
        val frame = result.output.lineSequence().singleOrNull {
            it.startsWith("$marker:") && it.endsWith(":$marker")
        }
        check(frame != null) { "Invalid Git command response" }
        val fields = frame.split(':')
        check(fields.size == 5) { "Invalid Git response frame" }
        return GitCommandOutput(
            Base64.decode(fields[2], Base64.DEFAULT), fields[1].toInt(),
            Base64.decode(fields[3], Base64.DEFAULT).toString(Charsets.UTF_8),
        )
    }

    suspend fun close() { local?.disconnect() }
}

internal fun quoteGitArgument(value: String): String {
    require(!value.contains('\u0000'))
    return "'" + value.replace("'", "'\"'\"'") + "'"
}
