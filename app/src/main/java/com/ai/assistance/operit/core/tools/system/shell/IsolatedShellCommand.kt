package com.ai.assistance.operit.core.tools.system.shell

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 每次调用独占进程，不向共享 libsu Shell 排队。 */
internal interface OwnedShellProcess {
    val input: InputStream
    val output: OutputStream
    val errors: InputStream
    fun exitCode(): Int?
    fun close()
}

internal class LocalOwnedShellProcess(private val process: Process) : OwnedShellProcess {
    override val input: InputStream get() = process.inputStream
    override val output: OutputStream get() = process.outputStream
    override val errors: InputStream get() = process.errorStream
    override fun exitCode(): Int? = try {
        process.exitValue()
    } catch (_: IllegalThreadStateException) {
        null
    }

    override fun close() {
        // 没有阻塞式读取线程；先销毁私有进程，再关闭其描述符。
        try {
            process.destroy()
            if (!process.waitFor(200, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        } finally {
            runCatching { output.close() }
            runCatching { input.close() }
            runCatching { errors.close() }
        }
    }
}

internal fun interface OwnedShellLauncher {
    fun launch(script: String): OwnedShellProcess
}

/** 控制管道独立于用户输出，用户命令拿不到握手输入。 */
internal class OwnedShellScript(
    private val shell: String = "/system/bin/sh",
    private val setsid: String = "/system/bin/setsid",
    private val grep: String = "/system/bin/grep",
    private val kill: String = "/system/bin/kill",
) {
    fun supervisor(command: String, token: String): String {
        val script = """
            task_token=${quote(token)}
            printf '%s\n' "${'$'}${'$'}"
            IFS= read -r gate || exit 125
            [ "${'$'}gate" = go ] || exit 125
            ${quote(shell)} -c ${quote(command)} </dev/null 1>&2
            result=${'$'}?
            printf '%s\n' "${'$'}result"
            IFS= read -r gate
        """.trimIndent()
        return "exec ${quote(setsid)} -w ${quote(shell)} -c ${quote(script)}"
    }

    fun killGroup(pid: Int, token: String): String {
        require(pid > 1)
        // 组长在清理前保留；授权延迟时用令牌防止误杀复用的 PID。
        return "${quote(grep)} -F -q ${quote(token)} /proc/$pid/cmdline && ${quote(kill)} -KILL -- -$pid"
    }

    companion object {
        fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}

internal data class IsolatedShellResult(
    val output: String,
    val exitCode: Int,
    val error: String = "",
)

/**
 * 借鉴 Ry 的进程组握手，使用可用字节读取避免子进程持有管道时等不到 EOF。
 * 成功返回不杀后台服务；取消、超时和协议失败仅终止本次拥有的进程组。
 */
internal class IsolatedShellCommand(
    private val script: OwnedShellScript = OwnedShellScript(),
    private val outputLimit: Int = 256 * 1024,
) {
    suspend fun execute(command: String, timeoutMs: Long, launcher: OwnedShellLauncher): IsolatedShellResult =
        withContext(Dispatchers.IO) {
            require(timeoutMs > 0) { "timeoutMs must be a positive integer" }
            val token = UUID.randomUUID().toString()
            val output = ShellOutputBuffer(outputLimit)
            var process: OwnedShellProcess? = null
            var groupId: Int? = null
            var completed = false
            try {
                val exitCode = withTimeoutOrNull(timeoutMs) {
                    val active = launcher.launch(script.supervisor(command, token))
                    process = active
                    val control = ShellControlReader(active.input)
                    var permitted = false
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        drainAvailable(active.errors, output)
                        val line = control.poll()
                        if (line != null) {
                            val value = line.toIntOrNull() ?: error("Invalid shell supervisor result")
                            if (groupId == null) {
                                check(value > 1) { "Shell supervisor did not establish a process group" }
                                groupId = value
                            } else {
                                // 写完成标记前，前台命令已退出；后台持有管道不影响完成。
                                return@withTimeoutOrNull value
                            }
                        }
                        if (groupId != null && !permitted) {
                            currentCoroutineContext().ensureActive()
                            active.output.write("go\n".toByteArray(Charsets.UTF_8))
                            active.output.flush()
                            permitted = true
                        }
                        val earlyExit = active.exitCode()
                        check(earlyExit == null) {
                            "Shell supervisor exited before completion (exit code $earlyExit); " +
                                "an isolated setsid process is required. " + output.text()
                        }
                        delay(20)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    error("Shell supervisor loop ended")
                }
                if (exitCode == null) {
                    IsolatedShellResult(output.text(), -1, "Shell command timed out after $timeoutMs ms")
                } else {
                    drainAvailable(checkNotNull(process).errors, output)
                    completed = true
                    // 正常结束让 su/组长自行退出，避免异常断开时影响已启动的后台服务。
                    checkNotNull(process).output.write("done\n".toByteArray(Charsets.UTF_8))
                    checkNotNull(process).output.flush()
                    withTimeoutOrNull(200) {
                        while (checkNotNull(process).exitCode() == null) delay(20)
                    }
                    IsolatedShellResult(output.text(), exitCode)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        val pid = groupId
                        if (!completed && pid != null) {
                            val killer = launcher.launch(script.killGroup(pid, token))
                            try {
                                val killed = withTimeoutOrNull(2_000) {
                                    while (killer.exitCode() == null) delay(20)
                                    killer.exitCode()
                                }
                                check(killed == 0) { "Shell process-group cleanup failed" }
                            } finally {
                                killer.close()
                            }
                        }
                    } finally {
                        process?.close()
                    }
                }
            }
        }

    private fun drainAvailable(input: InputStream, output: ShellOutputBuffer) {
        val buffer = ByteArray(8 * 1024)
        // 限制单轮工作，持续输出的后台进程不能饿死控制协议、取消和期限检查。
        repeat(32) {
            val available = input.available()
            if (available <= 0) return
            val count = input.read(buffer, 0, minOf(buffer.size, available))
            if (count < 0) return
            output.append(buffer, count)
        }
    }
}

/** 每行最多 32 字节；无可读数据时返回，不进行阻塞式 readLine。 */
private class ShellControlReader(private val input: InputStream) {
    private val line = StringBuilder()

    fun poll(): String? {
        repeat(33) {
            if (input.available() <= 0) return null
            val value = input.read()
            check(value >= 0) { "Shell supervisor pipe closed" }
            if (value == 10) return line.toString().also { line.clear() }
            check(line.length < 32) { "Oversized shell supervisor message" }
            line.append(value.toChar())
        }
        error("Oversized shell supervisor message")
    }
}

/** 保留输出尾部，持续输出不能无限占用内存；始终继续消费管道。 */
private class ShellOutputBuffer(limit: Int) {
    private val bytes = ByteArray(limit.also { require(it > 0) })
    private var size = 0
    private var cursor = 0
    private var truncated = false

    fun append(buffer: ByteArray, count: Int) {
        repeat(count) { index ->
            bytes[cursor] = buffer[index]
            cursor = (cursor + 1) % bytes.size
            if (size == bytes.size) truncated = true else size++
        }
    }

    fun text(): String {
        val ordered = ByteArray(size)
        val start = if (size == bytes.size) cursor else 0
        repeat(size) { ordered[it] = bytes[(start + it) % bytes.size] }
        val text = ordered.toString(Charsets.UTF_8).trimEnd()
        return if (truncated) "[Shell output truncated; showing last ${bytes.size} bytes]\n$text" else text
    }
}
