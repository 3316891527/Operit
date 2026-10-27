package com.ai.assistance.operit.data.resources.packs

import com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionSpec
import com.ai.assistance.operit.data.resources.models.ModelArchive
import com.ai.assistance.operit.data.resources.models.ModelFile
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourcePackInstallationsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val json = Json { encodeDefaults = true }
    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun spec(version: String, text: String = "original"): ResourcePackSpec = ResourcePackSpec(
        id = "sandbox-test", name = "Test", version = version,
        files = listOf(ModelFile("packages/test.js", text.toByteArray().size.toLong(), digest(text))),
        archive = ModelFile("test.zip", 0, "0".repeat(64)),
        extension = BuiltInExtensionSpec("sandbox", "packages/test.js", listOf("test")),
    )
    private fun directory(pack: ResourcePackSpec) = File(temporary.root, "${pack.id}/${pack.version}")
    private fun install(pack: ResourcePackSpec, text: String = "original"): File = directory(pack).apply {
        mkdirs()
        File(this, "packages").mkdirs()
        File(this, "packages/test.js").writeText(text)
        File(this, "operit-resource.json").writeText(json.encodeToString(pack))
    }
    private fun find(current: ResourcePackSpec, vararg history: ResourcePackSpec, force: Boolean = false) =
        ResourcePackInstallations.find(current, history.toList(), ::directory, force)

    @Test fun metadataOnlyVersionChangeReusesOriginalFilesWithoutCreatingNewPayload() {
        val old = spec("old"); val current = spec("new")
        val root = install(old)
        val marker = File(root, "operit-resource.json").readBytes()
        val result = checkNotNull(find(current, old))
        assertEquals(root, result.directory)
        assertTrue(result.hasCurrentContent(current))
        assertArrayEquals(marker, File(root, "operit-resource.json").readBytes())
        assertFalse(directory(current).exists())
    }

    @Test fun changedPayloadRemainsInstalledAndReportsAnUpdate() {
        val old = spec("old"); val current = spec("new", "updated")
        install(old)
        val result = checkNotNull(find(current, old))
        assertEquals(old.version, result.spec.version)
        assertFalse(result.hasCurrentContent(current))
        assertEquals("original", File(result.directory, "packages/test.js").readText())
    }

    @Test fun verifiedInstallationRecordSurvivesAnotherCatalogUpgrade() {
        val old = spec("first"); install(old)
        assertNotNull(find(spec("second", "new"), old))
        val result = checkNotNull(find(spec("third", "newer")))
        assertEquals(old.version, result.spec.version)
        assertTrue(File(directory(old).parentFile, ".installed/first.json").isFile)
    }

    @Test fun completeCurrentInstallationTakesPrecedence() {
        val old = spec("old"); val current = spec("current", "updated")
        install(old); install(current, "updated")
        assertEquals(current, checkNotNull(find(current, old)).spec)
    }

    @Test fun corruptHistoricalPayloadIsRejectedWithoutDeletion() {
        val old = spec("old"); val root = install(old, "damaged")
        assertNull(find(spec("new"), old, force = true))
        assertTrue(root.isDirectory)
        assertEquals("damaged", File(root, "packages/test.js").readText())
    }

    @Test fun unknownDirectoryCannotDeclareItselfABuiltInInstallation() {
        val unknown = spec("untrusted"); install(unknown)
        assertNull(find(spec("current")))
        assertFalse(File(directory(unknown).parentFile, ".installed").exists())
    }

    @Test fun markerIdentityAndVersionMustMatchPinnedManifest() {
        val old = spec("old"); val root = install(old)
        File(root, "operit-resource.json").writeText(json.encodeToString(old.copy(version = "different")))
        assertNull(find(spec("current"), old))
    }

    @Test fun changedPackageIdentityIsNotGrantedHistoricalBuiltInStatus() {
        val old = spec("old"); install(old)
        val current = spec("current").copy(extension = old.extension!!.copy(packageNames = listOf("another")))
        assertNull(find(current, old))
    }

    @Test fun missingHistoricalFileDoesNotCountAsInstalled() {
        val old = spec("old"); val root = install(old)
        File(root, "packages/test.js").delete()
        assertNull(find(spec("current"), old))
        assertTrue(File(root, "operit-resource.json").isFile)
    }

    @Test fun historicalBackupCanBeVerifiedAfterRoundTrip() {
        val old = spec("old"); val root = install(old)
        val bytes = ByteArrayOutputStream()
        ModelArchive.export(root, bytes, {}, { _, _ -> })
        val restored = temporary.newFolder("restored")
        ModelArchive.unpack(bytes.toByteArray().inputStream(), "backup.zip", restored)
        assertTrue(ResourcePackInstallations.verified(restored, old, force = true))
    }

    @Test fun explicitGroupDeletionRemovesAllVersionsAndRecords() {
        val old = spec("old"); val current = spec("current", "new")
        install(old); find(current, old); install(current, "new"); find(current, old)
        val group = directory(current).parentFile!!
        assertEquals(LocalModelDeleteOutcome.DELETED, LocalModelRuntimeRegistry.deleteIfUnused(group))
        assertFalse(group.exists())
        assertNull(find(current, old))
    }

    @Test fun usageOfHistoricalVersionProtectsTheWholeGroupFromDeletion() {
        val old = spec("old"); val root = install(old)
        val handle = checkNotNull(LocalModelRuntimeRegistry.acquire(root))
        try {
            assertEquals(LocalModelDeleteOutcome.IN_USE, LocalModelRuntimeRegistry.deleteIfUnused(root.parentFile!!))
            assertTrue(root.isDirectory)
        } finally { handle.close() }
    }

    @Test fun forceVerificationRejectsChangedBytesEvenWithRestoredTimestamp() {
        val old = spec("old"); val root = install(old); assertNotNull(find(old))
        val file = File(root, "packages/test.js"); val modified = file.lastModified()
        file.writeText("tampered"); file.setLastModified(modified)
        assertNull(find(old, force = true))
    }

    @Test fun recordedManifestCannotOverrideCurrentPinnedChecksums() {
        val current = spec("current"); install(current, "tampered")
        val group = directory(current).parentFile!!
        File(group, ".installed").mkdirs()
        File(group, ".installed/current.json").writeText(json.encodeToString(spec("current", "tampered")))
        assertNull(find(current, force = true))
    }

    @Test fun unsafeRecordedVersionCannotEscapeTheResourceGroup() {
        val current = spec("current"); val group = directory(current).parentFile!!
        File(group, ".installed").mkdirs()
        File(group, ".installed/unsafe.json").writeText(json.encodeToString(current.copy(version = "../outside")))
        assertNull(find(current))
        assertFalse(File(temporary.root, "outside").exists())
    }

    @Test fun incompleteRecordWriteIsNotAnInstallation() {
        val current = spec("current"); val group = directory(current).parentFile!!
        File(group, ".installed").mkdirs()
        File(group, ".installed/current.json.pending.tmp").writeText("unfinished")
        assertNull(find(current))
    }
}