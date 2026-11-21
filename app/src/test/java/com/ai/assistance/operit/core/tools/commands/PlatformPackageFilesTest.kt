package com.ai.assistance.operit.core.tools.commands

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class PlatformPackageFilesTest {
    private fun inDirectory(action: (File) -> Unit) {
        val root = Files.createTempDirectory("platform-package-test-").toFile()
        try { action(root) } finally { root.deleteRecursively() }
    }

    @Test fun `source directory produces a usable archive and omits development cache files`() = inDirectory { root ->
        val source = File(root, "source").apply { mkdirs() }
        File(source, "manifest.hjson").writeText("{toolpkg_id: sample, main: main.js}")
        File(source, "main.js").writeText("// 中文 UTF-8\nexports.main = () => '你好';")
        File(source, "assets").mkdirs()
        File(source, "assets/data.txt").writeText("one\ntwo")
        File(source, ".git").mkdirs()
        File(source, ".git/config").writeText("private development data")
        val archive = PlatformPackageFiles.archiveDirectory(source, File(root, "cache"))
        ZipFile(archive).use { zip ->
            assertEquals(setOf("manifest.hjson", "main.js", "assets/data.txt"), zip.entries().asSequence().map { it.name }.toSet())
            assertEquals("one\ntwo", zip.getInputStream(zip.getEntry("assets/data.txt")).reader().readText())
        }
    }

    @Test fun `invalid folders and recursive archive output are rejected`() = inDirectory { root ->
        assertThrows(IllegalArgumentException::class.java) { PlatformPackageFiles.archiveDirectory(root, File(root.parentFile, "missing-cache")) }
        File(root, "manifest.json").writeText("{}")
        assertThrows(IllegalArgumentException::class.java) { PlatformPackageFiles.archiveDirectory(root, File(root, "cache")) }
    }

    @Test fun `symbolic links are rejected without leaving partial archives`() = inDirectory { root ->
        val source = File(root, "source").apply { mkdirs() }
        File(source, "manifest.json").writeText("{}")
        val outside = File(root, "outside.txt").apply { writeText("outside") }
        Files.createSymbolicLink(File(source, "link.txt").toPath(), outside.toPath())
        val cache = File(root, "cache")
        assertThrows(IllegalArgumentException::class.java) { PlatformPackageFiles.archiveDirectory(source, cache) }
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun `replacement publishes complete content and preserves source`() = inDirectory { root ->
        val source = File(root, "source.js").apply { writeText("中文\nnew content") }
        val target = File(root, "packages/target.js")
        PlatformPackageFiles.replace(source, target)
        assertEquals(source.readText(), target.readText())
        source.writeText("replacement")
        PlatformPackageFiles.replace(source, target)
        assertEquals("replacement", target.readText())
        PlatformPackageFiles.replace(target, target)
        assertEquals("replacement", target.readText())
        assertEquals(listOf("target.js"), target.parentFile!!.list()!!.toList())
    }

    @Test fun `package filenames stay inside their directory and avoid normalized name collisions`() = inDirectory { root ->
        val left = PlatformPackageFiles.destination(root, "../中文/包", "js")
        val right = PlatformPackageFiles.destination(root, "../另一/包", "js")
        assertEquals(root.canonicalFile, left.canonicalFile.parentFile)
        assertNotEquals(left, right)
    }
}
