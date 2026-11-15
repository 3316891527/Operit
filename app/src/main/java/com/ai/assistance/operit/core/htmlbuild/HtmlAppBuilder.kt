package com.ai.assistance.operit.core.htmlbuild

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.core.subpack.ApkEditor
import com.ai.assistance.operit.core.subpack.ExeEditor
import com.ai.assistance.operit.core.subpack.KeyStoreHelper
import com.ai.assistance.operit.data.resources.OfflineResources
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 命令仅适配输入和输出，APK 清单、图标与签名继续使用现有原生引擎。 */
internal object HtmlAppBuilder {
    private val mutex = Mutex()

    suspend fun requireResources(context: Context, request: HtmlBuildRequest) {
        val group = if (request.target == "apk") OfflineResources.ANDROID_EXPORT else OfflineResources.WINDOWS_EXPORT
        check(OfflineResources.isReady(context, group)) {
            "缺少 ${if (request.target == "apk") "Android" else "Windows"} 打包模板，请先在应用的离线资源中安装对应的导出资源"
        }
    }

    suspend fun build(context: Context, request: HtmlBuildRequest, job: File, progress: (Float, String) -> Unit): List<String> {
        progress(0.25f, "项目已接收，正在等待构建资源")
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            val source = File(job, "input")
            val entry = File(source, request.entry)
            check(entry.isFile) { "项目中不存在入口 ${request.entry}" }
            // 保留入口所在目录的相对资源语义，根入口跳转到指定页面而不移动原文件。
            if (request.entry != "index.html") {
                val destination = Uri.encode(request.entry, "/")
                File(source, "index.html").writeText("<!doctype html><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" content=\"0;url=$destination\">", Charsets.UTF_8)
            }
            val artifacts = File(job, "artifacts")
            check(artifacts.mkdirs()) { "无法创建构建输出目录" }
            if (request.target == "apk") buildAndroid(context, request, job, source, artifacts, progress)
            else buildWindows(context, request, job, source, artifacts, progress)
        }
    }

    private suspend fun buildAndroid(context: Context, request: HtmlBuildRequest, job: File, source: File, artifacts: File, progress: (Float, String) -> Unit): List<String> {
        progress(0.30f, "正在校验并复制 Android 模板")
        val template = OfflineResources.copyExportTemplate(context, true, File(job, "template.apk"))
        val editor = ApkEditor.fromFile(context, template)
        try {
            currentCoroutineContext().ensureActive()
            progress(0.38f, "正在写入包名、应用名称和版本")
            editor.changePackageName(request.packageName).changeAppName(request.appName)
                .changeVersionName(request.versionName).changeVersionCode(request.versionCode.toString())
            if (request.iconBytes > 0) {
                progress(0.43f, "正在转换应用图标")
                File(job, "icon").inputStream().use { editor.changeIcon(it) }
            }
            progress(0.47f, "正在准备应用签名密钥")
            val key = KeyStoreHelper.getOrCreateKeystore(context)
            val output = File(artifacts, request.appName + ".apk")
            editor.withSignature(key, "android", "androidkey", "android").setOutput(output)
            val coroutine = currentCoroutineContext()
            val result = editor.repackAndSignWithWebContent(source) { ratio, message ->
                coroutine.ensureActive()
                progress(0.5f + ratio * 0.43f, message)
            }
            check(result.isFile && result.length() > 0) { "打包引擎没有生成 APK" }
            progress(0.94f, "APK 签名完成，正在准备传输成品")
            return listOf(result.name)
        } finally { editor.cleanup() }
    }

    private suspend fun buildWindows(context: Context, request: HtmlBuildRequest, job: File, source: File, artifacts: File, progress: (Float, String) -> Unit): List<String> {
        progress(0.30f, "正在校验并复制 Windows 模板")
        val template = OfflineResources.copyExportTemplate(context, false, File(job, "template.zip"))
        val directory = File(artifacts, request.appName)
        check(directory.mkdirs()) { "无法创建 Windows 输出目录" }
        ZipFile(template).use { archive ->
            val entries = archive.entries().toList()
            require(entries.size <= HTML_BUILD_MAX_FILES) { "Windows 模板文件过多" }
            var bytes = 0L
            var reportAt = 0L
            entries.forEachIndexed { index, entry ->
                currentCoroutineContext().ensureActive()
                val name = htmlBuildRelativePath(entry.name.trimEnd('/'))
                val target = File(directory, name)
                if (entry.isDirectory) check(target.mkdirs() || target.isDirectory) else {
                    check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                    archive.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                                require(bytes <= HTML_BUILD_MAX_BYTES) { "Windows 模板展开后超过 2 GiB" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                val now = System.nanoTime()
                if (now >= reportAt || index == entries.lastIndex) {
                    progress(0.35f + 0.2f * (index + 1) / entries.size, "正在展开 Windows 运行环境：${index + 1}/${entries.size}")
                    reportAt = now + 300_000_000
                }
            }
        }
        val executable = File(directory, "assistance_subpack.exe")
        check(executable.isFile) { "Windows 模板缺少 assistance_subpack.exe" }
        if (request.iconBytes > 0) {
            progress(0.60f, "正在写入 Windows 应用图标")
            File(job, "icon").inputStream().use { ExeEditor.fromFile(context, executable).changeIcon(it).setOutput(executable).process() }
        }
        val web = File(directory, "data/flutter_assets/assets/web_content")
        if (web.exists()) check(web.deleteRecursively()) { "无法清理模板网页" }
        check(web.mkdirs()) { "无法创建网页资源目录" }
        val files = source.walkTopDown().filter { it.isFile }.toList()
        var reportAt = 0L
        files.forEachIndexed { index, file ->
            currentCoroutineContext().ensureActive()
            val target = File(web, file.relativeTo(source).path)
            check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
            file.copyTo(target)
            val now = System.nanoTime()
            if (now >= reportAt || index == files.lastIndex) {
                progress(0.65f + 0.25f * (index + 1) / files.size, "正在写入网页文件：${index + 1}/${files.size} ${file.relativeTo(source).path}")
                reportAt = now + 300_000_000
            }
        }
        val finalExecutable = File(directory, request.appName + ".exe")
        if (finalExecutable != executable) check(executable.renameTo(finalExecutable)) { "无法设置 EXE 文件名" }
        progress(0.94f, "Windows 运行目录已生成，正在准备传输 EXE、DLL 和 data")
        return listOf(request.appName + "/" + finalExecutable.name)
    }
}