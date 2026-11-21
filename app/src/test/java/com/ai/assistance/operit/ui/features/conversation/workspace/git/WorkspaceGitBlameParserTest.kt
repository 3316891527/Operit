package com.ai.assistance.operit.ui.features.conversation.workspace.git

import com.ai.assistance.operit.ui.features.conversation.workspace.changes.ReviewContent
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.SnapshotKind
import com.ai.assistance.operit.ui.features.conversation.workspace.changes.reviewDiffSupported
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceGitBlameParserTest {
    @get:Rule val temporary = TemporaryFolder()
    private val commit = "a".repeat(40)
    private val parent = "b".repeat(40)

    @Test fun `real blame follows nested unicode rename with control characters`() {
        val repository = repository("renames")
        val original = "src/旧\t文件\n.kt"
        val renamed = "src/\"新文件.kt"
        val before = (1..12).joinToString("\n", postfix = "\n") { "line $it" }
        File(repository, original).apply { parentFile!!.mkdirs(); writeText(before) }
        git(repository, "add", "--", original)
        git(repository, "commit", "-qm", "initial")
        val root = git(repository, "rev-parse", "HEAD").trim()
        git(repository, "mv", "--", original, renamed)
        File(repository, renamed).writeText(before.replace("line 1\n", "changed\n"))
        git(repository, "add", "--", renamed)
        git(repository, "commit", "-qm", "rename and edit")
        File(repository, renamed).appendText("unsaved on disk\n")
        val status = git(repository, "status", "--porcelain=v1", "-z")
        val index = File(repository, ".git/index").readBytes()
        val changed = WorkspaceGitBlameParser.parse(blame(File(repository, "src"), repository, renamed, 1))
        assertEquals("rename and edit", changed.summary)
        assertEquals(renamed, changed.path)
        assertEquals(original, changed.previousPath)
        assertEquals(root, changed.previousCommit)
        assertEquals(1, changed.originalLine)
        val originalLine = WorkspaceGitBlameParser.parse(blame(repository, repository, renamed, 2))
        assertEquals(root, originalLine.commit)
        assertEquals(original, originalLine.path)
        assertTrue(originalLine.boundary)
        assertEquals("line 2", originalLine.text)
        assertEquals(status, git(repository, "status", "--porcelain=v1", "-z"))
        assertArrayEquals(index, File(repository, ".git/index").readBytes())
        assertEquals(before.replace("line 1\n", "changed\n") + "unsaved on disk\n", File(repository, renamed).readText())
    }

    @Test fun `shallow boundary preserves missing parent in raw commit history`() {
        val source = repository("source")
        File(source, "file.kt").writeText("first\n")
        git(source, "add", "file.kt")
        git(source, "commit", "-qm", "root")
        File(source, "file.kt").writeText("second\n")
        git(source, "add", "file.kt")
        git(source, "commit", "-qm", "next")
        val clone = File(temporary.root, "shallow")
        git(temporary.root, "clone", "--quiet", "--depth=1", "file://${source.absolutePath}", clone.absolutePath)
        val line = WorkspaceGitBlameParser.parse(blame(clone, clone, "file.kt", 1))
        assertTrue(line.boundary)
        assertEquals(listOf(line.commit), git(clone, "rev-list", "--parents", "--max-count=1", line.commit).trim().split(' '))
        val history = blameCommitHistory(git(clone, "cat-file", "commit", line.commit))
        assertEquals(1, history.parents.size)
        assertEquals(history.parents.single(), history.parent(line))
        assertNotEquals(line.commit, history.parent(line))
    }

    @Test fun `inserted and modified unsaved lines have no historical owner`() {
        assertEquals(mapOf(2 to 1, 3 to 2, 5 to 4, 6 to 5),
            blameLineMapping("a\nsame\nmiddle\nsame\nz\n", "new\na\nsame\nchanged\nsame\nz\n"))
    }

    @Test fun `line mapping normalizes CRLF and excludes virtual final line`() {
        assertEquals(mapOf(2 to 1, 3 to 2, 4 to 3), blameLineMapping("a\r\n\r\nb\r\n", "first\na\n\nb\n"))
        assertEquals(mapOf(1 to 1), blameLineMapping("a\n", "a"))
        assertEquals(mapOf(1 to 1), blameLineMapping("\n", "\n"))
        assertTrue(blameLineMapping("", "").isEmpty())
        assertTrue(blameLineMapping("", "new\n").isEmpty())
        assertEquals(mapOf(1 to 2), blameLineMapping("first\nlast", "last"))
    }

    @Test fun `mapping refuses oversized lines before diff preparation`() {
        assertThrows(IllegalArgumentException::class.java) { blameLineMapping("x".repeat(8193), "small") }
        assertThrows(IllegalArgumentException::class.java) { blameLineMapping("\n".repeat(20001), "") }
    }

    @Test fun `blame line bounds remain independent of review loading bounds`() {
        val boundary = "x".repeat(8192)
        assertEquals(mapOf(1 to 1), blameLineMapping(boundary, boundary))
        val longer = boundary + "x"
        for ((before, after) in listOf(longer to "small", "small" to longer)) {
            val content = ReviewContent(before, after, SnapshotKind.TEXT, SnapshotKind.TEXT)
            assertTrue(reviewDiffSupported(content))
            assertFalse(blameDiffSupported(content))
            assertThrows(IllegalArgumentException::class.java) { blameLineMapping(before, after) }
        }
    }
    @Test fun `blame newline budget includes both snapshots at the exact boundary`() {
        val before = "\n".repeat(10_000)
        val after = "\n".repeat(10_000)
        assertTrue(blameDiffSupported(ReviewContent(before, after, SnapshotKind.TEXT, SnapshotKind.TEXT)))
        assertFalse(blameDiffSupported(ReviewContent(before, after + "\n", SnapshotKind.TEXT, SnapshotKind.TEXT)))
        assertFalse(blameDiffSupported(ReviewContent(before + "\n", after, SnapshotKind.TEXT, SnapshotKind.TEXT)))
    }
    @Test fun `blame accepts absent text sides and rejects binary snapshots`() {
        val added = ReviewContent("", "short", SnapshotKind.ABSENT, SnapshotKind.TEXT)
        val deleted = ReviewContent("short", "", SnapshotKind.TEXT, SnapshotKind.ABSENT)
        assertTrue(blameDiffSupported(added))
        assertTrue(blameDiffSupported(deleted))
        assertFalse(blameDiffSupported(added.copy(after = null, afterKind = SnapshotKind.BINARY)))
    }
    @Test fun `porcelain accepts SHA256 and zero hash without fabricating an author`() {
        val sha256 = "f".repeat(64)
        val parsed = WorkspaceGitBlameParser.parse(record(hash = sha256, extra = "boundary\nprevious $parent old.kt\n"))
        assertEquals(sha256, parsed.commit)
        assertEquals("测试作者", parsed.author)
        assertEquals("test@example.invalid", parsed.email)
        assertEquals("+0800", parsed.timezone)
        assertEquals("old.kt", parsed.previousPath)
        assertTrue(parsed.boundary)
        assertFalse(parsed.uncommitted)
        assertTrue(WorkspaceGitBlameParser.parse(record(hash = "0".repeat(40))).uncommitted)
    }

    @Test fun `quoted path decodes octal UTF8 and raw leading quote remains literal`() {
        assertEquals("中\t文\n.kt", WorkspaceGitBlameParser.path("\"\\344\\270\\255\\t文\\n.kt\""))
        assertEquals("\"literal.kt", WorkspaceGitBlameParser.validatePath("\"literal.kt"))
        assertEquals("tab\tline\n.kt", WorkspaceGitBlameParser.validatePath("tab\tline\n.kt"))
        for (path in listOf("/outside", "../outside", "a/../b", "a//b", "a/./b", "a\u0000b", "\"\\400\"", "\"\\377\"", "\"\\q\"", "\"unterminated")) {
            assertThrows("Accepted invalid path: $path", Exception::class.java) { WorkspaceGitBlameParser.path(path) }
        }
    }

    @Test fun `malformed or multiple porcelain records are rejected`() {
        val invalid = listOf(
            record().replace("author 测试作者\n", ""),
            record(extra = "author other\n"),
            record().replace("+0800", "+2460"),
            record().replace("author-time 1700000000", "author-time 9223372036854775807"),
            record().replace(" 3 7 1\n", " 3 7 2\n"),
            record() + record(),
            record().substringBefore("\tcode"),
            record(extra = "previous invalid ../outside\n"),
        )
        invalid.forEach { assertThrows(Exception::class.java) { WorkspaceGitBlameParser.parse(it) } }
    }

    @Test fun `history distinguishes real root and selects declared merge parent`() {
        val root = blameCommitHistory("tree $commit\nauthor Test <test@example.invalid> 1 +0000\n\nroot message\n")
        assertTrue(root.parents.isEmpty())
        assertNull(root.parent(WorkspaceGitBlameParser.parse(record())))
        assertEquals("root message", root.message)
        val second = "c".repeat(40)
        val history = blameCommitHistory("tree $commit\nparent $parent\nparent $second\n\nsubject\n\nbody\n")
        val line = WorkspaceGitBlameParser.parse(record(extra = "previous $second old.kt\n"))
        assertEquals(second, history.parent(line))
        assertEquals(parent, history.parent(line.copy(previousCommit = null, previousPath = null)))
        assertEquals("subject\n\nbody", history.message)
        assertThrows(IllegalArgumentException::class.java) { history.parent(line.copy(previousCommit = commit)) }
    }

    private fun record(hash: String = commit, extra: String = ""): String =
        "$hash 3 7 1\nauthor 测试作者\nauthor-mail <test@example.invalid>\nauthor-time 1700000000\nauthor-tz +0800\nsummary edit\n${extra}filename src/file.kt\n\tcode\n"

    private fun repository(name: String): File = temporary.newFolder(name).also { git(it, "init", "-q") }

    private fun blame(directory: File, root: File, path: String, line: Int): String = git(directory,
        "-C", root.absolutePath, "blame", "--line-porcelain", "--encoding=UTF-8", "--no-textconv", "--ignore-revs-file", "",
        "-L", "$line,$line", "HEAD", "--", path)

    private fun git(directory: File, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "--no-optional-locks", "--literal-pathspecs", "-c", "core.quotepath=false",
            "-c", "user.name=测试作者", "-c", "user.email=test@example.invalid", "-c", "commit.gpgsign=false", "-C", directory.absolutePath) + arguments).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val error = process.errorStream.readBytes().toString(Charsets.UTF_8)
        assertEquals(error, 0, process.waitFor())
        return output
    }
}