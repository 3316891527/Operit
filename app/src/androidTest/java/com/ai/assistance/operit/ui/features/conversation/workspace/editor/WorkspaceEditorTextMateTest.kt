package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorTextMateTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val assets get() = instrumentation.targetContext.assets

    @Test fun rapidRequestsOnlyPublishTheLatestVersionOnTheMainThread() {
        val published = Collections.synchronizedList(mutableListOf<HighlightSnapshot>())
        val failures = Collections.synchronizedList(mutableListOf<Exception>())
        val completed = CountDownLatch(1)
        val owner = Any()
        lateinit var highlighter: EditorSyntaxHighlighter
        val latestText = "val text = \"中文👋\"; val n = 42"
        instrumentation.runOnMainSync {
            highlighter = EditorSyntaxHighlighter(assets, "kotlin", onFailure = { _, _, failure -> failures += failure; completed.countDown() }) {
                assertSame(Looper.getMainLooper(), Looper.myLooper())
                published += it
                completed.countDown()
            }
            for (version in 1..20) {
                val text = if (version == 20) latestText else "val old$version = $version"
                highlighter.requestHighlight(EditorTextSnapshot(EditorLineIndex.fromText(text), version, owner))
            }
        }
        try {
            assertTrue(completed.await(30, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertTrue(failures.toString(), failures.isEmpty())
            assertEquals(1, published.size)
            val result = published.single()
            assertEquals(20, result.version)
            assertSame(owner, result.text.owner)
            assertEquals(EditorTokenKind.STRING, result.kindAt(latestText.indexOf("中文")))
            assertEquals(EditorTokenKind.NUMBER, result.kindAt(latestText.lastIndexOf("42")))
        } finally {
            instrumentation.runOnMainSync { highlighter.release() }
        }
    }

    @Test fun changingLanguageInvalidatesPendingColorsAndUsesTheNewGrammar() {
        val published = Collections.synchronizedList(mutableListOf<HighlightSnapshot>())
        val failures = Collections.synchronizedList(mutableListOf<Exception>())
        val completed = CountDownLatch(1)
        val firstOwner = Any()
        val secondOwner = Any()
        lateinit var highlighter: EditorSyntaxHighlighter
        val text = "def greet():\n    return \"中文👋\""
        instrumentation.runOnMainSync {
            highlighter = EditorSyntaxHighlighter(assets, "kotlin", onFailure = { _, _, failure -> failures += failure; completed.countDown() }) {
                published += it
                completed.countDown()
            }
            highlighter.requestHighlight(EditorTextSnapshot(EditorLineIndex.fromText("val old = 1"), 0, firstOwner))
            highlighter.setLanguage("python")
            highlighter.requestHighlight(EditorTextSnapshot(EditorLineIndex.fromText(text), 0, secondOwner))
        }
        try {
            assertTrue(completed.await(30, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertTrue(failures.toString(), failures.isEmpty())
            assertEquals(1, published.size)
            assertSame(secondOwner, published.single().text.owner)
            assertEquals(EditorTokenKind.KEYWORD, published.single().kindAt(0))
            assertEquals(EditorTokenKind.STRING, published.single().kindAt(text.indexOf("中文")))
        } finally {
            instrumentation.runOnMainSync { highlighter.release() }
        }
    }

    @Test fun releasingAnEditorRemovesQueuedPublications() {
        var callbacks = 0
        instrumentation.runOnMainSync {
            val highlighter = EditorSyntaxHighlighter(assets, "javascript", onFailure = { _, _, _ -> callbacks++ }) { callbacks++ }
            highlighter.requestHighlight(EditorTextSnapshot(EditorLineIndex.fromText("const value = 42;"), 0, Any()))
            highlighter.release()
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { assertEquals(0, callbacks) }
    }

    @Test fun anUnknownExplicitLanguageReportsAnErrorInsteadOfPublishingColors() {
        val completed = CountDownLatch(1)
        var published = 0
        var failure: Exception? = null
        lateinit var highlighter: EditorSyntaxHighlighter
        instrumentation.runOnMainSync {
            highlighter = EditorSyntaxHighlighter(assets, "missing-language", onFailure = { _, _, error ->
                assertSame(Looper.getMainLooper(), Looper.myLooper())
                failure = error
                completed.countDown()
            }) { published++; completed.countDown() }
            highlighter.requestHighlight(EditorTextSnapshot(EditorLineIndex.fromText("value"), 0, Any()))
        }
        try {
            assertTrue(completed.await(30, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(0, published)
            assertTrue(failure is IllegalArgumentException)
        } finally {
            instrumentation.runOnMainSync { highlighter.release() }
        }
    }
}