package com.ai.assistance.operit.ui.features.conversation.workspace.git

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class GitReview(val files: List<ReviewFile>, val references: List<GitReference>, val currentBranch: String?, val upstream: String?, val missingBase: String? = null)

/** Git 查询只读，目录筛选始终以当前绑定范围为准。 */
internal class WorkspaceGitRepository(private val context: Context, private val directory: String, environment: String?) {
    private val env = changeEnvironment(environment)
    private var backend: String? = null

    suspend fun load(branchComparison: Boolean, baseReference: String? = null): GitReview = withContext(Dispatchers.IO) {
        withCommands { command ->
            val root = command.text("rev-parse", "--show-toplevel").removeSuffix("\n")
            val prefix = command.text("rev-parse", "--show-prefix").removeSuffix("\n")
            val inventory = parseReviewReferences(command.text("for-each-ref", "--format=%(refname)%00%(objectname)%00%(HEAD)%00%(upstream)%00%(committerdate:unix)%00%(symref)", "refs/heads", "refs/remotes"))
            val currentBranch = inventory.currentBranch
            val upstream = inventory.upstream
            val cachedRefs = inventory.references
            val remoteHeads = if (branchComparison) {
                command.text("remote").lineSequence().filter { it.isNotEmpty() }.associateWith { remote ->
                    parseRemoteReviewHeads(command.text("ls-remote", "--heads", "--", remote))
                }
            } else emptyMap()
            val refs = if (branchComparison) visibleReviewReferences(cachedRefs, remoteHeads)
                else cachedRefs.sortedWith(compareByDescending<GitReference> { it.updatedAt }.thenBy { it.name })
            val headResult = command.run(listOf("rev-parse", "--verify", "-q", "HEAD"))
            require(headResult.exitCode == 0 || headResult.exitCode == 1) { headResult.error }
            val unborn = headResult.exitCode == 1
            val head = if (unborn) null else headResult.bytes.toString(Charsets.UTF_8).trimEnd('\n')
            if (branchComparison && baseReference == null) return@withCommands GitReview(emptyList(), refs, currentBranch, upstream)
            if (branchComparison && refs.none { it.name == baseReference }) {
                return@withCommands GitReview(emptyList(), refs, currentBranch, upstream, missingBase = baseReference)
            }
            val base = if (branchComparison) {
                check(!unborn) { context.getString(R.string.workspace_changes_unborn) }
                val requested = checkNotNull(baseReference)
                require(refs.any { it.name == requested }) { "Unknown comparison reference: $requested" }
                val commit = command.text("rev-parse", "--verify", "--end-of-options", "$requested^{commit}").trimEnd('\n')
                command.text("merge-base", commit, checkNotNull(head)).trimEnd('\n')
            } else head
            val entries = if (branchComparison) {
                WorkspaceGitParser.nameStatus(command.text("diff", "--name-status", "-z", "--find-renames", checkNotNull(base), checkNotNull(head), "--", "."))
            } else {
                WorkspaceGitParser.status(command.text("status", "--porcelain=v1", "-z", "--untracked-files=all", "--", "."))
            }
            val stats = if (unborn) emptyMap() else {
                val args = listOf("diff", "--numstat", "-z", "--no-ext-diff", "--no-textconv", "--find-renames", checkNotNull(base)) +
                    (if (branchComparison) listOf(checkNotNull(head)) else emptyList()) + listOf("--", ".")
                WorkspaceGitParser.numstat(command.text(*args.toTypedArray()))
            }
            var cachedCharacters = 0
            val files = WorkspaceGitParser.restrict(entries, prefix).distinctBy { it.path }.map { entry ->
                val counts = stats[entry.path]
                val location = ChangeLocation(changePath(directory + "/" + entry.path.removePrefix(prefix)), env, checkNotNull(backend))
                val uncatalogued = if (entry.untracked || unborn) command.workingFile(location.path).let { (text, kind) ->
                    ReviewContent("", text, SnapshotKind.ABSENT, kind)
                } else null
                val newCounts = uncatalogued?.takeIf { reviewDiffAutomatic(it) }?.let {
                    WorkspaceDiff.compare("", checkNotNull(it.after))
                }
                val retained = uncatalogued?.takeIf { cachedCharacters + (it.after?.length ?: 0) <= 1024 * 1024 }?.also {
                    cachedCharacters += it.after?.length ?: 0
                }
                ReviewFile(
                    id = location.key, location = location, displayPath = entry.path.removePrefix(prefix),
                    status = entry.status, additions = newCounts?.additions ?: counts?.additions ?: 0,
                    deletions = newCounts?.deletions ?: counts?.deletions ?: 0,
                    binary = counts?.binary == true || uncatalogued?.afterKind == SnapshotKind.BINARY,
                    statisticsAvailable = newCounts != null || counts != null, initialContent = retained,
                    gitRoot = root, gitPath = entry.path, originalPath = entry.originalPath,
                    baseCommit = base, headCommit = if (branchComparison) head else null, untracked = entry.untracked, unborn = unborn,
                )
            }.sortedBy { it.displayPath }
            GitReview(files, refs, currentBranch, upstream)
        }
    }

    suspend fun content(file: ReviewFile): ReviewContent = withContext(Dispatchers.IO) {
        require(changePathContains(directory, file.location.path)) { "File outside workspace" }
        withCommands { command ->
            require(file.location.backend == backend) { "File backend changed" }
            suspend fun blob(commit: String, path: String): Pair<String?, SnapshotKind> {
                val prefix = command.text("rev-parse", "--show-prefix").removeSuffix("\n")
                require(prefix.isEmpty() || path.startsWith(prefix)) { "Git path outside workspace" }
                val entry = command.text("ls-tree", "--full-tree", "-z", commit, "--", path.removePrefix(prefix))
                if (entry.isEmpty()) return "" to SnapshotKind.ABSENT
                val spec = "$commit:$path"
                val size = command.text("cat-file", "-s", spec).trim().toLong()
                if (size > CHANGE_SNAPSHOT_LIMIT) return null to SnapshotKind.TOO_LARGE
                val type = command.text("cat-file", "-t", spec).trim()
                if (type != "blob") return null to SnapshotKind.BINARY
                val bytes = command.bytes("cat-file", "blob", spec)
                val text = decodeChangeText(bytes)
                return text to if (text == null) SnapshotKind.BINARY else SnapshotKind.TEXT
            }
            val before = if (file.untracked || file.unborn || file.status == "A") "" to SnapshotKind.ABSENT
                else blob(checkNotNull(file.baseCommit), file.originalPath ?: checkNotNull(file.gitPath))
            val after = if (file.headCommit != null) {
                if (file.status == "D") "" to SnapshotKind.ABSENT else blob(file.headCommit, checkNotNull(file.gitPath))
            } else {
                command.workingFile(file.location.path)
            }
            ReviewContent(before.first, after.first, before.second, after.second)
        }
    }

    private suspend fun <T> withCommands(block: suspend (WorkspaceGitCommand) -> T): T {
        val resolved = WorkspaceChangeAccess.resolvedBackend(context, env)
        check(backend == null || backend == resolved) { "Workspace backend changed" }
        backend = resolved
        val command = WorkspaceGitCommand(context, directory, env, resolved)
        try { return block(command) }
        finally { withContext(NonCancellable) { command.close() } }
    }
}
