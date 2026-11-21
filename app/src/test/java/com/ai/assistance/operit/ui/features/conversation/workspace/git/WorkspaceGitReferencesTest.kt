package com.ai.assistance.operit.ui.features.conversation.workspace.git

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceGitReferencesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun ref(name: String, time: Long, upstream: String? = null) = GitReference(name, "commit", upstream, time)

    @Test fun `deleted remote heads disappear while existing local branches remain`() {
        val refs = listOf(ref("refs/heads/local", 10, "refs/remotes/origin/deleted"),
            ref("refs/remotes/origin/deleted", 30), ref("refs/remotes/origin/live", 20))
        assertEquals(listOf("refs/remotes/origin/live", "refs/heads/local"),
            visibleReviewReferences(refs, mapOf("origin" to setOf("live"))).map { it.name })
    }

    @Test fun `references sort by latest commit then name with no remote required`() {
        val refs = listOf(ref("refs/heads/z", 10), ref("refs/heads/b", 20), ref("refs/heads/a", 20))
        assertEquals(listOf("refs/heads/a", "refs/heads/b", "refs/heads/z"), visibleReviewReferences(refs, emptyMap()).map { it.name })
    }

    @Test fun `remote names with slashes and branch names with slashes are distinct`() {
        val refs = listOf(ref("refs/remotes/team/fork/feature/a", 30), ref("refs/remotes/team/old", 20))
        assertEquals(listOf(refs.first()), visibleReviewReferences(refs, mapOf("team" to emptySet(), "team/fork" to setOf("feature/a"))))
    }

    @Test fun `reference inventory omits symbolic HEAD and preserves current upstream`() {
        val data = "refs/heads/main\u0000abc\u0000*\u0000refs/remotes/origin/main\u0000123\u0000\n" +
            "refs/remotes/origin/HEAD\u0000abc\u0000 \u0000\u0000123\u0000refs/remotes/origin/main\n"
        val parsed = parseReviewReferences(data)
        assertEquals(1, parsed.references.size)
        assertEquals("refs/heads/main", parsed.currentBranch)
        assertEquals("refs/remotes/origin/main", parsed.upstream)
        assertEquals(123L, parsed.references.single().updatedAt)
        assertThrows(IllegalArgumentException::class.java) { parseReviewReferences("malformed") }
        assertThrows(IllegalArgumentException::class.java) { parseRemoteReviewHeads("invalid") }
    }

    private fun git(directory: File, vararg args: String): String {
        val process = ProcessBuilder(listOf("git", "-C", directory.path) + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
        return output
    }

    @Test fun `real remote deletion filters stale tracking refs without deleting a local branch`() {
        val remote = temporary.newFolder("remote.git")
        val local = temporary.newFolder("local")
        git(local, "init", "-b", "main")
        git(local, "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "--allow-empty", "-m", "first")
        git(local, "clone", "--bare", "--no-hardlinks", local.path, remote.path)
        git(remote, "update-ref", "refs/heads/removed", "refs/heads/main")
        git(local, "remote", "add", "origin", remote.path)
        git(local, "update-ref", "refs/remotes/origin/main", "HEAD")
        git(local, "update-ref", "refs/remotes/origin/removed", "HEAD")
        git(local, "branch", "--track", "local-copy", "origin/removed")
        git(remote, "update-ref", "-d", "refs/heads/removed")
        val inventory = parseReviewReferences(git(local, "for-each-ref", "--format=%(refname)%00%(objectname)%00%(HEAD)%00%(upstream)%00%(committerdate:unix)%00%(symref)", "refs/heads", "refs/remotes"))
        assertTrue(inventory.references.any { it.name == "refs/remotes/origin/removed" })
        val visible = visibleReviewReferences(inventory.references, mapOf("origin" to parseRemoteReviewHeads(git(local, "ls-remote", "--heads", "--", "origin"))))
        assertFalse(visible.any { it.name == "refs/remotes/origin/removed" })
        assertTrue(visible.any { it.name == "refs/heads/local-copy" })
        git(local, "branch", "-D", "local-copy")
        assertFalse(parseReviewReferences(git(local, "for-each-ref", "--format=%(refname)%00%(objectname)%00%(HEAD)%00%(upstream)%00%(committerdate:unix)%00%(symref)", "refs/heads")).references.any { it.name == "refs/heads/local-copy" })
    }
}
