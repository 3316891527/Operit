package com.ai.assistance.operit.core.tools.builtin.web

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class WebVisitContentTest {
    private fun payload(body: String) = JsonObject(mapOf(
        "title" to JsonPrimitive("Title"),
        "url" to JsonPrimitive("https://example.com/final"),
        "content" to JsonPrimitive(body),
    )).toString()

    private fun assertFailure(reason: WebVisitFailure, block: () -> Unit) {
        try {
            block()
            fail("无效页面不能作为成功内容")
        } catch (error: WebVisitException) {
            assertEquals(reason, error.reason)
        }
    }

    @Test
    fun javascriptStringDecodingPreservesEscapesUnicodeAndLiteralBackslashes() {
        val body = "正文\nC:\\new\\test\t\"引号\" 😀 \\n不是换行"
        val encoded = Json.encodeToString(payload(body))
        assertEquals(body, decodeWebVisitContent(decodeWebVisitJavascript(encoded)).content)
    }

    @Test
    fun javascriptNullAndNonStringCallbacksFail() {
        for (value in listOf("null", "", "{}", "[]", "42", "broken")) {
            assertFailure(WebVisitFailure.INVALID_CONTENT) { decodeWebVisitJavascript(value) }
        }
    }

    @Test
    fun explicitScriptErrorCannotBecomeSuccessfulPageContent() {
        assertFailure(WebVisitFailure.EXTRACTION) { decodeWebVisitContent("""{"error":"script failed"}""") }
    }

    @Test
    fun malformedOrIncompletePayloadsFailInsteadOfReturningRawText() {
        for (value in listOf("Page timed out", "null", "{}", "{", """{"content":"body"}""")) {
            assertFailure(WebVisitFailure.INVALID_CONTENT) { decodeWebVisitContent(value) }
        }
    }

    @Test
    fun aTitleAndMetadataDoNotMakeAnEmptyPageSuccessful() {
        for (body in listOf("", " \n\t", "# Title\n---METADATA---\ndescription: text\n---CONTENT---\n  ")) {
            assertFailure(WebVisitFailure.EMPTY_CONTENT) { decodeWebVisitContent(payload(body)) }
        }
    }

    @Test
    fun validBodyKeepsTheFinalUrlAndDoesNotTreatOrdinaryErrorTextAsFailure() {
        val result = decodeWebVisitContent(payload("This article explains an error message."))
        assertEquals("https://example.com/final", result.url)
        assertEquals("This article explains an error message.", result.content)
    }

    @Test
    fun linkOnlyAndRequestedImageOnlyPagesRemainUsable() {
        val base = Json.parseToJsonElement(payload("")) as JsonObject
        val link = JsonObject(mapOf("url" to JsonPrimitive("https://example.com/next"), "text" to JsonPrimitive("Next")))
        val links = JsonObject(base + ("links" to JsonArray(listOf(link))))
        assertEquals(1, decodeWebVisitContent(links.toString()).links.size)
        val images = JsonObject(base + ("imageLinks" to JsonArray(listOf(JsonPrimitive("https://example.com/image.png")))))
        assertEquals(1, decodeWebVisitContent(images.toString()).imageLinks.size)
    }
}