package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import android.view.MotionEvent
import android.webkit.WebView

internal fun WebView.installWorkspaceTouchInterceptor() {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        false
    }
}

internal fun WebView.releaseWorkspaceWebView() {
    stopLoading()
    removeAllViews()
    destroy()
}
