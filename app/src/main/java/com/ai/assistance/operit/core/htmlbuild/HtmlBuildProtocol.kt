package com.ai.assistance.operit.core.htmlbuild

import kotlinx.serialization.Serializable

internal const val HTML_BUILD_MAX_BYTES = 2L * 1024 * 1024 * 1024
internal const val HTML_BUILD_MAX_FILES = 20_000

@Serializable
internal data class HtmlBuildRequest(
    val version: Int,
    val target: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val entry: String,
    val files: Int,
    val totalBytes: Long,
    val iconBytes: Long,
) {
    init {
        require(version == 1) { "构建协议不匹配，请重新打开终端更新命令" }
        require(target == "apk" || target == "exe") { "不支持的构建目标：$target" }
        require(appName.isNotBlank() && appName.length <= 128 && appName.trim() == appName &&
            appName !in setOf(".", "..") && !appName.endsWith('.') &&
            appName.none { it.isISOControl() || it in "<>:\"/\\|?*" }) { "应用名称包含无效文件名字符" }
        val reserved = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
        require(target != "exe" || appName.substringBefore('.').uppercase(java.util.Locale.ROOT) !in reserved) { "应用名称是 Windows 保留文件名" }
        require(packageName.length <= 200 && packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) { "Android 包名无效" }
        require(versionName.isNotBlank() && versionName.length <= 80 && versionCode > 0) { "版本信息无效" }
        htmlBuildRelativePath(entry)
        require(entry.endsWith(".html", true) || entry.endsWith(".htm", true)) { "入口必须是 HTML 文件" }
        require(files in 1..HTML_BUILD_MAX_FILES && totalBytes in 0..HTML_BUILD_MAX_BYTES && iconBytes in 0..16L * 1024 * 1024) { "项目超过构建传输限制" }
    }
}

@Serializable
internal data class HtmlBuildFile(val path: String, val size: Long)

/** 项目和产物只使用明确的相对路径，拒绝目录穿越和跨平台分隔符。 */
internal fun htmlBuildRelativePath(path: String): String {
    require(path.isNotEmpty() && path.toByteArray(Charsets.UTF_8).size <= 2048 && '\u0000' !in path && '\\' !in path && !path.startsWith('/')) { "构建文件路径无效" }
    val parts = path.split('/')
    require(parts.size <= 64 && parts.none { it.isEmpty() || it == "." || it == ".." }) { "构建文件路径无效" }
    return path
}
