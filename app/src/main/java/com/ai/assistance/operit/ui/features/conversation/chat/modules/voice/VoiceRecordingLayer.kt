package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

private class VoiceRecordingLayerEntry(val content: @Composable () -> Unit)

private class VoiceRecordingLayerState {
    var entry by mutableStateOf<VoiceRecordingLayerEntry?>(null)
}

private val LocalVoiceRecordingLayer = staticCompositionLocalOf<VoiceRecordingLayerState> {
    error("录音视觉层必须位于 VoiceRecordingOverlayHost 内")
}

/** 在主题根部绘制录音层，覆盖输入栏并保留原按钮已经持有的指针流。 */
@Composable
internal fun VoiceRecordingOverlayHost(content: @Composable () -> Unit) {
    val host = remember { VoiceRecordingLayerState() }
    CompositionLocalProvider(LocalVoiceRecordingLayer provides host) {
        Box(Modifier.fillMaxSize()) {
            // 视觉遮住输入栏时也隐藏其无障碍节点，避免读屏仍操作到下面的按钮。
            Box(Modifier.fillMaxSize().then(
                if (host.entry != null) Modifier.clearAndSetSemantics {} else Modifier,
            )) { content() }
            host.entry?.let { entry ->
                key(entry) { entry.content() }
            }
        }
    }
}

/** 生命周期仍属于原录音会话，颜色、字体和页面局部设置也从原位置传递。 */
@Composable
internal fun VoiceRecordingLayer(content: @Composable () -> Unit) {
    val host = LocalVoiceRecordingLayer.current
    val latestContent by rememberUpdatedState(content)
    val latestLocals by rememberUpdatedState(currentCompositionLocalContext)
    val entry = remember(host) {
        VoiceRecordingLayerEntry {
            CompositionLocalProvider(latestLocals) { latestContent() }
        }
    }
    DisposableEffect(host, entry) {
        check(host.entry == null) { "同一窗口不能同时承载两次录音" }
        host.entry = entry
        onDispose {
            if (host.entry === entry) host.entry = null
        }
    }
}
