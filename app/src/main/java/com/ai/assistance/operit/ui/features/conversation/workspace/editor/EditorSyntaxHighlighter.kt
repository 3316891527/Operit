package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightConfiguration
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight.EditorHighlightFiles

import android.content.res.AssetManager
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateGrammarRegistry
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class EditorSyntaxHighlighter(
    private val assets: AssetManager,
    initialLanguage: String,
    private val onFailure: (EditorTextSnapshot, String, Exception) -> Unit,
    private val configurationDirectory: File? = null,
    private val onResult: (HighlightSnapshot) -> Unit,
) {
    private data class Request(val text: EditorTextSnapshot, val language: String, val fileName: String, val sequence: Int)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val sequence = AtomicInteger()
    private val pending = AtomicReference<Request?>()
    private val scheduled = AtomicBoolean()
    private val reload = AtomicBoolean(true)
    private val observerLock = Any()
    private var observer: FileObserver? = null
    private var loadedRegistry: Result<TextMateGrammarRegistry>? = null
    @Volatile private var released = false
    private var language = initialLanguage
    private var fileName = ""
    private var latestText: EditorTextSnapshot? = null
    private var reportedFailure: Pair<String, String>? = null
    private val reloadRunnable = Runnable {
        if (!released) {
            reload.set(true)
            latestText?.let(::requestHighlight)
        }
    }

    fun setLanguage(language: String) {
        if (this.language.equals(language, ignoreCase = true)) return
        this.language = language
        reportedFailure = null
        sequence.incrementAndGet()
        pending.set(null)
    }

    fun setFileName(fileName: String) {
        if (this.fileName == fileName) return
        this.fileName = fileName
        sequence.incrementAndGet()
        pending.set(null)
        latestText?.let(::requestHighlight)
    }

    fun reloadConfiguration() {
        if (released || configurationDirectory == null) return
        mainHandler.removeCallbacks(reloadRunnable)
        mainHandler.postDelayed(reloadRunnable, 150L)
    }

    fun requestHighlight(text: EditorTextSnapshot) {
        if (released) return
        latestText = text
        pending.set(Request(text, language, fileName, sequence.incrementAndGet()))
        schedule()
    }

    private fun registry(): TextMateGrammarRegistry {
        if (reload.getAndSet(false) || loadedRegistry == null) {
            // 缓存本修订的成功或失败，损坏配置不会继续使用上一个修订的规则。
            loadedRegistry = runCatching {
                val configuration = if (configurationDirectory == null) EditorHighlightConfiguration.Empty else {
                    configurationDirectory.mkdirs()
                    require(configurationDirectory.isDirectory) { "Cannot create $configurationDirectory" }
                    synchronized(observerLock) {
                        observer?.stopWatching()
                        if (!released) {
                            observer = object : FileObserver(configurationDirectory.absolutePath, FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.DELETE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) {
                                override fun onEvent(event: Int, path: String?) {
                                    if (path == null || path.endsWith(".json")) reloadConfiguration()
                                }
                            }.also { it.startWatching() }
                        }
                    }
                    EditorHighlightFiles.load(configurationDirectory)
                }
                TextMateGrammarRegistry(configuration) { assets.open("workspace-textmate/$it").bufferedReader(Charsets.UTF_8) }
            }
        }
        return checkNotNull(loadedRegistry).getOrThrow()
    }

    /** 规则加载和分词均在同一个后台线程；只保留当前任务与最新请求。 */
    private fun schedule() {
        if (released || !scheduled.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    while (!released) {
                        val request = pending.getAndSet(null) ?: break
                        fun current(): Boolean = !released && sequence.get() == request.sequence && !Thread.currentThread().isInterrupted
                        if (!current()) continue
                        try {
                            val rules = registry().rulesFor(request.language, request.fileName)
                            val result = EditorTextMateHighlighter.highlight(request.text, rules, ::current)
                            if (result != null) mainHandler.post {
                                if (!released && sequence.get() == request.sequence) {
                                    reportedFailure = null
                                    onResult(result)
                                }
                            }
                        } catch (failure: Exception) {
                            if (current()) mainHandler.post {
                                if (!released && sequence.get() == request.sequence) {
                                    val identity = request.language to failure.toString()
                                    if (reportedFailure != identity) {
                                        reportedFailure = identity
                                        onFailure(request.text, request.language, failure)
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    scheduled.set(false)
                    if (!released && pending.get() != null) schedule()
                }
            }
        } catch (failure: RejectedExecutionException) {
            scheduled.set(false)
            if (!released) throw failure
        }
    }

    fun release() {
        released = true
        sequence.incrementAndGet()
        pending.set(null)
        latestText = null
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(observerLock) { observer?.stopWatching(); observer = null }
        executor.shutdownNow()
    }
}
