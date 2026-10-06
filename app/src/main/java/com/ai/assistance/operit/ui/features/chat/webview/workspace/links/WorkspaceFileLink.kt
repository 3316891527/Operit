package com.ai.assistance.operit.ui.features.chat.webview.workspace.links

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** 文件链接保存真实来源；目录与文件类型由该来源的文件工具判断。 */
internal data class WorkspaceFileLink(
    val path: String,
    val environment: String,
    val line: Int? = null,
)

/** 识别绝对路径及显式环境链接，普通网页和应用协议继续交给外部入口。 */
internal fun parseWorkspaceFileLink(url: String): WorkspaceFileLink? {
    val source = url.trim()
    val repositoryPath = Regex("^repo:([^/:]+):(/.*)$", RegexOption.IGNORE_CASE).matchEntire(source)
    val normalized = if (repositoryPath != null) {
        "repo://${repositoryPath.groupValues[1]}${repositoryPath.groupValues[2]}"
    } else {
        source
    }
    val uri = try {
        URI(normalized.replace(" ", "%20"))
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase()
    val authority = uri.authority.orEmpty()
    val query = uri.rawQuery.orEmpty().split('&').associate { entry ->
        val parts = entry.split('=', limit = 2)
        parts[0] to parts.getOrNull(1).orEmpty()
    }
    var path = uri.path.orEmpty()
    val environment = when (scheme) {
        null -> {
            if (!path.startsWith('/')) return null
            inferWorkspaceFileEnvironment(path)
        }
        "file" -> when (authority.lowercase()) {
            "", "localhost" -> inferWorkspaceFileEnvironment(path)
            "android", "linux" -> authority.lowercase()
            else -> return null
        }
        "android", "linux" -> {
            if (authority.isNotEmpty() && authority != "localhost") path = "/$authority$path"
            scheme
        }
        "repo" -> {
            if (authority.isEmpty()) return null
            "repo:$authority"
        }
        else -> return null
    }
    if (!path.startsWith('/')) return null
    val explicitEnvironment = query["environment"]?.let { URLDecoder.decode(it, "UTF-8") }
    if (explicitEnvironment != null && explicitEnvironment != "android" &&
        explicitEnvironment != "linux" && !explicitEnvironment.matches(Regex("repo:.+"))) return null

    val fragmentLine = uri.fragment?.let { fragment ->
        Regex("(?:L|line=)([0-9]+)(?:-L?[0-9]+)?").matchEntire(fragment)
            ?.groupValues?.get(1)?.toIntOrNull()
    }
    val suffix = Regex("^(.+):([0-9]+)$").matchEntire(path)
    val line = (fragmentLine ?: query["line"]?.toIntOrNull() ?: suffix?.groupValues?.get(2)?.toIntOrNull())
        ?.takeIf { it > 0 }
    if (suffix != null && line != null) path = suffix.groupValues[1]
    return WorkspaceFileLink(path, explicitEnvironment ?: environment, line)
}

private fun inferWorkspaceFileEnvironment(path: String): String {
    val deviceRoots = listOf("/sdcard", "/storage", "/data", "/system", "/vendor", "/apex")
    return if (deviceRoots.any { path == it || path.startsWith("$it/") }) "android" else "linux"
}
