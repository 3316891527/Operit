package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import org.junit.Assert.*
import org.junit.Test

class WorkspaceRecordingScopeTest {
    private val storage = "/storage/emulated/0"
    private fun location(path: String, backend: String = "android") = ChangeLocation(path, if (backend == "android") "android" else "linux", backend)

    @Test fun missingChatOrWorkspaceDisablesRecording() {
        assertNull(WorkspaceRecordingScope.create(null, "/project", null))
        assertNull(WorkspaceRecordingScope.create("chat", null, "linux"))
        assertNull(WorkspaceRecordingScope.create("chat", "  ", null))
    }

    @Test fun unrelatedAndPrefixSimilarDirectoriesAreOutsideTheBinding() {
        val root = location("/sdcard/project")
        assertTrue(boundMutationLocations(root, listOf(location("/sdcard/project-old/file"), location("/tmp/file")), storage).isEmpty())
        assertEquals(listOf(location("/sdcard/project/file")), boundMutationLocations(root, listOf(location("/sdcard/project/file")), storage))
    }

    @Test fun ancestorDeletionIsClippedToTheBoundDirectory() {
        val root = location("/sdcard/project")
        assertEquals(listOf(root), boundMutationLocations(root, listOf(location("/sdcard")), storage))
    }

    @Test fun sharedStorageAliasesMatchButDifferentBackendsDoNot() {
        val root = location("/sdcard/project")
        val target = location("/storage/emulated/0/project/file", "linux:local")
        assertEquals(listOf(target), boundMutationLocations(root, listOf(target), storage))
        assertTrue(boundMutationLocations(root, listOf(location("/sdcard/project/file", "linux:ssh:remote")), storage).isEmpty())
    }

    @Test fun changingChatOrDirectoryDoesNotMatchTheOldRecordingScope() {
        val scope = checkNotNull(WorkspaceRecordingScope.create("chat", "/project", "linux"))
        assertFalse(scope.matches("other", location("/project", "linux:local")))
        assertFalse(scope.matches("chat", location("/project-other", "linux:local")))
        assertTrue(scope.matches("chat", location("/project", "linux:local")))
    }
}
