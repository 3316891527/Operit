package com.ai.assistance.operit.ui.common.markdown

import android.content.Context
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.ai.assistance.operit.R
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val MERMAID_ASSET_PATH = "/assets/markdown/mermaid/"
private const val MERMAID_PREVIEW_URL = "https://appassets.androidplatform.net${MERMAID_ASSET_PATH}preview.html"

/** 聊天与工作区共用离线图表页面，源码只通过 JSON 参数传递。 */
@Composable
fun MermaidRenderer(code: String, modifier: Modifier = Modifier) {
    val errorMessage = stringResource(R.string.markdown_mermaid_render_failed)
    val loadingMessage = stringResource(R.string.loading)
    val payload = remember(code, errorMessage, loadingMessage) {
        JSONObject()
            .put("code", code)
            .put("errorMessage", errorMessage)
            .put("loadingMessage", loadingMessage)
            .toString()
    }
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    AndroidView(
        factory = { context -> MermaidWebView(context) },
        modifier = modifier.nestedScroll(nestedScrollInterop),
        update = { view -> view.render(payload) },
        onReset = null,
        onRelease = { view ->
            view.stopLoading()
            view.onPause()
            view.removeAllViews()
            view.destroy()
        },
    )
}

internal class MermaidWebView(context: Context) : WebView(context) {
    private var pageReady = false
    private var payload = ""
    private var renderedPayload = ""

    init {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        val allowedPaths = setOf("preview.html", "renderer.js", "mermaid.min.js")
            .map { "$MERMAID_ASSET_PATH$it" }.toSet()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        setBackgroundColor(android.graphics.Color.rgb(30, 30, 30))
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
                    uri.port == -1 && uri.path in allowedPaths
                ) {
                    return assetLoader.shouldInterceptRequest(uri)
                }
                // 图表只使用内置资源，外部链接和其他应用 assets 都不能作为页面资源读取。
                return WebResourceResponse(
                    "text/plain", "UTF-8", 403, "Forbidden", emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            @Deprecated("兼容旧版 WebView 的导航回调")
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = true

            override fun onPageFinished(view: WebView, url: String) {
                if (url != MERMAID_PREVIEW_URL) return
                pageReady = true
                renderIfReady()
            }
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        loadUrl(MERMAID_PREVIEW_URL)
    }

    fun render(payload: String) {
        this.payload = payload
        renderIfReady()
    }

    private fun renderIfReady() {
        if (!pageReady || payload.isEmpty() || payload == renderedPayload) return
        renderedPayload = payload
        evaluateJavascript("window.renderMermaid($payload)", null)
    }
}
