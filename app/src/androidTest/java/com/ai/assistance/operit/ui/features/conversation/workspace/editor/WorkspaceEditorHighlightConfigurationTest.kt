package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorHighlightConfigurationTest {
    @Test fun savedConfigurationReloadsWithoutEditingTextAndRecoversAfterAnInvalidRevision() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "highlight-test-${System.nanoTime()}")
        check(directory.mkdirs())
        val file = File(directory, "text.json")
        fun write(color: String) = file.writeText("""{
            "version":1,"mode":"replace","styles":{"标记":{"light":"$color"}},
            "rules":[{"words":["mark"],"style":"标记"}]
        }""", Charsets.UTF_8)
        write("#123456")
        val results = LinkedBlockingQueue<HighlightSnapshot>()
        val failures = LinkedBlockingQueue<Exception>()
        lateinit var highlighter: EditorSyntaxHighlighter
        instrumentation.runOnMainSync {
            highlighter = EditorSyntaxHighlighter(context.assets, "text", configurationDirectory = directory,
                onFailure = { _, _, failure -> failures.offer(failure) }) { results.offer(it) }
            highlighter.requestHighlight(EditorTextSnapshot(EditorLineIndex.fromText("mark"), 3, Any()))
        }
        try {
            val initial = checkNotNull(results.poll(30, TimeUnit.SECONDS))
            assertEquals(0xFF123456.toInt(), initial.styleAt(0).light)
            file.writeText("""{"version":2}""", Charsets.UTF_8)
            assertNotNull(failures.poll(30, TimeUnit.SECONDS))
            assertNull(results.poll(200, TimeUnit.MILLISECONDS))
            write("#654321")
            val updated = checkNotNull(results.poll(30, TimeUnit.SECONDS))
            assertEquals(3, updated.version)
            assertEquals(0xFF654321.toInt(), updated.styleAt(0).light)
            assertEquals(0xFF123456.toInt(), initial.styleAt(0).light)
        } finally {
            instrumentation.runOnMainSync { highlighter.release() }
            directory.deleteRecursively()
        }
    }
}
