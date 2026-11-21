package com.ai.assistance.operit.ui.features.conversation.workspace.git

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.*
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorTextSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class GitLineProvenance(val root: String, val environment: String, val backend: String, val line: GitBlameLine?)
internal data class GitBlameCommitDiff(
    val provenance: GitLineProvenance, val message: String, val diff: PreparedReviewDiff,
    val parent: String?, val beforePath: String?,
)

/** 仅按菜单请求查询历史，不修改编辑缓冲区、工作树、暂存区或远端。 */
internal class WorkspaceGitBlameRepository(
    private val context: Context, path: String, environment: String?, private val expectedBackend: String,
) {
    private val filePath = changePath(path)
    private val directory = filePath.substringBeforeLast('/', "").ifEmpty { "/" }
    private val fileName = filePath.substringAfterLast('/')
    private val env = changeEnvironment(environment)
    private val mutex = Mutex()
    private data class HeadKey(val root: String, val backend: String, val commit: String, val path: String)
    private data class HeadFile(val key: HeadKey, val content: Pair<String, SnapshotKind>)
    private data class Mapping(val key: HeadKey, val owner: Any, val version: Int, val lines: Map<Int, Int>)
    private var headFile: HeadFile? = null
    private var mapping: Mapping? = null
    private val lines = object : LinkedHashMap<Int, GitBlameLine>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, GitBlameLine>): Boolean = size > 16
    }
    private var commitDiff: GitBlameCommitDiff? = null

    suspend fun load(snapshot: EditorTextSnapshot, line: Int): GitLineProvenance = mutex.withLock { withContext(Dispatchers.IO) {
        require(line in 1..snapshot.lines.size)
        check(snapshot.length <= CHANGE_SNAPSHOT_LIMIT && snapshot.lines.line(line - 1).text.length <= BLAME_MAX_LINE_CHARACTERS) {
            context.getString(R.string.workspace_blame_too_large)
        }
        checkBackend()
        commands(directory) { command ->
            val rootResult = command.run(listOf("rev-parse", "--show-toplevel"))
            check(rootResult.exitCode == 0) { context.getString(R.string.workspace_blame_not_repository) + "\n" + rootResult.error }
            val root = rootResult.bytes.toString(Charsets.UTF_8).removeSuffix("\n")
            val headResult = command.run(listOf("rev-parse", "--verify", "-q", "HEAD"))
            check(headResult.exitCode == 0 || headResult.exitCode == 1) { headResult.error }
            if (headResult.exitCode == 1) return@commands GitLineProvenance(root, env, expectedBackend, null)
            val head = headResult.bytes.toString(Charsets.UTF_8).trimEnd('\n')
            require(Regex("[0-9a-f]{40}|[0-9a-f]{64}").matches(head)) { "Invalid Git HEAD" }
            val prefix = command.text("rev-parse", "--show-prefix").removeSuffix("\n")
            val gitPath = WorkspaceGitBlameParser.validatePath(prefix + fileName)
            val key = HeadKey(root, expectedBackend, head, gitPath)
            var cached = headFile
            if (cached?.key != key) {
                cached = HeadFile(key, blob(command, root, head, gitPath))
                headFile = cached
                mapping = null
                lines.clear()
                commitDiff = null
            }
            val content = checkNotNull(cached).content
            if (content.second == SnapshotKind.ABSENT) return@commands GitLineProvenance(root, env, expectedBackend, null)
            var mapped = mapping
            if (mapped == null || mapped.key != key || mapped.owner !== snapshot.owner || mapped.version != snapshot.version) {
                val map = withContext(Dispatchers.Default) {
                    val current = snapshot.toString()
                    check(current.toByteArray(Charsets.UTF_8).size <= CHANGE_SNAPSHOT_LIMIT) { context.getString(R.string.workspace_blame_too_large) }
                    check(blameDiffSupported(ReviewContent(content.first, current, SnapshotKind.TEXT, SnapshotKind.TEXT))) {
                        context.getString(R.string.workspace_blame_too_large)
                    }
                    blameLineMapping(content.first, current)
                }
                mapped = Mapping(key, snapshot.owner, snapshot.version, map)
                mapping = mapped
            }
            val originalLine = checkNotNull(mapped).lines[line] ?: return@commands GitLineProvenance(root, env, expectedBackend, null)
            // 路径已按仓库根目录生成，不能让嵌套目录再次影响 blame 的路径解析。
            val found = lines[originalLine] ?: WorkspaceGitBlameParser.parse(command.text(
                "-C", root, "blame", "--line-porcelain", "--encoding=UTF-8", "--no-textconv", "--ignore-revs-file", "",
                "-L", "$originalLine,$originalLine", head, "--", gitPath,
            )).also { lines[originalLine] = it }
            check(found.finalLine == originalLine) { "Git blame line coordinates changed" }
            GitLineProvenance(root, env, expectedBackend, if (found.uncommitted) null else found)
        }
    } }

    suspend fun change(provenance: GitLineProvenance): GitBlameCommitDiff = mutex.withLock { withContext(Dispatchers.IO) {
        checkBackend()
        check(provenance.backend == expectedBackend && provenance.environment == env) { context.getString(R.string.workspace_blame_backend_changed) }
        val retained = commitDiff
        if (retained != null && retained.provenance == provenance) return@withContext retained
        val line = checkNotNull(provenance.line)
        commands(provenance.root) { command ->
            val history = blameCommitHistory(command.text("cat-file", "commit", line.commit))
            val parent = history.parent(line)
            val parentPath = if (parent == null) null else {
                val available = command.run(listOf("cat-file", "-e", "$parent^{commit}"))
                check(available.exitCode == 0) { context.getString(R.string.workspace_blame_history_unavailable) + "\n" + available.error }
                if (line.previousCommit == parent) checkNotNull(line.previousPath) else {
                    val changes = WorkspaceGitParser.nameStatus(command.text(
                        "diff-tree", "--no-commit-id", "--name-status", "-r", "-z", "--find-renames", "--no-ext-diff", "--no-textconv", parent, line.commit, "--",
                    ))
                    val renamed = changes.singleOrNull { it.path == line.path }?.originalPath
                    if (renamed == null) line.path else WorkspaceGitBlameParser.validatePath(renamed)
                }
            }
            val before = if (parent == null) "" to SnapshotKind.ABSENT else blob(command, provenance.root, parent, checkNotNull(parentPath))
            val after = blob(command, provenance.root, line.commit, line.path)
            check(after.second != SnapshotKind.ABSENT) { "Blamed file is absent from its source commit" }
            val content = ReviewContent(before.first, after.first, before.second, after.second)
            check(blameDiffSupported(content)) { context.getString(R.string.workspace_blame_too_large) }
            val message = command.text("show", "--no-patch", "--encoding=UTF-8", "--format=%B", line.commit).trimEnd('\n')
            val prepared = withContext(Dispatchers.Default) { prepareReviewDiff(context, line.path, content) }
            GitBlameCommitDiff(provenance, message, prepared, parent, parentPath).also { commitDiff = it }
        }
    } }

    private suspend fun checkBackend() {
        check(WorkspaceChangeAccess.resolvedBackend(context, env) == expectedBackend) { context.getString(R.string.workspace_blame_backend_changed) }
    }

    private suspend fun blob(command: WorkspaceGitCommand, root: String, commit: String, path: String): Pair<String, SnapshotKind> {
        val inventory = command.text("-C", root, "ls-tree", "--full-tree", "-z", commit, "--", path)
        if (inventory.isEmpty()) return "" to SnapshotKind.ABSENT
        val record = inventory.split('\u0000').filter { it.isNotEmpty() }.single()
        val fields = record.substringBefore('\t').split(' ')
        check(record.substringAfter('\t') == path && fields.size == 3 && fields[0] in setOf("100644", "100755") && fields[1] == "blob") {
            context.getString(R.string.workspace_blame_no_text)
        }
        val size = command.text("cat-file", "-s", fields[2]).trim().toLong()
        check(size in 0L..CHANGE_SNAPSHOT_LIMIT.toLong()) { context.getString(R.string.workspace_blame_too_large) }
        val bytes = command.bytes("cat-file", "blob", fields[2])
        check(bytes.size.toLong() == size) { "Incomplete Git blame blob" }
        val text = checkNotNull(decodeChangeText(bytes)) { context.getString(R.string.workspace_blame_no_text) }
        return text to SnapshotKind.TEXT
    }

    private suspend fun <T> commands(directory: String, block: suspend (WorkspaceGitCommand) -> T): T {
        val command = WorkspaceGitCommand(context, directory, env, expectedBackend)
        try { return block(command) }
        finally { withContext(NonCancellable) { command.close() } }
    }
}