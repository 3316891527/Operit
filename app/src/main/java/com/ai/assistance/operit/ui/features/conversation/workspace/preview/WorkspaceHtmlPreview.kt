package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import android.content.Intent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.features.conversation.workspace.key
import com.ai.assistance.operit.ui.features.conversation.workspace.OpenFileInfo
import com.ai.assistance.operit.ui.features.conversation.workspace.search.FileSearchBar
import java.io.File

/** 使用 WebView 的页面查找，只匹配渲染文本；查询重组不会重新加载文件。 */
@Composable
internal fun WorkspaceHtmlPreview(
    fileInfo: OpenFileInfo,
    searchVisible: Boolean,
    onCloseSearch: () -> Unit,
    onFileChooserRequest: ((Intent, (Int, Intent?) -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var view by remember { mutableStateOf<WebView?>(null) }
    var query by remember(fileInfo.key) { mutableStateOf("") }
    var count by remember { mutableIntStateOf(0) }
    var active by remember { mutableIntStateOf(-1) }
    var searching by remember { mutableStateOf(false) }
    var pageReady by remember { mutableStateOf(false) }
    var pageRevision by remember { mutableIntStateOf(0) }
    val latestSearchVisible = rememberUpdatedState(searchVisible)
    val latestQuery = rememberUpdatedState(query)
    val latestChooser = rememberUpdatedState(onFileChooserRequest)
    val handler = remember(context) {
        WebViewHandler(context).apply {
            this.onFileChooserRequest = { intent, callback -> latestChooser.value?.invoke(intent, callback) }
            onPageStarted = { pageReady = false; count = 0; active = -1 }
            onPageFinished = { pageReady = true; pageRevision++ }
        }
    }
    // 非 Compose 状态只记录已提交页面，避免重组时重复触发导航和脚本。
    val loadedDocument = remember { arrayOf<Pair<String, String>?>(null) }
    BackHandler(searchVisible, onCloseSearch)
    LaunchedEffect(view, query, searchVisible, pageReady, pageRevision) {
        val webView = view ?: return@LaunchedEffect
        count = 0
        active = -1
        webView.setFindListener(null)
        searching = searchVisible && query.isNotEmpty()
        if (!searching || !pageReady) {
            webView.clearMatches()
            return@LaunchedEffect
        }
        val requestedQuery = query
        webView.setFindListener { ordinal, total, done ->
            if (done && latestSearchVisible.value && latestQuery.value == requestedQuery && pageReady) {
                count = total
                active = if (total == 0) -1 else ordinal
                searching = false
            }
        }
        // 原生异步查询会取消上一轮查找，非空查询之间不清空高亮回调。
        webView.findAllAsync(requestedQuery)
    }
    Column(modifier.fillMaxSize()) {
        AndroidView(
            factory = { webContext ->
                WebView(webContext).apply {
                    installWorkspaceTouchInterceptor()
                    handler.configureWebView(this, WebViewHandler.WebViewMode.WORKSPACE, "workspace_file_preview_${fileInfo.key}")
                    handler.currentWebView = this
                    view = this
                }
            },
            update = { webView ->
                val document = fileInfo.path to fileInfo.content
                if (loadedDocument[0] != document) {
                    loadedDocument[0] = document
                    webView.loadDataWithBaseURL("file://${File(fileInfo.path).parent}/", fileInfo.content, "text/html", "UTF-8", null)
                }
            },
            onRelease = { webView ->
                webView.setFindListener(null)
                handler.currentWebView = null
                if (view === webView) view = null
                webView.releaseWorkspaceWebView()
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (searchVisible) FileSearchBar(query, { if (query != it) { query = it; searching = it.isNotEmpty() } }, count, active,
            onPrevious = { if (count > 0 && !searching) view?.findNext(false) },
            onNext = { if (count > 0 && !searching) view?.findNext(true) },
            onClose = onCloseSearch, searching = searching)
    }
}
