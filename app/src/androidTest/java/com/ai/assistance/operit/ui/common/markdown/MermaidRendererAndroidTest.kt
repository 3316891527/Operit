package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MermaidRendererAndroidTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var browser: MermaidWebView

    @Test
    fun bundledBrowserRendersCommonDiagramTypesWithoutNetworkAccess() {
        val sources = listOf(
            "flowchart LR\nA[开始] --> B[结束]",
            "sequenceDiagram\nAlice->>Bob: Hello",
            "classDiagram\nAnimal <|-- Duck",
            "stateDiagram-v2\n[*] --> Ready\nReady --> [*]",
            "erDiagram\nCUSTOMER ||--o{ ORDER : places",
            "gantt\ntitle Plan\ndateFormat YYYY-MM-DD\nsection Work\nTask :2026-01-01, 1d",
            "pie\ntitle Plan\n\"Done\" : 60\n\"Remaining\" : 40",
            "mindmap\n  root((Plan))\n    Done\n    Remaining",
        )
        val payload = mutableStateOf(buildPayload(sources.first()))
        compose.setContent {
            AndroidView(
                factory = { context -> MermaidWebView(context).also { browser = it } },
                modifier = Modifier.width(320.dp).height(300.dp),
                update = { it.render(payload.value) },
                onRelease = { it.destroy() },
            )
        }
        for ((index, source) in sources.withIndex()) {
            compose.runOnIdle { payload.value = buildPayload(source) }
            val id = JSONObject.quote("mermaid-diagram-${index + 1}")
            compose.waitUntil(30_000) {
                javascript("document.querySelector('#diagram svg')?.id === $id && document.getElementById('status').hidden") == "true"
            }
        }
        compose.runOnIdle {
            assertTrue(browser.settings.blockNetworkLoads)
            assertEquals(false, browser.settings.allowFileAccess)
            assertEquals(false, browser.settings.allowContentAccess)
        }
    }

    @Test
    fun invalidDiagramShowsAnErrorAndSubsequentValidSourceCanRender() {
        val payload = mutableStateOf(buildPayload("flowchart LR\nA -->"))
        compose.setContent {
            AndroidView(
                factory = { context -> MermaidWebView(context).also { browser = it } },
                modifier = Modifier.width(320.dp).height(300.dp),
                update = { it.render(payload.value) },
                onRelease = { it.destroy() },
            )
        }
        compose.waitUntil(30_000) {
            javascript("document.getElementById('status')?.className === 'error'") == "true"
        }
        assertEquals("true", javascript("document.getElementById('status').textContent.startsWith('Diagram error')"))
        compose.runOnIdle { payload.value = buildPayload("flowchart LR\nA[Fixed] --> B[Done]") }
        compose.waitUntil(30_000) {
            javascript("document.querySelector('#diagram svg')?.id === 'mermaid-diagram-2' && document.getElementById('status').hidden") == "true"
        }
    }

    private fun buildPayload(source: String): String = JSONObject()
        .put("code", source)
        .put("loadingMessage", "Loading")
        .put("errorMessage", "Diagram error")
        .toString()

    private fun javascript(script: String): String {
        val result = AtomicReference("")
        val ready = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            browser.evaluateJavascript(script) {
                result.set(it)
                ready.countDown()
            }
        }
        assertTrue("WebView 未返回 JavaScript 结果", ready.await(3, TimeUnit.SECONDS))
        return result.get()
    }
}
