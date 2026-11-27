package com.ai.assistance.operit.core.tools.javascript

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubTerminalSessionGateTest {
    @Test
    fun `same name serializes create exec and close`() = runBlocking {
        withTimeout(5_000) {
            val gate = GithubTerminalSessionGate()
            val events = mutableListOf<String>()
            val finishCreate = CompletableDeferred<Unit>()
            val execStarted = CompletableDeferred<Unit>()
            val finishExec = CompletableDeferred<Unit>()
            val closeStarted = CompletableDeferred<Unit>()
            val finishClose = CompletableDeferred<Unit>()

            val first = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("shared") {
                    events += "first:create"
                    finishCreate.await()
                    events += "first:exec"
                    execStarted.complete(Unit)
                    finishExec.await()
                    events += "first:close:begin"
                    closeStarted.complete(Unit)
                    finishClose.await()
                    events += "first:close:end"
                    "first result"
                }
            }
            // 在第一次创建尚未完成时直接运行第二个协程，直到它挂起。
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("shared") {
                    events += "second:create"
                    events += "second:exec"
                    events += "second:close"
                    "second result"
                }
            }
            assertEquals(listOf("first:create"), events)

            finishCreate.complete(Unit)
            execStarted.await()
            assertEquals(listOf("first:create", "first:exec"), events)

            finishExec.complete(Unit)
            closeStarted.await()
            val beforeClose = listOf("first:create", "first:exec", "first:close:begin")
            assertEquals(beforeClose, events)

            finishClose.complete(Unit)
            assertEquals("first result", first.await())
            assertEquals("second result", second.await())
            assertEquals(
                beforeClose + listOf("first:close:end", "second:create", "second:exec", "second:close"),
                events
            )
        }
    }

    @Test
    fun `different names can run while the first call is still open`() = runBlocking {
        withTimeout(5_000) {
            val gate = GithubTerminalSessionGate()
            val firstEntered = CompletableDeferred<Unit>()
            val finishFirst = CompletableDeferred<Unit>()
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("first") {
                    firstEntered.complete(Unit)
                    finishFirst.await()
                    "first result"
                }
            }
            firstEntered.await()

            val second = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("second") { "second result" }
            }
            assertEquals("second result", second.await())
            assertFalse(first.isCompleted)
            finishFirst.complete(Unit)
            assertEquals("first result", first.await())
        }
    }

    @Test
    fun `cancelling a waiter does not run it or release the owner's lock`() = runBlocking {
        withTimeout(5_000) {
            val gate = GithubTerminalSessionGate()
            val finishOwner = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val owner = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("shared") {
                    events += "owner:enter"
                    finishOwner.await()
                    events += "owner:exit"
                }
            }
            val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("shared") { events += "cancelled:enter" }
            }
            cancelled.cancelAndJoin()
            assertTrue(cancelled.isCancelled)

            val successor = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("shared") { events += "successor:enter" }
            }
            assertEquals(listOf("owner:enter"), events)

            finishOwner.complete(Unit)
            owner.join()
            successor.join()
            assertEquals(listOf("owner:enter", "owner:exit", "successor:enter"), events)
        }
    }

    @Test
    fun `cancelling an owner lets a queued call proceed`() = runBlocking {
        withTimeout(5_000) {
            val gate = GithubTerminalSessionGate()
            val owner = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("shared") { awaitCancellation() }
            }
            val successor = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("shared") { "successor result" }
            }
            assertFalse(successor.isCompleted)

            owner.cancelAndJoin()
            assertTrue(owner.isCancelled)
            assertEquals("successor result", successor.await())
        }
    }

    @Test
    fun `an owner exception reaches its caller and lets a queued call proceed`() = runBlocking {
        withTimeout(5_000) {
            val gate = GithubTerminalSessionGate()
            val failOwner = CompletableDeferred<Unit>()
            val failure = IllegalStateException("terminal failure")
            val owner = async(start = CoroutineStart.UNDISPATCHED) {
                // 在调用方捕获异常，避免结构化并发顺带取消等待者。
                runCatching {
                    gate.withSession<Unit>("shared") {
                        failOwner.await()
                        throw failure
                    }
                }
            }
            val successor = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withSession("shared") { "successor result" }
            }
            assertFalse(successor.isCompleted)

            failOwner.complete(Unit)
            assertSame(failure, owner.await().exceptionOrNull())
            assertEquals("successor result", successor.await())
        }
    }

    @Test
    fun `a pre-cancelled coroutine does not execute an uncontended call`() = runBlocking {
        withTimeout(5_000) {
            val gate = GithubTerminalSessionGate()
            var entered = false
            var cancellationObserved = false
            val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().cancel()
                try {
                    gate.withSession("uncontended") { entered = true }
                } catch (_: CancellationException) {
                    cancellationObserved = true
                }
            }
            cancelled.join()
            assertTrue(cancellationObserved)
            assertFalse(entered)
            assertEquals("active result", gate.withSession("uncontended") { "active result" })
        }
    }

    @Test
    fun `github terminal exec uses the default or preserves the explicit name`() {
        assertEquals("github_tools_session", githubTerminalSessionKey("github", "terminal_exec", null))
        assertEquals("github_tools_session", githubTerminalSessionKey("github", "terminal_exec", ""))
        assertEquals("custom", githubTerminalSessionKey("github", "terminal_exec", "custom"))
        assertEquals("  custom  ", githubTerminalSessionKey("github", "terminal_exec", "  custom  "))
        assertEquals(" ", githubTerminalSessionKey("github", "terminal_exec", " "))
    }

    @Test
    fun `other packages or functions do not use the gate`() {
        assertNull(githubTerminalSessionKey("other", "terminal_exec", "shared"))
        assertNull(githubTerminalSessionKey("github", "repo_list", "shared"))
        assertNull(githubTerminalSessionKey("GitHub", "terminal_exec", null))
    }
}
