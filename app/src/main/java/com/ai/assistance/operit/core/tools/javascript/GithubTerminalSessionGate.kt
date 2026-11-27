package com.ai.assistance.operit.core.tools.javascript

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

/** 将同名会话的完整调用串行化，避免一次调用关闭另一次调用正在使用的会话。 */
internal class GithubTerminalSessionGate {
    private class Entry {
        val mutex = Mutex()
        var references = 0
    }

    private val entries = mutableMapOf<String, Entry>()

    suspend fun <T> withSession(sessionName: String, block: suspend () -> T): T {
        val context = currentCoroutineContext()
        // 只在查表和更新引用计数时同步；持有者和等待者共用同一个会话锁。
        val entry = synchronized(entries) {
            entries.getOrPut(sessionName) { Entry() }.also { it.references++ }
        }
        try {
            context.ensureActive()
            // 等待锁时若被取消，Mutex 会处理交接中的锁；这里只在成功取锁后解锁。
            entry.mutex.lock()
            try {
                // Mutex 的无竞争路径不检查取消，进入调用前再检查一次。
                context.ensureActive()
                return block()
            } finally {
                entry.mutex.unlock()
            }
        } finally {
            synchronized(entries) {
                entry.references--
                if (entry.references == 0) entries.remove(sessionName)
            }
        }
    }
}

/** 与 github.js 创建终端会话时使用的名称保持一致。 */
internal fun githubTerminalSessionKey(
    packageName: String,
    functionName: String,
    sessionName: String?
): String? {
    if (packageName != "github" || functionName != "terminal_exec") return null
    return if (sessionName.isNullOrEmpty()) "github_tools_session" else sessionName
}
