package com.ai.assistance.operit.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AntigravityOAuthProtocolTest {
    @Test
    fun authorizationCodeBody_includesClientSecret() {
        val fields = AntigravityOAuthProtocol.authorizationCodeBody(
            code = "authorization-code",
            redirectUri = "http://127.0.0.1:51121/oauth-callback",
            verifier = "pkce-verifier",
        )

        assertEquals(EXPECTED_CLIENT_SECRET, fields.value("client_secret"))
        assertEquals("authorization_code", fields.value("grant_type"))
    }

    @Test
    fun refreshTokenBody_includesClientSecret() {
        val fields = AntigravityOAuthProtocol.refreshTokenBody("refresh-token")

        assertEquals(EXPECTED_CLIENT_SECRET, fields.value("client_secret"))
        assertEquals("refresh_token", fields.value("grant_type"))
    }

    private fun List<Pair<String, String>>.value(name: String): String? {
        return firstOrNull { it.first == name }?.second
    }

    companion object {
        private const val EXPECTED_CLIENT_SECRET = "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"
    }
}
