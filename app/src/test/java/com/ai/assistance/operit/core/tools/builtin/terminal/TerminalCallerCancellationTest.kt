package com.ai.assistance.operit.core.tools.builtin.terminal

import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager
import com.ai.assistance.operit.core.tools.system.terminal.terminalCommandFlow
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.command.CommandCancellationOutcome
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCallerCancellationTest {
    @Test
    fun cancellingCallerStopsTheRunningCommandAndUnsubscribes() = runBlocking {
        withTimeout(5_000) {
            val caller = Job()
            val events = MutableSharedFlow<CommandExecutionEvent>()
            val submitted = CompletableDeferred<Unit>()
            val cancelled = Collections.synchronizedList(mutableListOf<String>())
            val result = invokeFromBridge(caller) {
                terminalCommandFlow(events, "session", "running",
                    submit = { submitted.complete(Unit) },
                    cancel = { cancelled += "running"; CommandCancellationOutcome.INTERRUPTED },
                ).toList()
            }
            try {
                submitted.await()
                caller.cancel()
                assertTrue(result.await().exceptionOrNull() is CancellationException)
                assertEquals(listOf("running"), cancelled.toList())
                assertEquals(0, events.subscriptionCount.value)
            } finally {
                caller.cancel()
            }
        }
    }

    @Test
    fun alreadyCancelledCallerDoesNotSubmitAnyCommand() = runBlocking {
        withTimeout(5_000) {
            val caller = Job().apply { cancel() }
            val submitted = AtomicBoolean(false)
            val result = invokeFromBridge(caller) { submitted.set(true) }

            assertTrue(result.await().exceptionOrNull() is CancellationException)
            assertFalse(submitted.get())
        }
    }

    @Test
    fun cancellingWhileSubmissionIsQueuedRemovesOnlyThatCommand() = runBlocking {
        withTimeout(5_000) {
            val caller = Job()
            val events = MutableSharedFlow<CommandExecutionEvent>()
            val queued = CompletableDeferred<Unit>()
            val releaseQueue = CompletableDeferred<Unit>()
            val executed = AtomicBoolean(false)
            val removed = CompletableDeferred<String>()
            val outcome = CompletableDeferred<CommandCancellationOutcome>()
            val result = invokeFromBridge(caller) {
                terminalCommandFlow(events, "session", "queued",
                    submit = {
                        queued.complete(Unit)
                        releaseQueue.await()
                        executed.set(true)
                    },
                    cancel = { removed.complete("queued"); CommandCancellationOutcome.REMOVED_FROM_QUEUE },
                    onCancellationOutcome = { outcome.complete(it) },
                ).toList()
            }
            try {
                queued.await()
                caller.cancel()
                assertTrue(result.await().exceptionOrNull() is CancellationException)
                assertEquals("queued", removed.await())
                assertEquals(CommandCancellationOutcome.REMOVED_FROM_QUEUE, outcome.await())
                assertFalse(executed.get())
                assertEquals(0, events.subscriptionCount.value)
            } finally {
                caller.cancel()
            }
        }
    }

    @Test
    fun independentInvocationFinishesNormallyAndDoesNotCancelAnotherCommand() = runBlocking {
        withTimeout(5_000) {
            val events = MutableSharedFlow<CommandExecutionEvent>()
            val cancelled = AtomicBoolean(false)
            val result = invokeFromBridge(null) {
                terminalCommandFlow(events, "session", "own-command",
                    submit = {
                        events.emit(CommandExecutionEvent("another-command", "session", "other", true, 0))
                        events.emit(CommandExecutionEvent("own-command", "session", "done", true, 0))
                    },
                    cancel = { cancelled.set(true); CommandCancellationOutcome.INTERRUPTED },
                ).toList()
            }
            assertEquals(listOf("done"), result.await().getOrThrow().map { it.outputChunk })
            assertFalse(cancelled.get())
            assertEquals(0, events.subscriptionCount.value)
        }
    }

    private fun <T> invokeFromBridge(
        caller: Job?,
        block: suspend CoroutineScope.() -> T,
    ): Deferred<Result<T>> {
        val completed = CompletableDeferred<Result<T>>()
        // 与 JS 原生桥接一致：原生线程传递上下文后调用同步工具入口。
        thread(name = "terminal-cancellation-test", isDaemon = true) {
            val runtime = caller?.let { ToolExecutionManager.ToolRuntimeContext(cancellationJob = it) }
            completed.complete(runCatching {
                ToolExecutionManager.withToolRuntimeContext(runtime) {
                    runTerminalWithCallerCancellation(block)
                }
            })
        }
        return completed
    }
}
