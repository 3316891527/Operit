package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.os.FileObserver
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** Android 的 FileObserver 共用事件线程，以同一队列中的写入标记等待先前通知处理完毕。 */
internal class FileObservationBarrier(private val directory: File) {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val observer: FileObserver

    init {
        check(directory.mkdirs() || directory.isDirectory) { "Cannot create observation barrier directory" }
        observer = object : FileObserver(directory.absolutePath, CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (event and CLOSE_WRITE != 0 && path != null) pending[path]?.complete(Unit)
            }
        }
        observer.startWatching()
    }

    fun close() {
        observer.stopWatching()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    suspend fun await() {
        val name = UUID.randomUUID().toString()
        val ready = CompletableDeferred<Unit>()
        pending[name] = ready
        val file = File(directory, name)
        try {
            file.writeText("")
            withTimeout(5_000) { ready.await() }
        } finally {
            pending.remove(name)
            file.delete()
        }
    }
}
