package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceHiddenPathsFilesTest {
    @Test fun oldRulesMoveToSeparateEnvironmentFilesAndClearingAFileDoesNotReimportThem() = runBlocking<Unit> {
        withDirectory { directory ->
            val legacy = setOf(WorkspaceLocation("/same"), WorkspaceLocation("/same", "linux"), WorkspaceLocation("/docs", "repo:shared/docs"))
            val files = WorkspaceHiddenPathsFiles(directory)
            assertEquals(legacy, files.read(legacy))
            val android = files.editableFile("android", legacy)
            val linux = files.editableFile("linux", legacy)
            val repository = files.editableFile("repo:shared/docs", legacy)
            assertNotEquals(android.path, linux.path)
            assertEquals(directory.canonicalFile, repository.parentFile!!.canonicalFile)
            assertEquals("/same\n", android.readText())
            assertEquals("/docs\n", repository.readText())
            android.writeText("")
            assertEquals(legacy.filterNot { it.environment == "android" }.toSet(), files.read(legacy))
        }
    }

    @Test fun editingAFileAndChangingOtherPreferencesKeepsTheEditedRules() = runBlocking<Unit> {
        withDirectory { directory ->
            val legacy = setOf(WorkspaceLocation("/old"))
            val files = WorkspaceHiddenPathsFiles(directory)
            val file = files.editableFile("android", legacy)
            file.writeText("/new path\n")
            val original = WorkspaceBrowserSettings(home = WorkspaceLocation("/home"), hidden = legacy, wordWrap = false)
            val changed = files.update(original) { it.copy(showDotFiles = true) }
            assertEquals(original.copy(hidden = setOf(WorkspaceLocation("/new path")), showDotFiles = true), changed)
            val extended = files.update(changed) { it.copy(hidden = it.hidden + WorkspaceLocation("/other")) }
            assertEquals("/new path\n/other\n", file.readText())
            assertEquals(extended.hidden, files.read(legacy))
        }
    }

    @Test fun savingThePlainTextFileNotifiesCollectorsAndAppliesTheNewRules() = runBlocking<Unit> {
        withDirectory { directory ->
            val files = WorkspaceHiddenPathsFiles(directory)
            val file = files.editableFile("android", emptySet())
            coroutineScope {
                val watching = CompletableDeferred<Unit>()
                val event = async {
                    files.changes.onEach { watching.complete(Unit) }.drop(1).first()
                }
                withTimeout(10_000) {
                    watching.await()
                    withContext(Dispatchers.IO) { file.writeText("/saved\n") }
                    event.await()
                }
            }
            assertEquals(setOf(WorkspaceLocation("/saved")), files.read(emptySet()))
        }
    }

    private suspend fun withDirectory(block: suspend (File) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "workspace-hidden-test-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        try { block(root) } finally { root.deleteRecursively() }
    }
}
