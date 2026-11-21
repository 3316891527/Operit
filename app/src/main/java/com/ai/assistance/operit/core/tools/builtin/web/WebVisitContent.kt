package com.ai.assistance.operit.core.tools.builtin.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val visitJson = Json { ignoreUnknownKeys = true }

/** evaluateJavascript 返回 JSON 编码的字符串，按 JSON 解码以保留反斜线、换行与 Unicode。 */
internal fun decodeWebVisitJavascript(value: String): String = try {
    val result = visitJson.parseToJsonElement(value)
    if (result !is JsonPrimitive || !result.isString) {
        throw WebVisitException(WebVisitFailure.INVALID_CONTENT)
    }
    result.content
} catch (error: WebVisitException) {
    throw error
} catch (error: Exception) {
    throw WebVisitException(WebVisitFailure.INVALID_CONTENT, error.message.orEmpty())
}

/** 错误包、无效 JSON 和空白页面都必须失败，不能成为成功结果或有效 visitKey。 */
internal fun decodeWebVisitContent(value: String): ExtractedWebData = try {
    val element = visitJson.parseToJsonElement(value)
    if (element !is JsonObject) throw WebVisitException(WebVisitFailure.INVALID_CONTENT)
    val error = (element["error"] as? JsonPrimitive)?.contentOrNull
    if (!error.isNullOrBlank()) throw WebVisitException(WebVisitFailure.EXTRACTION, error)
    val result = visitJson.decodeFromString<ExtractedWebData>(value)
    val body = result.content.substringAfter("---CONTENT---\n", result.content).trim()
    if (body.isBlank() && result.links.isEmpty() && result.imageLinks.isEmpty()) {
        throw WebVisitException(WebVisitFailure.EMPTY_CONTENT)
    }
    result
} catch (error: WebVisitException) {
    throw error
} catch (error: Exception) {
    throw WebVisitException(WebVisitFailure.INVALID_CONTENT, error.message.orEmpty())
}

@Serializable
internal data class ExtractedWebData(
    val title: String,
    val url: String,
    val content: String,
    val links: List<LinkInfo> = emptyList(),
    val imageLinks: List<String> = emptyList(),
) {
    @Serializable
    data class LinkInfo(val url: String, val text: String)
}
