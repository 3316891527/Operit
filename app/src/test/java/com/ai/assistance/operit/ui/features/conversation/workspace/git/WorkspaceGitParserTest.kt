package com.ai.assistance.operit.ui.features.conversation.workspace.git

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceGitParserTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `real porcelain preserves rename paths containing tabs and newlines`() {
        val repository = temporary.newFolder("repository")
        git(repository, "init", "-q")
        val original = "old\tname\n.as"
        val renamed = "new\tname\n.as"
        File(repository, original).writeText("trace('hello');\n")
        git(repository, "add", "--", original)
        git(repository, "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-qm", "initial")
        git(repository, "mv", "--", original, renamed)
        val parsed = WorkspaceGitParser.status(git(repository, "status", "--porcelain=v1", "-z"))
        assertEquals(listOf(GitStatusEntry(renamed, "R", original)), parsed)
        val stats = WorkspaceGitParser.numstat(git(repository, "diff", "HEAD", "--numstat", "-z", "--find-renames"))
        assertEquals(GitLineStats(0, 0, false), stats[renamed])
    }

    @Test fun `unborn repository reports untracked files as additions`() {
        val repository = temporary.newFolder("unborn")
        git(repository, "init", "-q")
        File(repository, "hello.as").writeText("trace(1);\n")
        assertEquals(listOf(GitStatusEntry("hello.as", "A", untracked = true)),
            WorkspaceGitParser.status(git(repository, "status", "--porcelain=v1", "-z", "--untracked-files=all")))
    }

    @Test fun `scope restricts both sides of a cross-boundary rename`() {
        val entries = listOf(
            GitStatusEntry("app/new.as", "R", "outside/old.as"),
            GitStatusEntry("outside/new.as", "R", "app/old.as"),
            GitStatusEntry("app/internal-new.as", "R", "app/internal-old.as"),
            GitStatusEntry("application/other.as", "M"),
        )
        assertEquals(listOf(
            GitStatusEntry("app/new.as", "A"), GitStatusEntry("app/old.as", "D"),
            GitStatusEntry("app/internal-new.as", "R", "app/internal-old.as"),
        ), WorkspaceGitParser.restrict(entries, "app/"))
    }

    @Test fun `binary stats and names do not split on filename tabs`() {
        assertEquals(mapOf("a\tb\n.bin" to GitLineStats(0, 0, true)), WorkspaceGitParser.numstat("-\t-\ta\tb\n.bin\u0000"))
        assertEquals(listOf(GitStatusEntry("next\t.as", "R", "old\n.as")),
            WorkspaceGitParser.nameStatus("R100\u0000old\n.as\u0000next\t.as\u0000"))
    }

    @Test fun `incomplete rename is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { WorkspaceGitParser.status("R  new.as\u0000") }
    }

    private fun git(directory: File, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "-C", directory.absolutePath) + arguments).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val error = process.errorStream.readBytes().toString(Charsets.UTF_8)
        assertEquals(error, 0, process.waitFor())
        return output
    }
}
