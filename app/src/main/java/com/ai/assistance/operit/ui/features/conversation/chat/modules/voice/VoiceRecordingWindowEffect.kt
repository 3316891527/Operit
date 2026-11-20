package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

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

/** 仅在录音准备或录制期间持有常亮；浮层关闭和所有结束路径都会恢复。 */
@Composable
internal fun VoiceRecordingWindowEffect(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        val previousKeepScreenOn = view.keepScreenOn
        val activity = ActivityLifecycleManager.getCurrentActivity()
        val window = activity?.window
        val insets = window?.decorView?.let(ViewCompat::getRootWindowInsets)
        val controller = if (window != null) WindowCompat.getInsetsController(window, window.decorView) else null
        val previousBehavior = controller?.systemBarsBehavior
        val navigationWasVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars())
        if (active) {
            view.keepScreenOn = true
            if (controller != null && navigationWasVisible != null) {
                // 沉浸模式和排除区只能减少误触，不能禁用系统强制保留的 Home 手势。
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
        onDispose {
            if (active) {
                view.keepScreenOn = previousKeepScreenOn
                if (controller != null && previousBehavior != null && navigationWasVisible != null) {
                    controller.systemBarsBehavior = previousBehavior
                    if (navigationWasVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
                    else controller.hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }
}