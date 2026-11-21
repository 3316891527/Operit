package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.voice.VoiceHoldAction
import com.ai.assistance.operit.core.chat.voice.VoiceHoldPhase
import com.ai.assistance.operit.core.chat.voice.VoiceMessageData
import com.ai.assistance.operit.core.chat.voice.VoiceMessageSendResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** 同窗口录音层保留原按钮的指针流，真实松手才结束本次录音。 */
@Composable
internal fun VoiceRecordingOverlay(
    gesture: VoiceRecordingGestureController,
    onDismiss: () -> Unit,
    onSend: suspend (VoiceMessageData, () -> Unit) -> VoiceMessageSendResult,
    onUseText: (String) -> Boolean,
) {
    val context = LocalContext.current
    val recordingId = remember { gesture.state.id }
    val session = remember { VoiceMessageSession(context.applicationContext) }
    val state by session.state.collectAsState()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestSend by rememberUpdatedState(onSend)
    val latestUseText by rememberUpdatedState(onUseText)
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var handledAction by remember { mutableStateOf<VoiceHoldAction?>(null) }
    var rejected by remember { mutableStateOf<String?>(null) }

    fun dismiss() {
        gesture.cancel(recordingId)
        session.close()
        latestDismiss()
    }
    fun useText() {
        val message = state.message ?: return
        if (latestUseText(message.transcript)) latestDismiss()
        else rejected = context.getString(R.string.voice_message_standalone)
    }
    fun submit() {
        val message = state.message ?: return
        if (sending) return
        rejected = null
        sending = true
        session.beginSend()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                when (latestSend(message, session::retainSentRecording)) {
                    VoiceMessageSendResult.SENT -> latestDismiss()
                    VoiceMessageSendResult.CONFIGURATION_REJECTED -> dismiss()
                    VoiceMessageSendResult.REJECTED -> rejected = context.getString(R.string.voice_message_standalone)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                session.sendFailed(error)
            } finally {
                session.finishSend()
                sending = false
            }
        }
    }
    LaunchedEffect(session) {
        if (gesture.state.id == recordingId &&
            (gesture.state.phase == VoiceHoldPhase.HOLDING || gesture.state.phase == VoiceHoldPhase.RELEASED)) session.start()
    }
    // 录音层与原长按共用窗口，常亮、导航栏和侧边手势排除仍由原会话持有。
    VoiceRecordingWindowEffect(active = state.phase == VoiceMessagePhase.PREPARING ||
        state.phase == VoiceMessagePhase.RECORDING ||
        (gesture.state.phase == VoiceHoldPhase.HOLDING && state.phase == VoiceMessagePhase.READY))
    VoiceRecordingInterruptionEffect(::dismiss)
    DisposableEffect(session) {
        onDispose { session.close() }
    }
    LaunchedEffect(gesture.state.phase, state.phase) {
        val hold = gesture.state
        if (hold.id != recordingId) return@LaunchedEffect
        if (hold.phase == VoiceHoldPhase.CANCELLED ||
            (hold.phase == VoiceHoldPhase.RELEASED && hold.action == VoiceHoldAction.CANCEL)) {
            dismiss()
        } else if (hold.phase == VoiceHoldPhase.RELEASED) {
            session.finish()
            if (state.phase == VoiceMessagePhase.READY) {
                val action = gesture.takeReleasedAction()
                handledAction = action
                when (action) {
                    VoiceHoldAction.SEND -> submit()
                    VoiceHoldAction.TRANSCRIBE -> session.transcribe()
                    VoiceHoldAction.CANCEL -> dismiss()
                    null -> Unit
                }
            }
        } else if (state.phase == VoiceMessagePhase.EDITING) {
            useText()
        }
    }
    VoiceRecordingLayer {
        val error = state.error ?: rejected
        val retry: (() -> Unit)? = when {
            error == null || state.recording == null -> null
            handledAction == VoiceHoldAction.TRANSCRIBE -> {
                { rejected = null; if (state.phase == VoiceMessagePhase.EDITING) useText() else session.transcribe() }
            }
            handledAction == VoiceHoldAction.SEND -> ::submit
            else -> null
        }
        VoiceRecordingOverlayContent(
            state = state, gesture = gesture, sending = sending, errorMessage = error,
            onDismiss = ::dismiss, onRetry = retry,
            retryLabel = context.getString(if (handledAction == VoiceHoldAction.TRANSCRIBE)
                R.string.voice_message_retry_transcribe else R.string.voice_message_send),
        )
    }
}