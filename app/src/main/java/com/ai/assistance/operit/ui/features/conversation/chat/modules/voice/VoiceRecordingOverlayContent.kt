package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.audio.VoiceMessageRecorder
import com.ai.assistance.operit.core.chat.voice.VoiceHoldAction
import com.ai.assistance.operit.core.chat.voice.VoiceHoldBounds
import com.ai.assistance.operit.core.chat.voice.VoiceHoldPhase
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

@Composable
internal fun VoiceRecordingOverlayContent(
    state: VoiceMessageState,
    gesture: VoiceRecordingGestureController,
    sending: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?,
    retryLabel: String,
) {
    val holding = gesture.state.phase == VoiceHoldPhase.HOLDING
    val action = gesture.state.action
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backdrop = if (dark) Color.Black else Color.White
    val foreground = if (dark) Color.White else Color.Black
    val bubbleColor by animateColorAsState(
        if (action == VoiceHoldAction.CANCEL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "voice-recording-color",
    )
    val bubbleContent = if (action == VoiceHoldAction.CANCEL) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    val busy = sending || state.phase == VoiceMessagePhase.TRANSCRIBING || state.phase == VoiceMessagePhase.EDITING
    val preparing = state.phase == VoiceMessagePhase.PREPARING
    val finishing = !holding && state.phase == VoiceMessagePhase.RECORDING
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to backdrop.copy(alpha = if (dark) 0.15f else 0.04f),
                0.52f to backdrop.copy(alpha = 0.82f),
                0.78f to backdrop,
                1f to backdrop,
            ),
        ).systemGestureExclusion().windowInsetsPadding(WindowInsets.safeGestures)
            .pointerInput(gesture) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // 原长按仍在输入按钮上；浮层收到的新增触点只会取消本次录音。
                    if (gesture.state.phase == VoiceHoldPhase.HOLDING) {
                        down.consume()
                        gesture.cancel()
                    }
                }
            },
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 260.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (onRetry != null) Button(onClick = onRetry) { Text(retryLabel) }
            } else if (busy || preparing || finishing) {
                CircularProgressIndicator(Modifier.size(36.dp))
                Text(
                    stringResource(when {
                        sending -> R.string.voice_message_sending
                        state.phase == VoiceMessagePhase.TRANSCRIBING || state.phase == VoiceMessagePhase.EDITING -> R.string.voice_message_transcribing
                        finishing -> R.string.voice_message_finishing
                        else -> R.string.voice_message_preparing
                    }),
                    color = foreground,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.animateContentSize().widthIn(max = 360.dp)
                            .fillMaxWidth(if (action == VoiceHoldAction.TRANSCRIBE) 0.9f else 0.65f)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.voice_message_send)) {
                                gesture.releaseTo(VoiceHoldAction.SEND)
                            },
                        color = bubbleColor,
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(voiceDurationLabel(state.elapsedMs), color = bubbleContent, style = MaterialTheme.typography.labelLarge)
                            VoiceWaveform(state.levels, Modifier.fillMaxWidth().height(28.dp),
                                live = state.phase == VoiceMessagePhase.RECORDING, color = bubbleContent)
                        }
                    }
                    Canvas(Modifier.align(Alignment.BottomCenter).offset(y = 8.dp).size(20.dp, 12.dp)) {
                        drawPath(Path().apply {
                            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
                        }, bubbleColor)
                    }
                }
                Text(
                    stringResource(when (action) {
                        VoiceHoldAction.SEND -> R.string.voice_message_release_send
                        VoiceHoldAction.CANCEL -> R.string.voice_message_release_cancel
                        VoiceHoldAction.TRANSCRIBE -> R.string.voice_message_release_transcribe
                    }),
                    color = foreground, style = MaterialTheme.typography.titleMedium,
                )
                if (holding && state.phase == VoiceMessagePhase.READY) {
                    Text(stringResource(R.string.voice_message_hold_limit, VoiceMessageRecorder.MAX_DURATION_MS / 1000), color = foreground.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (holding && errorMessage == null && !busy) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                VoiceRecordingTarget(gesture, VoiceHoldAction.CANCEL, Icons.Default.Close,
                    stringResource(R.string.cancel), foreground, Modifier.weight(1f))
                VoiceRecordingTarget(gesture, VoiceHoldAction.TRANSCRIBE, Icons.Default.Description,
                    stringResource(R.string.voice_message_transcribe), foreground, Modifier.weight(1f))
            }
            Text(stringResource(R.string.voice_message_hold_slide_hint), color = foreground.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 48.dp))
        } else {
            TextButton(onClick = onDismiss, enabled = !sending,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp)) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun VoiceRecordingTarget(
    gesture: VoiceRecordingGestureController,
    action: VoiceHoldAction,
    icon: ImageVector,
    label: String,
    foreground: Color,
    modifier: Modifier,
) {
    val view = LocalView.current
    val recordingId = gesture.state.id
    val selected = gesture.state.action == action
    val color by animateColorAsState(
        if (selected) {
            if (action == VoiceHoldAction.CANCEL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        } else foreground.copy(alpha = 0.08f),
        label = "voice-target-color",
    )
    val contentColor = if (selected) {
        if (action == VoiceHoldAction.CANCEL) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    } else foreground
    Surface(
        modifier = modifier.height(80.dp)
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.voiceScreenPoint(view, Offset.Zero)
                val bottomRight = coordinates.voiceScreenPoint(view, Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                gesture.setTarget(action, VoiceHoldBounds(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y), recordingId)
            }
            .clickable(role = Role.Button) { gesture.releaseTo(action) },
        shape = CircleShape,
        color = color,
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Icon(icon, null, Modifier.size(22.dp), tint = contentColor)
            Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}