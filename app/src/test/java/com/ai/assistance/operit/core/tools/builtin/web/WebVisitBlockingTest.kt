package com.ai.assistance.operit.core.tools.builtin.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class WebVisitBlockingTest {
    @Test
    fun documentedCloudflareChallengeHeaderIsCaseInsensitive() {
        assertEquals(WebVisitFailure.CLOUDFLARE_CHALLENGE,
            classifyWebVisitHttpError(503, mapOf("CF-Mitigated" to "challenge")).reason)
    }

    @Test
    fun ordinaryCloudflareServerErrorsAreNotChallenges() {
        assertEquals(WebVisitFailure.HTTP,
            classifyWebVisitHttpError(502, mapOf("server" to "cloudflare", "cf-ray" to "example")).reason)
    }

    @Test
    fun cloudflareForbiddenResponseIsReportedSeparatelyFromAChallenge() {
        assertEquals(WebVisitFailure.CLOUDFLARE_BLOCKED,
            classifyWebVisitHttpError(403, mapOf("Server" to "Cloudflare", "CF-Ray" to "example")).reason)
    }

    @Test
    fun explicitAccessDenialsAreSeparateFromMissingPages() {
        for (status in listOf(403, 451)) {
            assertEquals(WebVisitFailure.ACCESS_BLOCKED, classifyWebVisitHttpError(status, emptyMap()).reason)
        }
        assertEquals(WebVisitFailure.HTTP, classifyWebVisitHttpError(404, emptyMap()).reason)
    }

    @Test
    fun aSuccessfulHttpStatusCannotMakeAChallengeDocumentUsable() {
        val page = WebVisitPageSignals("Just a moment...", "Verifying you are human", cloudflareChallenge = true)
        assertEquals(WebVisitFailure.CLOUDFLARE_CHALLENGE, detectWebVisitBlock(page)?.reason)
    }

    @Test
    fun nativeCloudflareBlockPagesHaveTheirOwnFailure() {
        val page = WebVisitPageSignals("Attention Required! | Cloudflare", "Sorry, you have been blocked", cloudflareBlocked = true)
        assertEquals(WebVisitFailure.CLOUDFLARE_BLOCKED, detectWebVisitBlock(page)?.reason)
    }

    @Test
    fun aShortExplicitFirewallDenialPageIsRejected() {
        val page = WebVisitPageSignals("Request Rejected", "The requested URL was rejected. Please consult with your administrator.")
        assertEquals(WebVisitFailure.ACCESS_BLOCKED, detectWebVisitBlock(page)?.reason)
    }

    @Test
    fun ChineseAccessPolicyPagesAreRejected() {
        val page = WebVisitPageSignals("网站已被拦截", "该网站已被拦截，请联系网络管理员。")
        assertEquals(WebVisitFailure.ACCESS_BLOCKED, detectWebVisitBlock(page)?.reason)
    }

    @Test
    fun articlesAboutCloudflareAndFirewallsAreNotBlockPages() {
        for (title in listOf("Cloudflare documentation", "How to fix Access Denied", "Firewall configuration")) {
            assertNull(detectWebVisitBlock(WebVisitPageSignals(title,
                "This article explains Cloudflare, Turnstile and the message: The requested URL was rejected.")))
        }
    }

    @Test
    fun aTitleOrKeywordAloneIsInsufficientEvidence() {
        assertNull(detectWebVisitBlock(WebVisitPageSignals("Access Denied", "A short story about a forgotten password.")))
        assertNull(detectWebVisitBlock(WebVisitPageSignals("Normal page", "You have been blocked by a firewall.")))
        assertNull(detectWebVisitBlock(WebVisitPageSignals("Access Denied", "You don't have permission to access " + "article ".repeat(1000))))
    }

    @Test
    fun pageSignalsMustBeCompleteAndWellFormed() {
        val page = decodeWebVisitPageSignals("""{"title":"Normal","text":"body","cloudflareChallenge":false,"cloudflareBlocked":false}""")
        assertNull(detectWebVisitBlock(page))
        for (value in listOf("null", "broken", "{}")) {
            try {
                decodeWebVisitPageSignals(value)
                fail("无效检查结果不得当作正常页面")
            } catch (error: WebVisitException) {
                assertEquals(WebVisitFailure.INVALID_CONTENT, error.reason)
            }
        }
    }
}