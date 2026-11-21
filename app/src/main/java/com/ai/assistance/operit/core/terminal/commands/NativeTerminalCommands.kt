package com.ai.assistance.operit.core.terminal.commands

import android.content.Context
import com.ai.assistance.operit.core.htmlbuild.HtmlBuildCommandServer
import com.ai.assistance.operit.integrations.http.commands.WebChatCommandServer
import org.json.JSONObject
import java.io.File

object NativeTerminalCommands {
    private const val ASSET_ROOT = "terminal_commands"
    private const val COMMAND_DIRECTORY = "/usr/local/bin"

    @Synchronized
    fun updateLanguage(context: Context, language: String = com.ai.assistance.operit.util.android.LocaleUtils.getCurrentLanguage(context)): File {
        val file = File(context.filesDir, "terminal-commands/language")
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write((if (language.substringBefore('-').equals("zh", true)) "zh" else "en").toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) { atomic.failWrite(stream); throw error }
        return file
    }

    fun sessionInitializer(context: Context): String {
        val languageFile = updateLanguage(context)
        val manifest = context.assets.open("$ASSET_ROOT/manifest.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
        check(manifest.getInt("format") == 1) { "终端命令资源格式不受支持" }
        check(manifest.getString("target") == "aarch64-unknown-linux-gnu") { "终端命令架构不匹配" }
        val directory = File(context.filesDir, "terminal-commands")
        val binary = manifest.getJSONObject("binary")
        val installer = manifest.getJSONObject("installer")
        check(binary.getString("file") == "aarch64/apply_patch") { "终端命令资源路径无效" }
        check(installer.getString("file") == "install.sh") { "终端命令安装脚本路径无效" }
        val executable = NativeCommandAssets.install(File(directory, "apply_patch"), binary.getString("sha256")) {
            context.assets.open("$ASSET_ROOT/${binary.getString("file")}")
        }
        val script = NativeCommandAssets.install(File(directory, "install.sh"), installer.getString("sha256")) {
            context.assets.open("$ASSET_ROOT/${installer.getString("file")}")
        }
        // 文件目录由终端统一挂载，初始化在 Ubuntu 内执行，兼容普通会话和隐藏执行。
        val webRoot = "web_chat_command"
        val webManifest = context.assets.open("$webRoot/manifest.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
        check(webManifest.getInt("format") == 1 && webManifest.getString("target") == "aarch64-unknown-linux-gnu") {
            "web-chat 命令资源格式不受支持"
        }
        val webBinary = webManifest.getJSONObject("binary")
        val webInstaller = webManifest.getJSONObject("installer")
        check(webBinary.getString("file") == "aarch64/web-chat" && webInstaller.getString("file") == "install.sh") {
            "web-chat 命令资源路径无效"
        }
        val webDirectory = File(directory, "web-chat-command")
        val webExecutable = NativeCommandAssets.install(File(webDirectory, "web-chat"), webBinary.getString("sha256")) {
            context.assets.open("$webRoot/${webBinary.getString("file")}")
        }
        val webScript = NativeCommandAssets.install(File(webDirectory, "install.sh"), webInstaller.getString("sha256")) {
            context.assets.open("$webRoot/${webInstaller.getString("file")}")
        }
        val socket = WebChatCommandServer.ensureStarted(context)
        val patchInstall = "bash ${quote(script.absolutePath)} ${quote(executable.absolutePath)} ${quote(binary.getString("sha256"))} ${quote(COMMAND_DIRECTORY)}"
        val webInstall = "bash ${quote(webScript.absolutePath)} ${quote(webExecutable.absolutePath)} ${quote(webBinary.getString("sha256"))} ${quote(COMMAND_DIRECTORY)} ${quote(socket)}"
        val buildRoot = "html_build_command"
        val buildManifest = context.assets.open("$buildRoot/manifest.json").bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        check(buildManifest.getInt("format") == 1 && buildManifest.getString("target") == "aarch64-unknown-linux-gnu") { "build 命令资源格式不受支持" }
        val buildBinary = buildManifest.getJSONObject("binary")
        val buildInstaller = buildManifest.getJSONObject("installer")
        check(buildBinary.getString("file") == "aarch64/build" && buildInstaller.getString("file") == "install.sh") { "build 命令资源路径无效" }
        val buildDirectory = File(directory, "html-build-command")
        val buildExecutable = NativeCommandAssets.install(File(buildDirectory, "build"), buildBinary.getString("sha256")) {
            context.assets.open("$buildRoot/${buildBinary.getString("file")}")
        }
        val buildScript = NativeCommandAssets.install(File(buildDirectory, "install.sh"), buildInstaller.getString("sha256")) {
            context.assets.open("$buildRoot/${buildInstaller.getString("file")}")
        }
        val buildSocket = HtmlBuildCommandServer.ensureStarted(context)
        val buildInstall = "bash ${quote(buildScript.absolutePath)} ${quote(buildExecutable.absolutePath)} ${quote(buildBinary.getString("sha256"))} ${quote(COMMAND_DIRECTORY)} ${quote(buildSocket)}"
        return "export OPERIT_COMMAND_LANGUAGE_FILE=${quote(languageFile.absolutePath)} && $patchInstall && $webInstall && $buildInstall"
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
