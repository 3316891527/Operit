package com.ai.assistance.operit.core.tools.system.shell

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeTrue

/** 使用真实本地进程覆盖管道、独立执行及取消，Android 权限接入由设备验收。 */
class IsolatedShellCommandTest {
    private val script = OwnedShellScript("/bin/sh", "/usr/bin/setsid", "/bin/grep", "/bin/kill")
    private val launcher = OwnedShellLauncher {
        LocalOwnedShellProcess(ProcessBuilder("/bin/sh", "-c", it).start())
    }

    @Before fun requireLinuxProcessTools() {
        assumeTrue(listOf("/bin/sh", "/usr/bin/setsid", "/bin/grep", "/bin/kill").all { java.io.File(it).canExecute() })
    }

    private suspend fun run(command: String, timeoutMs: Long = 5_000): IsolatedShellResult =
        IsolatedShellCommand(script).execute(command, timeoutMs, launcher)

    @Test fun echoAndStderrAreCapturedWithoutControlMessages() = runBlocking {
        val result = run("printf '正常输出\\n'; printf '诊断输出\\n' >&2")
        assertEquals(0, result.exitCode)
        assertEquals("正常输出\n诊断输出", result.output)
        assertEquals("", result.error)
    }

    @Test fun multilineQuotedCommandsRemainIntact() = runBlocking {
        val result = run("printf '%s\\n' \"a'b\"\nprintf '%s' '\$HOME; echo no'")
        assertEquals("a'b\n\$HOME; echo no", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test fun catReceivesEofInsteadOfEatingTheSupervisorInput() = runBlocking {
        val result = run("cat; echo done")
        assertEquals("done", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test fun nonzeroExitPreservesDiagnostics() = runBlocking {
        val result = run("echo detail >&2; exit 7")
        assertEquals(7, result.exitCode)
        assertEquals("detail", result.output)
    }

    @Test fun largeStdoutAndStderrDoNotFillAnUnconsumedPipe() = runBlocking {
        val result = run("head -c 90000 /dev/zero | tr '\\000' a; head -c 90000 /dev/zero | tr '\\000' b >&2")
        assertEquals(0, result.exitCode)
        assertEquals(180000, result.output.length)
        assertTrue(result.output.startsWith("aaaa"))
        assertTrue(result.output.endsWith("bbbb"))
    }

    @Test fun outputBeyondLimitKeepsDrainingAndReportsTruncation() = runBlocking {
        val result = IsolatedShellCommand(script, 128).execute(
            "head -c 90000 /dev/zero | tr '\\000' x; echo final", 5_000, launcher
        )
        assertEquals(0, result.exitCode)
        assertTrue(result.output.startsWith("[Shell output truncated;"))
        assertTrue(result.output.endsWith("final"))
        assertTrue(result.output.length < 220)
    }

    @Test fun inheritedBackgroundPipeDoesNotDelayCompletion() = runBlocking {
        val started = System.nanoTime()
        val result = run("sleep 2 & echo foreground")
        assertEquals("foreground", result.output)
        assertEquals(0, result.exitCode)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_500)
    }

    @Test fun successfulBackgroundServiceIsNotKilled() = runBlocking {
        val file = Files.createTempFile("operit-shell-service", ".txt").toFile()
        try {
            val result = run("(sleep 0.2; echo survived > ${OwnedShellScript.quote(file.path)}) >/dev/null 2>&1 & echo started")
            assertEquals(0, result.exitCode)
            withTimeout(2_000) {
                while (file.readText().isEmpty()) delay(20)
            }
            assertEquals("survived\n", file.readText())
        } finally {
            file.delete()
        }
    }

    @Test fun slowCommandDoesNotQueueTheNextEcho() = runBlocking {
        val slow = async { run("sleep 1; echo slow") }
        delay(100)
        val fast = withTimeout(700) { run("echo fast") }
        assertEquals("fast", fast.output)
        assertEquals("slow", slow.await().output)
    }

    @Test fun timeoutReturnsFailureAndNextCallStillWorks() = runBlocking {
        val result = run("echo partial; sleep 10", 300)
        assertEquals(-1, result.exitCode)
        assertEquals("partial", result.output)
        assertTrue(result.error.contains("timed out"))
        assertEquals("OK", run("echo OK").output)
    }

    @Test fun cancellationKillsTheOwnedCommandBeforeReturning() = runBlocking {
        val file = Files.createTempFile("operit-shell-pid", ".txt").toFile()
        try {
            val job = launch { run("echo \$\$ > ${OwnedShellScript.quote(file.path)}; exec sleep 10") }
            withTimeout(2_000) { while (file.readText().isBlank()) delay(20) }
            val pid = file.readText().trim()
            job.cancelAndJoin()
            val state = try {
                java.io.File("/proc/$pid/stat").readText()
            } catch (_: java.io.FileNotFoundException) {
                ""
            }
            assertTrue(state.isEmpty() || state.substringAfterLast(") ").startsWith("Z"))
            assertEquals("OK", run("echo OK").output)
        } finally {
            file.delete()
        }
    }

    @Test fun cancellationBeforeHandshakeNeverStartsUserCommand() = runBlocking {
        val launched = CompletableDeferred<Unit>()
        val sentGate = ByteArrayOutputStream()
        val closed = CompletableDeferred<Unit>()
        val pending = OwnedShellLauncher {
            object : OwnedShellProcess {
                override val input = ByteArrayInputStream(byteArrayOf())
                override val output = sentGate
                override val errors = ByteArrayInputStream(byteArrayOf())
                override fun exitCode(): Int? = null
                override fun close() { closed.complete(Unit) }
            }.also { launched.complete(Unit) }
        }
        val job = launch { IsolatedShellCommand(script).execute("echo forbidden", 5_000, pending) }
        launched.await()
        job.cancelAndJoin()
        assertTrue(closed.isCompleted)
        assertEquals("", sentGate.toString())
    }

    @Test fun oversizedControlMessageFailsWithoutRunningOrRetrying() = runBlocking {
        val calls = AtomicInteger()
        val output = ByteArrayOutputStream()
        var closed = false
        val invalid = OwnedShellLauncher {
            calls.incrementAndGet()
            object : OwnedShellProcess {
                override val input = ByteArrayInputStream("9".repeat(40).toByteArray())
                override val output = output
                override val errors = ByteArrayInputStream(byteArrayOf())
                override fun exitCode(): Int? = null
                override fun close() { closed = true }
            }
        }
        try {
            IsolatedShellCommand(script).execute("echo forbidden", 5_000, invalid)
            fail("Expected control protocol failure")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("Oversized"))
        }
        assertEquals(1, calls.get())
        assertEquals("", output.toString())
        assertTrue(closed)
    }

    @Test fun invalidTimeoutDoesNotLaunchAProcess() = runBlocking {
        val calls = AtomicInteger()
        try {
            IsolatedShellCommand(script).execute("echo forbidden", 0) {
                calls.incrementAndGet()
                error("Must not launch")
            }
            fail("Expected invalid deadline")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, calls.get())
        }
    }
    @Test fun cancellingOneCallDoesNotKillAnotherCall() = runBlocking {
        val keep = async { run("sleep 0.4; echo retained") }
        val cancel = launch { run("sleep 10") }
        delay(100)
        cancel.cancelAndJoin()
        assertEquals("retained", keep.await().output)
    }

    @Test fun timeoutBeforeHandshakeClosesProcessWithoutSendingGo() = runBlocking {
        val sent = ByteArrayOutputStream()
        var closed = false
        val waiting = OwnedShellLauncher {
            object : OwnedShellProcess {
                override val input = ByteArrayInputStream(byteArrayOf())
                override val output = sent
                override val errors = ByteArrayInputStream(byteArrayOf())
                override fun exitCode(): Int? = null
                override fun close() { closed = true }
            }
        }
        val result = IsolatedShellCommand(script).execute("echo forbidden", 50, waiting)
        assertEquals(-1, result.exitCode)
        assertTrue(result.error.contains("timed out"))
        assertEquals("", sent.toString())
        assertTrue(closed)
    }

    @Test fun startupFailureKeepsDiagnosticsAndDoesNotReplayCommand() = runBlocking {
        val calls = AtomicInteger()
        val denied = OwnedShellLauncher {
            calls.incrementAndGet()
            LocalOwnedShellProcess(ProcessBuilder("/bin/sh", "-c", "echo denied >&2; exit 126").start())
        }
        try {
            IsolatedShellCommand(script).execute("echo forbidden", 5_000, denied)
            fail("Expected startup failure")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("denied"))
        }
        assertEquals(1, calls.get())
    }

    @Test fun cleanupFailureIsReportedInsteadOfReturningFalseSuccess() = runBlocking {
        val calls = AtomicInteger()
        var closed = 0
        val failing = OwnedShellLauncher {
            val index = calls.incrementAndGet()
            object : OwnedShellProcess {
                override val input = ByteArrayInputStream(if (index == 1) "12345\n".toByteArray() else byteArrayOf())
                override val output = ByteArrayOutputStream()
                override val errors = ByteArrayInputStream(byteArrayOf())
                override fun exitCode(): Int? = if (index == 1) null else 1
                override fun close() { closed++ }
            }
        }
        try {
            IsolatedShellCommand(script).execute("echo unused", 80, failing)
            fail("Expected cleanup failure")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("cleanup failed"))
        }
        assertEquals(2, calls.get())
        assertEquals(2, closed)
    }

}
