package com.ai.assistance.operit.core.htmlbuild

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Process
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/** 抽象本地 socket 只接受应用 UID 和真实 root，项目与成品均分块传输。 */
object HtmlBuildCommandServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json
    private var listener: LocalServerSocket? = null

    @Synchronized
    fun ensureStarted(context: Context): String {
        val name = "operit.build.${Process.myUid()}"
        if (listener != null) return name
        val server = LocalServerSocket(name)
        val application = context.applicationContext
        listener = server
        scope.launch {
            try {
                while (isActive) {
                    val socket = server.accept()
                    launch {
                        socket.use {
                            try {
                                handle(application, it)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                AppLogger.w("HtmlBuild", "构建连接已结束：${failure.javaClass.simpleName}")
                            }
                        }
                    }
                }
            } catch (failure: Exception) {
                AppLogger.e("HtmlBuild", "构建命令监听已结束", failure)
            } finally {
                server.close()
                synchronized(this@HtmlBuildCommandServer) { if (listener === server) listener = null }
            }
        }
        return name
    }

    private suspend fun handle(context: Context, socket: LocalSocket) {
        socket.soTimeout = 120_000
        val writer = BuildWriter(socket.outputStream)
        var job: File? = null
        try {
            check(socket.peerCredentials.uid in setOf(Process.myUid(), 0)) { "构建服务拒绝了当前进程身份" }
            val input = BufferedInputStream(socket.inputStream)
            val request = json.decodeFromString<HtmlBuildRequest>(readFrame(input))
            val directory = File(context.cacheDir, "html-build/jobs/${UUID.randomUUID()}")
            check(directory.mkdirs()) { "无法创建构建任务目录" }
            job = directory
            val stage = AtomicReference(0.01f to "构建请求已接受，正在检查模板")
            fun progress(value: Float, message: String) {
                require(value in 0f..1f)
                stage.set(value to message)
                writer.progress(value, message)
            }
            coroutineScope {
                // 长阶段输出真实的当前状态和耗时，不把等待时间伪装为完成百分比。
                val heartbeat = launch {
                    val start = System.nanoTime()
                    while (isActive) {
                        delay(2_000)
                        val current = stage.get()
                        writer.progress(current.first, "${current.second}（仍在执行，已用时 ${(System.nanoTime() - start) / 1_000_000_000} 秒）")
                    }
                }
                val outputs = try {
                    progress(0.01f, "构建请求已接受，正在检查 ${request.target} 模板")
                    HtmlAppBuilder.requireResources(context, request)
                    receiveProject(input, directory, request, ::progress)
                    HtmlAppBuilder.build(context, request, directory, ::progress)
                } finally { heartbeat.cancelAndJoin() }
                val artifacts = File(directory, "artifacts")
                val files = artifacts.walkTopDown().filter { it.isFile }.toList()
                require(files.size <= HTML_BUILD_MAX_FILES && files.sumOf { it.length() } <= HTML_BUILD_MAX_BYTES) { "构建成品超过传输限制" }
                val directories = artifacts.walkTopDown().filter { it.isDirectory && it != artifacts }.toList()
                require(directories.size <= HTML_BUILD_MAX_FILES) { "成品目录过多" }
                directories.forEach { writer.directory(htmlBuildRelativePath(it.relativeTo(artifacts).invariantSeparatorsPath)) }
                files.forEachIndexed { index, file ->
                    currentCoroutineContext().ensureActive()
                    writer.progress(0.95f + 0.04f * index / files.size, "正在传输成品：${index + 1}/${files.size} ${file.name}")
                    writer.artifact(htmlBuildRelativePath(file.relativeTo(artifacts).invariantSeparatorsPath), file)
                }
                writer.progress(1f, "所有阶段已完成，正在写入终端指定的输出目录")
                writer.result(true, "构建完成", outputs)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("HtmlBuild", "HTML 构建失败", failure)
            writer.result(false, failure.message ?: failure.javaClass.simpleName, emptyList())
        } finally {
            job?.let { if (!it.deleteRecursively()) AppLogger.w("HtmlBuild", "临时构建目录清理失败") }
        }
    }

    private suspend fun receiveProject(input: InputStream, directory: File, request: HtmlBuildRequest, progress: (Float, String) -> Unit) {
        val project = File(directory, "input")
        check(project.mkdirs())
        val names = HashSet<String>()
        val buffer = ByteArray(32 * 1024)
        var received = 0L
        var nextReport = 0L
        repeat(request.files) { index ->
            currentCoroutineContext().ensureActive()
            val file = json.decodeFromString<HtmlBuildFile>(readFrame(input))
            htmlBuildRelativePath(file.path)
            require(names.add(file.path) && file.size >= 0 && file.size <= request.totalBytes - received) { "项目文件清单不一致" }
            val destination = File(project, file.path)
            check(destination.parentFile?.mkdirs() == true || destination.parentFile?.isDirectory == true)
            destination.outputStream().use { output ->
                var remaining = file.size
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    check(count > 0) { "项目传输中断：${file.path}" }
                    output.write(buffer, 0, count)
                    remaining -= count
                    received += count
                    val now = System.nanoTime()
                    if (now >= nextReport) {
                        progress(0.03f + 0.20f * received / request.totalBytes.coerceAtLeast(1), "正在接收项目：${index + 1}/${request.files}，$received/${request.totalBytes} 字节")
                        nextReport = now + 300_000_000
                    }
                }
            }
        }
        require(received == request.totalBytes) { "项目字节数与清单不一致" }
        if (request.iconBytes > 0) {
            File(directory, "icon").outputStream().use { output ->
                var remaining = request.iconBytes
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    check(count > 0) { "图标传输中断" }
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
        }
        check(input.read() == -1) { "项目传输包含额外内容" }
        progress(0.24f, "项目接收完成：${request.files} 个文件，$received 字节")
    }

    private fun readFrame(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= 65_536) {
            when (val next = input.read()) {
                -1 -> error("构建请求不完整")
                10 -> return bytes.toString(Charsets.UTF_8.name())
                else -> bytes.write(next)
            }
        }
        error("构建请求控制帧过大")
    }

    private class BuildWriter(private val output: OutputStream) {
        private val lock = Any()
        private fun frame(value: JSONObject) = synchronized(lock) {
            value.put("version", 1)
            output.write((value.toString() + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        }
        fun progress(value: Float, message: String) = frame(JSONObject().put("kind", "progress").put("progress", value.toDouble()).put("message", message))
        fun directory(path: String) = frame(JSONObject().put("kind", "directory").put("path", path))
        fun result(success: Boolean, message: String, outputs: List<String>) = frame(JSONObject().put("kind", "result").put("success", success).put("message", message).put("outputs", JSONArray(outputs)))
        fun artifact(path: String, file: File) = synchronized(lock) {
            frame(JSONObject().put("kind", "artifact").put("path", path).put("size", file.length()))
            file.inputStream().use { it.copyTo(output, 32 * 1024) }
            output.flush()
        }
    }
}