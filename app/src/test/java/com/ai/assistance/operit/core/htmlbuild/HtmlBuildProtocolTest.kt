package com.ai.assistance.operit.core.htmlbuild

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HtmlBuildProtocolTest {
    private fun request() = HtmlBuildRequest(1, "apk", "示例", "com.example.demo", "1.0", 1, "index.html", 1, 16, 0)

    @Test fun nativeMetadataUsesTheSameCamelCaseFieldsAndRoundTrips() {
        val source = Json.encodeToString(request())
        assertTrue(source.contains("\"appName\""))
        assertTrue(source.contains("\"totalBytes\""))
        assertEquals(request(), Json.decodeFromString<HtmlBuildRequest>(source))
    }

    @Test fun protocolTargetsVersionsAndTransferLimitsAreValidatedBeforeBuilding() {
        for (mutate in listOf<(HtmlBuildRequest) -> HtmlBuildRequest>(
            { it.copy(version = 2) }, { it.copy(target = "ios") }, { it.copy(files = 0) },
            { it.copy(files = HTML_BUILD_MAX_FILES + 1) }, { it.copy(totalBytes = HTML_BUILD_MAX_BYTES + 1) },
            { it.copy(iconBytes = 16L * 1024 * 1024 + 1) }, { it.copy(versionCode = 0) },
        )) assertThrows(IllegalArgumentException::class.java) { mutate(request()) }
    }

    @Test fun pathsCannotEscapeThePrivateJobOrChangeSeparatorSemantics() {
        for (path in listOf("", "/outside", "../outside", "a/../../outside", "a\\outside", "a//b", "a/./b", "nul\u0000file")) {
            assertThrows(path, IllegalArgumentException::class.java) { htmlBuildRelativePath(path) }
        }
        assertThrows(IllegalArgumentException::class.java) { htmlBuildRelativePath(List(65) { "a" }.joinToString("/")) }
        assertThrows(IllegalArgumentException::class.java) { htmlBuildRelativePath("文".repeat(683)) }
        assertEquals("页面/assets/main.js", htmlBuildRelativePath("页面/assets/main.js"))
        assertEquals("pages/start.html", request().copy(entry = "pages/start.html").entry)
    }

    @Test fun applicationNamesDoNotBecomeOutputPathsOrWindowsDeviceNames() {
        for (name in listOf("../outside", ".", "..", "app/child", "app\\child", "app:", "name.", " leading")) {
            assertThrows(name, IllegalArgumentException::class.java) { request().copy(appName = name) }
        }
        assertThrows(IllegalArgumentException::class.java) { request().copy(target = "exe", appName = "CON") }
        assertEquals("exe", request().copy(target = "exe", appName = "Demo").target)
    }
}
