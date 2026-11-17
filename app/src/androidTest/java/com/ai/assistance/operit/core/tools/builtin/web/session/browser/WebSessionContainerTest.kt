package com.ai.assistance.operit.core.tools.builtin.web.session.browser

import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebSessionContainerTest {
    @Test fun oneWebViewCanMoveBetweenWorkbenchAndFloatingContainers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val webView = WebView(context)
            val floating = FrameLayout(context)
            val workbench = FrameLayout(context)
            val host = WebSessionWebViewHost()
            try {
                host.setActiveWebView(webView, null, null)
                host.attachContainer(floating)
                assertSame(floating, webView.parent)
                host.attachContainer(workbench)
                assertEquals(0, floating.childCount)
                assertSame(workbench, webView.parent)
                // 旧容器的延迟卸载不能解绑已经挂到工作台的同一 WebView。
                host.detachContainer(floating)
                assertSame(webView, host.currentWebView())
                host.clear()
                assertNull(webView.parent)
                host.setActiveWebView(webView, null, null)
                host.attachContainer(floating)
                assertSame(floating, webView.parent)
            } finally {
                host.clear()
                webView.destroy()
            }
        }
    }
}
