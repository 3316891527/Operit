package com.ai.assistance.operit.ui.common.markdown

import java.util.Collections
import java.util.IdentityHashMap

internal data class MarkdownImageLoadDiagnostic(
    val resource: String,
    val markdown: String,
    val reason: String,
) {
    fun logMessage(): String =
        "图片加载失败：resource=${resource.forLog()}; reason=${reason.forLog()}; markdown=${markdown.forLog()}"
}

internal fun formatMarkdownImageLoadDiagnostic(
    resource: String,
    markdown: String,
    throwable: Throwable,
): MarkdownImageLoadDiagnostic {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val reason = generateSequence(throwable) { it.cause }
        .takeWhile { seen.add(it) }
        .joinToString("\n") { error ->
            val message = error.message
            if (message.isNullOrBlank()) error.javaClass.name else "${error.javaClass.name}: $message"
        }
    return MarkdownImageLoadDiagnostic(
        resource = redactHttpResourceQuery(resource),
        markdown = redactHttpQueries(markdown),
        reason = redactHttpQueries(reason),
    )
}

private val httpResourcePrefix = Regex("""^(?:https?:)?//""", RegexOption.IGNORE_CASE)

private fun redactHttpResourceQuery(resource: String): String {
    if (!httpResourcePrefix.containsMatchIn(resource)) return resource
    val queryStart = resource.substringBefore('#').indexOf('?')
    return if (queryStart < 0) resource else resource.substring(0, queryStart + 1) + "…"
}

// 同时识别 Markdown 中反斜杠转义的 HTTP 链接；保留非 HTTP 路径的原样。
private val httpUrlWithQuery = Regex("""((?:https?\\?:\\?/\\?/|(?<![\w:/\\])\\?/\\?/)[^\s<>"?#]*\?)[^\s<>]*""", RegexOption.IGNORE_CASE)

private fun redactHttpQueries(text: String): String = httpUrlWithQuery.replace(text) { match ->
    // 保留链接的外层闭合符号，仍能通过诊断判断 Markdown 格式。
    val closing = match.value.takeLastWhile { it in ")]}'`\"" }
    match.groupValues[1] + "…" + closing
}

private fun String.forLog(): String = replace("\r", "\\r").replace("\n", "\\n")
