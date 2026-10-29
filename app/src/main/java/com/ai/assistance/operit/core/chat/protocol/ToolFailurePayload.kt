package com.ai.assistance.operit.core.chat.protocol

/** 环境标记是来源信息；只有整个错误正文等价时去重，不删除真正的附加输出。 */
internal fun toolFailurePayload(error: String?, output: String): String {
    val message = error.orEmpty().trim()
    val detail = output.trim()
    if (message.isEmpty()) return detail
    if (detail.isEmpty() || detail == message) return message
    val environment = Regex("""^\[(?:android|linux|repo:[^\]\r\n]+)\][ \t]*""", RegexOption.IGNORE_CASE)
    val messageBody = message.replaceFirst(environment, "").trim()
    val detailBody = detail.replaceFirst(environment, "").trim()
    val messageEnvironment = environment.find(message)?.value?.trim()
    val detailEnvironment = environment.find(detail)?.value?.trim()
    val sameSource = messageEnvironment == null || detailEnvironment == null ||
        messageEnvironment.equals(detailEnvironment, ignoreCase = true)
    if (sameSource && messageBody == detailBody) return if (detailBody != detail) detail else message
    return "$message\n\n$detail"
}
