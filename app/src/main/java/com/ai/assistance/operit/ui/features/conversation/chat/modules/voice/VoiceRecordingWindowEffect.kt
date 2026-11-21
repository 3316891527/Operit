package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ai.assistance.operit.core.application.ActivityLifecycleManager

/** 新会话创建和录音浮层共用中断规则，失焦、退后台和返回都只能取消。 */
@Composable
internal fun VoiceRecordingInterruptionEffect(onInterrupted: () -> Unit) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestInterrupted by rememberUpdatedState(onInterrupted)
    DisposableEffect(view, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) latestInterrupted()
        }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) latestInterrupted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val tree = view.viewTreeObserver
            if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focusListener)
        }
    }
    BackHandler { latestInterrupted() }
}

/** 录音期间临时持有原窗口的常亮、沉浸和手势排除，结束后恢复原值。 */
@Composable
internal fun VoiceRecordingWindowEffect(active: Boolean) {
    if (!active) return
    val view = LocalView.current
    val exclusionView = view.rootView
    DisposableEffect(view, exclusionView) {
        val previousKeepScreenOn = view.keepScreenOn
        val previousExclusions = ViewCompat.getSystemGestureExclusionRects(exclusionView).map(::Rect)
        val activity = ActivityLifecycleManager.getCurrentActivity()
        val window = activity?.window
        val insets = window?.decorView?.let(ViewCompat::getRootWindowInsets)
        val controller = if (window != null) WindowCompat.getInsetsController(window, window.decorView) else null
        val previousBehavior = controller?.systemBarsBehavior
        val navigationWasVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars())
        fun updateExclusionBounds() {
            if (exclusionView.width > 0 && exclusionView.height > 0) {
                // 原始长按属于主窗口；排除区必须延伸到左右边缘，不能只覆盖浮层按钮。
                ViewCompat.setSystemGestureExclusionRects(exclusionView,
                    listOf(Rect(0, 0, exclusionView.width, exclusionView.height)))
            }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateExclusionBounds() }
        exclusionView.addOnLayoutChangeListener(layoutListener)
        updateExclusionBounds()
        view.keepScreenOn = true
        if (controller != null && navigationWasVisible != null) {
            // 排除请求受系统裁定；普通应用不能禁用强制 Home、最近任务或厂商独立手势。
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
        onDispose {
            exclusionView.removeOnLayoutChangeListener(layoutListener)
            ViewCompat.setSystemGestureExclusionRects(exclusionView, previousExclusions)
            view.keepScreenOn = previousKeepScreenOn
            if (controller != null && previousBehavior != null && navigationWasVisible != null) {
                controller.systemBarsBehavior = previousBehavior
                if (navigationWasVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
                else controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }
}
