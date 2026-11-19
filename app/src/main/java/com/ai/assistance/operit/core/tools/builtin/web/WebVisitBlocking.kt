package com.ai.assistance.operit.core.tools.builtin.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 只读取主文档的结构和可见文字；不会为识别拦截发起额外请求。 */
internal val WEB_VISIT_PAGE_CHECK_SCRIPT = """
(function() {
    var text = document.body ? document.body.innerText : '';
    var title = document.title || '';
    var options = window._cf_chl_opt;
    var challengeOptions = !!(options && /^(managed|interactive|non-interactive)$/.test(options.cType || ''));
    var challengeForm = !!document.querySelector('form#challenge-form[action*="__cf_chl"], form#challenge-form[action*="/cdn-cgi/challenge-platform/"]');
    var challengeStage = !!document.querySelector('#challenge-running, #challenge-stage, #cf-challenge-running');
    var challengeScript = !!document.querySelector('script[src*="/cdn-cgi/challenge-platform/"]');
    var challengeTitle = /^(just a moment|checking your browser|verify you are human|请稍候|正在检查您的浏览器)/i.test(title.trim());
    var cloudflareError = !!document.querySelector('#cf-error-details');
    var cloudflareDenied = /sorry, you have been blocked|you have been blocked|error(?: code)?\s*:?\s*1020|access denied/i.test(text);
    return JSON.stringify({
        title: title,
        text: text.slice(0, 8192),
        cloudflareChallenge: challengeOptions || challengeForm || (challengeStage && challengeScript && challengeTitle),
        cloudflareBlocked: cloudflareError && cloudflareDenied
    });
})();
""".trimIndent()

internal data class WebVisitPageSignals(
    val title: String,
    val text: String,
    val cloudflareChallenge: Boolean = false,
    val cloudflareBlocked: Boolean = false,
)

internal fun decodeWebVisitPageSignals(value: String): WebVisitPageSignals = try {
    val data = Json.parseToJsonElement(value).jsonObject
    WebVisitPageSignals(
        title = data.getValue("title").jsonPrimitive.content,
        text = data.getValue("text").jsonPrimitive.content,
        cloudflareChallenge = data.getValue("cloudflareChallenge").jsonPrimitive.boolean,
        cloudflareBlocked = data.getValue("cloudflareBlocked").jsonPrimitive.boolean,
    )
} catch (error: Exception) {
    throw WebVisitException(WebVisitFailure.INVALID_CONTENT, error.message.orEmpty())
}

/** 仅明确的验证结构或拒绝页才失败，文章提到 Cloudflare、Turnstile 或防火墙不构成证据。 */
internal fun detectWebVisitBlock(page: WebVisitPageSignals): WebVisitException? {
    if (page.cloudflareChallenge) {
        return WebVisitException(WebVisitFailure.CLOUDFLARE_CHALLENGE, "Cloudflare challenge page: ${page.title.take(160)}")
    }
    if (page.cloudflareBlocked) {
        return WebVisitException(WebVisitFailure.CLOUDFLARE_BLOCKED, "Cloudflare block page: ${page.title.take(160)}")
    }
    if (page.text.length > 4096) return null
    val title = page.title.trim().lowercase()
    val denialTitle = listOf(
        "access denied", "request rejected", "403 forbidden", "access blocked", "website blocked",
        "访问被拒绝", "访问受限", "网站已被拦截", "禁止访问",
    ).any { title == it || title.startsWith("$it | ") || title.startsWith("$it - ") }
    if (!denialTitle) return null
    val text = page.text.lowercase()
    val denialMessage = listOf(
        "the requested url was rejected", "you don't have permission to access",
        "you do not have permission to access", "access has been denied", "request has been blocked",
        "blocked by your administrator", "blocked by a firewall", "blocked by the firewall",
        "访问被防火墙拦截", "该网站已被拦截", "已被网络管理员阻止", "您无权访问",
    ).any { it in text }
    return if (denialMessage) {
        WebVisitException(WebVisitFailure.ACCESS_BLOCKED, "Access policy block page: ${page.title.take(160)}")
    } else null
}

/** cf-mitigated 是 Cloudflare 公布的挑战标记；普通 Cloudflare 响应不能被判成验证页。 */
internal fun classifyWebVisitHttpError(status: Int, headers: Map<String, String>): WebVisitException {
    fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    return when {
        header("cf-mitigated")?.trim()?.equals("challenge", ignoreCase = true) == true ->
            WebVisitException(WebVisitFailure.CLOUDFLARE_CHALLENGE, "HTTP $status; cf-mitigated: challenge")
        status == 403 && header("server")?.trim()?.equals("cloudflare", ignoreCase = true) == true && header("cf-ray") != null ->
            WebVisitException(WebVisitFailure.CLOUDFLARE_BLOCKED, "HTTP $status; response served by Cloudflare")
        status == 403 || status == 451 -> WebVisitException(WebVisitFailure.ACCESS_BLOCKED, "HTTP $status")
        else -> WebVisitException(WebVisitFailure.HTTP, "HTTP $status")
    }
}
