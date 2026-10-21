package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.llmprovider.LlmRetryPolicy
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiTransportErrorTest {
    @Test
    fun hostResolutionUsesTheFirstCustomCode() {
        val result = ApiTransportError.describe(UnknownHostException("api.example.test"))!!
        val json = JSONObject(result.json(attempt = 1))

        assertEquals(5000, json.getJSONObject("error").getInt("code"))
        assertEquals("host_resolution_failed", json.getJSONObject("error").getString("type"))
        assertEquals("api.example.test", json.getJSONObject("error").getString("detail"))
    }

    @Test
    fun streamResetKeepsTheUnderlyingReasonInJson() {
        val result = ApiTransportError.describe(IOException("stream was reset: CANCEL"))!!
        val json = JSONObject(result.json(attempt = 2))

        assertEquals(5002, json.getJSONObject("error").getInt("code"))
        assertEquals("stream was reset: CANCEL", json.getJSONObject("error").getString("detail"))
    }

    @Test
    fun softwareAbortGetsItsOwnTransportCode() {
        val result = ApiTransportError.describe(SocketException("Software caused connection abort"))!!
        val json = JSONObject(result.json(attempt = 1))

        assertEquals(5003, json.getJSONObject("error").getInt("code"))
        assertEquals("connection_aborted", json.getJSONObject("error").getString("type"))
    }

    @Test
    fun firstTokenTimeoutIncludesUnderlyingTransportFailure() {
        val result = ApiTransportError.describe(
            FirstTokenTimeoutException(
                message = "stream was reset: CANCEL",
                cause = IOException("stream was reset: CANCEL"),
            )
        )!!
        val json = JSONObject(result.json(attempt = 3))
        val error = json.getJSONObject("error")

        assertEquals(5004, error.getInt("code"))
        assertEquals(300_000L, LlmRetryPolicy.FIRST_TOKEN_TIMEOUT_MS)
        assertEquals(300_000L, error.getLong("timeoutMs"))
        assertEquals("stream was reset: CANCEL", error.getString("detail"))
    }

    @Test
    fun connectionFailureGetsRetryMetadataWithoutEnglishSuffix() {
        val result = ApiTransportError.describe(ConnectException("Connection refused"))!!
        val json = JSONObject(
            result.json(
                attempt = 1,
                retry = ApiTransportErrorDescription.RetryInfo(nextAttempt = 2, delayMs = 1000),
            )
        )
        val error = json.getJSONObject("error")

        assertEquals(5006, error.getInt("code"))
        assertEquals(2, error.getJSONObject("retry").getInt("nextAttempt"))
        assertEquals(1000, error.getJSONObject("retry").getLong("delayMs"))
    }
}
