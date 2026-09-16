package com.ai.assistance.operit.data.storage

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocalModelDeleteOutcome {
    DELETED,
    IN_USE,
    FAILED,
}

class LocalModelUsageHandle internal constructor(
    private val canonicalPath: String,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            LocalModelRuntimeRegistry.release(canonicalPath)
        }
    }
}

object LocalModelRuntimeRegistry {
    private val lock = Any()
    private val referenceCounts = mutableMapOf<String, Int>()
    private val _activePaths = MutableStateFlow<Set<String>>(emptySet())

    val activePaths: StateFlow<Set<String>> = _activePaths.asStateFlow()

    fun acquire(path: File): LocalModelUsageHandle {
        val canonicalPath = runCatching { path.canonicalPath }.getOrDefault(path.absolutePath)
        synchronized(lock) {
            referenceCounts[canonicalPath] = (referenceCounts[canonicalPath] ?: 0) + 1
            publishActivePathsLocked()
        }
        return LocalModelUsageHandle(canonicalPath)
    }

    fun isInUse(path: File): Boolean {
        val canonicalPath = runCatching { path.canonicalPath }.getOrDefault(path.absolutePath)
        synchronized(lock) {
            return referenceCounts.keys.any { activePath -> pathsOverlap(canonicalPath, activePath) }
        }
    }

    fun deleteIfUnused(path: File): LocalModelDeleteOutcome {
        val canonicalFile =
            runCatching { path.canonicalFile }.getOrElse { return LocalModelDeleteOutcome.FAILED }
        synchronized(lock) {
            if (referenceCounts.keys.any { activePath -> pathsOverlap(canonicalFile.path, activePath) }) {
                return LocalModelDeleteOutcome.IN_USE
            }
            if (!canonicalFile.exists()) {
                return LocalModelDeleteOutcome.DELETED
            }
            val deleted =
                runCatching {
                    if (canonicalFile.isDirectory) {
                        canonicalFile.deleteRecursively()
                    } else {
                        canonicalFile.delete()
                    }
                }.getOrDefault(false)
            return if (deleted) LocalModelDeleteOutcome.DELETED else LocalModelDeleteOutcome.FAILED
        }
    }

    internal fun release(canonicalPath: String) {
        synchronized(lock) {
            val remaining = (referenceCounts[canonicalPath] ?: return) - 1
            if (remaining > 0) {
                referenceCounts[canonicalPath] = remaining
            } else {
                referenceCounts.remove(canonicalPath)
            }
            publishActivePathsLocked()
        }
    }

    private fun publishActivePathsLocked() {
        _activePaths.value = referenceCounts.keys.toSet()
    }

    private fun pathsOverlap(first: String, second: String): Boolean =
        isAtOrBelow(first, second) || isAtOrBelow(second, first)

    private fun isAtOrBelow(path: String, parent: String): Boolean =
        path == parent || path.startsWith(parent.trimEnd(File.separatorChar) + File.separator)
}
