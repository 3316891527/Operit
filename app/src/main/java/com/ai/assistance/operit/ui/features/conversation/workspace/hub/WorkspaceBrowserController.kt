package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.core.tools.builtin.ToolGetter
import com.ai.assistance.operit.core.tools.builtin.web.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.activateSessionOnMain
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.createBrowserHostCallbacks
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.createSessionTabOnMain
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.refreshSessionUiOnMain
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.workbenchBrowserHost

/** 工作台与浏览器工具共享会话；仅切换显示容器，不复制 WebView 或浏览数据。 */
internal class WorkspaceBrowserController(context: Context) {
    private val context = context.applicationContext
    private val tools = ToolGetter.getBrowserSessionTools(this.context)
    val state = StandardBrowserSessionTools.workbenchState

    fun open(sessionId: String? = null, newTab: Boolean = false) {
        val host = tools.workbenchBrowserHost()
        host.beginEmbedded()
        if (newTab || state.value.tabs.isEmpty()) {
            tools.createSessionTabOnMain(context, "about:blank")
        } else if (sessionId != null) {
            tools.activateSessionOnMain(sessionId)
        }
        tools.refreshSessionUiOnMain()
    }

    fun close(sessionId: String) {
        tools.createBrowserHostCallbacks(context).onCloseTab(sessionId)
    }

    @Composable
    fun Content(onReturn: () -> Unit, modifier: Modifier = Modifier) {
        val host = tools.workbenchBrowserHost()
        DisposableEffect(host) {
            host.beginEmbedded()
            tools.refreshSessionUiOnMain()
            onDispose { host.endEmbedded() }
        }
        host.Content(modifier, embedded = true, onMinimize = onReturn)
    }
}